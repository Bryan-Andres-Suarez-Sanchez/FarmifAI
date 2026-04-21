#!/usr/bin/env python3
"""Offline precision check for the LLM-first RAG cycle.

The script intentionally uses only app/src/main/assets/kb_nueva/extract/*.jsonl.
It does not run the LLM and it does not load Android/MindSpore assets, so it is
safe to run on a low-memory development machine.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Iterable


STOP_WORDS = {
    "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con",
    "por", "para", "al", "un", "una", "unos", "unas", "que", "me", "te",
    "se", "mi", "tu", "su", "sobre", "acerca", "esta", "estan", "estoy",
    "tengo", "tiene", "tienen", "hay", "bajo", "baja", "bajos", "bajas",
    "alto", "alta", "altos", "altas", "despues", "como", "cuando", "cual",
    "cuales", "donde", "porque", "dame", "paso", "pasos",
}

CANONICAL = {
    "fertilizante": "fertilizacion",
    "fertilizantes": "fertilizacion",
    "fertilizar": "fertilizacion",
    "abono": "fertilizacion",
    "abonos": "fertilizacion",
    "abonar": "fertilizacion",
    "riego": "riego",
    "regar": "riego",
    "irrigacion": "riego",
    "plagas": "plaga",
    "enfermedades": "enfermedad",
    "hongos": "hongo",
    "cafe": "cafe",
    "cafeto": "cafe",
    "cafetal": "cafe",
    "cafetera": "cafe",
    "cafetero": "cafe",
    "caficultura": "cafe",
    "siembra": "siembra",
    "sembrar": "siembra",
    "plantar": "siembra",
    "almacigo": "siembra",
    "cosechar": "cosecha",
    "cosechas": "cosecha",
    "maduracion": "cosecha",
    "beneficio": "poscosecha",
    "despulpado": "poscosecha",
    "fermentacion": "poscosecha",
    "secado": "poscosecha",
    "catacion": "calidad",
    "sensorial": "calidad",
    "humedad": "poscosecha",
    "poda": "poda",
    "podas": "poda",
    "podar": "poda",
    "recepa": "zoca",
    "zoca": "zoca",
    "zoqueo": "zoca",
    "renovar": "renovacion",
    "renovacion": "renovacion",
    "chupon": "brote",
    "chupones": "brote",
    "brote": "brote",
    "brotes": "brote",
    "rebrote": "brote",
    "rebrotes": "brote",
    "seleccionar": "seleccion",
    "seleccione": "seleccion",
    "selecciona": "seleccion",
    "seleccion": "seleccion",
    "preseleccion": "seleccion",
    "broca": "broca",
    "roya": "roya",
    "variedades": "variedad",
    "variedad": "variedad",
}

TOKEN_CACHE: dict[str, frozenset[str]] = {}
NUMBER_CACHE: dict[str, frozenset[str]] = {}
FACET_CACHE: dict[str, frozenset[str]] = {}


class Facet:
    ACTION = "action"
    TIMING = "timing"
    AMOUNT = "amount"
    THRESHOLD = "threshold"
    EFFECT = "effect"
    RISK = "risk"


@dataclass(frozen=True)
class Record:
    rid: str
    path: str
    title: str
    questions: tuple[str, ...]
    text: str
    entity_tokens: frozenset[str]
    tokens: frozenset[str]
    numbers: frozenset[str]
    facets: frozenset[str]


@dataclass(frozen=True)
class Case:
    query: str
    expected: str
    intent: str


def normalize(text: str) -> str:
    out = text.lower()
    out = re.sub(r"(?<=\d)\.(?=\d{3}(\D|$))", "", out)
    table = str.maketrans("áéíóúñ", "aeioun")
    out = out.translate(table)
    out = out.replace(",", ".")
    out = re.sub(r"[^a-z0-9.\s<>=%/]", " ", out)
    out = re.sub(r"\s+", " ", out).strip()
    return out


def canonical_token(token: str) -> str:
    token = CANONICAL.get(token, token)
    if len(token) > 4 and token.endswith("es"):
        token = token[:-2]
    elif len(token) > 3 and token.endswith("s"):
        token = token[:-1]
    return token


def tokens(text: str) -> frozenset[str]:
    cached = TOKEN_CACHE.get(text)
    if cached is not None:
        return cached
    out = set()
    for raw in normalize(text).split():
        token = canonical_token(raw)
        if len(token) >= 3 and token not in STOP_WORDS:
            out.add(token)
    result = frozenset(out)
    TOKEN_CACHE[text] = result
    return result


def numbers(text: str) -> frozenset[str]:
    cached = NUMBER_CACHE.get(text)
    if cached is not None:
        return cached
    result = frozenset(match.group(0).rstrip(".") for match in re.finditer(r"\b\d+(?:\.\d+)?\b", normalize(text)))
    NUMBER_CACHE[text] = result
    return result


def facets(text: str) -> frozenset[str]:
    cached = FACET_CACHE.get(text)
    if cached is not None:
        return cached
    norm = normalize(text)
    found = set()
    if re.search(r"\b(accion|aplicar|aplico|aplica|hago|debo|manejar|controlar|tratar|fertilizar|abonar|seleccionar|dejar|programar|renovar|podar|recolectar|adicionar|agregar|aportar|corregir)\b", norm):
        found.add(Facet.ACTION)
    if re.search(r"\b(cuando|momento|epoca|despues de|a los \d+ (mes|meses|dia|dias|semana|semanas|ano|anos))\b", norm):
        found.add(Facet.TIMING)
    if re.search(r"\b(dosis|cantidad|cuanto|\d+(?:\.\d+)?\s*(g|kg|l|ml|%|ppm|ha|hectarea|planta|sitio))\b", norm):
        found.add(Facet.AMOUNT)
    if re.search(r"(<=|>=|<|>)|\b(menor de|mayor de|por debajo de|por encima de|umbral)\b", norm) and re.search(r"\d", norm):
        found.add(Facet.THRESHOLD)
    if re.search(r"\b(efecto|beneficio|sirve|objetivo|resultado)\b", norm):
        found.add(Facet.EFFECT)
    if re.search(r"\b(riesgo|ignora|problema|consecuencia|afecta)\b", norm):
        found.add(Facet.RISK)
    result = frozenset(found)
    FACET_CACHE[text] = result
    return result


def strings_from_array(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def render_record(record: dict) -> str:
    lines: list[str] = []
    for key in ("title", "statement"):
        value = record.get(key)
        if value:
            lines.append(str(value).strip())
    labels = [
        ("condition", "Condicion"),
        ("action", "Accion"),
        ("expected_effect", "Efecto esperado"),
        ("risk_if_ignored", "Riesgo si se ignora"),
        ("applicability", "Aplicabilidad"),
    ]
    for key, label in labels:
        value = record.get(key)
        if value:
            lines.append(f"{label}: {str(value).strip()}")
    quant_data = record.get("quant_data")
    if isinstance(quant_data, list) and quant_data:
        lines.append("Datos cuantitativos:")
        for item in quant_data:
            if not isinstance(item, dict):
                continue
            metric = str(item.get("metric") or "dato").strip()
            exact = item.get("value_exact")
            value_min = item.get("value_min")
            value_max = item.get("value_max")
            unit = str(item.get("unit") or "").strip()
            qualifier = str(item.get("qualifier") or "").strip()
            if exact is not None:
                value = str(exact)
            elif value_min is not None and value_max is not None:
                value = f"{value_min}-{value_max}"
            elif value_min is not None:
                value = f">= {value_min}"
            elif value_max is not None:
                value = f"<= {value_max}"
            else:
                value = "sin valor"
            unit_part = f" {unit}" if unit else ""
            qual_part = f" ({qualifier})" if qualifier else ""
            lines.append(f"- {metric}: {value}{unit_part}{qual_part}")
    return "\n".join(line for line in lines if line).strip()


def load_records(extract_dir: Path) -> list[Record]:
    loaded: list[Record] = []
    for path in sorted(extract_dir.glob("*.jsonl")):
        for raw in path.read_text(encoding="utf-8").splitlines():
            if not raw.strip():
                continue
            data = json.loads(raw)
            questions = []
            questions.extend(strings_from_array(data.get("retrieval_hints")))
            questions.extend(strings_from_array(data.get("aliases")))
            if data.get("title"):
                questions.append(str(data["title"]).strip())
            questions = list(dict.fromkeys(question for question in questions if question))
            text = render_record(data)
            entity_text = " ".join(strings_from_array(data.get("entities")) + [str(data.get("topic") or "")])
            record_tokens = tokens(" ".join(questions) + " " + text)
            record_numbers = numbers(text)
            loaded.append(
                Record(
                    rid=str(data.get("id") or f"{path.name}:{len(loaded)}"),
                    path=path.name,
                    title=str(data.get("title") or "").strip(),
                    questions=tuple(questions),
                    text=text,
                    entity_tokens=tokens(entity_text),
                    tokens=record_tokens,
                    numbers=record_numbers,
                    facets=facets(text),
                )
            )
    return loaded


def build_cases(records: Iterable[Record]) -> list[Case]:
    cases: list[Case] = []
    for record in records:
        title = record.title or (record.questions[0] if record.questions else record.rid)
        for question in record.questions[:2]:
            cases.append(Case(question, record.rid, "hint"))
        cases.append(Case(f"Que significa {title}", record.rid, "informative"))
        if Facet.ACTION in record.facets:
            cases.append(Case(f"Que debo hacer cuando {title}", record.rid, "operational"))
        if Facet.THRESHOLD in record.facets or Facet.AMOUNT in record.facets or record.numbers:
            cases.append(Case(f"Cual es el criterio o cantidad para {title}", record.rid, "threshold"))
        if Facet.RISK in record.facets:
            cases.append(Case(f"Que pasa si no manejo {title}", record.rid, "risk"))
        if Facet.EFFECT in record.facets:
            cases.append(Case(f"Para que sirve {title}", record.rid, "effect"))
        if record.rid == "cap07_practice_006":
            cases.append(Case("que debo hacer cuando el fosforo es menor de 30 ppm", record.rid, "threshold"))
    return cases


def overlap_ratio(left: set[str] | frozenset[str], right: set[str] | frozenset[str]) -> float:
    if not left:
        return 0.0
    return len(set(left) & set(right)) / len(left)


def baseline_score(query: str, record: Record) -> float:
    query_tokens = tokens(query)
    if not query_tokens:
        return 0.0
    answer_tokens = record.tokens
    best = 0.0
    for question in record.questions or (record.title,):
        question_tokens = tokens(question)
        entry_tokens = set(question_tokens) | set(answer_tokens) | set(record.entity_tokens)
        question_overlap = overlap_ratio(query_tokens, question_tokens)
        entry_overlap = overlap_ratio(query_tokens, entry_tokens)
        entity_overlap = len(set(query_tokens) & set(record.entity_tokens))
        entity_score = min(0.95, 0.58 + entity_overlap * 0.12) if entity_overlap else 0.0
        score = max(question_overlap * 0.85 + entry_overlap * 0.15, entry_overlap * 0.75, entity_score)
        best = max(best, score)
    return max(0.0, min(1.0, best))


def candidate_score(query: str, record: Record) -> float:
    query_tokens = tokens(query)
    if not query_tokens:
        return 0.0
    query_numbers = numbers(query)
    query_facets = facets(query)
    query_units = set(tokens(query)) & {"ppm", "meq", "planta", "sitio", "mes", "meses", "dia", "dias", "semana", "semanas", "hectarea"}
    record_units = set(record.tokens) & {"ppm", "meq", "planta", "sitio", "mes", "meses", "dia", "dias", "semana", "semanas", "hectarea"}
    best_question_overlap = max((overlap_ratio(query_tokens, tokens(q)) for q in record.questions), default=0.0)
    entry_overlap = overlap_ratio(query_tokens, record.tokens)
    entity_overlap = overlap_ratio(query_tokens, record.entity_tokens)
    number_overlap = overlap_ratio(query_numbers, record.numbers)
    facet_coverage = overlap_ratio(query_facets, record.facets)
    overlap_count = len(set(query_tokens) & set(record.entity_tokens))
    boost = 0.12 if overlap_count >= 3 else 0.08 if overlap_count == 2 else 0.04 if overlap_count == 1 else 0.0
    score = (
        best_question_overlap * 0.38
        + entry_overlap * 0.22
        + entity_overlap * 0.18
        + number_overlap * 0.10
        + facet_coverage * 0.12
        + boost
    )
    if query_numbers and number_overlap <= 0:
        score *= 0.68
    if query_units and not (query_units & record_units):
        score *= 0.76
    if Facet.THRESHOLD in query_facets and Facet.THRESHOLD not in record.facets:
        score *= 0.82
    return max(0.0, min(1.0, score))


def ranked(query: str, records: list[Record], mode: str) -> list[tuple[float, Record]]:
    scorer = baseline_score if mode == "baseline" else candidate_score
    out = [(scorer(query, record), record) for record in records]
    out.sort(key=lambda item: (-item[0], item[1].rid))
    return out


def evidence_lines(record: Record, query: str) -> list[str]:
    q_tokens = tokens(query)
    q_numbers = numbers(query)
    q_facets = facets(query)
    selected: list[str] = []
    for raw in record.text.splitlines():
        line = raw.strip()
        if not line:
            continue
        line_tokens = tokens(line)
        line_numbers = numbers(line)
        line_facets = facets(line)
        relevant = (
            bool(set(line_tokens) & set(q_tokens))
            or bool(set(line_numbers) & set(q_numbers))
            or bool(set(line_facets) & set(q_facets))
            or len(selected) < 2
        )
        if relevant:
            selected.append(line)
        if len(selected) >= 7:
            break
    return selected or record.text.splitlines()[:3]


def evaluate(records: list[Record], cases: list[Case], min_score: float) -> dict:
    metrics = {
        "cases": len(cases),
        "baseline_top1": 0,
        "baseline_top3": 0,
        "candidate_top1": 0,
        "candidate_top3": 0,
        "candidate_context_chars": [],
        "baseline_context_chars": [],
        "candidate_expected_number_coverage": [],
        "baseline_expected_number_coverage": [],
        "candidate_expected_facet_coverage": [],
        "baseline_expected_facet_coverage": [],
        "regressions": [],
        "improvements": [],
    }
    record_by_id = {record.rid: record for record in records}
    for case in cases:
        base_ranked = ranked(case.query, records, "baseline")
        cand_ranked = ranked(case.query, records, "candidate")
        base_top = [record.rid for score, record in base_ranked[:3] if score >= min_score]
        cand_top = [record.rid for score, record in cand_ranked[:3] if score >= min_score]
        expected = case.expected
        base_hit1 = bool(base_top and base_top[0] == expected)
        cand_hit1 = bool(cand_top and cand_top[0] == expected)
        base_hit3 = expected in base_top
        cand_hit3 = expected in cand_top
        metrics["baseline_top1"] += int(base_hit1)
        metrics["candidate_top1"] += int(cand_hit1)
        metrics["baseline_top3"] += int(base_hit3)
        metrics["candidate_top3"] += int(cand_hit3)
        if base_hit1 and not cand_hit1:
            metrics["regressions"].append({"query": case.query, "expected": expected, "candidate_top": cand_top[:1]})
        if cand_hit1 and not base_hit1:
            metrics["improvements"].append({"query": case.query, "expected": expected, "baseline_top": base_top[:1]})

        expected_record = record_by_id[expected]
        base_context = "\n---\n".join(record.text for _, record in base_ranked[:3])
        cand_context = "\n---\n".join("\n".join(evidence_lines(record, case.query)) for _, record in cand_ranked[:3])
        metrics["baseline_context_chars"].append(len(base_context))
        metrics["candidate_context_chars"].append(len(cand_context))

        expected_numbers = expected_record.numbers
        base_numbers = numbers(base_context)
        cand_numbers = numbers(cand_context)
        metrics["baseline_expected_number_coverage"].append(overlap_ratio(expected_numbers, base_numbers) if expected_numbers else 1.0)
        metrics["candidate_expected_number_coverage"].append(overlap_ratio(expected_numbers, cand_numbers) if expected_numbers else 1.0)

        expected_facets = expected_record.facets
        base_facets = facets(base_context)
        cand_facets = facets(cand_context)
        metrics["baseline_expected_facet_coverage"].append(overlap_ratio(expected_facets, base_facets) if expected_facets else 1.0)
        metrics["candidate_expected_facet_coverage"].append(overlap_ratio(expected_facets, cand_facets) if expected_facets else 1.0)
    return metrics


def pct(value: float) -> float:
    return round(value * 100.0, 2)


def summarize(metrics: dict) -> dict:
    cases = max(1, metrics["cases"])
    return {
        "cases": metrics["cases"],
        "baseline_top1_pct": pct(metrics["baseline_top1"] / cases),
        "candidate_top1_pct": pct(metrics["candidate_top1"] / cases),
        "baseline_top3_pct": pct(metrics["baseline_top3"] / cases),
        "candidate_top3_pct": pct(metrics["candidate_top3"] / cases),
        "baseline_context_chars_avg": round(mean(metrics["baseline_context_chars"]), 1),
        "candidate_context_chars_avg": round(mean(metrics["candidate_context_chars"]), 1),
        "baseline_number_coverage_pct": pct(mean(metrics["baseline_expected_number_coverage"])),
        "candidate_number_coverage_pct": pct(mean(metrics["candidate_expected_number_coverage"])),
        "baseline_facet_coverage_pct": pct(mean(metrics["baseline_expected_facet_coverage"])),
        "candidate_facet_coverage_pct": pct(mean(metrics["candidate_expected_facet_coverage"])),
        "regressions": len(metrics["regressions"]),
        "improvements": len(metrics["improvements"]),
        "sample_regressions": metrics["regressions"][:8],
        "sample_improvements": metrics["improvements"][:8],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--extract-dir", default="app/src/main/assets/kb_nueva/extract")
    parser.add_argument("--min-score", type=float, default=0.15)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    records = load_records(Path(args.extract_dir))
    cases = build_cases(records)
    summary = summarize(evaluate(records, cases, args.min_score))
    if args.json:
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    else:
        for key, value in summary.items():
            if key.startswith("sample_"):
                continue
            print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
