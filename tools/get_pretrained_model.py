#!/usr/bin/env python3
"""
Descarga modelo PlantVillage pre-entrenado desde fuentes confiables.
"""
import os
import urllib.request
import ssl
import sys

# Crear contexto SSL que ignore certificados (para algunos servidores)
ssl._create_default_https_context = ssl._create_unverified_context

def download(url, filename):
    print(f"Descargando desde: {url[:60]}...")
    try:
        urllib.request.urlretrieve(url, filename)
        size = os.path.getsize(filename) / (1024*1024)
        print(f"✅ Descargado: {filename} ({size:.1f} MB)")
        return True
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

# Lista de modelos pre-entrenados en PlantVillage (ONNX format)
SOURCES = [
    # Modelo ONNX de PlantVillage en HuggingFace
    ("https://huggingface.co/wajidakram/plant-disease-classifier/resolve/main/plant_disease_model.onnx", "plantvillage.onnx"),
    # Alternativa - modelo h5 de Keras
    ("https://github.com/imskr/Plant_Disease_Detection/raw/master/plant_disease_classification_model.h5", "plantvillage.h5"),
]

print("="*60)
print("Buscando modelo PlantVillage pre-entrenado...")
print("="*60 + "\n")

for url, filename in SOURCES:
    if download(url, filename):
        print(f"\n✅ Modelo disponible: {filename}")
        break
else:
    print("\n❌ No se encontró ningún modelo pre-entrenado disponible online.")
    print("\nOpciones:")
    print("1. Instalar TensorFlow y entrenar (~2-4 horas en CPU)")
    print("2. Usar Google Colab para entrenar (gratis, más rápido)")
    print("3. Descargar manualmente de Kaggle:")
    print("   https://www.kaggle.com/datasets/vipoooool/new-plant-diseases-dataset")
