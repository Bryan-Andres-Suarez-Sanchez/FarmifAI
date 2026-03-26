#!/usr/bin/env python3
"""
Generate KB embeddings with automatic question enrichment.

Reads JSONL records from kb_nueva/extract/, automatically generates synthetic
retrieval questions from structured fields (entities, title, statement, condition),
then generates mean-pooling embeddings for all questions.

This script does NOT modify any JSONL source files. All enrichment happens
at embedding-generation time only.

Usage:
    source .venv_kb/bin/activate
    python generate_kb_embeddings.py
"""

import json
import os
import re
import unicodedata
from typing import List, Set, Tuple

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer


# ── Configuration ──────────────────────────────────────────────────────────
KB_DIR = "app/src/main/assets/kb_nueva/extract"
ASSETS_DIR = "app/src/main/assets"
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MAX_SEQ_LENGTH = 128
BATCH_SIZE = 32


# ── Text normalization (mirrors SemanticSearchHelper.kt) ───────────────────
def normalize_text(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = s.encode("ascii", "ignore").decode("ascii")
    return re.sub(r"\s+", " ", s.lower().strip())


def dedupe_questions(raw: List[str]) -> List[str]:
    seen: Set[str] = set()
    out: List[str] = []
    for v in raw:
        cleaned = re.sub(r"\s+", " ", v.strip())
        if not cleaned:
            continue
        key = normalize_text(cleaned)
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(cleaned)
    return out


# ── Question building (mirrors app logic) ─────────────────────────────────
def build_base_questions(rec: dict) -> List[str]:
    """Build questions exactly as the app does: retrieval_hints + aliases + title."""
    raw = []
    for h in rec.get("retrieval_hints", []):
        if h.strip():
            raw.append(h.strip())
    for a in rec.get("aliases", []):
        if a.strip():
            raw.append(a.strip())
    title = rec.get("title", "").strip()
    if title:
        raw.append(title)
    result = dedupe_questions(raw)
    if result:
        return result
    # Fallback: title + statement
    fallback = []
    if title:
        fallback.append(title)
    stmt = rec.get("statement", "").strip()
    if stmt:
        fallback.append(stmt[:180])
    return dedupe_questions(fallback)


# ── Synthetic question generation ──────────────────────────────────────────
# Templates for generating natural-language questions from structured fields.
# {entity} and {title} are replaced with actual values from the record.
TEMPLATES_BY_TYPE = {
    "practice": [
        "como {action_verb} en cafe",
        "como {action_verb} en el cafeto",
    ],
    "risk": [
        "que pasa con {entity} en cafe",
        "riesgo de {entity} en el cafeto",
    ],
    "rule": [
        "como manejar {entity} en cafe",
        "{entity} en el cafeto",
    ],
    "fact": [
        "que es {entity} en cafe",
        "{entity} en el cafeto",
    ],
    "definition": [
        "que es {entity}",
        "definicion de {entity} en cafe",
    ],
    "parameter": [
        "cual es {entity} en cafe",
        "{entity} del cafeto",
    ],
}


def generate_synthetic_questions(rec: dict) -> List[str]:
    """
    Generate additional questions from structured fields.
    Uses entities, title, condition, and action to create natural phrasings
    that users might actually type.
    """
    synthetic = []
    entities = rec.get("entities") or []
    title = (rec.get("title") or "").strip()
    condition = (rec.get("condition") or "").strip()
    action = (rec.get("action") or "").strip()
    statement = (rec.get("statement") or "").strip()
    record_type = (rec.get("record_type") or "").strip()

    # 1. Entity-based questions: combine each key entity with "cafe"
    #    This is the main fix: ensures "roya" → "roya en el cafe"
    coffee_terms = {"cafe", "café", "cafeto", "cafetal", "cafetero", "caficultura"}
    non_coffee_entities = [
        e.strip()
        for e in entities
        if e.strip() and normalize_text(e.strip()) not in coffee_terms
        and len(e.strip()) >= 3
    ]

    for entity in non_coffee_entities[:4]:  # limit to 4 most important entities
        entity_lower = entity.lower()
        # Only add "en cafe" suffix if the entity doesn't already mention cafe
        if not any(ct in normalize_text(entity_lower) for ct in coffee_terms):
            synthetic.append(f"{entity_lower} en cafe")

    # 2. Action-based question (for practice/rule types)
    if action and record_type in ("practice", "rule"):
        action_clean = action.lower().strip()
        if len(action_clean) > 10 and len(action_clean) < 80:
            if not any(ct in normalize_text(action_clean) for ct in coffee_terms):
                synthetic.append(f"{action_clean} en cafe")
            else:
                synthetic.append(action_clean)

    # 3. Condition as question (often matches user intent)
    if condition and len(condition) > 10 and len(condition) < 80:
        condition_clean = condition.lower().strip()
        if not any(ct in normalize_text(condition_clean) for ct in coffee_terms):
            synthetic.append(f"{condition_clean} en cafe")
        else:
            synthetic.append(condition_clean)

    return synthetic


# ── Main pipeline ──────────────────────────────────────────────────────────
def load_records(kb_dir: str) -> List[dict]:
    records = []
    for fname in sorted(os.listdir(kb_dir)):
        if not fname.endswith(".jsonl"):
            continue
        with open(os.path.join(kb_dir, fname)) as f:
            for line in f:
                line = line.strip()
                if line:
                    records.append(json.loads(line))
    return records


def build_all_questions(records: List[dict]) -> Tuple[List[str], List[int]]:
    """Build all questions (base + synthetic) for all records."""
    questions = []
    entry_ids = []

    entry_id = 0
    for rec in records:
        entry_id += 1
        base_qs = build_base_questions(rec)
        synthetic_qs = generate_synthetic_questions(rec)
        all_raw = base_qs + synthetic_qs
        deduped = dedupe_questions(all_raw)

        for q in deduped:
            questions.append(q)
            entry_ids.append(entry_id)

    return questions, entry_ids


def generate_embeddings(
    questions: List[str], tokenizer, model
) -> np.ndarray:
    """Generate mean-pooled, L2-normalized embeddings."""
    all_emb = []
    for i in range(0, len(questions), BATCH_SIZE):
        batch = questions[i : i + BATCH_SIZE]
        encoded = tokenizer(
            batch,
            padding=True,
            truncation=True,
            max_length=MAX_SEQ_LENGTH,
            return_tensors="pt",
        )
        with torch.no_grad():
            output = model(**encoded)

        token_emb = output.last_hidden_state
        attn_mask = encoded["attention_mask"].unsqueeze(-1).float()
        sum_emb = torch.sum(token_emb * attn_mask, dim=1)
        sum_mask = torch.clamp(attn_mask.sum(dim=1), min=1e-9)
        mean_pooled = sum_emb / sum_mask

        norms = torch.norm(mean_pooled, dim=1, keepdim=True)
        normalized = mean_pooled / torch.clamp(norms, min=1e-10)
        all_emb.append(normalized.numpy())

    return np.vstack(all_emb).astype(np.float32)


def main():
    print("Loading model...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    bert_model = AutoModel.from_pretrained(MODEL_NAME)
    bert_model.eval()

    print(f"Loading records from {KB_DIR}...")
    records = load_records(KB_DIR)
    print(f"  {len(records)} records loaded")

    print("Building questions (base + synthetic)...")
    questions, entry_ids = build_all_questions(records)
    base_questions, _ = zip(
        *[
            (q, eid)
            for rec_idx, rec in enumerate(records)
            for q, eid in zip(
                build_base_questions(rec),
                [rec_idx + 1] * len(build_base_questions(rec)),
            )
        ]
    )
    n_base = len(base_questions)
    n_synthetic = len(questions) - n_base
    print(f"  {len(questions)} total questions ({n_base} base + {n_synthetic} synthetic)")

    print("Generating mean-pooling embeddings...")
    embeddings = generate_embeddings(questions, tokenizer, bert_model)
    print(f"  Shape: {embeddings.shape}")

    # Save
    npy_path = os.path.join(ASSETS_DIR, "kb_embeddings.npy")
    np.save(npy_path, embeddings)
    print(f"  Saved {npy_path}")

    mapping = {
        "questions": questions,
        "entry_ids": entry_ids,
        "embedding_dim": 384,
        "model": MODEL_NAME.split("/")[-1],
        "method": "mean_pooling",
    }
    mapping_path = os.path.join(ASSETS_DIR, "kb_embeddings_mapping.json")
    with open(mapping_path, "w") as f:
        json.dump(mapping, f, ensure_ascii=False)
    print(f"  Saved {mapping_path}")

    # Sanity checks
    print("\nSanity checks:")
    test_queries = [
        "como combatir la roya en el cafe",
        "como controlar la broca del cafe",
        "puedo sembrar cafe y frijol juntos",
        "plagas del cafe",
        "cuando cosechar cafe",
        "que suelo necesita el cafe",
        "enfermedades del cafeto",
    ]
    emb_norm = embeddings / np.clip(
        np.linalg.norm(embeddings, axis=1, keepdims=True), 1e-10, None
    )
    for q in test_queries:
        enc = tokenizer(
            [q], padding=True, truncation=True, max_length=128, return_tensors="pt"
        )
        with torch.no_grad():
            out = bert_model(**enc)
        am = enc["attention_mask"].unsqueeze(-1).float()
        mp = torch.sum(out.last_hidden_state * am, dim=1) / torch.clamp(
            am.sum(dim=1), min=1e-9
        )
        qe = mp.squeeze().numpy()
        qe = qe / np.linalg.norm(qe)
        sims = emb_norm @ qe
        top = np.argsort(sims)[::-1][:3]
        print(
            f"  '{q}'\n"
            f"    #1 [{top[0]}] {sims[top[0]]:.3f} e={entry_ids[top[0]]} '{questions[top[0]][:60]}'\n"
            f"    #2 [{top[1]}] {sims[top[1]]:.3f} e={entry_ids[top[1]]} '{questions[top[1]][:60]}'\n"
            f"    #3 [{top[2]}] {sims[top[2]]:.3f} e={entry_ids[top[2]]} '{questions[top[2]][:60]}'"
        )

    print("\nDone!")


if __name__ == "__main__":
    main()
