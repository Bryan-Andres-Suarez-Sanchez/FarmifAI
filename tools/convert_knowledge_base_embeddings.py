"""Convert the notebook's trusted NumPy pickle matrix to Android-readable NPY.

Run from the repository root whenever embeddings_chunks_e5_small.pkl changes.
Never run this against an untrusted pickle: pickle loading can execute code.
"""

from pathlib import Path
import pickle

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/knowledge_base/embeddings_chunks_e5_small.pkl"
TARGET = ROOT / "app/src/main/assets/knowledge_base/embeddings_chunks_e5_small.npy"


def main() -> None:
    with SOURCE.open("rb") as stream:
        matrix = pickle.load(stream)
    matrix = np.asarray(matrix, dtype=np.float32, order="C")
    if matrix.ndim != 2 or matrix.shape[1] != 384:
        raise ValueError(f"Expected an N x 384 matrix, got {matrix.shape}")
    np.save(TARGET, matrix, allow_pickle=False)
    print(f"Converted {SOURCE.name} -> {TARGET.name}: {matrix.shape}, {matrix.dtype}")


if __name__ == "__main__":
    main()
