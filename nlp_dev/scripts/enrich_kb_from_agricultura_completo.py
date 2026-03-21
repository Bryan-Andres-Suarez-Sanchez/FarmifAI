#!/usr/bin/env python3
"""
Enriquece app/src/main/assets/agrochat_knowledge_base.json con pares prompt/response
provenientes de nlp_dev/data/datasets/agricultura_completo.json.

- Evita duplicados por pregunta normalizada.
- Asigna IDs consecutivos.
- Intenta inferir categoría por palabras clave.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KB_PATH = ROOT / "app" / "src" / "main" / "assets" / "agrochat_knowledge_base.json"
SOURCE_PATH = ROOT / "nlp_dev" / "data" / "datasets" / "agricultura_completo.json"


def normalize(text: str) -> str:
    text = text.lower().strip()
    text = (
        text.replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ñ", "n")
    )
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def infer_category(text: str) -> str:
    t = normalize(text)
    rules = [
        ("plagas", ["plaga", "pulgon", "mosca", "insect", "gusano", "maleza"]),
        ("riego", ["riego", "regar", "agua", "humedad", "encharc"]),
        ("fertilizacion", ["fertil", "abono", "compost", "nutrient", "urea", "npk"]),
        ("siembra", ["siembra", "sembrar", "plantar", "epoca", "trasplante", "germin"]),
        ("diagnostico", ["sintoma", "mancha", "amarill", "diagnost", "enfermedad", "hongo", "marchitez"]),
        ("cosecha", ["cosecha", "recolec", "postcosecha"]),
        ("cultivo", ["cultivo", "tomate", "maiz", "frijol", "cafe", "papa", "yuca", "banano", "platano"]),
    ]
    for category, keywords in rules:
        if any(k in t for k in keywords):
            return category
    return "general"


def extract_keywords(prompt: str, limit: int = 8) -> list[str]:
    stopwords = {
        "que", "como", "cuando", "donde", "para", "por", "con", "sin", "una", "uno", "unos", "unas",
        "del", "de", "la", "el", "las", "los", "mi", "mis", "tu", "sus", "sobre", "hoy", "es", "son",
    }
    tokens = normalize(prompt).split()
    keywords = []
    for token in tokens:
        if len(token) < 4 or token in stopwords:
            continue
        if token not in keywords:
            keywords.append(token)
        if len(keywords) >= limit:
            break
    return keywords


def main() -> None:
    kb = json.loads(KB_PATH.read_text(encoding="utf-8"))
    source = json.loads(SOURCE_PATH.read_text(encoding="utf-8"))

    if not isinstance(source, list):
        raise ValueError(f"Se esperaba lista en {SOURCE_PATH}")

    entries = kb.get("entries", [])
    categories = kb.get("categories", [])
    existing_questions = {
        normalize(q)
        for entry in entries
        for q in entry.get("questions", [])
    }

    next_id = (max((entry.get("id", 0) for entry in entries), default=0) + 1)
    added = 0

    for item in source:
        prompt = (item.get("prompt") or "").strip()
        response = (item.get("response") or "").strip()
        if not prompt or not response:
            continue

        n_prompt = normalize(prompt)
        if not n_prompt or n_prompt in existing_questions:
            continue

        category = infer_category(f"{prompt} {response}")
        if category not in categories:
            categories.append(category)

        entry = {
            "id": next_id,
            "category": category,
            "questions": [prompt],
            "answer": response,
        }
        keywords = extract_keywords(prompt)
        if keywords:
            entry["keywords"] = keywords

        entries.append(entry)
        existing_questions.add(n_prompt)
        next_id += 1
        added += 1

    kb["entries"] = entries
    kb["categories"] = categories
    kb["description"] = kb.get("description", "Base de conocimiento agrícola para AgroChat") + " (enriquecida)"

    KB_PATH.write_text(json.dumps(kb, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total_questions = sum(len(e.get("questions", [])) for e in entries)
    print(f"KB enriquecida. Nuevas entradas: {added}")
    print(f"Total entradas: {len(entries)}")
    print(f"Total preguntas: {total_questions}")


if __name__ == "__main__":
    main()
