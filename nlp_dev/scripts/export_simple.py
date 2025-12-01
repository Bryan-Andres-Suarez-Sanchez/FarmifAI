#!/usr/bin/env python3
"""
Script simplificado para exportar modelos GPT-2 a ONNX
Compatible con versiones antiguas de PyTorch/Transformers
"""

import torch
import json
import os
from pathlib import Path
from transformers import GPT2LMHeadModel, GPT2Config

def export_to_onnx_simple(model_path: str, output_path: str, max_length: int = 64):
    """
    Exporta modelo GPT-2 a ONNX de forma simple.
    """
    print(f"📦 Cargando modelo desde: {model_path}")
    
    # Cargar configuración
    config = GPT2Config.from_pretrained(model_path)
    config.use_cache = False  # Deshabilitar cache para ONNX
    
    # Cargar modelo
    model = GPT2LMHeadModel.from_pretrained(
        model_path,
        config=config
    )
    model.eval()
    
    # Crear directorio de salida
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    # Preparar inputs de ejemplo
    vocab_size = config.vocab_size
    dummy_input = torch.randint(0, vocab_size, (1, max_length), dtype=torch.long)
    
    print(f"🔄 Exportando a ONNX...")
    print(f"   Vocab size: {vocab_size}")
    print(f"   Max length: {max_length}")
    
    # Exportar a ONNX con API antigua compatible
    torch.onnx.export(
        model,
        (dummy_input,),
        output_path,
        opset_version=14,
        input_names=["input_ids"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence_length"},
            "logits": {0: "batch_size", 1: "sequence_length"}
        },
        do_constant_folding=True,
        export_params=True,
        verbose=False
    )
    
    print(f"✅ Modelo exportado a: {output_path}")
    
    # Guardar configuración
    config_path = output_path.replace('.onnx', '_config.json')
    config_data = {
        "model_path": model_path,
        "max_length": max_length,
        "vocab_size": vocab_size,
        "num_layers": config.n_layer,
        "hidden_size": config.n_embd,
        "num_heads": config.n_head,
    }
    
    with open(config_path, 'w') as f:
        json.dump(config_data, f, indent=2)
    
    print(f"✅ Configuración guardada: {config_path}")
    
    # Copiar archivos del tokenizer
    tokenizer_files = ['vocab.json', 'merges.txt', 'tokenizer.json', 'tokenizer_config.json', 'special_tokens_map.json']
    output_dir = os.path.dirname(output_path)
    
    for filename in tokenizer_files:
        src = os.path.join(model_path, filename)
        if os.path.exists(src):
            dst = os.path.join(output_dir, filename)
            import shutil
            shutil.copy(src, dst)
            print(f"✅ Copiado: {filename}")
    
    return output_path


def create_android_package(onnx_path: str, model_name: str):
    """
    Crea estructura de archivos para Android.
    """
    base_dir = os.path.dirname(onnx_path)
    android_dir = os.path.join(base_dir, f"{model_name}_android_package")
    
    os.makedirs(android_dir, exist_ok=True)
    
    # Copiar modelo ONNX
    import shutil
    shutil.copy(onnx_path, os.path.join(android_dir, f"{model_name}.onnx"))
    
    # Copiar configuración
    config_path = onnx_path.replace('.onnx', '_config.json')
    if os.path.exists(config_path):
        shutil.copy(config_path, os.path.join(android_dir, "config.json"))
    
    # Copiar tokenizer
    tokenizer_files = ['vocab.json', 'merges.txt', 'tokenizer.json']
    for filename in tokenizer_files:
        src = os.path.join(base_dir, filename)
        if os.path.exists(src):
            shutil.copy(src, os.path.join(android_dir, filename))
    
    # Crear README
    readme = f"""# {model_name} - Paquete para Android

## Archivos incluidos:

- `{model_name}.onnx` - Modelo en formato ONNX
- `config.json` - Configuración del modelo
- `vocab.json` - Vocabulario del tokenizer
- `merges.txt` - Merges para BPE
- `tokenizer.json` - Configuración completa del tokenizer

## Instrucciones de uso:

1. Copia estos archivos a `app/src/main/assets/` en tu proyecto Android

2. Actualiza MainActivity.kt para cargar el nuevo modelo:
   ```kotlin
   private val modelPath = "file:///android_asset/{model_name}.onnx"
   private val tokenizerPath = "file:///android_asset/tokenizer.json"
   ```

3. Recompila y ejecuta la aplicación

## Notas:

- El modelo está en formato ONNX (no MindSpore)
- Necesitarás usar ONNX Runtime en lugar de MindSpore Lite
- O convertir este ONNX a MindSpore .ms usando herramientas de Huawei

Para convertir a MindSpore:
- Usa MindSpore Lite Converter Tool
- Comando: `converter_lite --fmk=ONNX --modelFile={model_name}.onnx --outputFile={model_name}`
"""
    
    with open(os.path.join(android_dir, "README.md"), 'w') as f:
        f.write(readme)
    
    print(f"\n📦 Paquete Android creado en: {android_dir}")
    print(f"   Contiene: {len(os.listdir(android_dir))} archivos")
    
    return android_dir


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Exportar modelo GPT-2 a ONNX (versión simple)")
    parser.add_argument("--model", required=True, help="Ruta al modelo entrenado")
    parser.add_argument("--output", required=True, help="Ruta de salida del ONNX")
    parser.add_argument("--max-length", type=int, default=64, help="Longitud máxima de secuencia")
    parser.add_argument("--package-for-android", action="store_true", help="Crear paquete para Android")
    parser.add_argument("--model-name", default="model", help="Nombre del modelo para Android")
    
    args = parser.parse_args()
    
    try:
        # Exportar a ONNX
        onnx_path = export_to_onnx_simple(args.model, args.output, args.max_length)
        
        # Crear paquete Android si se solicita
        if args.package_for_android:
            android_dir = create_android_package(onnx_path, args.model_name)
        
        print("\n✅ ¡Exportación completada con éxito!")
        
    except Exception as e:
        print(f"\n❌ Error durante exportación: {e}")
        import traceback
        traceback.print_exc()
        exit(1)
