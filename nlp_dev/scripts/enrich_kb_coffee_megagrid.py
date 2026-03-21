#!/usr/bin/env python3
"""
Enriquecimiento MEGAGRID de la KB para cafe.
Genera muchas entradas nuevas de forma estructurada para ampliar cobertura.
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


def uniq_tokens(*parts: str) -> list[str]:
    raw: list[str] = []
    for p in parts:
        raw.extend([t for t in normalize(p).split() if len(t) >= 3])
    seen: set[str] = set()
    out: list[str] = []
    for t in raw:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out


def card(category: str, questions: list[str], answer: str, keywords: list[str]) -> dict[str, Any]:
    return {
        "category": category,
        "questions": questions,
        "answer": answer,
        "keywords": keywords,
    }


CARDS: list[dict[str, Any]] = []


PHENO_STAGES = [
    ("establecimiento", "planta joven consolidando raiz y estructura"),
    ("prefloracion", "acumulacion de reservas para respuesta reproductiva"),
    ("floracion", "apertura floral y definicion de potencial"),
    ("cuajado", "retencion inicial de frutos"),
    ("llenado temprano", "formacion de grano con alta demanda"),
    ("llenado avanzado", "acumulacion de materia seca en fruto"),
    ("maduracion", "transicion a cosecha selectiva"),
    ("cosecha principal", "extraccion intensa de biomasa y nutrientes"),
    ("poscosecha", "recuperacion de planta y reequilibrio"),
    ("rebrote pospoda", "reconstruccion de arquitectura"),
    ("renovacion", "rejuvenecimiento del lote"),
    ("reposo fisiologico", "restablecimiento de reservas"),
]

ISSUES = [
    ("amarillamiento foliar", "desbalance nutricional o raiz bajo estres", "revisar analisis y corregir por lote"),
    ("manchas foliares", "presion sanitaria y microclima predisponente", "inspeccionar focos y mejorar aireacion"),
    ("marchitez en horas de sol", "deficit hidrico o raiz comprometida", "evaluar humedad y estado radicular"),
    ("caida de frutos", "estres en etapa critica o manejo desfasado", "proteger agua/nutricion en ventana sensible"),
    ("defoliacion acelerada", "enfermedad, plaga o estres acumulado", "diagnosticar causa dominante y actuar temprano"),
    ("baja floracion", "reserva insuficiente o estres previo", "fortalecer manejo prefloral"),
    ("cuajado irregular", "floracion no sincronizada y estres hidrico", "estabilizar ambiente y carga"),
    ("llenado deficiente", "desbalance de potasio, agua o carga", "ajustar nutricion y manejo de sombra"),
    ("brotes debiles", "falta de vigor y desbalance nutricional", "recuperar suelo y fraccionar fertilizacion"),
    ("encharcamiento recurrente", "drenaje insuficiente y riesgo radicular", "intervenir drenajes y cobertura"),
    ("erosion visible", "suelo desnudo y escorrentia alta", "reforzar coberturas y obras de conservacion"),
    ("alta broca", "fruta residual y repase insuficiente", "cosecha sanitaria y monitoreo por umbral"),
    ("alta roya", "susceptibilidad y ambiente favorable", "manejo preventivo y seguimiento por severidad"),
    ("pasilla elevada", "problemas de madurez, plaga o proceso", "mejorar seleccion y control en beneficio"),
    ("taza inconsistente", "variabilidad entre lotes y proceso", "estandarizar protocolos y trazabilidad"),
    ("baja productividad sostenida", "lote envejecido o manejo no adaptado", "evaluar renovacion escalonada"),
]

for stage, stage_desc in PHENO_STAGES:
    for issue, cause, action in ISSUES:
        CARDS.append(
            card(
                "diagnostico",
                [
                    f"En cafe durante {stage} tengo {issue}, que hago",
                    f"Como manejar {issue} en etapa de {stage} del cafeto",
                    f"Diagnostico de {issue} en {stage} para cafe",
                    f"Que revisar en {stage} si aparece {issue} en cafetal",
                ],
                f"En cafe, cuando aparece {issue} durante {stage} ({stage_desc}), suele relacionarse con {cause}. "
                f"La recomendacion es {action}, con accion inmediata y verificacion semanal por lote.",
                uniq_tokens("cafe diagnostico fenologia", stage, issue, cause, action),
            )
        )


CLIMATE_FACTORS = [
    ("sequia prolongada", "reduce turgencia y frena crecimiento"),
    ("lluvia intensa continua", "aumenta lavado de nutrientes y riesgo sanitario"),
    ("radiacion elevada", "incrementa estres termico foliar"),
    ("humedad relativa alta", "favorece enfermedades foliares"),
    ("humedad relativa baja", "aumenta demanda evaporativa"),
    ("viento fuerte", "eleva estres mecanico e hidrico"),
    ("ola de calor", "afecta floracion y cuajado"),
    ("frio inusual", "ralentiza metabolismo"),
    ("variabilidad de lluvias", "desincroniza labores"),
    ("nubosidad persistente", "reduce eficiencia fotosintetica"),
]

for stage, _stage_desc in PHENO_STAGES:
    for factor, effect in CLIMATE_FACTORS:
        CARDS.append(
            card(
                "cultivo",
                [
                    f"Como manejar cafe en {stage} con {factor}",
                    f"Que hacer en {stage} del cafeto si hay {factor}",
                    f"Plan para {factor} durante {stage} en cafe",
                    f"Riesgo de {factor} en cafe en etapa {stage}",
                ],
                f"Durante {stage} en cafe, {factor} puede hacer que {effect}. "
                f"Conviene ajustar sombra, humedad de suelo y calendario de labores para reducir impacto por lote.",
                uniq_tokens("cafe clima manejo", stage, factor, effect),
            )
        )


NUTRIENTS = [
    ("nitrogeno", "N", "vigor vegetativo y brotacion"),
    ("fosforo", "P", "raiz, energia y establecimiento"),
    ("potasio", "K", "llenado, calidad y balance hidrico"),
    ("calcio", "Ca", "estructura de tejidos y raiz"),
    ("magnesio", "Mg", "fotosintesis y actividad foliar"),
    ("azufre", "S", "metabolismo y sintesis"),
    ("boro", "B", "floracion y cuajado"),
    ("zinc", "Zn", "crecimiento de brotes"),
    ("hierro", "Fe", "funcion clorofilica"),
    ("manganeso", "Mn", "procesos enzimaticos"),
    ("cobre", "Cu", "defensa y metabolismo"),
    ("molibdeno", "Mo", "asimilacion de nitrogeno"),
]

NUTRI_GOALS = [
    ("recuperar vigor", "ajustar dosis y fraccionamiento"),
    ("mejorar floracion", "sincronizar nutricion prefloral"),
    ("sostener cuajado", "proteger etapa sensible"),
    ("optimizar llenado", "balancear oferta en fase de alta demanda"),
    ("subir calidad de taza", "alinear nutricion con sanidad y cosecha"),
]

for nutrient, symbol, role in NUTRIENTS:
    for goal, strategy in NUTRI_GOALS:
        CARDS.append(
            card(
                "fertilizacion",
                [
                    f"Como usar {nutrient} ({symbol}) en cafe para {goal}",
                    f"Manejo de {nutrient} en cafeto para {goal}",
                    f"Estrategia con {nutrient} para {goal} en cafe",
                    f"Que cuidar con {nutrient} al buscar {goal} en cafe",
                ],
                f"En cafe, {nutrient} ({symbol}) aporta principalmente a {role}. "
                f"Para {goal}, la estrategia es {strategy} segun analisis de suelo/hoja y respuesta del lote.",
                uniq_tokens("cafe fertilizacion", nutrient, symbol, goal, role, strategy),
            )
        )


POSTHARVEST_ISSUES = [
    ("fermentacion acelerada", "temperatura alta y control insuficiente", "acortar ventanas y reforzar higiene"),
    ("fermentacion lenta", "baja actividad y lote heterogeneo", "uniformar madurez y temperatura"),
    ("olor avinagrado", "sobrefermentacion", "ajustar tiempo y limpieza"),
    ("olor a moho", "humedad alta en secado o bodega", "controlar humedad final y ventilacion"),
    ("secado desigual", "capa irregular y volteo insuficiente", "homogeneizar espesor y frecuencia"),
    ("rehumectacion nocturna", "proteccion insuficiente del lote", "cobertura nocturna y control ambiental"),
    ("quiebre de grano", "manejo mecanico agresivo", "calibrar equipo y flujo"),
    ("grano manchado", "contaminacion o secado deficiente", "mejorar higiene y secado"),
    ("pasilla alta", "materia prima heterogenea", "seleccion de madurez en cosecha"),
    ("inconsistencia sensorial", "variabilidad de proceso", "estandarizar protocolo por lote"),
    ("baja fragancia", "pobre manejo de poscosecha", "mejorar limpieza y estabilidad"),
    ("retrogusto corto", "proceso y materia prima dispareja", "ordenar cadena de calidad"),
    ("defecto a heno", "secado incompleto", "asegurar curva de secado"),
    ("defecto terroso", "bodega humeda o sucia", "sanear almacenamiento"),
    ("defecto medicinal", "contaminacion cruzada", "aislar insumos y limpiar equipos"),
    ("perdida de frescura", "almacenamiento prolongado sin control", "rotar inventario"),
]

for issue, likely, fix in POSTHARVEST_ISSUES:
    CARDS.append(
        card(
            "cosecha",
            [
                f"Como corregir {issue} en cafe",
                f"Causa de {issue} en poscosecha de cafe",
                f"Que hacer si aparece {issue} en un lote de cafe",
                f"Prevencion de {issue} en beneficio de cafe",
            ],
            f"En la poscosecha de cafe, {issue} suele relacionarse con {likely}. "
            f"Para corregirlo, se recomienda {fix} y mantener trazabilidad del lote.",
            uniq_tokens("cafe poscosecha defecto", issue, likely, fix),
        )
    )


MANAGEMENT_DECISIONS = [
    ("priorizar lotes por potencial", "asignar recursos donde hay mayor retorno"),
    ("renovar por bloques", "mantener flujo de caja mientras se rejuvenece"),
    ("enfocar control sanitario por focos", "evitar dispersion y bajar costo"),
    ("fraccionar fertilizacion", "mejorar eficiencia y respuesta"),
    ("escalonar cosecha", "reducir fruta sobremadura"),
    ("segmentar lotes para venta", "capturar diferencial de calidad"),
    ("medir costo por kilo", "detectar ineficiencias ocultas"),
    ("auditar proceso de beneficio", "reducir defectos recurrentes"),
    ("entrenar cuadrilla de recoleccion", "subir calidad de entrada"),
    ("programar podas por lote", "equilibrar vigor y carga"),
    ("documentar trazabilidad completa", "fortalecer negociacion"),
    ("gestionar agua de proceso", "reducir impacto y costo"),
    ("controlar seguridad laboral", "evitar interrupciones operativas"),
    ("validar indicadores semanales", "acelerar mejora continua"),
    ("monitorear clima local", "anticipar decisiones de campo"),
    ("optimizar flujo de bodega", "prevenir deterioro poscosecha"),
]

for decision, result in MANAGEMENT_DECISIONS:
    CARDS.append(
        card(
            "general",
            [
                f"Como aplicar {decision} en cafe",
                f"Beneficios de {decision} en finca cafetera",
                f"Plan para {decision} en caficultura",
                f"Que indicadores usar para {decision} en cafe",
            ],
            f"En caficultura, {decision} permite {result}. "
            f"Su implementacion funciona mejor con seguimiento por lote y metas de corto plazo.",
            uniq_tokens("cafe gestion", decision, result),
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
    print(f"Entradas nuevas cafe (ultramax-grid): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()

