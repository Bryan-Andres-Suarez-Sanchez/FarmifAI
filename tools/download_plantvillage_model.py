#!/usr/bin/env python3
"""
Descarga un modelo MobileNetV2 pre-entrenado en PlantVillage desde Hugging Face.
Ligero para dispositivos con 8GB RAM.
"""

import os
import urllib.request
import sys

# Modelos disponibles pre-entrenados en PlantVillage
# Opción 1: Modelo de Kaggle/HuggingFace - MobileNetV2 fine-tuned
MODELS = {
    # TensorFlow Lite model pre-trained on PlantVillage
    "plantvillage_tflite": {
        "url": "https://github.com/e9t/plant-disease-classification-api/raw/master/model/plant_disease_model.tflite",
        "filename": "plantvillage.tflite",
        "format": "tflite"
    }
}

def download_file(url, filename):
    """Descarga un archivo con barra de progreso."""
    print(f"Descargando {filename}...")
    print(f"URL: {url}")
    
    try:
        def progress(count, block_size, total_size):
            percent = int(count * block_size * 100 / total_size) if total_size > 0 else 0
            sys.stdout.write(f"\r  Progreso: {percent}%")
            sys.stdout.flush()
        
        urllib.request.urlretrieve(url, filename, progress)
        print(f"\n✅ Descargado: {filename}")
        return True
    except Exception as e:
        print(f"\n❌ Error descargando: {e}")
        return False

if __name__ == "__main__":
    print("=" * 60)
    print("Buscando modelo PlantVillage pre-entrenado...")
    print("=" * 60)
    
    # Intentar descargar modelo TFLite
    model = MODELS["plantvillage_tflite"]
    success = download_file(model["url"], model["filename"])
    
    if success:
        size = os.path.getsize(model["filename"]) / (1024*1024)
        print(f"\nModelo descargado: {size:.1f} MB")
        print("Formato: TensorFlow Lite")
    else:
        print("\nNo se pudo descargar. Intentando alternativa...")
