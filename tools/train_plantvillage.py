#!/usr/bin/env python3
"""
Entrena MobileNetV2 en PlantVillage con optimización de memoria.
Diseñado para sistemas con 8GB RAM.
"""

import os
import sys
import json
import urllib.request
import zipfile
import shutil

# Configuración de memoria antes de importar TensorFlow
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
os.environ['TF_FORCE_GPU_ALLOW_GROWTH'] = 'true'

print("="*60)
print("🌿 Entrenador PlantVillage - Versión Ligera")
print("="*60)

# 1. Descargar dataset si no existe
DATASET_URL = "https://data.mendeley.com/public-files/datasets/tywbtsjrjv/files/d5652a28-c1d8-4b76-97f3-72fb80f94efc/file_downloaded"
DATASET_ZIP = "plantvillage.zip"
DATASET_DIR = "plantvillage_dataset"

def download_dataset():
    """Descarga el dataset PlantVillage."""
    if os.path.exists(DATASET_DIR) and len(os.listdir(DATASET_DIR)) > 0:
        print("✅ Dataset ya existe")
        return True
    
    print("\n📥 Descargando dataset PlantVillage...")
    print("   (Esto puede tomar varios minutos)")
    
    try:
        def progress(count, block_size, total_size):
            percent = int(count * block_size * 100 / total_size) if total_size > 0 else 0
            mb = count * block_size / (1024*1024)
            sys.stdout.write(f"\r   Descargado: {mb:.1f} MB ({percent}%)")
            sys.stdout.flush()
        
        urllib.request.urlretrieve(DATASET_URL, DATASET_ZIP, progress)
        print("\n✅ Descarga completa")
        
        print("📦 Extrayendo...")
        with zipfile.ZipFile(DATASET_ZIP, 'r') as zip_ref:
            zip_ref.extractall(DATASET_DIR)
        
        os.remove(DATASET_ZIP)
        print("✅ Extracción completa")
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print("\nDescarga manual:")
        print("1. Ve a: https://data.mendeley.com/datasets/tywbtsjrjv/1")
        print("2. Descarga 'PlantVillage Dataset'")
        print("3. Extrae en: ./plantvillage_dataset/")
        return False

# 2. Entrenar modelo
def train_model():
    """Entrena MobileNetV2 en PlantVillage."""
    import tensorflow as tf
    from tensorflow.keras.applications import MobileNetV2
    from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
    from tensorflow.keras.models import Model
    from tensorflow.keras.preprocessing.image import ImageDataGenerator
    from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau
    import glob
    
    # Configurar para bajo consumo de memoria
    tf.config.threading.set_inter_op_parallelism_threads(2)
    tf.config.threading.set_intra_op_parallelism_threads(2)
    
    print(f"\nTensorFlow version: {tf.__version__}")
    
    # Encontrar directorio de datos
    possible_dirs = glob.glob(f"{DATASET_DIR}/**/color", recursive=True)
    if not possible_dirs:
        possible_dirs = glob.glob(f"{DATASET_DIR}/**/Plant*", recursive=True)
    if not possible_dirs:
        possible_dirs = [DATASET_DIR]
    
    DATA_DIR = possible_dirs[0]
    print(f"📁 Usando datos de: {DATA_DIR}")
    
    # Parámetros
    IMG_SIZE = 224
    BATCH_SIZE = 16  # Bajo para ahorrar RAM
    EPOCHS_PHASE1 = 3
    EPOCHS_PHASE2 = 5
    
    # Generador de datos con augmentación ligera
    datagen = ImageDataGenerator(
        rescale=1./255,
        rotation_range=15,
        width_shift_range=0.1,
        height_shift_range=0.1,
        horizontal_flip=True,
        validation_split=0.2
    )
    
    print("\n📊 Cargando imágenes...")
    train_gen = datagen.flow_from_directory(
        DATA_DIR,
        target_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        subset='training',
        shuffle=True
    )
    
    val_gen = datagen.flow_from_directory(
        DATA_DIR,
        target_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        subset='validation',
        shuffle=False
    )
    
    NUM_CLASSES = len(train_gen.class_indices)
    print(f"✅ {NUM_CLASSES} clases encontradas")
    print(f"   Imágenes entrenamiento: {train_gen.samples}")
    print(f"   Imágenes validación: {val_gen.samples}")
    
    # Guardar mapeo de clases
    class_names = list(train_gen.class_indices.keys())
    save_labels(class_names)
    
    # Crear modelo
    print("\n🏗️ Creando modelo MobileNetV2...")
    base_model = MobileNetV2(
        weights='imagenet',
        include_top=False,
        input_shape=(IMG_SIZE, IMG_SIZE, 3)
    )
    base_model.trainable = False
    
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = Dropout(0.3)(x)
    x = Dense(128, activation='relu')(x)  # Capa más pequeña para ahorrar RAM
    x = Dropout(0.3)(x)
    predictions = Dense(NUM_CLASSES, activation='softmax')(x)
    
    model = Model(inputs=base_model.input, outputs=predictions)
    
    model.compile(
        optimizer='adam',
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # Callbacks
    callbacks = [
        EarlyStopping(patience=2, restore_best_weights=True),
        ReduceLROnPlateau(factor=0.5, patience=1)
    ]
    
    # Fase 1: Entrenar solo cabeza
    print(f"\n🚀 Fase 1: Entrenando capas de clasificación ({EPOCHS_PHASE1} epochs)...")
    history1 = model.fit(
        train_gen,
        epochs=EPOCHS_PHASE1,
        validation_data=val_gen,
        callbacks=callbacks,
        verbose=1
    )
    
    # Fase 2: Fine-tuning
    print(f"\n🔧 Fase 2: Fine-tuning ({EPOCHS_PHASE2} epochs)...")
    base_model.trainable = True
    for layer in base_model.layers[:-20]:  # Congelar menos capas
        layer.trainable = False
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-5),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    history2 = model.fit(
        train_gen,
        epochs=EPOCHS_PHASE2,
        validation_data=val_gen,
        callbacks=callbacks,
        verbose=1
    )
    
    # Evaluar
    final_acc = history2.history['val_accuracy'][-1]
    print(f"\n📈 Accuracy final: {final_acc:.2%}")
    
    # Guardar modelo
    print("\n💾 Guardando modelo...")
    model.save('plant_disease_model.keras')
    model.save('plant_disease_savedmodel', save_format='tf')
    print("✅ Modelo guardado")
    
    return model, final_acc

def save_labels(class_names):
    """Guarda labels.json compatible con la app Android."""
    labels = []
    
    for i, name in enumerate(class_names):
        parts = name.split('___')
        crop = parts[0].replace('_', ' ')
        disease = parts[1].replace('_', ' ') if len(parts) > 1 else 'healthy'
        
        # Traducir enfermedades comunes
        translations = {
            'Early blight': 'Tizón temprano',
            'Late blight': 'Tizón tardío',
            'healthy': 'Saludable',
            'Bacterial spot': 'Mancha bacteriana',
            'Leaf Mold': 'Moho de hoja',
            'Septoria leaf spot': 'Mancha de Septoria',
            'Spider mites': 'Ácaros',
            'Target Spot': 'Mancha objetivo',
            'Yellow Leaf Curl Virus': 'Virus de rizado amarillo',
            'mosaic virus': 'Virus del mosaico',
            'Powdery mildew': 'Oídio',
            'Black rot': 'Podredumbre negra',
            'Cedar apple rust': 'Roya del cedro',
            'Esca': 'Esca',
            'Leaf blight': 'Tizón foliar',
            'Common rust': 'Roya común',
            'Northern Leaf Blight': 'Tizón del norte',
            'Gray leaf spot': 'Mancha gris',
            'Citrus greening': 'Enverdecimiento de cítricos',
            'Haunglongbing': 'Huanglongbing',
            'Black Rot': 'Podredumbre negra',
            'Scab': 'Sarna'
        }
        
        display = translations.get(disease, disease)
        if display != 'Saludable':
            display = f"{display}"
        
        labels.append({
            "id": i,
            "name": name,
            "crop": crop,
            "display": display
        })
    
    with open('plant_disease_labels.json', 'w', encoding='utf-8') as f:
        json.dump(labels, f, indent=2, ensure_ascii=False)
    
    print(f"✅ Labels guardados: {len(labels)} clases")

def convert_to_onnx():
    """Convierte a ONNX."""
    print("\n🔄 Convirtiendo a ONNX...")
    import subprocess
    
    result = subprocess.run([
        sys.executable, '-m', 'tf2onnx.convert',
        '--saved-model', 'plant_disease_savedmodel',
        '--output', 'plant_disease_model.onnx',
        '--opset', '13'
    ], capture_output=True, text=True)
    
    if result.returncode == 0:
        size = os.path.getsize('plant_disease_model.onnx') / (1024*1024)
        print(f"✅ ONNX creado: {size:.1f} MB")
        return True
    else:
        print(f"❌ Error: {result.stderr}")
        return False

if __name__ == "__main__":
    # Verificar si ya existe el modelo
    if os.path.exists('plant_disease_model.onnx'):
        print("✅ Modelo ONNX ya existe")
        size = os.path.getsize('plant_disease_model.onnx') / (1024*1024)
        print(f"   Tamaño: {size:.1f} MB")
        print("\n¿Deseas re-entrenar? (s/N): ", end='')
        if input().lower() != 's':
            sys.exit(0)
    
    # Paso 1: Descargar dataset
    if not download_dataset():
        sys.exit(1)
    
    # Paso 2: Entrenar
    model, acc = train_model()
    
    # Paso 3: Convertir a ONNX
    if convert_to_onnx():
        print("\n" + "="*60)
        print("✅ ¡ENTRENAMIENTO COMPLETADO!")
        print("="*60)
        print(f"\nArchivos generados:")
        print(f"  - plant_disease_model.onnx")
        print(f"  - plant_disease_labels.json")
        print(f"\nAccuracy: {acc:.2%}")
        print(f"\nSiguiente paso: Convertir a MindSpore:")
        print(f"  ./converter_lite --fmk=ONNX --modelFile=plant_disease_model.onnx --outputFile=plant_disease_model")
