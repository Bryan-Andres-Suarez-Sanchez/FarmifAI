#!/usr/bin/env python3
"""
Generate kb_embeddings.npy + kb_embeddings_mapping.json directly from
assets/kb_nueva/extract/*.records.jsonl.

This mirrors the runtime question generation used by SemanticSearchHelper
when loading records JSONL directly.
"""

from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer


PROJECT_ROOT = Path(__file__).resolve().parents[2]
RECORDS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "kb_nueva" / "extract"
EMBEDDINGS_PATH = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "kb_embeddings.npy"
MAPPING_PATH = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "kb_embeddings_mapping.json"

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


def normalize_text(text: str) -> str:
    text = text.lower()
    text = unicodedata.normalize("NFD", text)
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def dedupe_questions(raw: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in raw:
        cleaned = re.sub(r"\s+", " ", (value or "").strip())
        if not cleaned:
            continue
        key = normalize_text(cleaned)
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(cleaned)
    return out


def build_questions_from_record(record: dict) -> list[str]:
    raw: list[str] = []
    raw.extend(str(x).strip() for x in (record.get("retrieval_hints") or []) if str(x).strip())
    raw.extend(str(x).strip() for x in (record.get("aliases") or []) if str(x).strip())
    title = str(record.get("title") or "").strip()
    if title:
        raw.append(title)

    deduped = dedupe_questions(raw)
    if deduped:
        return deduped

    fallback: list[str] = []
    if title:
        fallback.append(title)
    statement = str(record.get("statement") or "").strip()
    if statement:
        fallback.append(statement[:180])
    return dedupe_questions(fallback)


def collect_questions_and_entry_ids() -> tuple[list[str], list[int]]:
    if not RECORDS_DIR.exists():
        raise FileNotFoundError(f"No existe el directorio de records: {RECORDS_DIR}")

    questions: list[str] = []
    entry_ids: list[int] = []
    next_id = 1

    for file_path in sorted(RECORDS_DIR.glob("*.jsonl")):
        with file_path.open("r", encoding="utf-8") as handle:
            for raw_line in handle:
                line = raw_line.strip()
                if not line:
                    continue
                record = json.loads(line)
                qs = build_questions_from_record(record)
                if not qs:
                    continue
                for q in qs:
                    questions.append(q)
                    entry_ids.append(next_id)
                next_id += 1

    return questions, entry_ids


def main() -> None:
    questions, entry_ids = collect_questions_and_entry_ids()
    print(f"Total preguntas: {len(questions)}")
    print(f"Total entry_ids: {len(entry_ids)}")

    model = SentenceTransformer(MODEL_NAME)
    embeddings = model.encode(
        questions,
        normalize_embeddings=True,
        show_progress_bar=True,
        convert_to_numpy=True,
    ).astype(np.float32)

    EMBEDDINGS_PATH.parent.mkdir(parents=True, exist_ok=True)
    np.save(EMBEDDINGS_PATH, embeddings)

    mapping = {
        "questions": questions,
        "entry_ids": entry_ids,
        "embedding_dim": int(embeddings.shape[1]),
        "embeddings_rows": int(embeddings.shape[0]),
        "model": MODEL_NAME,
        "method": "sentence_transformers_mean_pooling_records_jsonl",
        "source": "app/src/main/assets/kb_nueva/extract/*.records.jsonl",
    }
    with MAPPING_PATH.open("w", encoding="utf-8") as handle:
        json.dump(mapping, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print(f"Embeddings guardados en: {EMBEDDINGS_PATH}")
    print(f"Mapping guardado en: {MAPPING_PATH}")
    print(f"Shape embeddings: {embeddings.shape}")


if __name__ == "__main__":
    main()
