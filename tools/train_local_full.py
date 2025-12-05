#!/usr/bin/env python3
"""
Entrenamiento completo de PlantVillage para 8GB RAM
- Sin reducción de calidad
- Batch size pequeño para no saturar RAM
- Entrenamiento completo con fine-tuning
"""

import os
import sys
import gc
import json

# Cambiar al directorio del script
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)

# Limitar memoria de TensorFlow ANTES de importarlo
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
os.environ['TF_FORCE_GPU_ALLOW_GROWTH'] = 'true'

import tensorflow as tf

# Configurar límite de memoria (máx 5GB para dejar RAM al sistema)
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"GPU detectada: {gpus[0].name}")
    except RuntimeError as e:
        print(f"Error GPU: {e}")
else:
    print("Usando CPU (entrenamiento más lento pero funcional)")

# Limitar hilos para no saturar CPU/RAM
tf.config.threading.set_inter_op_parallelism_threads(2)
tf.config.threading.set_intra_op_parallelism_threads(4)

print(f"TensorFlow {tf.__version__}")


# ============ CONFIGURACIÓN ============
DATASET_DIR = "PlantVillage-Dataset/raw/color"  # Ya descargado con git clone
IMG_SIZE = 224
BATCH_SIZE = 8  # Pequeño para 8GB RAM
EPOCHS_PHASE1 = 10  # Entrenamiento completo
EPOCHS_PHASE2 = 15  # Fine-tuning completo
CHECKPOINT_DIR = "checkpoints"
MODEL_NAME = "plant_disease"


def check_dataset():
    """Verifica que el dataset existe"""
    if os.path.exists(DATASET_DIR):
        num_classes = len([d for d in os.listdir(DATASET_DIR) if os.path.isdir(os.path.join(DATASET_DIR, d))])
        print(f"✓ Dataset encontrado en {DATASET_DIR}")
        print(f"  {num_classes} clases detectadas")
        return True
    else:
        print(f"✗ Dataset no encontrado en {DATASET_DIR}")
        print("  Ejecuta: git clone --depth 1 https://github.com/spMohanty/PlantVillage-Dataset.git")
        return False


def create_data_generators():
    """Crea generadores de datos con augmentación"""
    from tensorflow.keras.preprocessing.image import ImageDataGenerator
    
    # Augmentación completa para mejor generalización
    train_datagen = ImageDataGenerator(
        rescale=1./255,
        rotation_range=30,
        width_shift_range=0.2,
        height_shift_range=0.2,
        shear_range=0.2,
        zoom_range=0.2,
        horizontal_flip=True,
        vertical_flip=True,
        fill_mode='nearest',
        validation_split=0.2
    )
    
    val_datagen = ImageDataGenerator(
        rescale=1./255,
        validation_split=0.2
    )
    
    print(f"\nCargando imágenes desde {DATASET_DIR}...")
    
    train_gen = train_datagen.flow_from_directory(
        DATASET_DIR,
        target_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        subset='training',
        shuffle=True
    )
    
    val_gen = val_datagen.flow_from_directory(
        DATASET_DIR,
        target_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        subset='validation',
        shuffle=False
    )
    
    return train_gen, val_gen


def create_model(num_classes):
    """Crea modelo MobileNetV2 con capas de clasificación"""
    from tensorflow.keras.applications import MobileNetV2
    from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout, BatchNormalization
    from tensorflow.keras.models import Model
    
    print("\nCreando modelo MobileNetV2...")
    
    base_model = MobileNetV2(
        weights='imagenet',
        include_top=False,
        input_shape=(IMG_SIZE, IMG_SIZE, 3)
    )
    base_model.trainable = False
    
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = BatchNormalization()(x)
    x = Dense(512, activation='relu')(x)
    x = Dropout(0.4)(x)
    x = BatchNormalization()(x)
    x = Dense(256, activation='relu')(x)
    x = Dropout(0.3)(x)
    predictions = Dense(num_classes, activation='softmax')(x)
    
    model = Model(inputs=base_model.input, outputs=predictions)
    
    total_params = model.count_params()
    trainable_params = sum([tf.keras.backend.count_params(w) for w in model.trainable_weights])
    
    print(f"  Total parámetros: {total_params:,}")
    print(f"  Entrenables: {trainable_params:,}")
    
    return model, base_model


def train_phase1(model, train_gen, val_gen):
    """Fase 1: Entrenar solo capas de clasificación"""
    from tensorflow.keras.callbacks import (
        EarlyStopping, ReduceLROnPlateau, 
        ModelCheckpoint, TensorBoard
    )
    
    os.makedirs(CHECKPOINT_DIR, exist_ok=True)
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks = [
        EarlyStopping(
            monitor='val_accuracy',
            patience=5,
            restore_best_weights=True,
            verbose=1
        ),
        ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=3,
            min_lr=1e-6,
            verbose=1
        ),
        ModelCheckpoint(
            f'{CHECKPOINT_DIR}/phase1_best.keras',
            monitor='val_accuracy',
            save_best_only=True,
            verbose=1
        )
    ]
    
    print("\n" + "="*50)
    print("FASE 1: Entrenando capas de clasificación")
    print(f"  Epochs: {EPOCHS_PHASE1}")
    print(f"  Batch size: {BATCH_SIZE}")
    print("="*50 + "\n")
    
    # Calcular steps por epoch
    steps_per_epoch = train_gen.samples // BATCH_SIZE
    validation_steps = val_gen.samples // BATCH_SIZE
    
    history = model.fit(
        train_gen,
        epochs=EPOCHS_PHASE1,
        steps_per_epoch=steps_per_epoch,
        validation_data=val_gen,
        validation_steps=validation_steps,
        callbacks=callbacks,
        verbose=1
    )
    
    # Liberar memoria
    gc.collect()
    
    return history


def train_phase2(model, base_model, train_gen, val_gen):
    """Fase 2: Fine-tuning de capas convolucionales"""
    from tensorflow.keras.callbacks import (
        EarlyStopping, ReduceLROnPlateau, ModelCheckpoint
    )
    
    print("\n" + "="*50)
    print("FASE 2: Fine-tuning")
    print("="*50)
    
    # Descongelar últimas 50 capas del backbone
    base_model.trainable = True
    fine_tune_at = len(base_model.layers) - 50
    
    for layer in base_model.layers[:fine_tune_at]:
        layer.trainable = False
    
    trainable_layers = sum([1 for l in base_model.layers if l.trainable])
    print(f"  Capas descongeladas: {trainable_layers}")
    
    # Learning rate muy bajo para fine-tuning
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks = [
        EarlyStopping(
            monitor='val_accuracy',
            patience=7,
            restore_best_weights=True,
            verbose=1
        ),
        ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=3,
            min_lr=1e-7,
            verbose=1
        ),
        ModelCheckpoint(
            f'{CHECKPOINT_DIR}/phase2_best.keras',
            monitor='val_accuracy',
            save_best_only=True,
            verbose=1
        )
    ]
    
    print(f"  Epochs: {EPOCHS_PHASE2}")
    print("="*50 + "\n")
    
    steps_per_epoch = train_gen.samples // BATCH_SIZE
    validation_steps = val_gen.samples // BATCH_SIZE
    
    history = model.fit(
        train_gen,
        epochs=EPOCHS_PHASE2,
        steps_per_epoch=steps_per_epoch,
        validation_data=val_gen,
        validation_steps=validation_steps,
        callbacks=callbacks,
        verbose=1
    )
    
    gc.collect()
    
    return history


def save_labels(class_indices):
    """Guarda archivo de labels para Android"""
    translations = {
        'healthy': 'Saludable',
        'Early_blight': 'Tizón temprano',
        'Late_blight': 'Tizón tardío',
        'Bacterial_spot': 'Mancha bacteriana',
        'Leaf_Mold': 'Moho foliar',
        'Septoria_leaf_spot': 'Septoriosis',
        'Spider_mites Two-spotted_spider_mite': 'Ácaros',
        'Target_Spot': 'Mancha diana',
        'Tomato_Yellow_Leaf_Curl_Virus': 'Virus rizado amarillo',
        'Tomato_mosaic_virus': 'Virus del mosaico',
        'powdery_mildew': 'Oídio',
        'Black_rot': 'Podredumbre negra',
        'Cedar_apple_rust': 'Roya del manzano',
        'Esca_(Black_Measles)': 'Esca',
        'Leaf_blight_(Isariopsis_Leaf_Spot)': 'Tizón foliar',
        'Common_rust_': 'Roya común',
        'Northern_Leaf_Blight': 'Tizón norteño',
        'Cercospora_leaf_spot Gray_leaf_spot': 'Mancha gris',
        'Haunglongbing_(Citrus_greening)': 'Huanglongbing',
        'Apple_scab': 'Sarna del manzano'
    }
    
    labels = []
    for name, idx in sorted(class_indices.items(), key=lambda x: x[1]):
        parts = name.split('___')
        crop = parts[0].replace('_', ' ')
        disease_raw = parts[1] if len(parts) > 1 else 'healthy'
        display = translations.get(disease_raw, disease_raw.replace('_', ' '))
        
        labels.append({
            "id": idx,
            "name": name,
            "crop": crop,
            "display": display
        })
    
    with open(f'{MODEL_NAME}_labels.json', 'w', encoding='utf-8') as f:
        json.dump(labels, f, indent=2, ensure_ascii=False)
    
    print(f"\n✓ Labels guardados: {MODEL_NAME}_labels.json ({len(labels)} clases)")
    return labels


def export_to_tflite(model):
    """Exporta modelo a TFLite (compatible con MindSpore converter)"""
    print("\n" + "="*50)
    print("EXPORTANDO MODELO")
    print("="*50)
    
    # Guardar en formato Keras 3
    keras_path = f'{MODEL_NAME}.keras'
    model.save(keras_path)
    print(f"✓ Modelo Keras guardado: {keras_path}")
    
    # Convertir a TFLite directamente desde el modelo en memoria
    print("\nConvirtiendo a TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    
    tflite_model = converter.convert()
    
    tflite_path = f'{MODEL_NAME}.tflite'
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
    print(f"✓ TFLite guardado: {tflite_path} ({size_mb:.1f} MB)")
    
    # Intentar exportar a ONNX si está disponible
    try:
        import subprocess
        print("\nConvirtiendo a ONNX...")
        
        # Primero guardamos como SavedModel para ONNX
        saved_model_path = f'{MODEL_NAME}_savedmodel'
        tf.saved_model.save(model, saved_model_path)
        
        result = subprocess.run([
            sys.executable, '-m', 'tf2onnx.convert',
            '--saved-model', saved_model_path,
            '--output', f'{MODEL_NAME}.onnx',
            '--opset', '13'
        ], capture_output=True, text=True)
        
        if result.returncode == 0 and os.path.exists(f'{MODEL_NAME}.onnx'):
            size_mb = os.path.getsize(f'{MODEL_NAME}.onnx') / (1024 * 1024)
            print(f"✓ ONNX guardado: {MODEL_NAME}.onnx ({size_mb:.1f} MB)")
        else:
            print(f"⚠ ONNX conversion falló: {result.stderr[:200] if result.stderr else 'Unknown error'}")
    except Exception as e:
        print(f"⚠ ONNX no disponible: {e}")
    
    return tflite_path


def main():
    print("\n" + "="*60)
    print("  ENTRENAMIENTO PLANTVILLAGE - CALIDAD COMPLETA")
    print("  Optimizado para 8GB RAM")
    print("="*60 + "\n")
    
    # 1. Verificar dataset
    if not check_dataset():
        print("Error: No se encontró el dataset")
        sys.exit(1)
    
    # 2. Crear generadores
    train_gen, val_gen = create_data_generators()
    num_classes = len(train_gen.class_indices)
    print(f"\n✓ {num_classes} clases detectadas")
    print(f"✓ {train_gen.samples:,} imágenes de entrenamiento")
    print(f"✓ {val_gen.samples:,} imágenes de validación")
    
    # 3. Crear modelo
    model, base_model = create_model(num_classes)
    
    # 4. Fase 1: Entrenar clasificador
    history1 = train_phase1(model, train_gen, val_gen)
    acc1 = max(history1.history['val_accuracy'])
    print(f"\n★ Mejor accuracy Fase 1: {acc1:.2%}")
    
    # 5. Fase 2: Fine-tuning
    history2 = train_phase2(model, base_model, train_gen, val_gen)
    acc2 = max(history2.history['val_accuracy'])
    print(f"\n★ Mejor accuracy Fase 2: {acc2:.2%}")
    
    # 6. Guardar labels
    save_labels(train_gen.class_indices)
    
    # 7. Exportar modelo
    tflite_path = export_to_tflite(model)
    
    # 8. Instrucciones finales
    print("\n" + "="*60)
    print("  ¡ENTRENAMIENTO COMPLETADO!")
    print("="*60)
    print(f"\n  Accuracy final: {acc2:.2%}")
    print(f"\n  Archivos generados:")
    print(f"    - {MODEL_NAME}.tflite")
    print(f"    - {MODEL_NAME}_labels.json")
    if os.path.exists(f'{MODEL_NAME}.onnx'):
        print(f"    - {MODEL_NAME}.onnx")
    
    print("\n  Siguiente paso - Convertir a MindSpore:")
    print("  " + "-"*50)
    print(f"  ./mindspore-lite-2.3.1-linux-x64/tools/converter/converter_lite \\")
    print(f"      --fmk=TFLITE \\")
    print(f"      --modelFile={MODEL_NAME}.tflite \\")
    print(f"      --outputFile={MODEL_NAME}")
    print("")
    print(f"  cp {MODEL_NAME}.ms ../app/src/main/assets/")
    print(f"  cp {MODEL_NAME}_labels.json ../app/src/main/assets/")
    print("="*60 + "\n")


if __name__ == '__main__':
    main()
