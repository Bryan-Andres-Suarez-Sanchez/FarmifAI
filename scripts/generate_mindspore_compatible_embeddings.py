#!/usr/bin/env python3
"""
Generar embeddings compatibles con MindSpore.

Este script genera embeddings usando el mismo método que MindSpore usa en Android:
1. Carga el modelo transformer
2. Obtiene el pooler_output (o CLS token) en lugar de hacer mean pooling
3. Normaliza los embeddings con L2 norm

Esto asegura que los embeddings pre-calculados sean compatibles con los que
MindSpore genera en tiempo de ejecución.
"""

import os
import sys
import json
import numpy as np
from pathlib import Path

# Configurar rutas
PROJECT_ROOT = Path(__file__).parent
APP_ASSETS = PROJECT_ROOT / "app" / "src" / "main" / "assets"
KB_PATH = APP_ASSETS / "agrochat_knowledge_base.json"

# Modelo
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MAX_SEQ_LENGTH = 128


def load_model_components():
    """Cargar tokenizer y modelo transformer."""
    print("📦 Cargando modelo y tokenizer...")
    
    from transformers import AutoTokenizer, AutoModel
    import torch
    
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()
    
    print(f"✅ Modelo cargado: {MODEL_NAME}")
    print(f"   - Hidden size: {model.config.hidden_size}")
    print(f"   - Vocab size: {model.config.vocab_size}")
    
    return tokenizer, model


def encode_with_pooler_output(tokenizer, model, texts, batch_size=32):
    """
    Generar embeddings usando pooler_output en lugar de mean pooling.
    
    MindSpore parece usar el output[1] que tiene 384 dims directamente,
    lo cual corresponde al pooler_output del modelo BERT/RoBERTa.
    """
    import torch
    
    all_embeddings = []
    
    for i in range(0, len(texts), batch_size):
        batch_texts = texts[i:i + batch_size]
        
        # Tokenizar
        inputs = tokenizer(
            batch_texts,
            padding="max_length",
            truncation=True,
            max_length=MAX_SEQ_LENGTH,
            return_tensors="pt"
        )
        
        with torch.no_grad():
            outputs = model(**inputs)
            
            # El modelo tiene dos outputs principales:
            # 1. last_hidden_state: [batch, seq_len, hidden_size] - todos los tokens
            # 2. pooler_output: [batch, hidden_size] - output del pooler (CLS procesado)
            
            # Intentar usar pooler_output si existe
            if hasattr(outputs, 'pooler_output') and outputs.pooler_output is not None:
                embeddings = outputs.pooler_output
                print(f"   Usando pooler_output: {embeddings.shape}")
            else:
                # Fallback: usar el [CLS] token directamente (primer token)
                embeddings = outputs.last_hidden_state[:, 0, :]
                print(f"   Usando CLS token: {embeddings.shape}")
            
            # Normalizar L2
            embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)
            
            all_embeddings.append(embeddings.cpu().numpy())
        
        print(f"   Procesado: {min(i + batch_size, len(texts))}/{len(texts)}")
    
    return np.vstack(all_embeddings)


def encode_with_mean_pooling(tokenizer, model, texts, batch_size=32):
    """
    Generar embeddings usando mean pooling (como sentence-transformers).
    Este es el método estándar de sentence-transformers.
    """
    import torch
    
    all_embeddings = []
    
    for i in range(0, len(texts), batch_size):
        batch_texts = texts[i:i + batch_size]
        
        inputs = tokenizer(
            batch_texts,
            padding="max_length",
            truncation=True,
            max_length=MAX_SEQ_LENGTH,
            return_tensors="pt"
        )
        
        with torch.no_grad():
            outputs = model(**inputs)
            
            # Mean pooling sobre todos los tokens (con attention mask)
            attention_mask = inputs['attention_mask']
            token_embeddings = outputs.last_hidden_state
            
            # Expandir attention_mask para broadcast
            input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
            
            # Sumar embeddings de tokens válidos
            sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
            sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # Normalizar L2
            embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)
            
            all_embeddings.append(embeddings.cpu().numpy())
        
        print(f"   Procesado: {min(i + batch_size, len(texts))}/{len(texts)}")
    
    return np.vstack(all_embeddings)


def test_similarity(embeddings, questions, test_query):
    """Probar similitud con una query."""
    from sentence_transformers import SentenceTransformer
    
    # Generar embedding de prueba con sentence-transformers (para comparar)
    st_model = SentenceTransformer(MODEL_NAME)
    st_embedding = st_model.encode([test_query])[0]
    st_embedding = st_embedding / np.linalg.norm(st_embedding)
    
    # Calcular similitud con embeddings generados
    similarities = np.dot(embeddings, st_embedding)
    
    print(f"\n🔍 Test query: '{test_query}'")
    print(f"   Top 5 matches:")
    
    top_indices = np.argsort(similarities)[-5:][::-1]
    for idx in top_indices:
        print(f"   - [{similarities[idx]:.4f}] {questions[idx]}")
    
    return similarities


def main():
    print("\n" + "="*70)
    print("   GENERADOR DE EMBEDDINGS COMPATIBLES CON MINDSPORE")
    print("="*70)
    
    # 1. Cargar base de conocimiento
    print("\n📂 Cargando base de conocimiento...")
    with open(KB_PATH, 'r', encoding='utf-8') as f:
        kb = json.load(f)
    
    # Extraer todas las preguntas
    all_questions = []
    question_to_entry = []
    
    for entry in kb["entries"]:
        for q in entry["questions"]:
            all_questions.append(q)
            question_to_entry.append(entry["id"])
    
    print(f"✅ {len(all_questions)} preguntas de {len(kb['entries'])} entradas")
    
    # 2. Cargar modelo
    tokenizer, model = load_model_components()
    
    # 3. Generar embeddings con diferentes métodos
    print("\n🔄 Generando embeddings con POOLER OUTPUT...")
    pooler_embeddings = encode_with_pooler_output(tokenizer, model, all_questions)
    print(f"✅ Shape: {pooler_embeddings.shape}")
    
    print("\n🔄 Generando embeddings con MEAN POOLING...")
    mean_embeddings = encode_with_mean_pooling(tokenizer, model, all_questions)
    print(f"✅ Shape: {mean_embeddings.shape}")
    
    # 4. Comparar métodos
    print("\n📊 Comparación de métodos:")
    
    test_queries = [
        "control de plagas en papa",
        "plagas de la papa",
        "¿Cómo cultivar tomates?",
        "pH del suelo",
    ]
    
    print("\n=== POOLER OUTPUT ===")
    for q in test_queries:
        # Generar embedding de test con el mismo método
        test_inputs = tokenizer([q], padding="max_length", truncation=True, 
                                max_length=MAX_SEQ_LENGTH, return_tensors="pt")
        import torch
        with torch.no_grad():
            test_outputs = model(**test_inputs)
            if hasattr(test_outputs, 'pooler_output') and test_outputs.pooler_output is not None:
                test_emb = test_outputs.pooler_output[0]
            else:
                test_emb = test_outputs.last_hidden_state[0, 0, :]
            test_emb = torch.nn.functional.normalize(test_emb, p=2, dim=0).numpy()
        
        similarities = np.dot(pooler_embeddings, test_emb)
        best_idx = np.argmax(similarities)
        print(f"   '{q}' -> [{similarities[best_idx]:.4f}] '{all_questions[best_idx]}'")
    
    print("\n=== MEAN POOLING ===")
    for q in test_queries:
        test_inputs = tokenizer([q], padding="max_length", truncation=True, 
                                max_length=MAX_SEQ_LENGTH, return_tensors="pt")
        import torch
        with torch.no_grad():
            test_outputs = model(**test_inputs)
            attention_mask = test_inputs['attention_mask']
            token_embeddings = test_outputs.last_hidden_state
            input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
            sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
            sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
            test_emb = (sum_embeddings / sum_mask)[0]
            test_emb = torch.nn.functional.normalize(test_emb, p=2, dim=0).numpy()
        
        similarities = np.dot(mean_embeddings, test_emb)
        best_idx = np.argmax(similarities)
        print(f"   '{q}' -> [{similarities[best_idx]:.4f}] '{all_questions[best_idx]}'")
    
    # 5. Guardar embeddings
    print("\n💾 Guardando embeddings...")
    
    # Guardar pooler embeddings (para probar con MindSpore)
    pooler_path = APP_ASSETS / "kb_embeddings_pooler.npy"
    np.save(pooler_path, pooler_embeddings.astype(np.float32))
    print(f"✅ Pooler embeddings: {pooler_path}")
    
    # Guardar mean embeddings (original de sentence-transformers)
    mean_path = APP_ASSETS / "kb_embeddings_mean.npy"
    np.save(mean_path, mean_embeddings.astype(np.float32))
    print(f"✅ Mean embeddings: {mean_path}")
    
    # Determinar cuál usar basándose en el test
    # Por ahora, vamos a guardar ambos y probar en el dispositivo
    
    # También guardar el mapeo de preguntas
    mapping_data = {
        "questions": all_questions,
        "entry_ids": question_to_entry,
        "embedding_dim": pooler_embeddings.shape[1],
        "model": MODEL_NAME,
        "method": "pooler_output"
    }
    
    mapping_path = APP_ASSETS / "kb_embeddings_mapping.json"
    with open(mapping_path, 'w', encoding='utf-8') as f:
        json.dump(mapping_data, f, ensure_ascii=False, indent=2)
    print(f"✅ Mapping: {mapping_path}")
    
    print("\n" + "="*70)
    print("   ¡COMPLETADO!")
    print("="*70)
    print(f"""
Próximos pasos:
1. Copiar kb_embeddings_pooler.npy como kb_embeddings.npy
2. Instalar en el dispositivo y probar
3. Si funciona, listo. Si no, probar con kb_embeddings_mean.npy
    """)


if __name__ == "__main__":
    main()
