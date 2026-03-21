#!/usr/bin/env python3
"""
Enriquecimiento OMNISCALE de la KB con contenido solo de cafe.
Meta: ampliar cobertura conversacional para preguntas abiertas y tecnicas.
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


def tokens(*parts: str) -> list[str]:
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


PHENOLOGY = [
    ("brotacion inicial", "estimular brotes sanos", "balance de poda, agua y nutricion"),
    ("prefloracion", "acumular reservas y vigor", "manejo de estres y preparacion nutricional"),
    ("floracion", "lograr floracion uniforme", "estabilidad hidrica y sanidad de copa"),
    ("cuajado", "asegurar retencion de frutos", "evitar estres hidrico y nutricional"),
    ("llenado temprano", "definir potencial de grano", "potasio, agua y control sanitario"),
    ("llenado avanzado", "sostener peso y calidad", "nutricion balanceada y manejo de carga"),
    ("maduracion", "uniformidad de cosecha", "monitoreo de madurez y pases oportunos"),
    ("cosecha principal", "maxima recuperacion de fruta madura", "seleccion de recolecta y repase"),
    ("poscosecha inmediata", "recuperar planta", "nutricion de soporte y manejo sanitario"),
    ("reposo fisiologico", "reconstruir reservas", "cobertura de suelo y manejo de estres"),
    ("rebrote pospoda", "armar nueva arquitectura", "seleccion de ejes y control de brotes"),
    ("ciclo de renovacion", "rejuvenecer lote", "plan escalonado por bloques"),
]

for stage, objective, priority in PHENOLOGY:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como manejar cafe en etapa de {stage}",
                f"Que priorizar en {stage} del cafeto",
                f"Plan tecnico para {stage} en cafe",
                f"Recomendaciones de {stage} para cafetal",
            ],
            f"En cafe, la etapa de {stage} busca {objective}. "
            f"La prioridad tecnica es {priority}, con decisiones por lote segun vigor y clima local.",
            tokens("cafe fenologia manejo", stage, objective, priority),
        )
    )
    CARDS.append(
        card(
            "diagnostico",
            [
                f"Problemas frecuentes de {stage} en cafe",
                f"Como diagnosticar fallas en {stage} del cafeto",
                f"Senales de alerta en {stage} de cafe",
                f"Que revisar si falla {stage} en cafetal",
            ],
            f"Si {stage} no avanza bien en cafe, revisa vigor previo, humedad del suelo, sanidad de focos y calidad de labores. "
            f"Corregir temprano en esta etapa mejora todo el ciclo productivo siguiente.",
            tokens("cafe diagnostico fenologia", stage, "senales", "alerta"),
        )
    )


SOIL_INDICATORS = [
    ("pH del suelo", "disponibilidad de nutrientes", "corregir acidez o alcalinidad segun analisis"),
    ("materia organica", "retencion de agua y actividad biologica", "aumentar reciclaje de residuos bien descompuestos"),
    ("capacidad de intercambio cationico", "reserva de nutrientes", "mejorar estructura y contenido organico"),
    ("saturacion de bases", "equilibrio calcio magnesio potasio", "ajustar enmiendas y fertilizacion"),
    ("aluminio intercambiable", "toxicidad radicular", "aplicar enmiendas para reducir limitacion"),
    ("conductividad electrica", "riesgo de salinidad", "evitar excesos y manejar lavado cuando aplique"),
    ("densidad aparente", "compactacion del perfil", "descompactar biologicamente y proteger estructura"),
    ("porosidad total", "aireacion de raices", "incrementar cobertura y materia organica"),
    ("infiltracion", "entrada de agua al perfil", "manejar cobertura y reducir escorrentia"),
    ("capacidad de campo", "reserva util de humedad", "planificar riego y conservacion de suelo"),
    ("punto de marchitez", "agua no disponible para planta", "reducir estres con manejo de cobertura"),
    ("textura del suelo", "retencion y drenaje", "ajustar manejo segun fraccion arena limo arcilla"),
    ("profundidad efectiva", "espacio radicular", "evitar compactacion y erosion"),
    ("pendiente", "riesgo de perdida de suelo", "implementar curvas y barreras vivas"),
    ("escorrentia", "arrastre de fertilidad", "mejorar infiltracion y cobertura continua"),
    ("estabilidad de agregados", "resistencia a erosion", "incrementar carbono y minima perturbacion"),
    ("actividad microbiana", "ciclado de nutrientes", "favorecer suelo vivo y residuos organicos"),
    ("respiracion del suelo", "dinamica biologica", "equilibrar humedad y aporte organico"),
    ("temperatura del suelo", "funcion radicular", "regular con sombra y cobertura"),
    ("humedad gravimetrica", "estado hidrico real", "tomar decisiones de riego por dato"),
    ("capilaridad", "movimiento de agua en perfil", "mejorar estructura para flujo uniforme"),
    ("erosion laminar", "perdida progresiva de capa fertil", "proteger superficie del lote"),
    ("erosion en surcos", "degradacion acelerada", "obras de conservacion y direccion de aguas"),
    ("costra superficial", "baja infiltracion", "romper sellado con manejo organico"),
    ("compactacion en subsuelo", "raiz limitada", "estrategia de recuperacion de estructura"),
    ("retencion de fosforo", "eficiencia de fertilizacion", "ajustar fuente y forma de aplicacion"),
    ("balance calcio magnesio", "estructura y absorcion", "revisar relacion cationica"),
    ("balance potasio magnesio", "antagonismos nutricionales", "evitar desbalances por exceso"),
    ("sodio intercambiable", "riesgo de dispersion", "corregir con manejo de sales"),
    ("oxigenacion del perfil", "salud radicular", "mejorar drenaje y porosidad"),
]

for ind, impact, action in SOIL_INDICATORS:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como interpretar {ind} en cafe",
                f"Importancia de {ind} para cafetal",
                f"Que hacer si {ind} no esta bien en cafe",
                f"Decision tecnica con {ind} en cultivo de cafe",
            ],
            f"En caficultura, {ind} influye en {impact}. "
            f"Cuando este indicador se desvía, conviene {action} y hacer seguimiento por lote.",
            tokens("cafe suelo indicador", ind, impact, action),
        )
    )


EXCESS_CASES = [
    ("nitrogeno", "exceso de brotes tiernos y mayor susceptibilidad sanitaria", "fraccionar y ajustar dosis al potencial real"),
    ("potasio", "antagonismo con magnesio y calcio", "balancear formula y revisar tejido"),
    ("fosforo", "baja eficiencia economica por sobreaplicacion", "recalibrar segun analisis"),
    ("calcio", "bloqueo relativo de otros elementos", "ajustar relacion entre cationes"),
    ("magnesio", "desbalance cationico en suelo", "revisar saturacion de bases"),
    ("azufre", "acidificacion adicional del sistema", "equilibrar fuentes y frecuencia"),
    ("boro", "fitotoxicidad en tejido sensible", "usar dosis pequenas y precisas"),
    ("zinc", "interferencia con otros micronutrientes", "evitar sobrecorrecciones"),
    ("cobre", "riesgo de toxicidad acumulada", "manejar con criterio de analisis"),
    ("manganeso", "estres por niveles altos en ambientes acidos", "ajustar pH y manejo de suelo"),
    ("hierro", "manchas por desequilibrio nutricional", "corregir causa de fondo"),
    ("molibdeno", "desbalance de micronutrientes", "ajustar plan con precision"),
]

for nut, issue, adjustment in EXCESS_CASES:
    CARDS.append(
        card(
            "fertilizacion",
            [
                f"Exceso de {nut} en cafe",
                f"Que pasa si me paso con {nut} en cafeto",
                f"Como corregir sobredosis de {nut} en cafe",
                f"Riesgos de aplicar mucho {nut} en cafetal",
            ],
            f"En cafe, un exceso de {nut} puede causar {issue}. "
            f"Para corregirlo conviene {adjustment}, priorizando decisiones por lote y no recetas generales.",
            tokens("cafe nutricion exceso fertilizacion", nut, issue),
        )
    )


WEED_SCENARIOS = [
    ("arvenses de hoja ancha", "compiten por nutrientes y luz", "control oportuno y cobertura noble"),
    ("arvenses de hoja angosta", "consumen agua en periodos secos", "manejo integrado por etapa del cultivo"),
    ("arvenses rastreras", "dificultan labores de cosecha", "regular altura y evitar enmalezamiento"),
    ("arvenses perennes", "rebrote constante desde raiz", "estrategia escalonada y persistente"),
    ("malezas en plateo", "afectan establecimiento de plantas jovenes", "control selectivo temprano"),
    ("malezas en entrecalles", "reducen eficiencia operativa", "manejo mecanico y coberturas"),
    ("malezas en vivero", "compiten con plantulas", "higiene y control preventivo"),
    ("malezas hospederas de plaga", "sostienen poblaciones indeseadas", "manejo focalizado de hospederos"),
    ("malezas en epoca de lluvia", "crecimiento acelerado", "frecuencia de control ajustada a clima"),
    ("malezas en epoca seca", "disputa de humedad", "priorizar control en zonas criticas"),
    ("lote abandonado con malezas", "acumula focos sanitarios", "plan de recuperacion por fases"),
    ("rebrote rapido despues de control", "alta presion de banco de semillas", "combinar estrategias"),
    ("suelo desnudo por desyerbe extremo", "aumenta erosion y temperatura", "restablecer cobertura"),
    ("maleza alta en floracion", "interfiere con labores y observacion", "limpieza oportuna de lotes"),
    ("maleza en bordes de lote", "reserva de plagas y enfermedades", "manejo perimetral continuo"),
    ("maleza en zonas humedas", "favorece ambientes de enfermedad", "regular cobertura y drenaje"),
    ("maleza trepadora", "sombrea excesivamente el cafeto", "retiro dirigido y monitoreo"),
    ("maleza espinosa", "dificulta cosecha y poda", "control preventivo por seguridad"),
    ("maleza invasiva", "desplaza cobertura util", "respuesta temprana y seguimiento"),
    ("maleza resistente a manejo repetido", "cae eficiencia del control", "rotar practicas y enfoque"),
]

for weed, risk, plan in WEED_SCENARIOS:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como manejar {weed} en cafe",
                f"Que hacer con {weed} en cafetal",
                f"Control de {weed} en cultivo de cafe",
                f"Estrategia para {weed} en finca cafetera",
            ],
            f"En cafe, {weed} puede ser un problema porque {risk}. "
            f"La recomendacion es {plan}, manteniendo cobertura funcional y control por lotes.",
            tokens("cafe arvenses malezas manejo", weed, risk, plan),
        )
    )


POSTHARVEST_VARIABLES = [
    ("temperatura de fermentacion", "velocidad del proceso y riesgo de defectos", "controlar tiempos y limpieza"),
    ("tiempo de fermentacion", "calidad sensorial final", "ajustar segun clima y madurez"),
    ("higiene de tanques", "inocuidad del proceso", "lavado y protocolo entre lotes"),
    ("separacion por madurez", "consistencia del perfil de taza", "clasificar cereza antes de procesar"),
    ("densidad de cereza", "homogeneidad de lote", "seleccionar para reducir dispersion"),
    ("calibracion de despulpadora", "dano mecanico de grano", "ajustar equipo antes de cada jornada"),
    ("velocidad de secado", "estabilidad de calidad", "evitar extremos de secado"),
    ("espesor de capa en secado", "uniformidad de humedad", "manejar capas controladas"),
    ("frecuencia de volteo", "homogeneidad del secado", "programar volteo regular"),
    ("rehumectacion nocturna", "riesgo de moho y deterioro", "proteger lotes en la noche"),
    ("flujo de aire en secado", "eficiencia de remocion de humedad", "mejorar ventilacion"),
    ("temperatura de secado mecanico", "integridad del grano", "usar rangos moderados"),
    ("control de humedad final", "seguridad de almacenamiento", "medir antes de embodegar"),
    ("actividad de agua", "estabilidad microbiologica", "verificar parametro de almacenamiento"),
    ("limpieza de bodega", "reduccion de contaminacion", "rutina de saneamiento"),
    ("orden de estibas", "ventilacion y seguridad", "apilar con separacion de piso y pared"),
    ("rotacion de inventario", "conservacion de frescura", "salida por antiguedad"),
    ("control de olores", "proteccion del perfil sensorial", "aislar de combustibles y quimicos"),
    ("trazabilidad de proceso", "mejora de decisiones comerciales", "registrar cada etapa"),
    ("separacion por micro-lote", "diferenciacion de calidad", "evitar mezclas no deseadas"),
    ("control de defectos fisicos", "factor de rendimiento", "evaluar muestra por lote"),
    ("catacion de control interno", "retroalimentacion de proceso", "hacer pruebas periodicas"),
    ("consistencia de protocolo", "repetibilidad de calidad", "estandarizar rutinas"),
    ("gestion de agua de beneficio", "impacto ambiental y costos", "optimizar consumo"),
    ("aprovechamiento de subproductos", "economia circular", "compostar con manejo tecnico"),
]

for var, effect, key in POSTHARVEST_VARIABLES:
    CARDS.append(
        card(
            "cosecha",
            [
                f"Como controlar {var} en cafe",
                f"Importancia de {var} en poscosecha de cafe",
                f"Que pasa si falla {var} en cafe",
                f"Buenas practicas de {var} para cafe",
            ],
            f"En poscosecha de cafe, {var} influye en {effect}. "
            f"Para sostener calidad conviene {key} y llevar control por lote.",
            tokens("cafe poscosecha calidad", var, effect, key),
        )
    )


CUPPING_ATTRIBUTES = [
    ("fragancia", "limpieza de proceso y secado estable"),
    ("aroma", "madurez de cosecha y manejo poscosecha"),
    ("acidez", "balance de madurez y proceso"),
    ("cuerpo", "densidad de grano y manejo de secado"),
    ("dulzor", "uniformidad de cosecha y proceso"),
    ("sabor principal", "consistencia del lote"),
    ("retrogusto", "calidad integral del proceso"),
    ("balance", "armonizacion de atributos"),
    ("uniformidad", "estandarizacion por lote"),
    ("limpieza de taza", "higiene y ausencia de contaminacion"),
    ("puntaje global", "resultado acumulado de todo el sistema"),
    ("notas frutales", "control de fermentacion"),
    ("notas florales", "potencial varietal y proceso limpio"),
    ("notas a cacao", "perfil de secado y tueste"),
    ("notas a panela", "madurez y manejo estable"),
    ("astringencia", "defectos de cosecha o proceso"),
    ("amargor excesivo", "sobreextraccion o defecto en materia prima"),
    ("fermento no deseado", "descontrol de tiempos y limpieza"),
]

for attr, relation in CUPPING_ATTRIBUTES:
    CARDS.append(
        card(
            "general",
            [
                f"Como mejorar {attr} en cafe",
                f"Que define {attr} en taza de cafe",
                f"Por que cae {attr} en mi lote de cafe",
                f"Recomendaciones para subir {attr} en cafe",
            ],
            f"En cafe, el atributo {attr} se relaciona con {relation}. "
            f"Para mejorarlo se requiere consistencia desde cosecha hasta almacenamiento.",
            tokens("cafe calidad taza catacion", attr, relation),
        )
    )


BUSINESS_DECISIONS = [
    ("priorizar lotes de alta respuesta", "enfocar recursos donde hay mayor retorno"),
    ("reducir costo por kilo", "eliminar ineficiencias de cosecha y beneficio"),
    ("mejorar margen neto", "alinear costo, rendimiento y calidad"),
    ("programar renovacion por bloques", "evitar caidas bruscas de ingreso"),
    ("organizar mano de obra de cosecha", "subir productividad de cuadrillas"),
    ("diseñar indicadores por lote", "tomar decisiones basadas en datos"),
    ("ajustar plan nutricional", "evitar sobrecostos sin retorno"),
    ("detectar cuellos de botella", "acelerar mejora de proceso"),
    ("separar lotes por calidad", "capturar mejores precios"),
    ("estandarizar protocolos", "reducir variacion entre lotes"),
    ("ordenar flujo de caja", "sostener operaciones en temporada critica"),
    ("comparar escenarios de inversion", "escoger mejoras de mayor impacto"),
    ("medir adopcion de recomendaciones", "ver si la asistencia tecnica funciona"),
    ("planear visitas tecnicas", "asegurar seguimiento efectivo"),
    ("fortalecer trazabilidad", "negociar mejor con compradores"),
    ("construir narrativa comercial", "mostrar evidencia de calidad"),
    ("reducir reprocesos", "bajar costos ocultos de operacion"),
    ("gestionar riesgo climatico", "proteger produccion anual"),
    ("usar datos historicos", "anticipar problemas de cada ciclo"),
    ("mejorar entrenamiento del equipo", "aumentar consistencia operativa"),
    ("auditar cumplimiento interno", "cerrar brechas antes de auditorias externas"),
    ("segmentar estrategia de mercado", "vender cada lote por su potencial"),
    ("optimizar logistica de beneficio", "minimizar perdidas de calidad"),
    ("medir eficiencia hidrica", "reducir costos y huella"),
    ("fortalecer seguridad laboral", "evitar interrupciones por incidentes"),
    ("alinear incentivos de recolecta", "premiar calidad y no solo volumen"),
    ("controlar inventario de bodega", "evitar deterioro por tiempos largos"),
    ("definir metas trimestrales", "hacer seguimiento mas corto"),
    ("cerrar ciclo de mejora continua", "aprender de cada cosecha"),
    ("evaluar retorno de asistencia tecnica", "priorizar acciones efectivas"),
]

for decision, value in BUSINESS_DECISIONS:
    CARDS.append(
        card(
            "general",
            [
                f"Como aplicar {decision} en cafe",
                f"Estrategia para {decision} en finca cafetera",
                f"Plan practico de {decision} en caficultura",
                f"Errores al implementar {decision} en cafe",
            ],
            f"En cafe, {decision} permite {value}. "
            f"Para que funcione, define responsables, indicadores y seguimiento por lote.",
            tokens("cafe gestion negocio", decision, value),
        )
    )


SYMPTOM_COMBINATIONS = [
    ("hojas amarillas y brotes pequenos", "desbalance nutricional y raiz limitada"),
    ("manchas foliares y defoliacion", "presion de enfermedad foliar"),
    ("frutos perforados y pasilla alta", "plaga de fruto no controlada"),
    ("marchitez y suelo encharcado", "problema radicular por drenaje"),
    ("baja floracion y poco cuajado", "estres previo a etapa reproductiva"),
    ("llenado pobre y caida de frutos", "deficit hidrico o nutricional"),
    ("muerte de ramas y copa cerrada", "focos sanitarios y ventilacion deficiente"),
    ("sabor avinagrado y olor fuerte", "fermentacion no controlada"),
    ("grano con moho y bodega humeda", "fallas de secado y almacenamiento"),
    ("rendimiento bajo y lote envejecido", "necesidad de renovacion por bloques"),
    ("aroma plano y taza inconsistente", "variacion de proceso por lote"),
    ("poco vigor y compactacion", "limitacion fisica de suelo"),
    ("rebrote debil despues de poda", "reserva insuficiente y manejo inadecuado"),
    ("enfermedades recurrentes en misma zona", "foco cronico sin saneamiento estructural"),
    ("fruta verde en cosecha principal", "mala sincronizacion de pases"),
    ("sequia prolongada y brotacion detenida", "estres hidrico severo"),
    ("lluvias fuertes y erosion visible", "cobertura insuficiente y drenaje pobre"),
    ("alto costo y baja calidad", "ineficiencia de proceso"),
    ("buen volumen pero mal precio", "falta de diferenciacion y trazabilidad"),
    ("perdidas de grano en beneficio", "fallas operativas y de capacitacion"),
]

for combo, likely in SYMPTOM_COMBINATIONS:
    CARDS.append(
        card(
            "diagnostico",
            [
                f"En cafe tengo {combo}, que puede ser",
                f"Como interpretar {combo} en cafetal",
                f"Diagnostico para {combo} en cafe",
                f"Que revisar si aparece {combo} en cafeto",
            ],
            f"Cuando en cafe aparece {combo}, normalmente se asocia a {likely}. "
            f"Conviene confirmar en campo por lote y definir una accion inmediata con seguimiento semanal.",
            tokens("cafe diagnostico sintoma", combo, likely),
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
    print(f"Entradas nuevas cafe (omniscale): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()

