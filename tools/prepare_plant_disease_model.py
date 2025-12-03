#!/usr/bin/env python3
"""
Script para preparar el modelo de diagnóstico de enfermedades de plantas.

Este script automatiza el proceso completo:
1. Descargar el dataset PlantVillage
2. Entrenar un modelo MobileNetV2 (o usar pre-entrenado)
3. Exportar a ONNX
4. Convertir a MindSpore Lite (.ms)

Requisitos:
    pip install torch torchvision onnx kaggle mindspore-lite

Uso:
    python prepare_plant_disease_model.py --download-only    # Solo descargar dataset
    python prepare_plant_disease_model.py --train            # Entrenar desde cero
    python prepare_plant_disease_model.py --from-pretrained  # Usar modelo pre-entrenado de HF
    python prepare_plant_disease_model.py --convert-only     # Solo convertir ONNX existente
"""

import os
import sys
import argparse
import shutil
from pathlib import Path

# Configuración
PROJECT_ROOT = Path(__file__).parent.parent
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
MODELS_DIR = PROJECT_ROOT / "tools" / "models"
DATASET_DIR = PROJECT_ROOT / "tools" / "datasets" / "plantvillage"

# Parámetros del modelo
NUM_CLASSES = 38
INPUT_SIZE = 224
MODEL_NAME = "plant_disease_model"


def check_dependencies():
    """Verificar dependencias necesarias."""
    missing = []
    
    try:
        import torch
        print(f"✓ PyTorch {torch.__version__}")
    except ImportError:
        missing.append("torch torchvision")
    
    try:
        import onnx
        print(f"✓ ONNX {onnx.__version__}")
    except ImportError:
        missing.append("onnx")
    
    if missing:
        print(f"\n❌ Dependencias faltantes:")
        print(f"   pip install {' '.join(missing)}")
        return False
    
    return True


def download_plantvillage():
    """Descargar dataset PlantVillage desde Kaggle."""
    print("\n📦 Descargando PlantVillage dataset...")
    
    # Método 1: Kaggle API
    try:
        import kaggle
        os.makedirs(DATASET_DIR, exist_ok=True)
        kaggle.api.dataset_download_files(
            'emmarex/plantdisease',
            path=str(DATASET_DIR),
            unzip=True
        )
        print(f"✓ Dataset descargado en {DATASET_DIR}")
        return True
    except Exception as e:
        print(f"⚠ Kaggle API falló: {e}")
    
    # Método 2: Instrucciones manuales
    print("""
📋 INSTRUCCIONES MANUALES:
    
1. Ve a: https://www.kaggle.com/datasets/emmarex/plantdisease
2. Descarga el dataset (1.24 GB)
3. Extrae en: {dataset_dir}
   
La estructura debe ser:
   {dataset_dir}/
       PlantVillage/
           Apple___Apple_scab/
           Apple___Black_rot/
           ...
           Tomato___healthy/
    """.format(dataset_dir=DATASET_DIR))
    
    return False


def download_pretrained():
    """Descargar modelo pre-entrenado de HuggingFace."""
    print("\n📦 Buscando modelo pre-entrenado...")
    
    try:
        from transformers import AutoModelForImageClassification, AutoConfig
        import torch
        
        # Hay varios modelos pre-entrenados en HF para PlantVillage
        # Ejemplo: nateraw/vit-base-beans, linkanjarad/mobilenet_v2_1.0_224-plant-disease
        model_id = "linkanjarad/mobilenet_v2_1.0_224-plant-disease"
        
        print(f"   Descargando desde: {model_id}")
        
        model = AutoModelForImageClassification.from_pretrained(model_id)
        config = AutoConfig.from_pretrained(model_id)
        
        # Guardar
        os.makedirs(MODELS_DIR, exist_ok=True)
        model_path = MODELS_DIR / f"{MODEL_NAME}.pt"
        torch.save({
            'model_state_dict': model.state_dict(),
            'config': config,
            'num_classes': NUM_CLASSES,
            'model_id': model_id
        }, model_path)
        
        print(f"✓ Modelo guardado en: {model_path}")
        return True, model_path
        
    except Exception as e:
        print(f"⚠ No se pudo descargar modelo pre-entrenado: {e}")
        return False, None


def train_mobilenet():
    """Entrenar MobileNetV2 en PlantVillage."""
    import torch
    import torch.nn as nn
    from torch.utils.data import DataLoader
    from torchvision import datasets, transforms, models
    from torch.optim import Adam
    from torch.optim.lr_scheduler import StepLR
    
    print("\n🏋️ Entrenando MobileNetV2...")
    
    # Verificar dataset
    data_path = DATASET_DIR / "PlantVillage"
    if not data_path.exists():
        print(f"❌ Dataset no encontrado en: {data_path}")
        print("   Ejecuta primero: python prepare_plant_disease_model.py --download-only")
        return None
    
    # Transformaciones
    train_transform = transforms.Compose([
        transforms.Resize((INPUT_SIZE, INPUT_SIZE)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(10),
        transforms.ColorJitter(brightness=0.2, contrast=0.2),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])
    
    val_transform = transforms.Compose([
        transforms.Resize((INPUT_SIZE, INPUT_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])
    
    # Dataset
    full_dataset = datasets.ImageFolder(data_path, transform=train_transform)
    
    # Split 80-20
    train_size = int(0.8 * len(full_dataset))
    val_size = len(full_dataset) - train_size
    train_dataset, val_dataset = torch.utils.data.random_split(
        full_dataset, [train_size, val_size]
    )
    val_dataset.dataset.transform = val_transform
    
    train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True, num_workers=4)
    val_loader = DataLoader(val_dataset, batch_size=32, shuffle=False, num_workers=4)
    
    print(f"   Clases: {len(full_dataset.classes)}")
    print(f"   Train: {len(train_dataset)}, Val: {len(val_dataset)}")
    
    # Modelo
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"   Dispositivo: {device}")
    
    model = models.mobilenet_v2(pretrained=True)
    model.classifier[1] = nn.Linear(model.last_channel, NUM_CLASSES)
    model = model.to(device)
    
    # Entrenamiento
    criterion = nn.CrossEntropyLoss()
    optimizer = Adam(model.parameters(), lr=0.001)
    scheduler = StepLR(optimizer, step_size=5, gamma=0.1)
    
    best_acc = 0.0
    epochs = 10
    
    for epoch in range(epochs):
        # Train
        model.train()
        running_loss = 0.0
        correct = 0
        total = 0
        
        for batch_idx, (images, labels) in enumerate(train_loader):
            images, labels = images.to(device), labels.to(device)
            
            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            
            running_loss += loss.item()
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()
            
            if (batch_idx + 1) % 100 == 0:
                print(f"   Epoch {epoch+1} [{batch_idx+1}/{len(train_loader)}] "
                      f"Loss: {running_loss/(batch_idx+1):.4f} "
                      f"Acc: {100.*correct/total:.2f}%")
        
        # Validation
        model.eval()
        val_correct = 0
        val_total = 0
        
        with torch.no_grad():
            for images, labels in val_loader:
                images, labels = images.to(device), labels.to(device)
                outputs = model(images)
                _, predicted = outputs.max(1)
                val_total += labels.size(0)
                val_correct += predicted.eq(labels).sum().item()
        
        val_acc = 100. * val_correct / val_total
        print(f"   Epoch {epoch+1}/{epochs} - Val Acc: {val_acc:.2f}%")
        
        # Guardar mejor modelo
        if val_acc > best_acc:
            best_acc = val_acc
            os.makedirs(MODELS_DIR, exist_ok=True)
            model_path = MODELS_DIR / f"{MODEL_NAME}.pt"
            torch.save({
                'model_state_dict': model.state_dict(),
                'classes': full_dataset.classes,
                'num_classes': NUM_CLASSES,
                'best_acc': best_acc
            }, model_path)
            print(f"   ✓ Mejor modelo guardado ({val_acc:.2f}%)")
        
        scheduler.step()
    
    print(f"\n✓ Entrenamiento completado. Mejor acc: {best_acc:.2f}%")
    return MODELS_DIR / f"{MODEL_NAME}.pt"


def export_to_onnx(model_path):
    """Exportar modelo PyTorch a ONNX."""
    import torch
    import torch.nn as nn
    from torchvision import models
    
    print("\n📦 Exportando a ONNX...")
    
    # Cargar modelo
    checkpoint = torch.load(model_path, map_location='cpu')
    
    model = models.mobilenet_v2(pretrained=False)
    model.classifier[1] = nn.Linear(model.last_channel, NUM_CLASSES)
    model.load_state_dict(checkpoint['model_state_dict'])
    model.eval()
    
    # Input de ejemplo
    dummy_input = torch.randn(1, 3, INPUT_SIZE, INPUT_SIZE)
    
    # Exportar
    onnx_path = MODELS_DIR / f"{MODEL_NAME}.onnx"
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=['input'],
        output_names=['output'],
        dynamic_axes={
            'input': {0: 'batch_size'},
            'output': {0: 'batch_size'}
        },
        opset_version=11
    )
    
    print(f"✓ ONNX exportado: {onnx_path}")
    
    # Verificar
    import onnx
    onnx_model = onnx.load(onnx_path)
    onnx.checker.check_model(onnx_model)
    print("✓ ONNX válido")
    
    return onnx_path


def convert_to_mindspore(onnx_path):
    """Convertir ONNX a MindSpore Lite (.ms)."""
    print("\n📦 Convirtiendo a MindSpore Lite...")
    
    output_ms = ASSETS_DIR / "plant_disease_model.ms"
    
    # Usar converter_lite de MindSpore
    cmd = f"""
converter_lite \\
    --fmk=ONNX \\
    --modelFile={onnx_path} \\
    --outputFile={output_ms.with_suffix('')} \\
    --inputShape="input:1,3,224,224"
    """
    
    print(f"   Ejecutando: {cmd}")
    
    result = os.system(cmd)
    
    if result == 0 and output_ms.exists():
        print(f"✓ Modelo MindSpore guardado: {output_ms}")
        print(f"   Tamaño: {output_ms.stat().st_size / 1024 / 1024:.2f} MB")
        return output_ms
    else:
        print("""
❌ La conversión falló. Alternativas:

1. Instalar MindSpore Lite tools:
   pip install mindspore-lite

2. Usar el conversor online de Huawei:
   https://www.mindspore.cn/lite/docs/en/master/quick_start/converter_tool.html

3. Convertir manualmente:
   converter_lite --fmk=ONNX --modelFile={onnx} --outputFile={out}
        """.format(onnx=onnx_path, out=output_ms.with_suffix('')))
        return None


def create_labels_file(classes=None):
    """Crear archivo de etiquetas JSON."""
    # Clases estándar de PlantVillage
    default_classes = [
        ("Apple___Apple_scab", "Sarna del manzano", "Manzano"),
        ("Apple___Black_rot", "Pudrición negra del manzano", "Manzano"),
        ("Apple___Cedar_apple_rust", "Roya del manzano", "Manzano"),
        ("Apple___healthy", "Manzano sano", "Manzano"),
        ("Blueberry___healthy", "Arándano sano", "Arándano"),
        ("Cherry_(including_sour)___Powdery_mildew", "Oídio del cerezo", "Cerezo"),
        ("Cherry_(including_sour)___healthy", "Cerezo sano", "Cerezo"),
        ("Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot", "Mancha gris del maíz", "Maíz"),
        ("Corn_(maize)___Common_rust_", "Roya común del maíz", "Maíz"),
        ("Corn_(maize)___Northern_Leaf_Blight", "Tizón norteño del maíz", "Maíz"),
        ("Corn_(maize)___healthy", "Maíz sano", "Maíz"),
        ("Grape___Black_rot", "Pudrición negra de uva", "Uva"),
        ("Grape___Esca_(Black_Measles)", "Yesca de la vid", "Uva"),
        ("Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Tizón de la hoja de vid", "Uva"),
        ("Grape___healthy", "Vid sana", "Uva"),
        ("Orange___Haunglongbing_(Citrus_greening)", "Huanglongbing cítricos", "Naranja"),
        ("Peach___Bacterial_spot", "Mancha bacteriana durazno", "Durazno"),
        ("Peach___healthy", "Durazno sano", "Durazno"),
        ("Pepper,_bell___Bacterial_spot", "Mancha bacteriana pimiento", "Pimiento"),
        ("Pepper,_bell___healthy", "Pimiento sano", "Pimiento"),
        ("Potato___Early_blight", "Tizón temprano papa", "Papa"),
        ("Potato___Late_blight", "Tizón tardío papa", "Papa"),
        ("Potato___healthy", "Papa sana", "Papa"),
        ("Raspberry___healthy", "Frambuesa sana", "Frambuesa"),
        ("Soybean___healthy", "Soja sana", "Soja"),
        ("Squash___Powdery_mildew", "Oídio de calabaza", "Calabaza"),
        ("Strawberry___Leaf_scorch", "Quemadura foliar fresa", "Fresa"),
        ("Strawberry___healthy", "Fresa sana", "Fresa"),
        ("Tomato___Bacterial_spot", "Mancha bacteriana tomate", "Tomate"),
        ("Tomato___Early_blight", "Tizón temprano tomate", "Tomate"),
        ("Tomato___Late_blight", "Tizón tardío tomate", "Tomate"),
        ("Tomato___Leaf_Mold", "Moho foliar tomate", "Tomate"),
        ("Tomato___Septoria_leaf_spot", "Septoriosis tomate", "Tomate"),
        ("Tomato___Spider_mites Two-spotted_spider_mite", "Araña roja tomate", "Tomate"),
        ("Tomato___Target_Spot", "Mancha diana tomate", "Tomate"),
        ("Tomato___Tomato_Yellow_Leaf_Curl_Virus", "Virus rizado amarillo tomate", "Tomate"),
        ("Tomato___Tomato_mosaic_virus", "Virus mosaico tomate", "Tomate"),
        ("Tomato___healthy", "Tomate sano", "Tomate"),
    ]
    
    import json
    
    labels = {
        "version": "1.0",
        "model": "MobileNetV2-PlantVillage",
        "num_classes": len(default_classes),
        "labels": [
            {
                "id": i,
                "name": name,
                "display": display,
                "crop": crop
            }
            for i, (name, display, crop) in enumerate(default_classes)
        ]
    }
    
    output_path = ASSETS_DIR / "plant_disease_labels.json"
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)
    
    print(f"✓ Etiquetas guardadas: {output_path}")
    return output_path


def main():
    parser = argparse.ArgumentParser(description="Preparar modelo de diagnóstico de plantas")
    parser.add_argument('--download-only', action='store_true', help='Solo descargar dataset')
    parser.add_argument('--train', action='store_true', help='Entrenar desde cero')
    parser.add_argument('--from-pretrained', action='store_true', help='Usar modelo pre-entrenado')
    parser.add_argument('--convert-only', type=str, help='Solo convertir archivo ONNX existente')
    parser.add_argument('--create-labels', action='store_true', help='Crear archivo de etiquetas')
    
    args = parser.parse_args()
    
    print("="*60)
    print("   PREPARACIÓN DE MODELO PLANT DISEASE CLASSIFIER")
    print("="*60)
    
    # Crear directorio de salida
    os.makedirs(MODELS_DIR, exist_ok=True)
    
    if args.create_labels:
        create_labels_file()
        return
    
    if args.download_only:
        download_plantvillage()
        return
    
    if args.convert_only:
        onnx_path = Path(args.convert_only)
        if onnx_path.exists():
            convert_to_mindspore(onnx_path)
        else:
            print(f"❌ No se encontró: {onnx_path}")
        return
    
    if not check_dependencies():
        return
    
    # Flujo principal
    model_path = None
    
    if args.from_pretrained:
        success, model_path = download_pretrained()
        if not success:
            print("⚠ No se pudo obtener modelo pre-entrenado, entrenando...")
            model_path = train_mobilenet()
    elif args.train:
        model_path = train_mobilenet()
    else:
        # Por defecto: intentar pre-entrenado, luego entrenar
        success, model_path = download_pretrained()
        if not success:
            print("\n💡 Para entrenar desde cero, ejecuta:")
            print("   python prepare_plant_disease_model.py --train")
            return
    
    if model_path and model_path.exists():
        onnx_path = export_to_onnx(model_path)
        if onnx_path:
            ms_path = convert_to_mindspore(onnx_path)
            if ms_path:
                print("\n" + "="*60)
                print("   ¡COMPLETADO!")
                print("="*60)
                print(f"""
Modelo listo: {ms_path}

Próximos pasos:
1. Verifica que el archivo esté en assets/
2. Recompila la app con: ./gradlew assembleDebug
3. Instala en el dispositivo
4. El botón de cámara debería aparecer 📸

Tamaño del modelo: ~{ms_path.stat().st_size / 1024 / 1024:.1f} MB
                """)


if __name__ == "__main__":
    main()
