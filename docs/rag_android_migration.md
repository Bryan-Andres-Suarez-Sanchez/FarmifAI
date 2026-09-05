# Notebook-equivalent offline RAG

`rag_slm_colab.ipynb` is the source of truth. The Android path is:

1. `rag_knowledge_base.json["chunks"]`
2. NLTK-compatible Spanish preprocessing and `BM25Okapi` Top-10
3. normalized `intfloat/multilingual-e5-small` query embedding (`query: ` prefix) and dot-product Top-10 against normalized `passage: ` embeddings
4. reciprocal-rank fusion with `k=60` and one-based rank denominators
5. `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` scoring of every fused `(query, chunk)` pair
6. reranked Top-3, formatted literally and injected inside `<knowledge>`

The previous canonical-token, entity, category, ANN, score-floor, and lexical-fallback formulas are not in the active ranking path.

## Generate required assets

The repository does not contain the notebook's uploaded `knowledge_base.json`, so supply that exact file:

```powershell
python -m pip install -r tools/rag_requirements.txt
python tools/export_notebook_rag_assets.py --knowledge-base C:\path\to\knowledge_base.json
```

The exporter produces the corpus, NLTK stopword list, normalized E5 matrix, both tokenizer JSON files, both ONNX models, and `tools/rag_parity_reference.json`. The two large ONNX files are excluded from APK packaging. Provision them after installing the app:

```powershell
adb shell run-as edu.unicauca.app.agrochat mkdir -p files/models
adb push app/src/main/assets/rag_e5_small.onnx /data/local/tmp/rag_e5_small.onnx
adb push app/src/main/assets/rag_mmarco_reranker.onnx /data/local/tmp/rag_mmarco_reranker.onnx
adb shell run-as edu.unicauca.app.agrochat cp /data/local/tmp/rag_e5_small.onnx files/models/rag_e5_small.onnx
adb shell run-as edu.unicauca.app.agrochat cp /data/local/tmp/rag_mmarco_reranker.onnx files/models/rag_mmarco_reranker.onnx
```

No network operation occurs during retrieval or reranking. Sessions and tokenizers are loaded once and released with the activity.

## Build and test

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Compare Android debug traces/results stage-by-stage with `tools/rag_parity_reference.json` after assets are generated.
