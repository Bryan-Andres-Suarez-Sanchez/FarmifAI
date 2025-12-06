#!/usr/bin/env python3
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
