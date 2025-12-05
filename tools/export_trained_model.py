#!/usr/bin/env python3
"""
Exporta el modelo entrenado desde el checkpoint a TFLite
"""
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)

import tensorflow as tf

MODEL_NAME = "plant_disease"
CHECKPOINT_PATH = "checkpoints/phase2_best.keras"

def main():
    print("="*50)
    print("EXPORTANDO MODELO ENTRENADO")
    print("="*50)
    
    # Cargar modelo desde checkpoint
    if not os.path.exists(CHECKPOINT_PATH):
        print(f"✗ No se encontró el checkpoint: {CHECKPOINT_PATH}")
        sys.exit(1)
    
    print(f"\nCargando modelo desde {CHECKPOINT_PATH}...")
    model = tf.keras.models.load_model(CHECKPOINT_PATH)
    print("✓ Modelo cargado")
    
    # Exportar a TFLite
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
    
    # También guardar como .keras
    keras_path = f'{MODEL_NAME}.keras'
    model.save(keras_path)
    size_mb = os.path.getsize(keras_path) / (1024 * 1024)
    print(f"✓ Keras guardado: {keras_path} ({size_mb:.1f} MB)")
    
    print("\n" + "="*50)
    print("EXPORTACIÓN COMPLETADA")
    print("="*50)
    print(f"\nArchivos generados:")
    print(f"  - {tflite_path}")
    print(f"  - {keras_path}")
    print(f"  - plant_disease_labels.json")
    
    print("\n  Siguiente paso - Convertir a MindSpore:")
    print("  " + "-"*40)
    print(f"  ./mindspore-lite-2.3.1-linux-x64/tools/converter/converter_lite \\")
    print(f"      --fmk=TFLITE \\")
    print(f"      --modelFile={MODEL_NAME}.tflite \\")
    print(f"      --outputFile={MODEL_NAME}")
    print("")
    print(f"  cp {MODEL_NAME}.ms ../app/src/main/assets/")
    print(f"  cp {MODEL_NAME}_labels.json ../app/src/main/assets/")
    print("="*50)

if __name__ == '__main__':
    main()
