#!/usr/bin/env python3
"""
Builds AgroChat KB JSON from chapter-level .records.jsonl files.

Input schema (per line) comes from:
  kb nueva/extract/*.records.jsonl

Output schema matches app expectation:
{
  "version": "...",
  "description": "...",
  "categories": [...],
  "entries": [
    {
      "id": 1,
      "category": "general",
      "questions": [...],
      "answer": "...",
      ... extra metadata fields ...
    }
  ]
}
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from pathlib import Path
from typing import Dict, Iterable, List


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").strip())


def strip_accents(text: str) -> str:
    normalized = unicodedata.normalize("NFD", text)
    return "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")


def slugify(text: str) -> str:
    text = strip_accents((text or "").lower())
    text = re.sub(r"[^a-z0-9]+", "_", text)
    return text.strip("_")


def humanize_token(token: str) -> str:
    return normalize_space((token or "").replace("_", " "))


def dedupe_preserve_order(values: Iterable[str]) -> List[str]:
    seen = set()
    output = []
    for value in values:
        cleaned = normalize_space(value)
        if not cleaned:
            continue
        key = strip_accents(cleaned.lower())
        if key in seen:
            continue
        seen.add(key)
        output.append(cleaned)
    return output


def format_numeric_value(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def format_quant_item(item: Dict) -> str:
    metric = humanize_token(item.get("metric", "dato"))
    unit = normalize_space(item.get("unit") or "")
    qualifier = normalize_space(item.get("qualifier") or "")

    exact = format_numeric_value(item.get("value_exact"))
    value_min = format_numeric_value(item.get("value_min"))
    value_max = format_numeric_value(item.get("value_max"))

    if exact is not None:
        value_repr = exact
    elif value_min is not None and value_max is not None:
        value_repr = f"{value_min}-{value_max}"
    elif value_min is not None:
        value_repr = f">= {value_min}"
    elif value_max is not None:
        value_repr = f"<= {value_max}"
    else:
        value_repr = "sin valor numerico"

    if unit:
        value_repr = f"{value_repr} {unit}"
    if qualifier:
        value_repr = f"{value_repr} ({qualifier})"

    return f"{metric}: {value_repr}"


def format_source_ref(source_ref: Dict) -> str:
    if not isinstance(source_ref, dict):
        return ""
    document = normalize_space(source_ref.get("document") or "")
    pages = source_ref.get("pages") or []
    if not isinstance(pages, list):
        pages = []
    pages_fmt = ", ".join(str(p) for p in pages if p is not None)
    if document and pages_fmt:
        return f"{document} (paginas {pages_fmt})"
    if document:
        return document
    if pages_fmt:
        return f"paginas {pages_fmt}"
    return ""


def build_answer(record: Dict) -> str:
    lines = []

    title = normalize_space(record.get("title") or "")
    statement = normalize_space(record.get("statement") or "")
    condition = normalize_space(record.get("condition") or "")
    action = normalize_space(record.get("action") or "")
    expected_effect = normalize_space(record.get("expected_effect") or "")
    risk_if_ignored = normalize_space(record.get("risk_if_ignored") or "")
    applicability = normalize_space(record.get("applicability") or "")
    source_attr = normalize_space(record.get("source_attribution") or "")
    source_ref = format_source_ref(record.get("source_ref"))
    evidence_strength = normalize_space(record.get("evidence_strength") or "")
    priority = normalize_space(record.get("priority") or "")
    record_type = normalize_space(record.get("record_type") or "")

    if title:
        lines.append(title)
    if statement:
        lines.append(statement)
    if condition:
        lines.append(f"Condicion: {condition}.")
    if action:
        lines.append(f"Recomendacion: {action}.")
    if expected_effect:
        lines.append(f"Efecto esperado: {expected_effect}.")
    if risk_if_ignored:
        lines.append(f"Riesgo si se ignora: {risk_if_ignored}.")

    quant_data = record.get("quant_data") or []
    if isinstance(quant_data, list) and quant_data:
        lines.append("Datos clave:")
        for item in quant_data[:8]:
            if isinstance(item, dict):
                lines.append(f"- {format_quant_item(item)}")

    classification = record.get("classification")
    if isinstance(classification, dict):
        criterion = humanize_token(classification.get("criterion") or "")
        classes = classification.get("classes") or []
        if isinstance(classes, list) and classes:
            class_text = ", ".join(humanize_token(c) for c in classes if c)
            if criterion:
                lines.append(f"Clasificacion ({criterion}): {class_text}.")
            else:
                lines.append(f"Clasificacion: {class_text}.")

    if applicability:
        lines.append(f"Aplica para: {applicability}.")

    entities = record.get("entities") or []
    if isinstance(entities, list) and entities:
        entities_text = ", ".join(humanize_token(e) for e in entities[:8] if e)
        if entities_text:
            lines.append(f"Terminos clave: {entities_text}.")

    source_bits = [bit for bit in [source_attr, source_ref] if bit]
    if source_bits:
        lines.append(f"Fuente: {' | '.join(source_bits)}.")

    metadata_bits = [bit for bit in [f"tipo={record_type}" if record_type else "", f"prioridad={priority}" if priority else "", f"evidencia={evidence_strength}" if evidence_strength else ""] if bit]
    if metadata_bits:
        lines.append(f"Ficha tecnica: {', '.join(metadata_bits)}.")

    uncertain = record.get("uncertain_text")
    uncertainty_note = normalize_space(record.get("uncertainty_note") or "")
    if uncertain:
        lines.append("Nota: este contenido tiene incertidumbre declarada en la fuente.")
        if uncertainty_note:
            lines.append(f"Detalle de incertidumbre: {uncertainty_note}.")

    return "\n".join(lines).strip()


def build_questions(record: Dict) -> List[str]:
    retrieval_hints = record.get("retrieval_hints") or []
    aliases = record.get("aliases") or []
    entities = record.get("entities") or []
    record_type = humanize_token(record.get("record_type") or "")
    topic = humanize_token(record.get("topic") or "")
    subtopic = humanize_token(record.get("subtopic") or "")
    chapter_title = humanize_token(record.get("chapter_title") or "")
    title = normalize_space(record.get("title") or "")

    generated: List[str] = []

    if isinstance(retrieval_hints, list):
        generated.extend(str(x) for x in retrieval_hints if x)
    if isinstance(aliases, list):
        generated.extend(str(x) for x in aliases if x)
    if title:
        generated.append(title)

    if topic:
        generated.append(f"{topic} en cafe")
        if record_type:
            generated.append(f"{record_type} sobre {topic} en cafe")

    if subtopic and subtopic != topic:
        generated.append(f"{topic}: {subtopic}" if topic else subtopic)

    if record_type == "definition" and topic:
        generated.append(f"que es {topic}")
    elif record_type == "classification" and topic:
        generated.append(f"clasificacion de {topic}")
    elif record_type in {"rule", "practice", "parameter"} and topic:
        generated.append(f"como manejar {topic} en cafe")
    elif record_type == "risk" and topic:
        generated.append(f"riesgos de {topic} en cafe")
    elif record_type == "fact" and topic:
        generated.append(f"dato clave de {topic} en cafe")

    if chapter_title:
        generated.append(f"tema {chapter_title}")

    if isinstance(entities, list):
        for entity in entities[:2]:
            entity_h = humanize_token(str(entity))
            if not entity_h:
                continue
            if topic:
                generated.append(f"{topic} y {entity_h}")
            else:
                generated.append(entity_h)

    return dedupe_preserve_order(generated)


def chapter_category(record: Dict) -> str:
    chapter_id = normalize_space(str(record.get("chapter_id") or "00"))
    chapter_title = normalize_space(record.get("chapter_title") or "")
    chapter_slug = slugify(chapter_title)[:48] if chapter_title else "general"
    return f"cap{chapter_id}_{chapter_slug}"


def load_records(input_dir: Path) -> List[Dict]:
    records: List[Dict] = []
    files = sorted(input_dir.glob("*.jsonl"))
    if not files:
        raise FileNotFoundError(f"No se encontraron archivos *.jsonl en: {input_dir}")

    for file_path in files:
        with file_path.open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise ValueError(f"JSON invalido en {file_path}:{line_number}: {exc}") from exc
                record["_source_file"] = file_path.name
                records.append(record)
    return records


def build_general_entries(next_id_start: int = 1) -> List[Dict]:
    entries = [
        {
            "id": next_id_start,
            "category": "general",
            "questions": [
                "hola",
                "buenos dias",
                "buenas tardes",
                "buenas noches",
                "quien eres",
            ],
            "answer": "Hola, soy FarmifAI. Te apoyo con recomendaciones tecnicas de caficultura basadas en una base de conocimiento estructurada.",
            "record_type": "general",
            "priority": "high",
        },
        {
            "id": next_id_start + 1,
            "category": "general",
            "questions": [
                "que temas cubres",
                "en que me puedes ayudar",
                "cobertura de la base de conocimiento",
                "temas de cafe que conoces",
            ],
            "answer": (
                "Puedo ayudarte en temas de caficultura como sistemas de produccion, crecimiento del cafeto, "
                "productividad, establecimiento del cafetal, manejo de arvenses, densidad de siembra, renovacion, "
                "sistemas agroforestales, nutricion, cafes especiales, sistemas intercalados y buenas practicas agricolas."
            ),
            "record_type": "general",
            "priority": "high",
        },
        {
            "id": next_id_start + 2,
            "category": "general",
            "questions": [
                "de donde sale la informacion",
                "cual es la fuente",
                "las recomendaciones tienen respaldo",
            ],
            "answer": (
                "La informacion proviene de material tecnico de caficultura y fue convertida a registros estructurados "
                "con trazabilidad por documento y pagina cuando la referencia esta disponible."
            ),
            "record_type": "general",
            "priority": "medium",
        },
        {
            "id": next_id_start + 3,
            "category": "general",
            "questions": [
                "gracias",
                "muchas gracias",
                "te agradezco",
            ],
            "answer": "Con gusto. Si quieres, te ayudo a aterrizar la recomendacion a tu lote y etapa del cultivo.",
            "record_type": "general",
            "priority": "medium",
        },
        {
            "id": next_id_start + 4,
            "category": "general",
            "questions": [
                "adios",
                "hasta luego",
                "nos vemos",
            ],
            "answer": "Hasta pronto. Que tengas una excelente jornada en campo.",
            "record_type": "general",
            "priority": "medium",
        },
    ]
    return entries


def build_kb(records: List[Dict]) -> Dict:
    records_sorted = sorted(
        records,
        key=lambda r: (
            int(str(r.get("chapter_id", "0")) or "0"),
            str(r.get("record_type") or ""),
            str(r.get("id") or ""),
        ),
    )

    entries: List[Dict] = build_general_entries(next_id_start=1)
    next_id = len(entries) + 1

    for record in records_sorted:
        questions = build_questions(record)
        if not questions:
            continue

        entry = {
            "id": next_id,
            "category": chapter_category(record),
            "questions": questions,
            "answer": build_answer(record),
            "record_id": record.get("id"),
            "chapter_id": record.get("chapter_id"),
            "chapter_title": record.get("chapter_title"),
            "record_type": record.get("record_type"),
            "priority": record.get("priority"),
            "topic": record.get("topic"),
            "subtopic": record.get("subtopic"),
            "source_ref": record.get("source_ref"),
            "source_attribution": record.get("source_attribution"),
            "evidence_strength": record.get("evidence_strength"),
            "uncertain_text": record.get("uncertain_text"),
            "uncertainty_note": record.get("uncertainty_note"),
        }

        entries.append(entry)
        next_id += 1

    categories = sorted({entry["category"] for entry in entries})
    total_questions = sum(len(entry["questions"]) for entry in entries)

    return {
        "version": "3.0-records-jsonl",
        "description": "Base de conocimiento AgroChat construida desde records.jsonl de caficultura",
        "categories": categories,
        "entries": entries,
        "metadata": {
            "record_entries": len(entries) - 5,
            "general_entries": 5,
            "total_entries": len(entries),
            "total_questions": total_questions,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Convierte records.jsonl a agrochat_knowledge_base.json")
    parser.add_argument(
        "--input-dir",
        default="kb nueva/extract",
        help="Directorio con archivos *.records.jsonl",
    )
    parser.add_argument(
        "--output-kb",
        default="app/src/main/assets/agrochat_knowledge_base.json",
        help="Ruta de salida para la KB en assets",
    )
    parser.add_argument(
        "--output-dataset",
        default="nlp_dev/data/datasets/agrochat_knowledge_base.json",
        help="Ruta de salida opcional para dataset espejo",
    )
    args = parser.parse_args()

    input_dir = Path(args.input_dir)
    output_kb = Path(args.output_kb)
    output_dataset = Path(args.output_dataset)

    records = load_records(input_dir)
    kb = build_kb(records)

    output_kb.parent.mkdir(parents=True, exist_ok=True)
    output_dataset.parent.mkdir(parents=True, exist_ok=True)

    with output_kb.open("w", encoding="utf-8") as handle:
        json.dump(kb, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    with output_dataset.open("w", encoding="utf-8") as handle:
        json.dump(kb, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    total_entries = len(kb.get("entries", []))
    total_questions = sum(len(entry.get("questions", [])) for entry in kb.get("entries", []))
    print(f"KB generada: {output_kb}")
    print(f"Dataset espejo: {output_dataset}")
    print(f"Entradas: {total_entries}")
    print(f"Preguntas: {total_questions}")
    print(f"Categorias: {len(kb.get('categories', []))}")


if __name__ == "__main__":
    main()
