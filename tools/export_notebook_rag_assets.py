#!/usr/bin/env python3
"""Export the exact notebook retrievers for offline Android use and emit parity traces.

Usage:
  python tools/export_notebook_rag_assets.py --knowledge-base knowledge_base.json

Large ONNX files are written to app/src/main/assets by default but excluded from
the APK. Provision them to /data/data/edu.unicauca.app.agrochat/files/models/.
"""
from __future__ import annotations

import argparse
import json
import shutil
import unicodedata
from pathlib import Path

import numpy as np
import torch
from nltk import download
from nltk.corpus import stopwords
from nltk.stem.snowball import SnowballStemmer
from rank_bm25 import BM25Okapi
from sentence_transformers import CrossEncoder, SentenceTransformer
from transformers import AutoModel, AutoModelForSequenceClassification, AutoTokenizer

E5 = "intfloat/multilingual-e5-small"
RERANKER = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"
QUERIES = [
    "¿Cuáles son las condiciones de suelo para el café?",
    "¿Cómo se controla la roya del cafeto?",
    "¿Qué densidad de siembra se recomienda?",
    "¿Cuándo debo fertilizar el cultivo?",
    "¿Cómo manejar las arvenses del cafetal?",
]


def records_to_knowledge_base(records_dir: Path, output_file: Path) -> Path:
    """Converts the project's extracted evidence records to notebook chunks."""
    chunks = []
    for source in sorted(records_dir.glob("*.records.jsonl")):
        with source.open("r", encoding="utf-8") as stream:
            for line_number, line in enumerate(stream, 1):
                if not line.strip():
                    continue
                record = json.loads(line)
                fields = [
                    ("Titulo", record.get("title")),
                    ("Contenido", record.get("statement")),
                    ("Condicion", record.get("condition")),
                    ("Accion", record.get("action")),
                    ("Efecto esperado", record.get("expected_effect")),
                    ("Riesgo si se ignora", record.get("risk_if_ignored")),
                    ("Aplicabilidad", record.get("applicability")),
                ]
                text = "\n".join(f"{label}: {value}" for label, value in fields if value)
                quant = record.get("quant_data") or []
                if quant:
                    text += "\nDatos cuantitativos: " + json.dumps(quant, ensure_ascii=False)
                source_ref = record.get("source_ref") or {}
                chunks.append({
                    "document_id": source_ref.get("document") or record.get("chapter_title") or source.stem,
                    "chunk_number": record.get("id") or str(line_number),
                    "text": text,
                })
    if not chunks:
        raise ValueError(f"No records found in {records_dir}")
    output_file.write_text(json.dumps({"chunks": chunks}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Generated {len(chunks)} notebook chunks at {output_file}")
    return output_file


def remove_accents(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--knowledge-base", type=Path)
    parser.add_argument("--records-dir", type=Path)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--parity-output", type=Path, default=Path("tools/rag_parity_reference.json"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.knowledge_base is None:
        if args.records_dir is None:
            parser.error("provide --knowledge-base or --records-dir")
        args.knowledge_base = records_to_knowledge_base(args.records_dir, args.output / "rag_knowledge_base.json")
    download("stopwords", quiet=True)
    spanish_stopwords = {remove_accents(w.lower()) for w in stopwords.words("spanish")}
    stemmer = SnowballStemmer("spanish")

    def preprocess(text: str) -> list[str]:
        import re
        text = re.sub(r"[^a-z0-9\s]", " ", remove_accents(text.lower()))
        return [s for token in text.split() if len(token) > 1 and token not in spanish_stopwords
                if len(s := stemmer.stem(token)) > 1]

    data = json.loads(args.knowledge_base.read_text(encoding="utf-8"))
    chunks = data["chunks"]
    destination_kb = args.output / "rag_knowledge_base.json"
    if args.knowledge_base.resolve() != destination_kb.resolve():
        shutil.copyfile(args.knowledge_base, destination_kb)
    (args.output / "rag_spanish_stopwords.txt").write_text("\n".join(sorted(spanish_stopwords)) + "\n", encoding="utf-8")

    e5_tokenizer = AutoTokenizer.from_pretrained(E5)
    e5_model = SentenceTransformer(E5)
    embeddings = e5_model.encode([f"passage: {c['text']}" for c in chunks], batch_size=128,
                                 convert_to_numpy=True, normalize_embeddings=True)
    np.save(args.output / "rag_e5_embeddings.npy", embeddings.astype(np.float32))
    e5_tokenizer.backend_tokenizer.save(str(args.output / "rag_e5_tokenizer.json"))

    class E5Wrapper(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.model = AutoModel.from_pretrained(E5)

        def forward(self, input_ids, attention_mask):
            hidden = self.model(input_ids=input_ids, attention_mask=attention_mask,
                                ).last_hidden_state
            mask = attention_mask.unsqueeze(-1).to(hidden.dtype)
            pooled = (hidden * mask).sum(1) / mask.sum(1).clamp(min=1e-9)
            return torch.nn.functional.normalize(pooled, p=2, dim=1)

    dummy = e5_tokenizer("query: prueba", padding="max_length", truncation=True,
                         max_length=512, return_tensors="pt")
    torch.onnx.export(E5Wrapper().eval(), tuple(dummy[k] for k in ("input_ids", "attention_mask")),
                      args.output / "rag_e5_small.onnx", input_names=["input_ids", "attention_mask"],
                      output_names=["sentence_embedding"], dynamic_axes=None, opset_version=17)

    reranker_tokenizer = AutoTokenizer.from_pretrained(RERANKER)
    reranker_tokenizer.backend_tokenizer.save(str(args.output / "rag_mmarco_tokenizer.json"))
    reranker_model = AutoModelForSequenceClassification.from_pretrained(RERANKER).eval()
    pair = reranker_tokenizer("consulta", "pasaje", padding="max_length", truncation=True,
                              max_length=512, return_tensors="pt")
    class RerankerWrapper(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, input_ids, attention_mask):
            return self.model(input_ids=input_ids, attention_mask=attention_mask).logits

    torch.onnx.export(RerankerWrapper(reranker_model), tuple(pair[k] for k in ("input_ids", "attention_mask")),
                      args.output / "rag_mmarco_reranker.onnx",
                      input_names=["input_ids", "attention_mask"], output_names=["logits"],
                      dynamic_axes=None, opset_version=17)

    bm25 = BM25Okapi([preprocess(c["text"]) for c in chunks])
    cross_encoder = CrossEncoder(RERANKER)
    traces = []
    for query in QUERIES:
        bm_scores = bm25.get_scores(preprocess(query))
        bm = [(int(i), float(bm_scores[i])) for i in np.argsort(bm_scores)[::-1][:10]]
        query_embedding = e5_model.encode(f"query: {query}", normalize_embeddings=True, convert_to_numpy=True)
        similarities = embeddings @ query_embedding
        semantic = [(int(i), float(similarities[i])) for i in np.argsort(similarities)[::-1][:10]]
        rrf = {}
        for ranking in (bm, semantic):
            for rank, (idx, _) in enumerate(ranking):
                rrf[idx] = rrf.get(idx, 0.0) + 1.0 / (60 + rank + 1)
        fused = sorted(rrf.items(), key=lambda item: item[1], reverse=True)
        scores = cross_encoder.predict([(query, chunks[idx]["text"]) for idx, _ in fused])
        reranked = sorted(zip((idx for idx, _ in fused), map(float, scores)), key=lambda item: item[1], reverse=True)
        final = reranked[:3]
        context = "\n\n".join(f"Documento: {chunks[i]['document_id']} (Fragmento {chunks[i]['chunk_number']})\nContenido: {chunks[i]['text']}" for i, _ in final)
        traces.append({"query": query, "bm25": bm, "semantic": semantic, "rrf": fused,
                       "reranker": reranked, "final_ids": [i for i, _ in final], "context": context})
    args.parity_output.write_text(json.dumps(traces, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
