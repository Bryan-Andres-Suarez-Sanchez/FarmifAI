#!/usr/bin/env python3
"""
🔍 Exportar Sentence Encoder a MindSpore Lite

Este script:
1. Descarga un modelo de sentence embeddings multilingüe
2. Lo exporta a ONNX
3. Lo convierte a MindSpore Lite (.ms)
4. Pre-calcula embeddings de la base de conocimiento

Modelos disponibles (de menor a mayor):
- paraphrase-multilingual-MiniLM-L12-v2: 471MB, buena calidad
- distiluse-base-multilingual-cased-v2: 539MB, muy buena calidad  
- paraphrase-multilingual-mpnet-base-v2: 1.1GB, mejor calidad

Para móvil usaremos el más pequeño.
"""

import os
import sys
import json
import subprocess
from pathlib import Path

# Configurar rutas
PROJECT_ROOT = Path(__file__).parent.parent
MODELS_DIR = PROJECT_ROOT / "models"
EXPORTED_DIR = MODELS_DIR / "exported"
KB_PATH = PROJECT_ROOT / "data" / "datasets" / "agrochat_knowledge_base.json"
ANDROID_ASSETS = PROJECT_ROOT.parent / "app" / "src" / "main" / "assets"

# MindSpore Lite converter
MS_LITE_HOME = PROJECT_ROOT / "tools" / "mindspore-lite-2.2.0-linux-x64"
CONVERTER_PATH = MS_LITE_HOME / "tools" / "converter" / "converter" / "converter_lite"

# Modelo a usar - el más pequeño multilingüe
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MAX_SEQ_LENGTH = 128  # Suficiente para preguntas cortas


def step_1_download_model():
    """Descargar el modelo de sentence-transformers."""
    print("\n" + "="*60)
    print("[1/5] Descargando modelo Sentence Encoder...")
    print("="*60)
    
    from sentence_transformers import SentenceTransformer
    
    print(f"📦 Modelo: {MODEL_NAME}")
    model = SentenceTransformer(MODEL_NAME)
    
    # Guardar localmente
    local_path = MODELS_DIR / "sentence_encoder"
    model.save(str(local_path))
    print(f"✅ Modelo guardado en: {local_path}")
    
    return model


def step_2_export_to_onnx(model):
    """Exportar el modelo a ONNX."""
    print("\n" + "="*60)
    print("[2/5] Exportando a ONNX...")
    print("="*60)
    
    import torch
    from transformers import AutoTokenizer, AutoModel
    
    # Cargar componentes
    model_path = MODELS_DIR / "sentence_encoder"
    tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    transformer = AutoModel.from_pretrained(str(model_path))
    transformer.eval()
    
    # Crear input de ejemplo
    dummy_text = "¿Cómo cultivar tomates?"
    inputs = tokenizer(
        dummy_text,
        padding="max_length",
        truncation=True,
        max_length=MAX_SEQ_LENGTH,
        return_tensors="pt"
    )
    
    onnx_path = EXPORTED_DIR / "sentence_encoder.onnx"
    EXPORTED_DIR.mkdir(parents=True, exist_ok=True)
    
    print(f"🔄 Exportando a: {onnx_path}")
    
    # Exportar solo el transformer (sin pooling, lo haremos en Android)
    torch.onnx.export(
        transformer,
        (inputs["input_ids"], inputs["attention_mask"]),
        str(onnx_path),
        opset_version=14,
        input_names=["input_ids", "attention_mask"],
        output_names=["last_hidden_state"],
        # Dimensiones FIJAS para MindSpore Lite
        do_constant_folding=True,
    )
    
    size_mb = onnx_path.stat().st_size / (1024 * 1024)
    print(f"✅ ONNX exportado: {onnx_path} ({size_mb:.1f} MB)")
    
    # Guardar también el tokenizer
    tokenizer.save_pretrained(str(EXPORTED_DIR / "tokenizer"))
    print(f"✅ Tokenizer guardado")
    
    return onnx_path


def step_3_convert_to_mindspore(onnx_path):
    """Convertir ONNX a MindSpore Lite."""
    print("\n" + "="*60)
    print("[3/5] Convirtiendo a MindSpore Lite...")
    print("="*60)
    
    if not CONVERTER_PATH.exists():
        print(f"⚠️ Converter no encontrado: {CONVERTER_PATH}")
        print("   Saltando conversión a MindSpore...")
        return None
    
    output_path = EXPORTED_DIR / "sentence_encoder"
    ms_path = EXPORTED_DIR / "sentence_encoder.ms"
    
    # Configurar entorno
    env = os.environ.copy()
    env["LD_LIBRARY_PATH"] = f"{MS_LITE_HOME}/runtime/lib:{MS_LITE_HOME}/tools/converter/lib:" + env.get("LD_LIBRARY_PATH", "")
    
    # Comando de conversión con FP16
    cmd = [
        str(CONVERTER_PATH),
        "--fmk=ONNX",
        f"--modelFile={onnx_path}",
        f"--outputFile={output_path}",
        "--optimize=general",
        "--fp16=on"
    ]
    
    print(f"🔄 Ejecutando: {' '.join(cmd[:4])}...")
    
    result = subprocess.run(cmd, env=env, capture_output=True, text=True)
    
    if result.returncode != 0:
        print(f"❌ Error en conversión:")
        print(result.stderr)
        return None
    
    if ms_path.exists():
        size_mb = ms_path.stat().st_size / (1024 * 1024)
        print(f"✅ MindSpore Lite: {ms_path} ({size_mb:.1f} MB)")
        return ms_path
    else:
        print("❌ No se generó archivo .ms")
        return None


def step_4_generate_embeddings():
    """Pre-calcular embeddings de todas las preguntas."""
    print("\n" + "="*60)
    print("[4/5] Generando embeddings de la base de conocimiento...")
    print("="*60)
    
    from sentence_transformers import SentenceTransformer
    import numpy as np
    
    # Cargar modelo
    model = SentenceTransformer(str(MODELS_DIR / "sentence_encoder"))
    
    # Cargar base de conocimiento
    with open(KB_PATH, 'r', encoding='utf-8') as f:
        kb = json.load(f)
    
    # Preparar datos para embeddings
    all_questions = []
    question_to_entry = []  # Mapeo pregunta -> índice de entrada
    
    for entry in kb["entries"]:
        for q in entry["questions"]:
            all_questions.append(q)
            question_to_entry.append(entry["id"])
    
    print(f"📊 Total de preguntas: {len(all_questions)}")
    print(f"📊 Total de entradas: {len(kb['entries'])}")
    
    # Generar embeddings
    print("🔄 Calculando embeddings...")
    embeddings = model.encode(all_questions, show_progress_bar=True)
    
    # Guardar embeddings y mapeo
    embeddings_data = {
        "questions": all_questions,
        "entry_ids": question_to_entry,
        "embeddings": embeddings.tolist(),
        "embedding_dim": embeddings.shape[1],
        "model": MODEL_NAME
    }
    
    embeddings_path = EXPORTED_DIR / "kb_embeddings.json"
    with open(embeddings_path, 'w', encoding='utf-8') as f:
        json.dump(embeddings_data, f, ensure_ascii=False)
    
    size_mb = embeddings_path.stat().st_size / (1024 * 1024)
    print(f"✅ Embeddings guardados: {embeddings_path} ({size_mb:.1f} MB)")
    
    # También guardar en formato binario más compacto para Android
    np.save(EXPORTED_DIR / "kb_embeddings.npy", embeddings)
    print(f"✅ Embeddings binarios: {EXPORTED_DIR / 'kb_embeddings.npy'}")
    
    return embeddings_path


def step_5_copy_to_android(ms_path):
    """Copiar archivos a Android assets."""
    print("\n" + "="*60)
    print("[5/5] Copiando a Android assets...")
    print("="*60)
    
    import shutil
    
    ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
    
    files_to_copy = [
        (EXPORTED_DIR / "kb_embeddings.npy", "kb_embeddings.npy"),
        (KB_PATH, "agrochat_knowledge_base.json"),
    ]
    
    if ms_path and ms_path.exists():
        files_to_copy.append((ms_path, "sentence_encoder.ms"))
    
    # Copiar tokenizer
    tokenizer_src = EXPORTED_DIR / "tokenizer"
    if tokenizer_src.exists():
        # Solo necesitamos tokenizer.json para Android
        tokenizer_json = tokenizer_src / "tokenizer.json"
        if tokenizer_json.exists():
            files_to_copy.append((tokenizer_json, "sentence_tokenizer.json"))
    
    for src, dst in files_to_copy:
        if src.exists():
            dest_path = ANDROID_ASSETS / dst
            shutil.copy2(src, dest_path)
            size_mb = dest_path.stat().st_size / (1024 * 1024)
            print(f"✅ {dst} ({size_mb:.2f} MB)")
        else:
            print(f"⚠️ No encontrado: {src}")
    
    print(f"\n📱 Archivos copiados a: {ANDROID_ASSETS}")


def test_similarity():
    """Probar el sistema de similitud."""
    print("\n" + "="*60)
    print("🧪 PRUEBA DE SIMILITUD")
    print("="*60)
    
    from sentence_transformers import SentenceTransformer
    import numpy as np
    
    # Cargar modelo y embeddings
    model = SentenceTransformer(str(MODELS_DIR / "sentence_encoder"))
    
    with open(EXPORTED_DIR / "kb_embeddings.json", 'r', encoding='utf-8') as f:
        kb_data = json.load(f)
    
    with open(KB_PATH, 'r', encoding='utf-8') as f:
        kb = json.load(f)
    
    kb_embeddings = np.array(kb_data["embeddings"])
    
    # Crear diccionario de respuestas
    id_to_answer = {e["id"]: e["answer"] for e in kb["entries"]}
    
    # Preguntas de prueba
    test_questions = [
        "¿Cómo siembro tomates?",
        "tengo mosca blanca que hago",
        "hojas amarillas en mi planta",
        "hola",
        "que fertilizante le pongo al maiz",
        "cuando cosechar el tomate",
    ]
    
    for q in test_questions:
        print(f"\n❓ Pregunta: '{q}'")
        
        # Generar embedding de la pregunta
        q_embedding = model.encode([q])[0]
        
        # Calcular similitud coseno
        similarities = np.dot(kb_embeddings, q_embedding) / (
            np.linalg.norm(kb_embeddings, axis=1) * np.linalg.norm(q_embedding)
        )
        
        # Encontrar mejor match
        best_idx = np.argmax(similarities)
        best_score = similarities[best_idx]
        best_question = kb_data["questions"][best_idx]
        best_entry_id = kb_data["entry_ids"][best_idx]
        
        print(f"📌 Match: '{best_question}' (score: {best_score:.3f})")
        print(f"💬 Respuesta: {id_to_answer[best_entry_id][:100]}...")


def main():
    print("\n" + "🚀"*30)
    print("   EXPORTACIÓN DE SENTENCE ENCODER PARA AGROCHAT")
    print("🚀"*30)
    
    try:
        # Paso 1: Descargar modelo
        model = step_1_download_model()
        
        # Paso 2: Exportar a ONNX
        onnx_path = step_2_export_to_onnx(model)
        
        # Paso 3: Convertir a MindSpore Lite
        ms_path = step_3_convert_to_mindspore(onnx_path)
        
        # Paso 4: Generar embeddings de KB
        step_4_generate_embeddings()
        
        # Paso 5: Copiar a Android
        step_5_copy_to_android(ms_path)
        
        # Prueba de similitud
        test_similarity()
        
        print("\n" + "✅"*30)
        print("   ¡EXPORTACIÓN COMPLETADA!")
        print("✅"*30)
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
