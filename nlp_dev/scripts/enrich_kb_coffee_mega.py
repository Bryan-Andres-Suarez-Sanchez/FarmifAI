#!/usr/bin/env python3
"""
Expande de forma masiva la KB de AgroChat con contenido SOLO de café.
- Evita duplicados por pregunta normalizada y respuesta repetida.
- Mantiene IDs consecutivos.
- Cubre ciclo completo: vivero, establecimiento, manejo, diagnostico,
  plagas/enfermedades, cosecha, beneficio, secado, calidad y gestion.
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


def entry(category: str, questions: list[str], answer: str, keywords: list[str]) -> dict[str, Any]:
    return {
        "category": category,
        "questions": questions,
        "answer": answer,
        "keywords": keywords,
    }


def make_q(base: str, variants: list[str]) -> list[str]:
    return [base] + variants


CARDS: list[dict[str, Any]] = []

# ---------------------------------------------------------------------------
# 1) Manejo general, planeacion y establecimiento
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "general",
        make_q(
            "¿Cómo planear un lote nuevo de café?",
            [
                "Plan de establecimiento de cafetal",
                "Qué revisar antes de sembrar café",
                "Checklist para abrir un cafetal",
            ],
        ),
        "Antes de sembrar, define: altitud y clima, análisis de suelo, variedad objetivo, sistema de sombra, densidad, acceso a agua y mano de obra. Un buen diseño inicial evita costos altos de corrección en años 2 y 3.",
        ["cafe", "planificacion", "lote", "siembra", "densidad", "sombra"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo dividir la finca cafetera por lotes?",
            [
                "Sectorización del cafetal",
                "Bloques de manejo en café",
                "Cómo organizar lotes para decisiones",
            ],
        ),
        "Divide la finca en lotes homogéneos por edad, variedad, pendiente y productividad. Eso permite decisiones precisas de fertilización, control sanitario y renovación, sin mezclar problemas distintos.",
        ["cafe", "lotes", "finca", "manejo", "organizacion"],
    ),
    entry(
        "general",
        make_q(
            "¿Qué registros mínimos llevar en café?",
            [
                "Bitácora del cafetal",
                "Datos clave de producción de café",
                "Control de costos y labores en café",
            ],
        ),
        "Registra por lote: fecha de labores, fertilización, controles de broca/roya, cosecha, rendimiento y defectos de calidad. Con historial puedes identificar qué práctica sí mejora productividad y rentabilidad.",
        ["cafe", "registros", "costos", "rendimiento", "lote"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo escoger variedad de café para mi zona?",
            [
                "Elección de variedad de café",
                "Qué variedad de café sembrar",
                "Variedad café según clima",
            ],
        ),
        "Escoge variedad por adaptación local, presión de roya, mercado objetivo y manejo disponible. Si tu zona tiene alta presión de enfermedad, prioriza materiales resistentes; si apuntas a nicho de calidad, evalúa riesgo productivo y manejo más exigente.",
        ["cafe", "variedad", "adaptacion", "roya", "mercado"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo decidir entre café a sol y café con sombra?",
            [
                "Sistema productivo café sol o sombrío",
                "Ventajas y riesgos de sombra en café",
                "Elección de sistema de caficultura",
            ],
        ),
        "En zonas cálidas, laderas y suelos frágiles, el sombrío bien manejado ayuda a estabilizar microclima y suelo. En zonas más frescas y con manejo intensivo, café a sol puede rendir bien. La decisión debe balancear productividad, sanidad y sostenibilidad.",
        ["cafe", "sol", "sombra", "microclima", "suelo"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cómo preparar hoyos de siembra para café?",
            [
                "Preparación de sitio para plantar café",
                "Hoyado en cafetal nuevo",
                "Paso a paso para siembra de café",
            ],
        ),
        "Marca el lote, abre hoyos con tiempo y mezcla suelo superficial con materia orgánica bien descompuesta. Evita compactar el fondo y protege el sitio con cobertura para conservar humedad.",
        ["cafe", "siembra", "hoyos", "materia", "organica", "humedad"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Qué hacer el día del trasplante de café?",
            [
                "Trasplante correcto de colinos de café",
                "Cómo sembrar colino sin estresarlo",
                "Manejo de colino al plantar",
            ],
        ),
        "Trasplanta temprano o al final de la tarde, hidrata colinos, evita romper raíces y siembra a nivel correcto (sin enterrar demasiado el cuello). Riega de establecimiento si no llueve.",
        ["cafe", "trasplante", "colino", "raices", "riego"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cómo proteger el café joven en los primeros meses?",
            [
                "Manejo de cafeto recién sembrado",
                "Cuidados del café en establecimiento",
                "Protección inicial del cafetal",
            ],
        ),
        "Prioriza sombra temporal regulada, control oportuno de arvenses, humedad estable del suelo y reposición rápida de plantas falladas. Los primeros meses definen la uniformidad del lote.",
        ["cafe", "establecimiento", "sombra", "arvenses", "humedad"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cuándo resembrar fallas en un cafetal nuevo?",
            [
                "Resiembra de fallas en café",
                "Reposición de plantas perdidas café",
                "Uniformidad del lote de café",
            ],
        ),
        "Repón fallas lo más pronto posible en la misma temporada para no crear diferencias grandes de edad. Un lote desuniforme encarece cosecha, poda y fertilización.",
        ["cafe", "resiembra", "fallas", "uniformidad", "manejo"],
    ),
])

# ---------------------------------------------------------------------------
# 2) Vivero y semillero
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "siembra",
        make_q(
            "¿Cómo manejar un semillero de café sano?",
            [
                "Semillero de café paso a paso",
                "Producción de chapolas de café",
                "Buenas prácticas en semillero cafetero",
            ],
        ),
        "Usa semilla de buena procedencia, sustrato limpio y sombra controlada. Mantén humedad sin encharcar y selecciona plántulas vigorosas para pasar a bolsa.",
        ["cafe", "semillero", "chapolas", "sustrato", "humedad"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Qué sustrato usar en bolsa para café?",
            [
                "Mezcla de sustrato para vivero de café",
                "Tierra para colinos de café",
                "Cómo preparar sustrato de almácigo café",
            ],
        ),
        "Busca un sustrato suelto, con buena aireación y drenaje, enriquecido con materia orgánica madura. Evita sustratos muy pesados o contaminados que frenen raíces.",
        ["cafe", "vivero", "sustrato", "drenaje", "raices"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cómo evitar raíces deformadas en colinos de café?",
            [
                "Problemas de raíz en vivero de café",
                "Raíz en espiral en bolsas de café",
                "Calidad radicular de colinos",
            ],
        ),
        "No retrases demasiado el trasplante desde bolsa al lote, usa tamaño de bolsa adecuado y selecciona plantas con raíz principal recta y buen volumen radicular.",
        ["cafe", "raiz", "colinos", "vivero", "trasplante"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cómo endurecer colinos antes de llevarlos al campo?",
            [
                "Acondicionamiento de colinos de café",
                "Preaclimatación de plantas de café",
                "Endurecimiento de vivero a lote",
            ],
        ),
        "Una a dos semanas antes del trasplante, reduce gradualmente sombra y riego excesivo para que la planta se adapte al campo. Así disminuye el choque post-siembra.",
        ["cafe", "colinos", "endurecimiento", "trasplante", "vivero"],
    ),
    entry(
        "siembra",
        make_q(
            "¿Cómo detectar colinos de baja calidad?",
            [
                "Selección negativa en vivero de café",
                "Descartar plantas débiles de café",
                "Señales de colino no apto",
            ],
        ),
        "Descarta colinos amarillos, muy delgados, con deformaciones, daños de raíz o síntomas de plaga/enfermedad. Sembrar material débil compromete la productividad del lote desde el inicio.",
        ["cafe", "colino", "calidad", "vivero", "seleccion"],
    ),
])

# ---------------------------------------------------------------------------
# 3) Podas, arquitectura y renovacion
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "cultivo",
        make_q(
            "¿Cómo definir un plan de poda en café?",
            [
                "Calendario de podas en cafetal",
                "Plan anual de poda del café",
                "Manejo de arquitectura del cafeto",
            ],
        ),
        "Diseña poda por lote según edad, vigor y carga productiva. Combina poda de formación, mantenimiento y renovación para sostener producción y facilitar cosecha.",
        ["cafe", "poda", "renovacion", "arquitectura", "lote"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cuándo conviene recepar café?",
            [
                "Recepa de cafetales",
                "Cuándo hacer zoqueo fuerte",
                "Renovación drástica del café",
            ],
        ),
        "La recepa es útil cuando el cafetal envejeció, perdió vigor o tiene estructura improductiva. Debe hacerse con plan de nutrición y control sanitario para asegurar buen rebrote.",
        ["cafe", "recepa", "zoqueo", "renovacion", "rebrote"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Qué es la poda de formación en café?",
            [
                "Formación inicial del cafeto",
                "Arquitectura temprana del café",
                "Estructura de planta de café joven",
            ],
        ),
        "Es la poda que define estructura productiva temprana, distribución de ramas y altura manejable. Una buena formación reduce costos futuros y mejora uniformidad.",
        ["cafe", "poda", "formacion", "estructura", "uniformidad"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo manejar rebrotes después de zoca en café?",
            [
                "Selección de chupones en zoca",
                "Manejo de brotes en renovación café",
                "Rebrote productivo de cafeto",
            ],
        ),
        "Después de zoca, selecciona brotes vigorosos y elimina exceso para concentrar energía. Mantén nutrición y sanidad para que el rebrote llegue rápido a fase productiva.",
        ["cafe", "zoca", "rebrote", "chupones", "nutricion"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Qué errores evitar al podar café?",
            [
                "Malas prácticas de poda en cafetal",
                "Fallas comunes en renovación de café",
                "Errores que bajan rendimiento por poda",
            ],
        ),
        "Evita podar sin criterio por lote, dejar heridas grandes sin manejo sanitario y descuidar nutrición posterior. Poda sin plan suele causar caída temporal o prolongada de productividad.",
        ["cafe", "poda", "errores", "productividad", "sanidad"],
    ),
])

# ---------------------------------------------------------------------------
# 4) Clima, agua y resiliencia
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "riego",
        make_q(
            "¿Cuándo sí vale la pena regar café?",
            [
                "Riego suplementario en cafetal",
                "Riego en sequía para café",
                "Cómo decidir riego de café",
            ],
        ),
        "El riego suplementario se justifica en establecimiento o en sequías que comprometen floración/llenado. Antes de regar, mejora cobertura y materia orgánica para retener humedad.",
        ["cafe", "riego", "sequias", "floracion", "humedad"],
    ),
    entry(
        "riego",
        make_q(
            "¿Cómo conservar humedad en cafetales sin riego?",
            [
                "Ahorro de agua en café",
                "Manejo de sequía sin riego",
                "Retención de humedad en cafetal",
            ],
        ),
        "Usa cobertura vegetal, acolchado orgánico, sombra regulada y control de escorrentía. Estas prácticas reducen evaporación y amortiguan el estrés hídrico.",
        ["cafe", "humedad", "cobertura", "sombra", "sequias"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo responde el café al calor extremo?",
            [
                "Golpe de calor en cafetal",
                "Estrés térmico en café",
                "Manejo de altas temperaturas en café",
            ],
        ),
        "El calor extremo puede reducir fotosíntesis, aumentar aborto floral y acelerar maduración irregular. Ajusta sombra, suelo y nutrición para reducir el impacto.",
        ["cafe", "calor", "estres", "floracion", "sombra"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo manejar exceso de lluvia en cafetal?",
            [
                "Lluvias fuertes en cultivo de café",
                "Manejo de invierno en cafetales",
                "Control de escorrentía en café",
            ],
        ),
        "Con lluvias fuertes, prioriza drenajes limpios, conservación de suelo y monitoreo sanitario. Exceso de humedad favorece enfermedades foliares y de raíz.",
        ["cafe", "lluvia", "drenaje", "suelo", "enfermedades"],
    ),
    entry(
        "cultivo",
        make_q(
            "¿Cómo adaptar el café al cambio climático?",
            [
                "Resiliencia climática en caficultura",
                "Plan de adaptación para cafetal",
                "Estrategia climática para finca cafetera",
            ],
        ),
        "Combina renovación varietal, manejo de sombra, suelos vivos, monitoreo sanitario y diversificación de ingresos. La adaptación no es una práctica única, es un sistema.",
        ["cafe", "clima", "resiliencia", "renovacion", "sombra", "suelo"],
    ),
])

# ---------------------------------------------------------------------------
# 5) Suelo y fertilizacion: diagnostico de deficiencias
# ---------------------------------------------------------------------------
NUTRIENTS = [
    ("nitrógeno", "hojas pálidas y bajo vigor", "ajusta fuente nitrogenada fraccionada y mejora materia orgánica", ["nitrogeno", "hojas", "vigor"]),
    ("fósforo", "crecimiento lento y raíces pobres", "aplica fósforo según análisis y mejora pH disponible", ["fosforo", "raices", "crecimiento"]),
    ("potasio", "bordes necróticos y menor llenado", "refuerza potasio en etapa productiva y balance hídrico", ["potasio", "llenado", "calidad"]),
    ("calcio", "tejidos frágiles y raíz débil", "corrige con enmiendas cálcicas según análisis", ["calcio", "enmiendas", "raiz"]),
    ("magnesio", "clorosis entre nervaduras", "aplica fuentes magnésicas y equilibra K/Mg", ["magnesio", "clorosis", "equilibrio"]),
    ("azufre", "amarillamiento general en tejido joven", "incorpora fuente con azufre y mejora mineralización", ["azufre", "amarillamiento", "nutricion"]),
    ("boro", "fallas de floración y cuajado", "aplica dosis bajas y precisas de boro", ["boro", "floracion", "cuajado"]),
    ("zinc", "hojas pequeñas y entrenudos cortos", "corrige zinc por suelo o foliar según diagnóstico", ["zinc", "hojas", "entrenudos"]),
    ("hierro", "clorosis en hojas jóvenes", "revisa pH y disponibilidad, corrige con estrategia localizada", ["hierro", "clorosis", "ph"]),
    ("manganeso", "moteado y clorosis específica", "ajusta pH y balance de micronutrientes", ["manganeso", "micronutrientes", "ph"]),
]

for nutrient, symptoms, correction, kws in NUTRIENTS:
    CARDS.append(
        entry(
            "diagnostico",
            [
                f"Deficiencia de {nutrient} en café",
                f"Síntomas de falta de {nutrient} en cafeto",
                f"Cómo corregir déficit de {nutrient} en café",
                f"Mi café tiene señales de falta de {nutrient}",
            ],
            f"Cuando falta {nutrient} en café suelen aparecer {symptoms}. Para corregir, {correction}. Confirma con análisis de suelo/foliar para evitar confundirlo con estrés hídrico o daño de raíces.",
            ["cafe", nutrient] + kws,
        )
    )

CARDS.extend([
    entry(
        "fertilizacion",
        make_q(
            "¿Cómo fraccionar fertilización en café durante el año?",
            [
                "Aplicaciones divididas de fertilizante en café",
                "Fraccionamiento nutricional del cafetal",
                "Calendario de fertilización por etapas",
            ],
        ),
        "Fracciona aplicaciones para mejorar eficiencia y reducir pérdidas por lluvia/lixiviación. Alinea la nutrición con floración, llenado y recuperación postcosecha.",
        ["cafe", "fertilizacion", "fraccionamiento", "eficiencia"],
    ),
    entry(
        "fertilizacion",
        make_q(
            "¿Cómo evitar pérdidas de fertilizante en ladera cafetera?",
            [
                "Fertilización en pendientes de café",
                "Cómo no botar fertilizante en lluvia",
                "Eficiencia de fertilización en cafetal de ladera",
            ],
        ),
        "Aplica con suelo húmedo pero sin lluvia fuerte inmediata, en banda y con cobertura. Evita fertilizar sobre suelo desnudo o en eventos de escorrentía intensa.",
        ["cafe", "ladera", "fertilizante", "escorrentia", "cobertura"],
    ),
    entry(
        "fertilizacion",
        make_q(
            "¿Cuándo usar fertilización foliar en café?",
            [
                "Fertilizantes foliares para cafeto",
                "Aplicación foliar en café",
                "Uso correcto de nutrición foliar en cafetal",
            ],
        ),
        "La vía foliar complementa pero no reemplaza la nutrición de suelo. Úsala para ajustes puntuales o etapas críticas, siempre con diagnóstico y dosis seguras.",
        ["cafe", "foliar", "nutricion", "suelo", "diagnostico"],
    ),
])

# ---------------------------------------------------------------------------
# 6) Plagas de café
# ---------------------------------------------------------------------------
PESTS = [
    ("cochinillas en raíz", "debilitamiento, amarillamiento y raíces con colonias blancas", "mejora sanidad del suelo y controla focos", ["cochinilla", "raiz", "amarillamiento"]),
    ("escamas en ramas", "presencia de escamas adheridas y debilitamiento de brotes", "poda focos y fortalece manejo integrado", ["escamas", "ramas", "brotes"]),
    ("minador de la hoja", "galerías claras dentro de la hoja", "monitorea y evita tratamientos indiscriminados", ["minador", "hoja", "galerias"]),
    ("ácaros", "bronceado foliar y pérdida de área fotosintética", "reduce estrés hídrico y maneja focos", ["acaros", "follaje", "estres"]),
    ("hormiga arriera", "defoliación rápida en focos", "maneja nidos de forma dirigida y temprana", ["hormiga", "defoliacion", "nidos"]),
    ("nematodos", "raíz dañada y bajo desarrollo", "usa material sano y mejora biología del suelo", ["nematodos", "raiz", "suelo"]),
]

for pest, signs, control, kws in PESTS:
    CARDS.append(
        entry(
            "plagas",
            [
                f"¿Cómo identificar {pest} en café?",
                f"Síntomas de {pest} en cafetal",
                f"Manejo de {pest} en cafeto",
                f"Control integrado de {pest}",
            ],
            f"Para {pest}, observa {signs}. El manejo debe integrar monitoreo por lote, acciones culturales y {control}. Evalúa resultado por incidencia y recuperación del vigor.",
            ["cafe", "plaga", "manejo"] + kws,
        )
    )

CARDS.extend([
    entry(
        "plagas",
        make_q(
            "¿Cómo hacer monitoreo sanitario semanal en café?",
            [
                "Ronda de monitoreo en cafetal",
                "Inspección de plagas y enfermedades en café",
                "Chequeo sanitario por lotes de café",
            ],
        ),
        "Define ruta fija por lote, registra hallazgos en formato simple y compara tendencia semanal. Monitorear con disciplina permite intervenir antes de que el daño sea caro.",
        ["cafe", "monitoreo", "sanitario", "lote", "tendencia"],
    ),
    entry(
        "plagas",
        make_q(
            "¿Cómo priorizar control por focos en café?",
            [
                "Manejo por puntos calientes en cafetal",
                "Control localizado de plagas en café",
                "Estrategia de focos sanitarios",
            ],
        ),
        "Ubica focos con mayor incidencia y actúa primero allí para cortar dispersión. El control por focos reduce costos y mejora oportunidad frente a tratar todo el lote igual.",
        ["cafe", "focos", "plagas", "incidencia", "costos"],
    ),
])

# ---------------------------------------------------------------------------
# 7) Enfermedades de café
# ---------------------------------------------------------------------------
DISEASES = [
    ("antracnosis en café", "lesiones oscuras en brotes, flores o frutos", "poda sanitaria, ventilación y manejo preventivo", ["antracnosis", "brotes", "frutos"]),
    ("mancha de hierro", "manchas con halo y debilitamiento foliar", "equilibra nutrición y reduce estrés", ["mancha", "hierro", "follaje"]),
    ("ojo de gallo", "manchas redondeadas típicas y defoliación", "regular sombra y disminuir humedad persistente", ["ojo", "gallo", "defoliacion"]),
    ("mal rosado", "costras rosadas en ramas y secamiento", "poda de focos y desinfección de herramientas", ["mal", "rosado", "ramas"]),
    ("llaga radical", "marchitez y muerte por daño en raíz", "saneamiento del foco y mejora de drenaje", ["llaga", "radical", "marchitez"]),
    ("cercosporiosis", "manchas en hojas y frutos, caída de calidad", "nutrición balanceada y manejo integrado", ["cercospora", "hojas", "frutos"]),
]

for disease, signs, management, kws in DISEASES:
    CARDS.append(
        entry(
            "diagnostico",
            [
                f"¿Cómo reconocer {disease}?",
                f"Síntomas de {disease} en cafetal",
                f"Manejo de {disease} en café",
                f"Prevención de {disease}",
            ],
            f"En {disease} suelen verse {signs}. Para manejarla, aplica monitoreo por lote y {management}. Evita esperar a que el daño esté avanzado para intervenir.",
            ["cafe", "enfermedad", "diagnostico", "manejo"] + kws,
        )
    )

CARDS.extend([
    entry(
        "diagnostico",
        make_q(
            "¿Cómo diferenciar daño por nutrición vs enfermedad en café?",
            [
                "Diagnóstico diferencial en cafetal",
                "Clorosis por deficiencia o por patógeno en café",
                "Cómo no confundir problemas en café",
            ],
        ),
        "Revisa patrón del daño (uniforme o focal), órganos afectados, historia de manejo y evolución en días/semanas. Si hay duda, confirma con análisis o apoyo técnico antes de intervenir fuerte.",
        ["cafe", "diagnostico", "deficiencia", "enfermedad", "patron"],
    ),
    entry(
        "diagnostico",
        make_q(
            "¿Cómo tomar muestras para diagnóstico de enfermedad en café?",
            [
                "Muestreo fitosanitario en cafetal",
                "Enviar muestras de café a laboratorio",
                "Toma de muestra de hojas/frutos café",
            ],
        ),
        "Toma tejido representativo con síntomas iniciales y avanzados, etiqueta con lote/fecha y evita contaminación cruzada. Una muestra bien tomada ahorra tratamientos equivocados.",
        ["cafe", "muestra", "laboratorio", "diagnostico", "fitosanitario"],
    ),
])

# ---------------------------------------------------------------------------
# 8) Cosecha y calidad en finca
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "cosecha",
        make_q(
            "¿Cómo entrenar recolectores para mejorar calidad de café?",
            [
                "Capacitación de cosecha selectiva",
                "Entrenamiento de recolectores en café",
                "Mejorar selección de cereza madura",
            ],
        ),
        "Define criterios claros de madurez, haz calibración al inicio de jornada y retroalimentación diaria. La calidad mejora cuando el equipo entiende el estándar y lo mide.",
        ["cafe", "cosecha", "recolectores", "calidad", "madurez"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo reducir verde y sobremaduro en la cosecha?",
            [
                "Control de madurez en recolección de café",
                "Evitar mezcla de frutos en café",
                "Selección de cereza por estado de madurez",
            ],
        ),
        "Organiza pases más frecuentes en picos de maduración y supervisa calidad por recolector. Mezclar verde/sobremaduro aumenta defectos y resta valor comercial.",
        ["cafe", "madurez", "verde", "sobremaduro", "calidad"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Qué hacer con frutos pintones en café?",
            [
                "Manejo de cereza pintona",
                "Recolectar o dejar frutos pintones café",
                "Decisión sobre frutos no maduros",
            ],
        ),
        "En general conviene privilegiar maduro uniforme para calidad. Los pintones deben manejarse según protocolo del lote para no degradar el perfil final.",
        ["cafe", "pintones", "madurez", "calidad", "cosecha"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo medir rendimiento de recolección por jornal?",
            [
                "Productividad de cosecha en café",
                "Indicadores de recolector cafetero",
                "Control de rendimiento en cosecha",
            ],
        ),
        "Mide kilos de cereza por jornal y porcentaje de frutos no conformes por recolector. Combinar productividad con calidad evita pagar volumen con defectos.",
        ["cafe", "jornal", "rendimiento", "calidad", "cosecha"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo evitar contaminación en cosecha de café?",
            [
                "Inocuidad en recolección de café",
                "Buenas prácticas de higiene en cosecha",
                "Prevención de contaminantes en café cereza",
            ],
        ),
        "Usa recipientes limpios, evita contacto con combustibles/químicos y protege cereza de lodo, animales y lluvia prolongada. La contaminación en cosecha impacta taza y aceptación comercial.",
        ["cafe", "inocuidad", "cosecha", "contaminacion", "higiene"],
    ),
])

# ---------------------------------------------------------------------------
# 9) Beneficio, fermentacion y secado
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "cosecha",
        make_q(
            "¿Cómo estandarizar el beneficio de café en finca?",
            [
                "Estandarización de proceso poscosecha café",
                "Protocolos de beneficio de café",
                "Consistencia en beneficio húmedo",
            ],
        ),
        "Define protocolo por lote: tiempos máximos entre cosecha-despulpado, control de fermentación, lavado y secado con meta de humedad. Lo que no se estandariza no se puede mejorar.",
        ["cafe", "beneficio", "estandar", "fermentacion", "secado"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Qué pasa si se retrasa el despulpado del café?",
            [
                "Riesgo por despulpado tardío",
                "Tiempo entre cosecha y despulpado café",
                "Fermentación no controlada en cereza",
            ],
        ),
        "Retrasar el despulpado aumenta fermentación no controlada, defectos y heterogeneidad. Procesa cereza lo más pronto posible para conservar limpieza de taza.",
        ["cafe", "despulpado", "tiempo", "fermentacion", "defectos"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo controlar fermentación sin instrumentos costosos?",
            [
                "Control práctico de fermentación café",
                "Fermentación por tiempo y temperatura",
                "Monitoreo sencillo de fermentación",
            ],
        ),
        "Empieza con lotes pequeños, registra hora de inicio/fin, temperatura ambiente y resultado en taza. Ajusta un factor por vez para construir tu curva de proceso.",
        ["cafe", "fermentacion", "control", "tiempo", "taza"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo evitar sobrefermentación en café?",
            [
                "Defectos por exceso de fermentación",
                "Se me pasó la fermentación del café",
                "Control de tiempos de fermentación",
            ],
        ),
        "Evita sobrefermentar controlando tiempos por lote, temperatura y carga del tanque. Lava y seca oportunamente cuando se alcance el punto objetivo.",
        ["cafe", "sobrefermentacion", "tiempos", "lote", "secado"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo secar café cuando hay varios días de lluvia?",
            [
                "Secado de café en clima húmedo",
                "Plan de contingencia para secado",
                "Manejo de secado en temporada lluviosa",
            ],
        ),
        "Prioriza capas delgadas, volteo frecuente, ventilación y control de carga diaria de secador. Evita acumular lotes húmedos sin capacidad de secado real.",
        ["cafe", "secado", "lluvia", "ventilacion", "capas"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo saber si el secado quedó heterogéneo en café?",
            [
                "Humedad dispareja en café pergamino",
                "Secado desigual del café",
                "Detección de variación de humedad",
            ],
        ),
        "Señales: lotes con olores distintos, granos de textura desigual o variación fuerte en lectura de humedad. Homogeneiza antes de almacenar para evitar deterioro.",
        ["cafe", "secado", "humedad", "heterogeneo", "almacenamiento"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cómo organizar lotes por perfil de calidad en café?",
            [
                "Segmentación de lotes de café",
                "Separar café comercial y especial",
                "Clasificación interna de lotes",
            ],
        ),
        "Separa lotes por variedad, altitud, fecha y protocolo de proceso. Catación y registros te permiten decidir qué va a mercado comercial y qué puede ir a especialidad.",
        ["cafe", "lotes", "calidad", "catacion", "mercado"],
    ),
])

# ---------------------------------------------------------------------------
# 10) Almacenamiento, trilla y calidad de taza
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "cosecha",
        make_q(
            "¿Cómo conservar aroma del café pergamino almacenado?",
            [
                "Preservación de calidad en bodega de café",
                "Aroma del café en almacenamiento",
                "Manejo postsecado del pergamino",
            ],
        ),
        "Guarda café con humedad segura, en bodega limpia, sin olores extraños y con rotación por fecha de secado. Evita mezclar lotes muy distintos en un mismo espacio.",
        ["cafe", "almacenamiento", "aroma", "pergamino", "bodega"],
    ),
    entry(
        "cosecha",
        make_q(
            "¿Cuánto tiempo almacenar café pergamino sin perder calidad?",
            [
                "Vida útil del café pergamino",
                "Tiempo de bodega para café seco",
                "Rotación de inventario de café",
            ],
        ),
        "Depende de humedad, temperatura y empaque, pero la regla práctica es rotar inventario y evitar almacenamiento prolongado innecesario. Entre más controladas las condiciones, más estable la calidad.",
        ["cafe", "almacenamiento", "inventario", "rotacion", "calidad"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo interpretar resultados de catación para mejorar la finca?",
            [
                "Uso de catación para decisiones en cafetal",
                "Retroalimentación de taza a manejo agronómico",
                "Cómo conectar catación y proceso",
            ],
        ),
        "Usa la catación como retroalimentación del manejo: compara lotes por protocolo, fecha y parcela. Ajusta solo una variable por ciclo para identificar qué mejora realmente la taza.",
        ["cafe", "catacion", "taza", "lotes", "mejora"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo reducir defectos físicos en café antes de venta?",
            [
                "Control de defectos de grano en café",
                "Menos pasillas y defectos en lote",
                "Aseguramiento de calidad física en café",
            ],
        ),
        "Mejora selección de cosecha, clasificación inicial, fermentación controlada y secado homogéneo. La calidad física se construye desde el árbol hasta la bodega.",
        ["cafe", "defectos", "grano", "calidad", "venta"],
    ),
])

# ---------------------------------------------------------------------------
# 11) Gestión, economía y comercialización
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "general",
        make_q(
            "¿Cómo calcular costo real por kilo de café?",
            [
                "Costeo de producción en caficultura",
                "Costo por kilo pergamino seco",
                "Estructura de costos del café",
            ],
        ),
        "Separa costos directos (mano de obra, insumos, cosecha, beneficio) e indirectos (administración, herramientas, transporte). Calcula por lote para saber dónde ganas y dónde pierdes.",
        ["cafe", "costos", "kilo", "rentabilidad", "lote"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo mejorar rentabilidad sin bajar calidad en café?",
            [
                "Rentabilidad en finca cafetera",
                "Cómo ganar más con café sin perder taza",
                "Eficiencia productiva en caficultura",
            ],
        ),
        "Enfócate en eficiencia por lote: fertilización ajustada, control temprano de focos, cosecha selectiva y reducción de reprocesos en beneficio. Más eficiencia suele subir margen sin sacrificar calidad.",
        ["cafe", "rentabilidad", "eficiencia", "calidad", "margen"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo negociar mejor la venta de café?",
            [
                "Comercialización de café en finca",
                "Estrategia de venta de lotes de café",
                "Venta de café con mejor precio",
            ],
        ),
        "Llega con trazabilidad de lote, humedad controlada y evidencias de calidad. Negociar con datos técnicos y consistencia mejora tu poder comercial.",
        ["cafe", "venta", "precio", "trazabilidad", "calidad"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo priorizar inversiones en una finca cafetera?",
            [
                "Decisiones de inversión en café",
                "Qué invertir primero en caficultura",
                "Plan financiero de cafetal",
            ],
        ),
        "Prioriza inversiones que quiten cuellos de botella: material vegetal, sanidad, secado y registros de gestión. Invierte primero donde más se pierde valor o productividad.",
        ["cafe", "inversion", "finca", "cuellos", "productividad"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo evaluar si conviene renovar un lote de café?",
            [
                "Decidir renovación de cafetal",
                "Análisis de renovación vs mantenimiento",
                "Rentabilidad de renovar café",
            ],
        ),
        "Compara rendimiento actual, costos de sostener lote envejecido y proyección de recuperación con renovación. Si el lote consume recursos y no responde, renovar suele ser más rentable.",
        ["cafe", "renovacion", "rentabilidad", "lote", "decision"],
    ),
])

# ---------------------------------------------------------------------------
# 12) Seguridad, personas y sostenibilidad
# ---------------------------------------------------------------------------
CARDS.extend([
    entry(
        "general",
        make_q(
            "¿Qué prácticas de seguridad aplicar en labores de café?",
            [
                "Seguridad laboral en cafetal",
                "Protección personal en finca cafetera",
                "Prevención de riesgos en café",
            ],
        ),
        "Usa equipos de protección según labor, capacita al personal en manejo seguro de herramientas e insumos y define protocolos para emergencias. Seguridad también es productividad.",
        ["cafe", "seguridad", "laboral", "proteccion", "riesgos"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo reducir impacto ambiental del beneficio de café?",
            [
                "Gestión ambiental en beneficio cafetero",
                "Menor contaminación en poscosecha café",
                "Manejo sostenible de subproductos café",
            ],
        ),
        "Reduce consumo de agua, maneja adecuadamente subproductos y evita vertimientos directos sin tratamiento. Un beneficio más limpio protege suelo/agua y mejora reputación comercial.",
        ["cafe", "ambiental", "beneficio", "agua", "subproductos"],
    ),
    entry(
        "general",
        make_q(
            "¿Cómo integrar árboles y biodiversidad en cafetal?",
            [
                "Biodiversidad en sistemas de café",
                "Agroforestería cafetera",
                "Servicios ecosistémicos en caficultura",
            ],
        ),
        "Un sistema con diversidad de sombra y coberturas bien manejadas puede mejorar resiliencia, conservación de suelo y presencia de enemigos naturales de plagas.",
        ["cafe", "biodiversidad", "agroforesteria", "sombra", "suelo"],
    ),
])


def main() -> None:
    kb = json.loads(KB_PATH.read_text(encoding="utf-8"))
    entries = kb.get("entries", [])
    categories = kb.get("categories", [])

    existing_q = {
        normalize(q)
        for e in entries
        for q in e.get("questions", [])
    }
    existing_answer = {
        normalize(e.get("answer", ""))
        for e in entries
        if e.get("answer")
    }

    next_id = max((e.get("id", 0) for e in entries), default=0) + 1
    added = 0

    for card in CARDS:
        unique_questions = []
        for q in card["questions"]:
            nq = normalize(q)
            if nq and nq not in existing_q:
                unique_questions.append(q)

        if not unique_questions:
            continue

        n_answer = normalize(card["answer"])
        if n_answer in existing_answer:
            continue

        category = card["category"]
        if category not in categories:
            categories.append(category)

        entry_obj = {
            "id": next_id,
            "category": category,
            "questions": unique_questions,
            "answer": card["answer"],
            "keywords": card["keywords"],
        }

        entries.append(entry_obj)
        for q in unique_questions:
            existing_q.add(normalize(q))
        existing_answer.add(n_answer)

        next_id += 1
        added += 1

    kb["entries"] = entries
    kb["categories"] = categories
    KB_PATH.write_text(json.dumps(kb, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total_q = sum(len(e.get("questions", [])) for e in entries)
    print(f"Entradas nuevas café (mega): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()
