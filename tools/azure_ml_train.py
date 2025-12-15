#!/usr/bin/env python3
"""
Script para entrenar modelo de cultivos colombianos en Azure ML.
El entrenamiento corre en la nube y continúa aunque cierres VS Code.

Uso:
    python azure_ml_train.py --submit    # Enviar job a Azure
    python azure_ml_train.py --status    # Ver estado del job
    python azure_ml_train.py --download  # Descargar resultados
"""

import argparse
import os
import sys
from pathlib import Path

def check_azure_sdk():
    """Verificar si el SDK de Azure ML está instalado."""
    try:
        from azure.ai.ml import MLClient
        from azure.identity import DefaultAzureCredential
        return True
    except ImportError:
        print("❌ Azure ML SDK no instalado. Instalando...")
        os.system("pip install azure-ai-ml azure-identity")
        return False

def get_ml_client():
    """Obtener cliente de Azure ML."""
    from azure.ai.ml import MLClient
    from azure.identity import DeviceCodeCredential
    from dotenv import load_dotenv
    
    # Cargar variables desde .env.azure
    load_dotenv('.env.azure')
    
    # Configuración - EDITAR CON TUS VALORES o usa .env.azure
    subscription_id = os.environ.get("AZURE_SUBSCRIPTION_ID", "<tu-subscription-id>")
    resource_group = os.environ.get("AZURE_RESOURCE_GROUP", "<tu-resource-group>")
    workspace_name = os.environ.get("AZURE_WORKSPACE_NAME", "<tu-workspace-name>")
    tenant_id = os.environ.get("AZURE_TENANT_ID", None)
    
    if "<tu-" in subscription_id:
        print("\n⚠️  Configura tus credenciales de Azure:")
        print("   export AZURE_SUBSCRIPTION_ID='xxx'")
        print("   export AZURE_RESOURCE_GROUP='xxx'")
        print("   export AZURE_WORKSPACE_NAME='xxx'")
        print("\n   O edita el archivo .env.azure")
        sys.exit(1)
    
    print(f"📋 Conectando a Azure ML:")
    print(f"   Subscription: {subscription_id[:8]}...")
    print(f"   Resource Group: {resource_group}")
    print(f"   Workspace: {workspace_name}")
    if tenant_id:
        print(f"   Tenant: {tenant_id[:8]}...")
    
    # Usar Device Code que es más confiable
    print("\n🔐 Autenticación por código de dispositivo...")
    credential = DeviceCodeCredential(tenant_id=tenant_id)
    
    return MLClient(credential, subscription_id, resource_group, workspace_name)

def submit_training_job():
    """Enviar job de entrenamiento a Azure ML."""
    from azure.ai.ml import command, Input
    from azure.ai.ml.entities import Environment
    
    ml_client = get_ml_client()
    
    print("🚀 Preparando job de entrenamiento...")
    
    # Crear entorno personalizado con TensorFlow
    env = Environment(
        name="tensorflow-training",
        description="Entorno con TensorFlow para entrenamiento",
        conda_file="./training_script/conda.yaml",
        image="mcr.microsoft.com/azureml/openmpi4.1.0-ubuntu20.04:latest"
    )
    
    # Crear el job
    job = command(
        code="./training_script",  # Carpeta con el script
        command="python train_colombia.py",
        environment=env,
        compute="cpu-train",  # Cluster CPU
        display_name="colombia-crop-disease-training",
        description="Entrenamiento de modelo de enfermedades de cultivos colombianos",
        experiment_name="plant-disease-classification",
        resources={
            "instance_count": 1,
        },
    )
    
    # Enviar
    returned_job = ml_client.jobs.create_or_update(job)
    
    print(f"\n✅ Job enviado exitosamente!")
    print(f"   Nombre: {returned_job.name}")
    print(f"   Estado: {returned_job.status}")
    print(f"\n🔗 Ver en Azure ML Studio:")
    print(f"   {returned_job.studio_url}")
    print(f"\n⏳ El entrenamiento continuará aunque cierres VS Code.")
    print(f"   Usa 'python azure_ml_train.py --status' para ver el progreso.")
    
    # Guardar ID del job
    with open(".azure_job_id", "w") as f:
        f.write(returned_job.name)
    
    return returned_job

def check_job_status():
    """Ver estado del job actual."""
    ml_client = get_ml_client()
    
    if not os.path.exists(".azure_job_id"):
        print("❌ No hay job activo. Usa --submit primero.")
        return
    
    with open(".azure_job_id") as f:
        job_name = f.read().strip()
    
    job = ml_client.jobs.get(job_name)
    
    print(f"\n📊 Estado del Job: {job_name}")
    print(f"   Status: {job.status}")
    print(f"   Display Name: {job.display_name}")
    
    if job.status == "Completed":
        print(f"\n✅ ¡Entrenamiento completado!")
        print(f"   Usa 'python azure_ml_train.py --download' para descargar resultados.")
    elif job.status == "Failed":
        print(f"\n❌ El job falló. Revisa los logs en Azure ML Studio:")
        print(f"   {job.studio_url}")
    elif job.status == "Running":
        print(f"\n⏳ Entrenamiento en progreso...")
        print(f"   Tiempo transcurrido: {job.duration}")
    
    print(f"\n🔗 Ver detalles: {job.studio_url}")

def download_results():
    """Descargar resultados del job completado."""
    ml_client = get_ml_client()
    
    if not os.path.exists(".azure_job_id"):
        print("❌ No hay job activo. Usa --submit primero.")
        return
    
    with open(".azure_job_id") as f:
        job_name = f.read().strip()
    
    job = ml_client.jobs.get(job_name)
    
    if job.status != "Completed":
        print(f"⚠️  El job no está completado. Estado actual: {job.status}")
        return
    
    print(f"📥 Descargando resultados de {job_name}...")
    
    output_dir = Path("./azure_outputs")
    output_dir.mkdir(exist_ok=True)
    
    ml_client.jobs.download(job_name, download_path=str(output_dir), output_name="default")
    
    print(f"\n✅ Resultados descargados en: {output_dir}")
    print(f"\n📁 Archivos:")
    for f in output_dir.rglob("*"):
        if f.is_file():
            size = f.stat().st_size / (1024*1024)
            print(f"   {f.relative_to(output_dir)}: {size:.2f} MB")

def create_training_script():
    """Crear el script de entrenamiento para Azure ML."""
    script_dir = Path("training_script")
    script_dir.mkdir(exist_ok=True)
    
    # El script de entrenamiento
    script_content = '''#!/usr/bin/env python3
"""Script de entrenamiento para Azure ML Job."""

import tensorflow as tf
import time
import os
import json
import shutil
import urllib.request
import zipfile
from pathlib import Path

# Configuración
CONFIG = {
    'IMG_SIZE': 224,
    'EPOCHS_PHASE1': 25,
    'EPOCHS_PHASE2': 35,
    'LEARNING_RATE_1': 1e-3,
    'LEARNING_RATE_2': 1e-5,
    'LABEL_SMOOTHING': 0.1,
    'DROPOUT_RATE': 0.4,
    'L2_REG': 0.01,
}

# Detectar GPU
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    for gpu in gpus:
        tf.config.experimental.set_memory_growth(gpu, True)
    BATCH_SIZE = 64 if len(gpus) > 0 else 32
    tf.keras.mixed_precision.set_global_policy('mixed_float16')
    print(f"✅ {len(gpus)} GPU(s) detectada(s), batch_size={BATCH_SIZE}")
else:
    BATCH_SIZE = 16
    print("⚠️ Sin GPU, usando CPU")

# Directorios
UNIFIED_DIR = Path('colombia_crops_dataset')
UNIFIED_DIR.mkdir(exist_ok=True)
OUTPUT_DIR = Path('./outputs')
OUTPUT_DIR.mkdir(exist_ok=True)

print("📥 Descargando datasets...")

# 1. Descargar PlantVillage
PLANTVILLAGE_URL = "https://github.com/spMohanty/PlantVillage-Dataset/archive/refs/heads/master.zip"
if not os.path.exists("PlantVillage-Dataset-master"):
    urllib.request.urlretrieve(PLANTVILLAGE_URL, "plantvillage.zip")
    with zipfile.ZipFile("plantvillage.zip", 'r') as z:
        z.extractall(".")
    os.remove("plantvillage.zip")

# Procesar PlantVillage
COLOMBIAN_CROPS = ['Potato', 'Corn', 'Tomato', 'Pepper', 'Orange']
CROP_TRANSLATIONS = {'Potato': 'Papa', 'Corn': 'Maiz', 'Tomato': 'Tomate', 'Pepper': 'Pimiento', 'Orange': 'Naranja'}
DISEASE_TRANSLATIONS = {
    'healthy': 'Saludable', 'Early_blight': 'Tizon_temprano', 'Late_blight': 'Tizon_tardio',
    'Common_rust_': 'Roya_comun', 'Northern_Leaf_Blight': 'Tizon_norteno',
    'Bacterial_spot': 'Mancha_bacteriana', 'Septoria_leaf_spot': 'Septoriosis',
}

PV_DIR = Path("PlantVillage-Dataset-master/raw/color")
for folder in sorted(os.listdir(PV_DIR)):
    for crop in COLOMBIAN_CROPS:
        if folder.startswith(crop):
            parts = folder.split('___')
            crop_name = CROP_TRANSLATIONS.get(parts[0], parts[0])
            disease = parts[1] if len(parts) > 1 else 'healthy'
            disease_name = DISEASE_TRANSLATIONS.get(disease, disease.replace('_', ' '))
            new_name = f"{crop_name}___{disease_name}"
            src = PV_DIR / folder
            dst = UNIFIED_DIR / new_name
            if not dst.exists() and src.exists():
                shutil.copytree(src, dst)
                print(f"   ✅ {new_name}: {len(list(dst.glob('*')))} imgs")

# 2. Descargar Cassava (Yuca)
import tensorflow_datasets as tfds
cassava_classes = {0: 'Yuca___Anublo_bacterial', 1: 'Yuca___Rayado_marron', 
                   2: 'Yuca___Acaro_verde', 3: 'Yuca___Mosaico', 4: 'Yuca___Saludable'}

for class_name in cassava_classes.values():
    (UNIFIED_DIR / class_name).mkdir(exist_ok=True)

yuca_count = sum(len(list((UNIFIED_DIR / c).glob('*'))) for c in cassava_classes.values())
if yuca_count < 1000:
    cassava_ds, _ = tfds.load('cassava', with_info=True, as_supervised=True)
    for split in ['train', 'validation', 'test']:
        if split in cassava_ds:
            for i, (image, label) in enumerate(cassava_ds[split]):
                class_name = cassava_classes[int(label)]
                img_path = UNIFIED_DIR / class_name / f"{split}_{i}.jpg"
                tf.io.write_file(str(img_path), tf.io.encode_jpeg(image))

print("✅ Datasets descargados")

# Crear generadores
from tensorflow.keras.preprocessing.image import ImageDataGenerator

IMG_SIZE = CONFIG['IMG_SIZE']
train_datagen = ImageDataGenerator(
    rescale=1./255, rotation_range=40, width_shift_range=0.3, height_shift_range=0.3,
    shear_range=0.2, zoom_range=0.3, horizontal_flip=True, vertical_flip=True,
    brightness_range=[0.6, 1.4], fill_mode='reflect', validation_split=0.15
)
val_datagen = ImageDataGenerator(rescale=1./255, validation_split=0.15)

train_gen = train_datagen.flow_from_directory(str(UNIFIED_DIR), target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE, class_mode='categorical', subset='training', shuffle=True, seed=42)
val_gen = val_datagen.flow_from_directory(str(UNIFIED_DIR), target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE, class_mode='categorical', subset='validation', shuffle=False)

NUM_CLASSES = len(train_gen.class_indices)
print(f"📊 {train_gen.samples} training, {val_gen.samples} validation, {NUM_CLASSES} clases")

# Crear modelo
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout, BatchNormalization
from tensorflow.keras.models import Model
from tensorflow.keras.regularizers import l2

base_model = MobileNetV2(weights='imagenet', include_top=False, input_shape=(IMG_SIZE, IMG_SIZE, 3))
base_model.trainable = False

x = base_model.output
x = GlobalAveragePooling2D()(x)
x = BatchNormalization()(x)
x = Dense(1024, activation='relu', kernel_regularizer=l2(CONFIG['L2_REG']))(x)
x = Dropout(CONFIG['DROPOUT_RATE'])(x)
x = BatchNormalization()(x)
x = Dense(512, activation='relu', kernel_regularizer=l2(CONFIG['L2_REG']))(x)
x = Dropout(CONFIG['DROPOUT_RATE'])(x)
x = Dense(256, activation='relu', kernel_regularizer=l2(CONFIG['L2_REG']))(x)
x = Dropout(0.3)(x)
predictions = Dense(NUM_CLASSES, activation='softmax', dtype='float32')(x)

model = Model(inputs=base_model.input, outputs=predictions)
print(f"🏗️ Modelo: {model.count_params():,} parámetros")

# Entrenar Fase 1
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau, ModelCheckpoint
from tensorflow.keras.losses import CategoricalCrossentropy

loss_fn = CategoricalCrossentropy(label_smoothing=CONFIG['LABEL_SMOOTHING'])
model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=CONFIG['LEARNING_RATE_1']),
              loss=loss_fn, metrics=['accuracy'])

callbacks1 = [
    EarlyStopping(monitor='val_accuracy', patience=5, restore_best_weights=True),
    ReduceLROnPlateau(monitor='val_loss', factor=0.5, patience=2, min_lr=1e-6),
    ModelCheckpoint(str(OUTPUT_DIR / 'best_phase1.keras'), monitor='val_accuracy', save_best_only=True)
]

print("🚀 FASE 1: Entrenando clasificador...")
start = time.time()
history1 = model.fit(train_gen, epochs=CONFIG['EPOCHS_PHASE1'], validation_data=val_gen, callbacks=callbacks1, verbose=1)
phase1_time = time.time() - start
print(f"✅ Fase 1: {phase1_time/60:.1f} min, acc={max(history1.history['val_accuracy']):.2%}")

# Entrenar Fase 2
model.load_weights(str(OUTPUT_DIR / 'best_phase1.keras'))
base_model.trainable = True
for layer in base_model.layers[:100]:
    layer.trainable = False

model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=CONFIG['LEARNING_RATE_2']),
              loss=loss_fn, metrics=['accuracy'])

callbacks2 = [
    EarlyStopping(monitor='val_accuracy', patience=7, restore_best_weights=True),
    ReduceLROnPlateau(monitor='val_loss', factor=0.3, patience=3, min_lr=1e-7),
    ModelCheckpoint(str(OUTPUT_DIR / 'best_phase2.keras'), monitor='val_accuracy', save_best_only=True)
]

print("🔧 FASE 2: Fine-tuning...")
start = time.time()
history2 = model.fit(train_gen, epochs=CONFIG['EPOCHS_PHASE2'], validation_data=val_gen, callbacks=callbacks2, verbose=1)
phase2_time = time.time() - start
final_acc = max(history2.history['val_accuracy'])
print(f"✅ Fase 2: {phase2_time/60:.1f} min, acc={final_acc:.2%}")

# Guardar modelo
model.load_weights(str(OUTPUT_DIR / 'best_phase2.keras'))
model.save(str(OUTPUT_DIR / 'colombia_crop_disease.keras'))

# Convertir a TFLite
tf.keras.mixed_precision.set_global_policy('float32')
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()
with open(OUTPUT_DIR / 'colombia_crop_disease.tflite', 'wb') as f:
    f.write(tflite_model)

# Guardar labels
labels = [{"id": i, "name": name, "crop": name.split('___')[0], 
           "display": name.split('___')[1] if '___' in name else 'Saludable'}
          for i, name in enumerate(train_gen.class_indices.keys())]
with open(OUTPUT_DIR / 'colombia_crop_labels.json', 'w', encoding='utf-8') as f:
    json.dump({"labels": labels}, f, indent=2, ensure_ascii=False)

print(f"""
{'='*60}
🎉 ENTRENAMIENTO COMPLETADO
{'='*60}
   Accuracy: {final_acc:.2%}
   Tiempo total: {(phase1_time + phase2_time)/60:.1f} min
   Clases: {NUM_CLASSES}
   
   Archivos en ./outputs/:
   - colombia_crop_disease.keras
   - colombia_crop_disease.tflite
   - colombia_crop_labels.json
{'='*60}
""")
'''
    
    with open(script_dir / "train_colombia.py", "w") as f:
        f.write(script_content)
    
    # Requirements
    with open(script_dir / "requirements.txt", "w") as f:
        f.write("tensorflow>=2.12\ntensorflow-datasets\nalbumentations\n")
    
    print(f"✅ Script de entrenamiento creado en {script_dir}/")

def main():
    parser = argparse.ArgumentParser(description="Entrenar modelo en Azure ML")
    parser.add_argument("--submit", action="store_true", help="Enviar job de entrenamiento")
    parser.add_argument("--status", action="store_true", help="Ver estado del job")
    parser.add_argument("--download", action="store_true", help="Descargar resultados")
    parser.add_argument("--create-script", action="store_true", help="Crear script de entrenamiento")
    parser.add_argument("--setup", action="store_true", help="Configurar Azure ML (crear compute, etc.)")
    
    args = parser.parse_args()
    
    if not any([args.submit, args.status, args.download, args.create_script, args.setup]):
        parser.print_help()
        print("\n📋 Pasos para entrenar en Azure ML:")
        print("   1. python azure_ml_train.py --create-script  # Crear script")
        print("   2. python azure_ml_train.py --setup          # Configurar Azure")
        print("   3. python azure_ml_train.py --submit         # Enviar job")
        print("   4. python azure_ml_train.py --status         # Ver progreso")
        print("   5. python azure_ml_train.py --download       # Descargar resultados")
        return
    
    if args.create_script:
        create_training_script()
    
    if args.setup:
        setup_azure_ml()
    
    if args.submit:
        check_azure_sdk()
        create_training_script()
        submit_training_job()
    
    if args.status:
        check_azure_sdk()
        check_job_status()
    
    if args.download:
        check_azure_sdk()
        download_results()

def setup_azure_ml():
    """Configurar Azure ML (crear compute cluster)."""
    check_azure_sdk()
    from azure.ai.ml import MLClient
    from azure.ai.ml.entities import AmlCompute
    
    ml_client = get_ml_client()
    
    print("🔧 Configurando Azure ML...")
    
    # Crear compute cluster con GPU
    compute_name = "gpu-cluster"
    
    # Lista de tamaños de VM con GPU a intentar (ordenados por disponibilidad)
    gpu_sizes = [
        "Standard_NC4as_T4_v3",      # T4 - más común en estudiantes
        "Standard_NC8as_T4_v3",      # T4 con más RAM
        "Standard_NC6s_v3",          # V100
        "Standard_NV6ads_A10_v5",    # A10
    ]
    
    try:
        compute = ml_client.compute.get(compute_name)
        print(f"✅ Compute '{compute_name}' ya existe ({compute.size})")
    except Exception:
        print(f"📦 Creando compute cluster '{compute_name}'...")
        
        # Intentar cada tamaño hasta que uno funcione
        for gpu_size in gpu_sizes:
            print(f"   Intentando {gpu_size}...")
            try:
                compute = AmlCompute(
                    name=compute_name,
                    size=gpu_size,
                    min_instances=0,
                    max_instances=1,
                    idle_time_before_scale_down=300,  # 5 min
                )
                ml_client.compute.begin_create_or_update(compute).result()
                print(f"✅ Compute '{compute_name}' creado con {gpu_size}")
                break
            except Exception as e:
                if "quota" in str(e).lower():
                    print(f"   ❌ Sin cuota para {gpu_size}")
                    continue
                else:
                    raise e
        else:
            print("\n❌ No tienes cuota para ninguna GPU.")
            print("   Ve a Azure Portal → Quotas → Request increase")
            print("   O usa CPU (más lento): Standard_DS3_v2")
            sys.exit(1)
    
    print("\n📋 Compute clusters disponibles:")
    for c in ml_client.compute.list():
        print(f"   - {c.name}: {c.size}")

if __name__ == "__main__":
    main()
