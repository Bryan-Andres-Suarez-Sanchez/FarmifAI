#!/usr/bin/env python3
"""
Test de Similitud Semántica para AgroChat

Este script evalúa la calidad de la búsqueda semántica comparando:
1. Queries de usuario vs preguntas de la KB
2. Diferentes métodos de pooling (pooler_output vs mean_pooling)
3. Casos problemáticos conocidos

Genera un reporte detallado con métricas de precisión.
"""

import os
import json
import numpy as np
from pathlib import Path
from datetime import datetime
from typing import List, Tuple, Dict

# Configuración
PROJECT_ROOT = Path(__file__).parent
APP_ASSETS = PROJECT_ROOT / "app" / "src" / "main" / "assets"
KB_PATH = APP_ASSETS / "agrochat_knowledge_base.json"
EMBEDDINGS_PATH = APP_ASSETS / "kb_embeddings.npy"

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MAX_SEQ_LENGTH = 128

# ═══════════════════════════════════════════════════════════════════════════════
# CASOS DE PRUEBA
# ═══════════════════════════════════════════════════════════════════════════════

# Formato: (query_usuario, palabras_clave_esperadas_en_respuesta, categoria_esperada)
# Si la respuesta top-1 contiene alguna de las palabras clave, es un match correcto

TEST_CASES = [
    # === RIEGO ===
    ("cómo regar las plántulas de tomate", ["regar", "riego", "plántula", "tomate", "agua"], "riego"),
    ("cuánta agua necesita el tomate", ["agua", "riego", "tomate", "litros"], "riego"),
    ("frecuencia de riego para tomates", ["riego", "frecuencia", "tomate"], "riego"),
    ("riego por goteo ventajas", ["goteo", "riego", "ventaja"], "riego"),
    ("exceso de agua en plantas", ["exceso", "agua", "encharcamiento"], "riego"),
    
    # === PLAGAS Y ENFERMEDADES ===
    ("plagas del tomate", ["plaga", "tomate", "insecto"], "plagas"),
    ("gusano cogollero en maíz", ["gusano", "cogollero", "maíz"], "plagas"),
    ("mosca blanca control", ["mosca", "blanca", "control"], "plagas"),
    ("hojas amarillas en tomate", ["amarill", "hoja", "tomate", "deficiencia"], "enfermedades"),
    ("moho en hojas de tomate", ["moho", "hongo", "tomate"], "enfermedades"),
    ("manchas negras en hojas", ["mancha", "hoja", "enfermedad"], "enfermedades"),
    ("trips en cultivos", ["trips", "plaga", "control"], "plagas"),
    ("pulgón como eliminar", ["pulgón", "control", "eliminar"], "plagas"),
    
    # === FERTILIZACIÓN ===
    ("fertilizante para tomate", ["fertiliz", "tomate", "NPK", "nutriente"], "fertilizacion"),
    ("abono orgánico casero", ["abono", "orgánico", "compost"], "fertilizacion"),
    ("cuándo fertilizar", ["fertiliz", "aplicar", "momento"], "fertilizacion"),
    ("NPK qué significa", ["NPK", "nitrógeno", "fósforo", "potasio"], "fertilizacion"),
    ("deficiencia de nitrógeno", ["nitrógeno", "deficiencia", "amarill"], "fertilizacion"),
    
    # === CULTIVO/SIEMBRA ===
    ("cómo sembrar tomate", ["sembrar", "tomate", "siembra", "plantar"], "cultivo"),
    ("época de siembra maíz", ["siembra", "época", "maíz", "sembrar"], "cultivo"),
    ("distancia entre plantas", ["distancia", "espacio", "plantar"], "cultivo"),
    ("preparar terreno para cultivo", ["terreno", "suelo", "preparar"], "cultivo"),
    ("rotación de cultivos", ["rotación", "cultivo", "alternar"], "cultivo"),
    
    # === SUELO ===
    ("pH del suelo ideal", ["pH", "suelo", "ácido", "alcalino"], "suelo"),
    ("mejorar drenaje del suelo", ["drenaje", "suelo", "agua"], "suelo"),
    ("tipos de suelo agricultura", ["suelo", "tipo", "arcill", "aren"], "suelo"),
    
    # === COSECHA ===
    ("cuándo cosechar tomates", ["cosechar", "tomate", "madurez"], "cosecha"),
    ("señales de madurez del tomate", ["madur", "tomate", "color", "cosechar"], "cosecha"),
    
    # === SALUDOS Y OTROS ===
    ("hola", ["hola", "bienvenido", "ayuda", "asistente"], "saludo"),
    ("buenos días", ["hola", "buenos", "día"], "saludo"),
    ("qué puedes hacer", ["ayuda", "puedo", "información"], "capacidades"),
    ("gracias", ["gracias", "gusto", "ayuda"], "despedida"),
    
    # === CASOS DIFÍCILES (conocidos por fallar) ===
    ("cómo regar plántulas de tomate", ["regar", "plántula", "tomate"], "riego"),  # NO debe dar "moho"
    ("necesito agua para mis tomates", ["agua", "riego", "tomate"], "riego"),
    ("mis plantas de tomate se ven secas", ["riego", "agua", "seco"], "riego"),
    ("las hojas del tomate están caídas", ["marchit", "agua", "riego", "hoja"], "problemas"),
]

# ═══════════════════════════════════════════════════════════════════════════════


def load_kb() -> Tuple[Dict, List[str], List[int]]:
    """Cargar KB y extraer preguntas."""
    with open(KB_PATH, 'r', encoding='utf-8') as f:
        kb = json.load(f)
    
    questions = []
    entry_ids = []
    
    for entry in kb["entries"]:
        for q in entry["questions"]:
            questions.append(q)
            entry_ids.append(entry["id"])
    
    return kb, questions, entry_ids


def load_embeddings() -> np.ndarray:
    """Cargar embeddings pre-calculados."""
    return np.load(EMBEDDINGS_PATH)


def load_model():
    """Cargar modelo transformer."""
    from transformers import AutoTokenizer, AutoModel
    import torch
    
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()
    
    return tokenizer, model


def compute_embedding_pooler(tokenizer, model, text: str) -> np.ndarray:
    """Computar embedding usando pooler_output."""
    import torch
    
    inputs = tokenizer([text], padding="max_length", truncation=True,
                       max_length=MAX_SEQ_LENGTH, return_tensors="pt")
    
    with torch.no_grad():
        outputs = model(**inputs)
        if hasattr(outputs, 'pooler_output') and outputs.pooler_output is not None:
            emb = outputs.pooler_output[0]
        else:
            emb = outputs.last_hidden_state[0, 0, :]
        emb = torch.nn.functional.normalize(emb, p=2, dim=0).numpy()
    
    return emb


def compute_embedding_mean(tokenizer, model, text: str) -> np.ndarray:
    """Computar embedding usando mean pooling."""
    import torch
    
    inputs = tokenizer([text], padding="max_length", truncation=True,
                       max_length=MAX_SEQ_LENGTH, return_tensors="pt")
    
    with torch.no_grad():
        outputs = model(**inputs)
        attention_mask = inputs['attention_mask']
        token_embeddings = outputs.last_hidden_state
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
        sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
        emb = (sum_embeddings / sum_mask)[0]
        emb = torch.nn.functional.normalize(emb, p=2, dim=0).numpy()
    
    return emb


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """Calcular similitud coseno."""
    return float(np.dot(a, b))


def find_top_k(query_emb: np.ndarray, kb_embeddings: np.ndarray, 
               questions: List[str], k: int = 5) -> List[Tuple[int, float, str]]:
    """Encontrar top-K matches."""
    similarities = np.dot(kb_embeddings, query_emb)
    top_indices = np.argsort(similarities)[-k:][::-1]
    
    results = []
    for idx in top_indices:
        results.append((idx, float(similarities[idx]), questions[idx]))
    
    return results


def check_match(response_text: str, expected_keywords: List[str]) -> bool:
    """Verificar si la respuesta contiene alguna palabra clave esperada."""
    response_lower = response_text.lower()
    for kw in expected_keywords:
        if kw.lower() in response_lower:
            return True
    return False


def run_tests(tokenizer, model, kb, questions, entry_ids, kb_embeddings, 
              embedding_method: str = "mean") -> Dict:
    """Ejecutar todos los tests y retornar métricas."""
    
    compute_fn = compute_embedding_mean if embedding_method == "mean" else compute_embedding_pooler
    
    results = {
        "method": embedding_method,
        "total": len(TEST_CASES),
        "correct_top1": 0,
        "correct_top3": 0,
        "correct_top5": 0,
        "failures": [],
        "details": []
    }
    
    entries_map = {e["id"]: e for e in kb["entries"]}
    
    for query, keywords, expected_cat in TEST_CASES:
        query_emb = compute_fn(tokenizer, model, query)
        top_k = find_top_k(query_emb, kb_embeddings, questions, k=5)
        
        # Verificar matches
        top1_correct = False
        top3_correct = False
        top5_correct = False
        
        for rank, (idx, score, matched_q) in enumerate(top_k):
            entry = entries_map[entry_ids[idx]]
            answer = entry.get("answer", "")
            
            is_match = check_match(matched_q + " " + answer, keywords)
            
            if rank == 0 and is_match:
                top1_correct = True
            if rank < 3 and is_match:
                top3_correct = True
            if is_match:
                top5_correct = True
        
        if top1_correct:
            results["correct_top1"] += 1
        if top3_correct:
            results["correct_top3"] += 1
        if top5_correct:
            results["correct_top5"] += 1
        
        # Guardar detalles
        detail = {
            "query": query,
            "expected_keywords": keywords,
            "expected_category": expected_cat,
            "top1_correct": top1_correct,
            "top3_correct": top3_correct,
            "top5_correct": top5_correct,
            "top_results": [
                {"rank": i+1, "score": f"{score:.4f}", "question": q[:60]}
                for i, (_, score, q) in enumerate(top_k)
            ]
        }
        results["details"].append(detail)
        
        if not top1_correct:
            results["failures"].append({
                "query": query,
                "expected": keywords,
                "got": top_k[0][2][:60],
                "score": f"{top_k[0][1]:.4f}"
            })
    
    # Calcular métricas
    results["accuracy_top1"] = results["correct_top1"] / results["total"] * 100
    results["accuracy_top3"] = results["correct_top3"] / results["total"] * 100
    results["accuracy_top5"] = results["correct_top5"] / results["total"] * 100
    
    return results


def generate_report(results_pooler: Dict, results_mean: Dict) -> str:
    """Generar reporte en formato Markdown."""
    
    report = f"""# Reporte de Evaluación de Similitud Semántica
## AgroChat - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

---

## Resumen Ejecutivo

| Método | Top-1 Accuracy | Top-3 Accuracy | Top-5 Accuracy |
|--------|----------------|----------------|----------------|
| **Pooler Output** | {results_pooler['accuracy_top1']:.1f}% | {results_pooler['accuracy_top3']:.1f}% | {results_pooler['accuracy_top5']:.1f}% |
| **Mean Pooling** | {results_mean['accuracy_top1']:.1f}% | {results_mean['accuracy_top3']:.1f}% | {results_mean['accuracy_top5']:.1f}% |

**Mejor método:** {"Mean Pooling" if results_mean['accuracy_top1'] > results_pooler['accuracy_top1'] else "Pooler Output"}

---

## Detalles por Método

### 1. Pooler Output

- Total de pruebas: {results_pooler['total']}
- Correctos en Top-1: {results_pooler['correct_top1']}
- Correctos en Top-3: {results_pooler['correct_top3']}
- Correctos en Top-5: {results_pooler['correct_top5']}

#### Fallos ({len(results_pooler['failures'])}):

| Query | Score | Obtuvo |
|-------|-------|--------|
"""
    
    for fail in results_pooler['failures'][:15]:
        report += f"| {fail['query'][:40]} | {fail['score']} | {fail['got'][:40]} |\n"
    
    report += f"""

### 2. Mean Pooling

- Total de pruebas: {results_mean['total']}
- Correctos en Top-1: {results_mean['correct_top1']}
- Correctos en Top-3: {results_mean['correct_top3']}
- Correctos en Top-5: {results_mean['correct_top5']}

#### Fallos ({len(results_mean['failures'])}):

| Query | Score | Obtuvo |
|-------|-------|--------|
"""
    
    for fail in results_mean['failures'][:15]:
        report += f"| {fail['query'][:40]} | {fail['score']} | {fail['got'][:40]} |\n"
    
    report += """

---

## Análisis Detallado de Casos Problemáticos

"""
    
    # Encontrar casos donde ambos métodos fallan
    pooler_failures = {f['query'] for f in results_pooler['failures']}
    mean_failures = {f['query'] for f in results_mean['failures']}
    both_fail = pooler_failures & mean_failures
    
    if both_fail:
        report += "### Casos donde AMBOS métodos fallan:\n\n"
        for q in list(both_fail)[:10]:
            report += f"- `{q}`\n"
        report += "\n"
    
    # Casos donde mean es mejor
    mean_better = pooler_failures - mean_failures
    if mean_better:
        report += "### Casos donde Mean Pooling es MEJOR:\n\n"
        for q in list(mean_better)[:10]:
            report += f"- `{q}`\n"
        report += "\n"
    
    # Casos donde pooler es mejor
    pooler_better = mean_failures - pooler_failures
    if pooler_better:
        report += "### Casos donde Pooler Output es MEJOR:\n\n"
        for q in list(pooler_better)[:10]:
            report += f"- `{q}`\n"
        report += "\n"
    
    report += """
---

## Recomendaciones

"""
    
    if results_mean['accuracy_top1'] > results_pooler['accuracy_top1']:
        diff = results_mean['accuracy_top1'] - results_pooler['accuracy_top1']
        report += f"""
1. **Usar Mean Pooling** - Tiene {diff:.1f}% mejor precisión en Top-1
2. Regenerar `kb_embeddings.npy` usando mean pooling
3. Asegurar que MindSpore en Android use `last_hidden_state` con mean pooling

"""
    else:
        report += """
1. **Mantener Pooler Output** - Tiene mejor o igual precisión
2. Los embeddings actuales son adecuados

"""
    
    report += f"""
## Configuración del Test

- Modelo: `{MODEL_NAME}`
- Dimensión embeddings: 384
- Secuencia máxima: {MAX_SEQ_LENGTH}
- Total casos de prueba: {len(TEST_CASES)}
- KB Path: `{KB_PATH}`
- Embeddings Path: `{EMBEDDINGS_PATH}`

---

*Reporte generado automáticamente*
"""
    
    return report


def main():
    print("="*70)
    print("   TEST DE SIMILITUD SEMÁNTICA - AGROCHAT")
    print("="*70)
    
    # Cargar recursos
    print("\n[1/4] Cargando base de conocimiento...")
    kb, questions, entry_ids = load_kb()
    print(f"      {len(questions)} preguntas de {len(kb['entries'])} entradas")
    
    print("\n[2/4] Cargando modelo transformer...")
    tokenizer, model = load_model()
    print(f"      Modelo: {MODEL_NAME}")
    
    print("\n[3/4] Generando embeddings de prueba...")
    
    # Generar embeddings con ambos métodos para la KB
    import torch
    
    # Pooler embeddings
    print("      Generando embeddings (pooler_output)...")
    pooler_embeddings = []
    for i, q in enumerate(questions):
        emb = compute_embedding_pooler(tokenizer, model, q)
        pooler_embeddings.append(emb)
        if (i+1) % 100 == 0:
            print(f"         {i+1}/{len(questions)}")
    pooler_embeddings = np.array(pooler_embeddings)
    
    # Mean embeddings
    print("      Generando embeddings (mean_pooling)...")
    mean_embeddings = []
    for i, q in enumerate(questions):
        emb = compute_embedding_mean(tokenizer, model, q)
        mean_embeddings.append(emb)
        if (i+1) % 100 == 0:
            print(f"         {i+1}/{len(questions)}")
    mean_embeddings = np.array(mean_embeddings)
    
    print("\n[4/4] Ejecutando tests...")
    
    # Ejecutar tests con ambos métodos
    print("      Testing pooler_output...")
    results_pooler = run_tests(tokenizer, model, kb, questions, entry_ids, 
                               pooler_embeddings, "pooler")
    
    print("      Testing mean_pooling...")
    results_mean = run_tests(tokenizer, model, kb, questions, entry_ids, 
                             mean_embeddings, "mean")
    
    # Generar reporte
    print("\n" + "="*70)
    print("   RESULTADOS")
    print("="*70)
    
    print(f"\n   POOLER OUTPUT:")
    print(f"      Top-1 Accuracy: {results_pooler['accuracy_top1']:.1f}%")
    print(f"      Top-3 Accuracy: {results_pooler['accuracy_top3']:.1f}%")
    print(f"      Top-5 Accuracy: {results_pooler['accuracy_top5']:.1f}%")
    
    print(f"\n   MEAN POOLING:")
    print(f"      Top-1 Accuracy: {results_mean['accuracy_top1']:.1f}%")
    print(f"      Top-3 Accuracy: {results_mean['accuracy_top3']:.1f}%")
    print(f"      Top-5 Accuracy: {results_mean['accuracy_top5']:.1f}%")
    
    # Guardar reporte
    report = generate_report(results_pooler, results_mean)
    report_path = PROJECT_ROOT / "SEMANTIC_SIMILARITY_REPORT.md"
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(report)
    print(f"\n   Reporte guardado en: {report_path}")
    
    # Guardar resultados detallados en JSON
    results_json = {
        "timestamp": datetime.now().isoformat(),
        "pooler": results_pooler,
        "mean": results_mean
    }
    json_path = PROJECT_ROOT / "semantic_test_results.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results_json, f, ensure_ascii=False, indent=2)
    print(f"   Resultados JSON: {json_path}")
    
    # Recomendación final
    print("\n" + "="*70)
    if results_mean['accuracy_top1'] > results_pooler['accuracy_top1']:
        print("   RECOMENDACIÓN: Usar MEAN POOLING")
        print("   Los embeddings con mean pooling tienen mejor precisión.")
    else:
        print("   RECOMENDACIÓN: Mantener POOLER OUTPUT")
    print("="*70)
    
    return results_pooler, results_mean


if __name__ == "__main__":
    main()
