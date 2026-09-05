#!/usr/bin/env python3
"""Generate Android RAG embeddings from a FarmifAI knowledge_base.json.

The output row order is exactly the JSON `chunks` order. The Android app relies
on row N corresponding to chunk N, so never reorder either file independently.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer


MODEL_NAME = "intfloat/multilingual-e5-small"
DIMENSION = 384


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("knowledge_base", type=Path, help="Input knowledge_base.json")
    parser.add_argument("output", type=Path, help="Output .npy file")
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--device", default=None, help="cpu, cuda, or omit for automatic selection")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.batch_size < 1:
        raise ValueError("--batch-size must be positive")

    with args.knowledge_base.open("r", encoding="utf-8") as stream:
        root = json.load(stream)
    chunks = root.get("chunks") if isinstance(root, dict) else None
    if not isinstance(chunks, list) or not chunks:
        raise ValueError("Expected a non-empty JSON object with a 'chunks' array")

    texts: list[str] = []
    for index, chunk in enumerate(chunks):
        if not isinstance(chunk, dict) or not isinstance(chunk.get("text"), str) or not chunk["text"].strip():
            raise ValueError(f"Chunk {index} has no non-empty string field 'text'")
        texts.append(f"passage: {chunk['text'].strip()}")

    print(f"Loading {MODEL_NAME} and encoding {len(texts)} chunks...")
    model = SentenceTransformer(MODEL_NAME, device=args.device)
    matrix = model.encode(
        texts,
        batch_size=args.batch_size,
        convert_to_numpy=True,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    matrix = np.asarray(matrix, dtype=np.float32, order="C")
    if matrix.shape != (len(chunks), DIMENSION):
        raise ValueError(f"Unexpected matrix shape {matrix.shape}; expected {(len(chunks), DIMENSION)}")
    if not np.isfinite(matrix).all():
        raise ValueError("Embedding matrix contains NaN or infinite values")
    norms = np.linalg.norm(matrix, axis=1)
    if not np.allclose(norms, 1.0, atol=1e-4):
        raise ValueError(f"Embeddings are not normalized: norm range {norms.min()}..{norms.max()}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(".tmp.npy")
    np.save(temporary, matrix, allow_pickle=False)
    temporary.replace(args.output)
    print(
        f"OK: {args.output}\n"
        f"chunks={len(chunks)} shape={matrix.shape} dtype={matrix.dtype} "
        f"norm={norms.min():.6f}..{norms.max():.6f}"
    )


if __name__ == "__main__":
    main()
