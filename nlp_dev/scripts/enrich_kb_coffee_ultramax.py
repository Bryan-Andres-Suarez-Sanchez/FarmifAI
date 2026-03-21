#!/usr/bin/env python3
"""
Enriquecimiento ULTRAMAX de la KB para cafe.
Objetivo: ampliar cobertura conversacional con bloques tecnicos
de manejo agronomico, diagnostico, poscosecha y gestion.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
KB_PATH = ROOT / "app" / "src" / "main" / "assets" / "agrochat_knowledge_base.json"


def normalize(text: str) -> str:
    text = text.lower().strip()
    text = (
        text.replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ñ", "n")
    )
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def split_tokens(*parts: str) -> list[str]:
    all_tokens: list[str] = []
    for part in parts:
        all_tokens.extend([t for t in normalize(part).split() if len(t) >= 3])
    seen: set[str] = set()
    ordered: list[str] = []
    for t in all_tokens:
        if t not in seen:
            seen.add(t)
            ordered.append(t)
    return ordered


def card(category: str, questions: list[str], answer: str, keywords: list[str]) -> dict[str, Any]:
    return {
        "category": category,
        "questions": questions,
        "answer": answer,
        "keywords": keywords,
    }


CARDS: list[dict[str, Any]] = []


ALTITUDE_SCENARIOS = [
    ("<1000 msnm", "temperaturas altas y maduracion acelerada", "sombra regulada y manejo hidrico fino"),
    ("1000-1200 msnm", "ambiente calido-intermedio", "balance entre productividad y sanidad"),
    ("1200-1400 msnm", "transicion climatica con buena adaptabilidad", "lotes diferenciados por exposicion"),
    ("1400-1600 msnm", "condiciones frecuentes para buena taza", "consistencia en cosecha selectiva"),
    ("1600-1800 msnm", "maduracion mas lenta y potencial de calidad", "control sanitario preventivo"),
    ("1800-2000 msnm", "mayor amplitud termica", "manejo de nutricion por etapa"),
    (">2000 msnm", "crecimiento mas lento y alto riesgo de frio", "variedad adaptada y manejo conservador"),
    ("ladera baja", "mayor estres termico en dias soleados", "cobertura de suelo y sombreo"),
    ("ladera media", "variacion de humedad por pendiente", "curvas a nivel y drenaje"),
    ("ladera alta", "mayor exposicion a viento y frio", "barreras vivas y monitoreo"),
    ("zona de valle", "mayor acumulacion de humedad", "aireacion y vigilancia de enfermedades"),
    ("zona de cresta", "evaporacion mas alta", "retencion de humedad y cobertura"),
    ("orientacion oriente", "radiacion matinal predominante", "ajuste de podas y sombra"),
    ("orientacion occidente", "radiacion intensa en tarde", "proteger tejido en horas criticas"),
    ("microcuenca humeda", "ambiente predisponente a enfermedad", "sanidad y ventilacion"),
    ("microcuenca seca", "alto riesgo de estres hidrico", "cosecha de agua y riego de apoyo"),
]

for zone, condition, focus in ALTITUDE_SCENARIOS:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como manejar cafe en {zone}",
                f"Recomendaciones para cafe en {zone}",
                f"Riesgos de cafe en {zone}",
                f"Que priorizar en cafetal de {zone}",
            ],
            f"En cafe cultivado en {zone}, suele presentarse {condition}. "
            f"La estrategia tecnica recomendada es {focus}, ajustando decisiones por lote y fenologia.",
            split_tokens("cafe altitud manejo", zone, condition, focus),
        )
    )


SHADE_SPECIES = [
    ("guamo", "sombra y aporte de biomasa", "podas para evitar exceso de humedad"),
    ("nogal cafetero", "estructura de sombra y diversificacion", "distancia y densidad controlada"),
    ("poro", "aporte de materia organica y sombra", "manejo de competencia radicular"),
    ("matarraton", "biomasa y fijacion biologica", "podas sincronizadas con necesidades del cafeto"),
    ("inga", "sombra funcional en agroforesteria", "regulacion periodica de copa"),
    ("cedro", "componente maderable con sombra parcial", "balance luz-produccion"),
    ("guayacan", "arbol de soporte ecologico", "evitar sombreo excesivo"),
    ("carbonero", "sombra y cobertura vertical", "control de densidad"),
    ("laurel", "componente de sistema mixto", "podas preventivas"),
    ("guanabano", "diversificacion con frutal", "manejo de competencia"),
    ("naranjo", "diversificacion e ingreso adicional", "orden de copa y luz"),
    ("aguacate", "arbol de valor economico complementario", "ajuste de distancia de siembra"),
    ("platano", "sombrio transitorio y flujo de caja", "evitar competencia temprana"),
    ("banano", "proteccion inicial del cafeto joven", "manejo de densidad"),
    ("acacia", "sombra y servicios ecosistemicos", "poda de regulacion"),
    ("guadua", "barrera y proteccion de suelos", "manejo de bordes"),
]

for species, value, caution in SHADE_SPECIES:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como integrar {species} en sistemas de cafe",
                f"Beneficios de {species} en cafetal",
                f"Riesgos de usar {species} como sombra en cafe",
                f"Manejo recomendado de {species} en cafe",
            ],
            f"En caficultura, {species} puede aportar {value}. "
            f"Para que funcione bien, se recomienda {caution} y monitorear respuesta del lote.",
            split_tokens("cafe sombra agroforesteria", species, value, caution),
        )
    )


NUTRIENT_INTERACTIONS = [
    ("N-K", "crecimiento y llenado de fruto", "balancear dosis segun carga y analisis"),
    ("N-P", "vigor y desarrollo radicular", "ajustar etapa fenologica"),
    ("K-Mg", "antagonismo por desbalance cationico", "evitar exceso unilateral"),
    ("Ca-Mg", "estructura de suelo y absorcion", "revisar saturacion de bases"),
    ("Ca-K", "competencia por sitios de intercambio", "mantener relacion equilibrada"),
    ("P-Zn", "bloqueos por sobreaplicacion de fosforo", "dosificar con precision"),
    ("N-S", "sintesis de proteinas", "acompanar fuentes nitrogenadas"),
    ("B-Ca", "floracion y cuajado", "controlar dosis de micronutrientes"),
    ("Fe-Mn", "equilibrio en suelos acidos", "ajustar pH y monitoreo"),
    ("Cu-Zn", "interacciones de micronutrientes", "evitar sobredosis"),
    ("Na-Ca", "dispersion de suelos y estructura", "controlar sodicidad"),
    ("K-B", "calidad de fruto y transporte", "aplicar segun demanda"),
    ("P-Ca", "disponibilidad en suelos reactivos", "usar estrategias por ambiente"),
    ("Mg-S", "metabolismo fotosintetico", "corregir deficiencias sin excesos"),
    ("N-B", "brotacion y floracion", "fraccionar aplicaciones"),
    ("K-Ca-Mg", "triangulo cationico del suelo", "optimizar relacion para absorcion"),
]

for interaction, impact, guideline in NUTRIENT_INTERACTIONS:
    CARDS.append(
        card(
            "fertilizacion",
            [
                f"Como manejar interaccion {interaction} en cafe",
                f"Impacto de {interaction} en nutricion de cafeto",
                f"Problemas por desbalance {interaction} en cafe",
                f"Recomendacion tecnica sobre {interaction} para cafe",
            ],
            f"En cafe, la interaccion {interaction} influye en {impact}. "
            f"Para minimizar riesgos se recomienda {guideline} con base en analisis y seguimiento por lote.",
            split_tokens("cafe nutricion interaccion", interaction, impact, guideline),
        )
    )


PRACTICAL_PROTOCOLS = [
    ("diagnostico rapido de campo", "recorrer lote, mapear focos y priorizar accion en 48 horas"),
    ("muestreo de broca", "definir puntos, contar incidencia y activar umbral"),
    ("muestreo de roya", "evaluar severidad por estrato y orientar intervencion"),
    ("verificacion de humedad de pergamino", "medir antes de embodegar y registrar lote"),
    ("control de fermentacion", "estandarizar tiempo, limpieza y temperatura"),
    ("control de secado", "uniformar capa, volteo y proteccion nocturna"),
    ("auditoria interna de bodega", "revisar higiene, ventilacion y trazabilidad"),
    ("revision de drenajes", "inspeccionar flujo y puntos de encharcamiento"),
    ("chequeo de compactacion", "evaluar penetracion y salud radicular"),
    ("evaluacion de cobertura de suelo", "detectar zonas desnudas y riesgo de erosion"),
    ("calibracion de despulpadora", "ajustar equipo para reducir dano mecanico"),
    ("plan de repase de cosecha", "definir frecuencia y responsables"),
    ("control de residuos de poda", "retiro y manejo sanitario por foco"),
    ("revision de sombra", "estimar porcentaje y podar segun necesidad"),
    ("control de agua en beneficio", "registrar consumo y optimizar uso"),
    ("seguimiento de indicadores por lote", "actualizar productividad, sanidad y calidad"),
]

for protocol, steps in PRACTICAL_PROTOCOLS:
    CARDS.append(
        card(
            "diagnostico",
            [
                f"Como hacer {protocol} en cafe",
                f"Paso a paso de {protocol} para cafetal",
                f"Checklist de {protocol} en finca cafetera",
                f"Errores comunes en {protocol} de cafe",
            ],
            f"Para cafe, el protocolo de {protocol} se basa en {steps}. "
            f"Aplicarlo con disciplina mejora decisiones tecnicas y reduce respuestas tardias.",
            split_tokens("cafe protocolo campo", protocol, steps),
        )
    )


MARKET_AND_VALUE = [
    ("segmentacion por lote", "vender cada lote por su potencial y consistencia"),
    ("historial de calidad", "demostrar mejora continua frente a compradores"),
    ("trazabilidad robusta", "justificar diferencial de precio"),
    ("estandarizacion de proceso", "reducir variabilidad de taza"),
    ("catacion periodica", "tomar decisiones con retroalimentacion sensorial"),
    ("estrategia de microlotes", "capturar valor en nichos especializados"),
    ("negociacion con evidencia tecnica", "aumentar confianza comercial"),
    ("gestionar prima de calidad", "alinear practica de campo con mercado"),
    ("estabilidad de humedad en bodega", "evitar perdida de valor por deterioro"),
    ("consistencia de cosecha selectiva", "disminuir defectos de taza"),
    ("marca de origen de finca", "comunicar identidad y diferenciales"),
    ("planeacion de oferta", "cumplir entregas con calidad estable"),
    ("control de costos ocultos", "mejorar margen real de operacion"),
    ("evaluacion de riesgo comercial", "diversificar canales de venta"),
    ("acuerdos de largo plazo", "dar estabilidad al flujo de caja"),
    ("documentacion para compradores", "mostrar trazabilidad y protocolo"),
]

for strategy, benefit in MARKET_AND_VALUE:
    CARDS.append(
        card(
            "general",
            [
                f"Como aplicar {strategy} en cafe",
                f"Beneficio de {strategy} para vender cafe",
                f"Plan practico de {strategy} en finca cafetera",
                f"Que medir para mejorar {strategy} en cafe",
            ],
            f"En cafe, {strategy} ayuda a {benefit}. "
            f"Su exito depende de registros ordenados, ejecucion por lote y seguimiento continuo.",
            split_tokens("cafe mercado valor", strategy, benefit),
        )
    )


def main() -> None:
    kb = json.loads(KB_PATH.read_text(encoding="utf-8"))
    entries = kb.get("entries", [])
    categories = kb.get("categories", [])

    existing_q = {normalize(q) for e in entries for q in e.get("questions", [])}
    existing_answer = {normalize(e.get("answer", "")) for e in entries if e.get("answer")}

    next_id = max((e.get("id", 0) for e in entries), default=0) + 1
    added = 0

    for c in CARDS:
        unique_questions = []
        for q in c["questions"]:
            nq = normalize(q)
            if nq and nq not in existing_q:
                unique_questions.append(q)

        if not unique_questions:
            continue

        n_answer = normalize(c["answer"])
        if n_answer in existing_answer:
            continue

        category = c["category"]
        if category not in categories:
            categories.append(category)

        entries.append(
            {
                "id": next_id,
                "category": category,
                "questions": unique_questions,
                "answer": c["answer"],
                "keywords": c["keywords"],
            }
        )
        for q in unique_questions:
            existing_q.add(normalize(q))
        existing_answer.add(n_answer)
        next_id += 1
        added += 1

    kb["entries"] = entries
    kb["categories"] = categories
    KB_PATH.write_text(json.dumps(kb, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total_q = sum(len(e.get("questions", [])) for e in entries)
    print(f"Entradas nuevas cafe (ultramax): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()

