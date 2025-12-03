#!/usr/bin/env python3
"""
Entrena MobileNetV2 en PlantVillage de forma ligera (8GB RAM).
Usa TensorFlow con configuración de bajo consumo de memoria.
"""

import os
import sys

print("="*60)
print("Entrenamiento ligero de PlantVillage")
print("RAM disponible: ~8GB")
print("="*60)

# Verificar si podemos instalar TensorFlow
print("\n1. Verificando dependencias...")

try:
    import tensorflow as tf
    print(f"   TensorFlow: {tf.__version__}")
except ImportError:
    print("   TensorFlow no instalado.")
    print("\n   Para entrenar necesitas instalar TensorFlow:")
    print("   pip install tensorflow==2.13.0")
    print("\n   Alternativa: Usar modelo pre-convertido de Kaggle")
    sys.exit(1)

# Si TensorFlow está disponible, continuar con entrenamiento
print("\n2. Configurando para bajo consumo de memoria...")

# Limitar memoria GPU si existe
gpus = tf.config.experimental.list_physical_devices('GPU')
if gpus:
    for gpu in gpus:
        tf.config.experimental.set_memory_growth(gpu, True)

# Configurar para CPU con límite de threads
tf.config.threading.set_inter_op_parallelism_threads(2)
tf.config.threading.set_intra_op_parallelism_threads(2)

print("   Configuración de memoria: OK")
print("\n3. El entrenamiento tomaría ~2-4 horas en CPU")
print("   Alternativa recomendada: Usar modelo de Kaggle")
