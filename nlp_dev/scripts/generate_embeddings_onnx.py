#!/usr/bin/env python3
"""
Genera embeddings de la KB usando ONNX Runtime directamente.
Esto produce embeddings compatibles con MindSpore (mismo modelo ONNX).
Usa menos RAM que sentence-transformers.
"""

import json
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer
import os

# Paths
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
KB_PATH = os.path.join(PROJECT_DIR, "..", "app", "src", "main", "assets", "agrochat_knowledge_base.json")
ONNX_MODEL_PATH = os.path.join(PROJECT_DIR, "models", "exported", "sentence_encoder.onnx")
OUTPUT_PATH = os.path.join(PROJECT_DIR, "..", "app", "src", "main", "assets", "kb_embeddings.npy")

# Tokenizer (el mismo que usa Android - XLM-RoBERTa)
TOKENIZER_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MAX_LENGTH = 128

def mean_pooling(model_output, attention_mask):
    """Mean pooling - same as sentence-transformers"""
    token_embeddings = model_output  # [batch, seq_len, hidden_size]
    input_mask_expanded = np.expand_dims(attention_mask, axis=-1)
    input_mask_expanded = np.broadcast_to(input_mask_expanded, token_embeddings.shape).astype(np.float32)
    
    sum_embeddings = np.sum(token_embeddings * input_mask_expanded, axis=1)
    sum_mask = np.clip(np.sum(input_mask_expanded, axis=1), a_min=1e-9, a_max=None)
    
    return sum_embeddings / sum_mask

def normalize(embeddings):
    """L2 normalize"""
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    return embeddings / np.clip(norms, a_min=1e-9, a_max=None)

def main():
    print("=== Generando embeddings con ONNX Runtime ===")
    print(f"Modelo: {ONNX_MODEL_PATH}")
    print(f"KB: {KB_PATH}")
    
    # Cargar KB
    with open(KB_PATH, 'r', encoding='utf-8') as f:
        kb_data = json.load(f)
    
    # Extraer todas las preguntas en orden (entries es una lista)
    questions = []
    entries = kb_data['entries']
    # Ordenar por ID para consistencia
    entries_sorted = sorted(entries, key=lambda x: x['id'])
    for entry in entries_sorted:
        for q in entry['questions']:
            questions.append(q)
    
    print(f"Total preguntas: {len(questions)}")
    
    # Cargar tokenizer
    print("Cargando tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_NAME)
    
    # Cargar modelo ONNX
    print("Cargando modelo ONNX...")
    session = ort.InferenceSession(ONNX_MODEL_PATH)
    
    # Ver inputs/outputs del modelo
    print("Inputs del modelo:")
    for inp in session.get_inputs():
        print(f"  {inp.name}: {inp.shape} {inp.type}")
    print("Outputs del modelo:")
    for out in session.get_outputs():
        print(f"  {out.name}: {out.shape}")
    
    # Generar embeddings
    print("Generando embeddings...")
    all_embeddings = []
    
    for i, question in enumerate(questions):
        # Tokenizar
        encoded = tokenizer(
            question,
            padding='max_length',
            truncation=True,
            max_length=MAX_LENGTH,
            return_tensors='np'
        )
        
        input_ids = encoded['input_ids'].astype(np.int64)
        attention_mask = encoded['attention_mask'].astype(np.int64)
        
        # Inferencia ONNX
        outputs = session.run(
            None,
            {
                'input_ids': input_ids,
                'attention_mask': attention_mask
            }
        )
        
        # outputs[0] = last_hidden_state [1, seq_len, hidden_size]
        # outputs[1] = pooled output "1606" [1, 384] - esto es lo que usa MindSpore en Android
        
        # Usar el output pooled (índice 1) para coincidir con MindSpore
        pooled_output = outputs[1]  # [1, 384]
        
        # Normalizar
        embedding = normalize(pooled_output)
        
        all_embeddings.append(embedding[0])
        
        if (i + 1) % 20 == 0:
            print(f"  Procesadas {i + 1}/{len(questions)} preguntas")
    
    # Convertir a numpy array
    embeddings_array = np.array(all_embeddings, dtype=np.float32)
    
    print(f"\nEmbeddings shape: {embeddings_array.shape}")
    print(f"Norma del primero: {np.linalg.norm(embeddings_array[0])}")
    print(f"Primeros valores [0]: {embeddings_array[0][:5]}")
    
    # Guardar
    np.save(OUTPUT_PATH, embeddings_array)
    print(f"\nGuardado en: {OUTPUT_PATH}")
    
    # Verificar
    loaded = np.load(OUTPUT_PATH)
    print(f"Verificación - shape: {loaded.shape}")
    print(f"Verificación - primeros valores: {loaded[0][:5]}")

if __name__ == "__main__":
    main()
