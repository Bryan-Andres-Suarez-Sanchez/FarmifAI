#!/usr/bin/env python3
"""
Enriquecimiento GLOSSARY para cafe.
Agrega terminos clave para mejorar cobertura de preguntas tipo:
"que es X", "para que sirve X", "como usar X en cafe".
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


def toks(*parts: str) -> list[str]:
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


TERMS: list[tuple[str, str, str, str]] = [
    # Vivero y establecimiento
    ("almacigo", "siembra", "fase inicial de produccion de plantulas", "obtener material uniforme para campo"),
    ("chapola", "siembra", "plantula joven de cafe en etapa temprana", "seleccionar material vigoroso"),
    ("colino", "siembra", "planta de cafe lista para trasplante", "establecer lotes con buena calidad inicial"),
    ("endurecimiento de colinos", "siembra", "acondicionamiento previo al trasplante", "reducir choque al pasar a campo"),
    ("resiembra de fallas", "siembra", "reposicion de plantas perdidas", "conservar uniformidad del lote"),
    ("prendimiento", "siembra", "supervivencia inicial de plantas sembradas", "asegurar establecimiento exitoso"),
    ("plateo", "siembra", "limpieza alrededor de planta joven", "reducir competencia temprana"),
    ("hoyado", "siembra", "preparacion de hoyos de siembra", "facilitar desarrollo radicular"),
    ("distancia de siembra", "siembra", "separacion entre plantas y surcos", "equilibrar densidad y manejo"),
    ("densidad de poblacion", "siembra", "numero de plantas por area", "definir potencial productivo"),
    ("curvas a nivel", "siembra", "trazado en contorno de ladera", "reducir erosion y escorrentia"),
    ("siembra al inicio de lluvias", "siembra", "establecimiento en temporada favorable", "mejorar prendimiento"),
    # Manejo agronomico
    ("fenologia", "cultivo", "estudio de etapas del cultivo", "sincronizar labores con ciclo de planta"),
    ("brotacion", "cultivo", "emision de nuevos brotes", "formar estructura productiva"),
    ("prefloracion", "cultivo", "fase previa a floracion", "acumular reservas para buena respuesta"),
    ("floracion", "cultivo", "apertura de flores", "definir potencial de produccion"),
    ("cuajado", "cultivo", "transicion de flor a fruto", "asegurar retencion de frutos"),
    ("llenado de grano", "cultivo", "fase de acumulacion de materia en fruto", "determinar peso final"),
    ("maduracion", "cultivo", "cambio fisiologico a punto de cosecha", "programar recolecta selectiva"),
    ("reposo fisiologico", "cultivo", "fase de recuperacion del cafeto", "reconstruir reservas"),
    ("arquitectura del cafeto", "cultivo", "diseno de ejes y ramas productivas", "facilitar manejo y cosecha"),
    ("deschuponado", "cultivo", "retiro de brotes no deseados", "evitar competencia improductiva"),
    ("poda de formacion", "cultivo", "poda para definir estructura", "mejorar orden del arbol"),
    ("poda de mantenimiento", "cultivo", "ajuste periodico de copa", "equilibrar vigor y carga"),
    ("poda sanitaria", "cultivo", "retiro de tejido enfermo", "disminuir focos de problema"),
    ("zoca", "cultivo", "renovacion por corte de tallo", "rejuvenecer plantas envejecidas"),
    ("recepa", "cultivo", "corte de renovacion en cafeto", "reiniciar estructura productiva"),
    ("renovacion escalonada", "cultivo", "renovar por bloques y no todo a la vez", "proteger flujo de caja"),
    ("sombrio regulado", "cultivo", "manejo de sombra con porcentaje adecuado", "amortiguar estres ambiental"),
    ("agroforesteria cafetera", "cultivo", "integracion de arboles y cafe", "resiliencia y conservacion de suelo"),
    ("cobertura del suelo", "cultivo", "vegetacion o residuos que protegen superficie", "reducir perdida de humedad"),
    ("barreras vivas", "cultivo", "filas vegetales para frenar escorrentia", "control de erosion"),
    ("mulch organico", "cultivo", "capa de material organico sobre suelo", "conservar humedad y regular temperatura"),
    ("arvenses nobles", "cultivo", "coberturas manejables que no compiten en exceso", "proteger suelo"),
    ("manejo integrado de arvenses", "cultivo", "combinacion de metodos para malezas", "control eficiente y sostenible"),
    ("manejo por lotes", "general", "toma de decisiones diferenciada por bloque", "hacer recomendaciones mas precisas"),
    ("bitacora tecnica", "general", "registro de labores y resultados", "aprender y ajustar manejo"),
    ("indicadores por lote", "general", "metricas de productividad, sanidad y calidad", "medir progreso real"),
    # Suelo y agua
    ("analisis de suelo", "fertilizacion", "evaluacion quimica y fisica del suelo", "definir plan nutricional"),
    ("analisis foliar", "fertilizacion", "medicion de nutrientes en hojas", "validar estado nutricional del cultivo"),
    ("muestreo representativo", "fertilizacion", "toma de muestra que refleja el lote", "evitar decisiones sesgadas"),
    ("pH de suelo", "fertilizacion", "medida de acidez o alcalinidad", "regular disponibilidad de nutrientes"),
    ("materia organica", "fertilizacion", "fraccion biologica del suelo", "mejorar estructura y retencion de agua"),
    ("capacidad de intercambio cationico", "fertilizacion", "capacidad del suelo para retener cationes", "estabilidad nutricional"),
    ("saturacion de bases", "fertilizacion", "proporcion de cationes basicos en complejo de cambio", "balancear nutricion"),
    ("aluminio intercambiable", "fertilizacion", "fraccion potencialmente toxica en suelos acidos", "proteger raiz"),
    ("conductividad electrica", "fertilizacion", "indicador de sales en suelo", "prevenir estres por salinidad"),
    ("compactacion", "cultivo", "reduccion de porosidad del suelo", "evitar limitacion radicular"),
    ("infiltracion", "cultivo", "entrada de agua al perfil", "mejorar aprovechamiento de lluvias"),
    ("escorrentia", "cultivo", "agua que corre sobre superficie", "controlar perdida de suelo"),
    ("drenaje", "cultivo", "salida de exceso de agua del perfil", "reducir anoxia en raiz"),
    ("capacidad de campo", "riego", "agua retenida util despues de drenaje", "base para plan de riego"),
    ("punto de marchitez", "riego", "humedad minima para que planta no se marchite", "evitar estres severo"),
    ("riego suplementario", "riego", "aporte adicional de agua en deficit", "sostener etapas sensibles"),
    ("deficit hidrico", "riego", "falta de agua para demanda del cultivo", "priorizar acciones de mitigacion"),
    ("balance hidrico", "riego", "relacion entre aporte y consumo de agua", "planear riego y conservacion"),
    ("cosecha de agua", "riego", "captacion y almacenamiento de agua lluvia", "resiliencia en sequia"),
    ("eficiencia hidrica", "general", "aprovechamiento del agua por unidad producida", "reducir costo e impacto"),
    # Nutricion
    ("fertilizacion racional", "fertilizacion", "aplicacion de nutrientes con base en evidencia", "aumentar eficiencia"),
    ("fraccionamiento de fertilizante", "fertilizacion", "dividir dosis en varias aplicaciones", "disminuir perdidas"),
    ("formula NPK", "fertilizacion", "relacion de nitrogeno fosforo potasio", "ajustar oferta de nutrientes"),
    ("nitrogeno", "fertilizacion", "nutriente clave de crecimiento vegetativo", "sostener vigor"),
    ("fosforo", "fertilizacion", "nutriente asociado a energia y raiz", "mejorar establecimiento y floracion"),
    ("potasio", "fertilizacion", "nutriente ligado a llenado y balance hidrico", "fortalecer calidad de fruto"),
    ("calcio", "fertilizacion", "nutriente estructural de tejidos", "mejorar raiz y firmeza"),
    ("magnesio", "fertilizacion", "componente central de clorofila", "soportar fotosintesis"),
    ("azufre", "fertilizacion", "nutriente de sintesis metabolica", "complementar nutricion"),
    ("boro", "fertilizacion", "micronutriente ligado a floracion y cuajado", "mejorar proceso reproductivo"),
    ("zinc", "fertilizacion", "micronutriente de crecimiento y enzimas", "favorecer brotacion"),
    ("hierro", "fertilizacion", "micronutriente asociado a clorofila", "evitar clorosis"),
    ("manganeso", "fertilizacion", "micronutriente de procesos enzimaticos", "apoyar metabolismo"),
    ("cobre", "fertilizacion", "micronutriente de tejidos y defensa", "equilibrar funcionamiento vegetal"),
    ("molibdeno", "fertilizacion", "micronutriente del metabolismo de nitrogeno", "optimizar asimilacion"),
    ("dolomita", "fertilizacion", "enmienda con calcio y magnesio", "corregir acidez y balance cationico"),
    ("enmienda calcica", "fertilizacion", "material para ajustar acidez", "mejorar ambiente radicular"),
    ("antagonismo nutricional", "fertilizacion", "interferencia entre nutrientes por desbalance", "evitar bloqueos"),
    ("deficiencia nutricional", "diagnostico", "insuficiencia de un nutriente esencial", "corregir con precision"),
    ("toxicidad nutricional", "diagnostico", "exceso de nutriente con efecto negativo", "ajustar dosis y frecuencia"),
    ("bioinsumo", "fertilizacion", "insumo biologico para complementar manejo", "mejorar eficiencia del sistema"),
    ("compost", "fertilizacion", "material organico estabilizado", "aportar carbono y nutrientes"),
    ("lombricompuesto", "fertilizacion", "abono organico procesado por lombrices", "mejorar calidad de suelo"),
    ("pulpa compostada", "fertilizacion", "subproducto de cafe estabilizado", "reciclar nutrientes del sistema"),
    # Plagas y enfermedades
    ("monitoreo fitosanitario", "plagas", "seguimiento periodico de incidencias", "detectar problemas a tiempo"),
    ("umbral de accion", "plagas", "nivel de incidencia para intervenir", "evitar acciones tardias o innecesarias"),
    ("manejo integrado de plagas", "plagas", "combinacion de estrategias preventivas y de control", "reducir dano y costo"),
    ("foco sanitario", "diagnostico", "zona puntual con incidencia alta", "intervenir de forma dirigida"),
    ("incidencia", "diagnostico", "proporcion de unidades afectadas", "dimensionar alcance del problema"),
    ("severidad", "diagnostico", "grado de dano en tejido o planta", "priorizar intervencion"),
    ("inoculo", "enfermedad", "fuente de propagulos de patogeno", "disenar manejo preventivo"),
    ("susceptibilidad", "enfermedad", "tendencia de planta a enfermar", "escoger variedad y manejo adecuado"),
    ("resistencia varietal", "enfermedad", "capacidad genetica de tolerar enfermedad", "reducir riesgo sanitario"),
    ("broca del cafe", "plagas", "plaga que perfora fruto y afecta grano", "proteger rendimiento y calidad"),
    ("roya del cafe", "enfermedad", "enfermedad foliar de alto impacto", "evitar defoliacion y perdida de vigor"),
    ("cercospora", "enfermedad", "enfermedad asociada a manchas en hoja y fruto", "proteger calidad"),
    ("antracnosis", "enfermedad", "problema sanitario en brotes y frutos", "evitar perdida productiva"),
    ("ojo de gallo", "enfermedad", "enfermedad foliar en ambientes humedos", "controlar defoliacion"),
    ("llaga negra", "enfermedad", "problema radicular en suelos limitantes", "evitar muerte de plantas"),
    ("llaga estrellada", "enfermedad", "enfermedad de raiz en focos", "sanear y recuperar suelo"),
    ("mal rosado", "enfermedad", "problema en ramas favorecido por humedad", "reducir secamiento"),
    ("muerte descendente", "enfermedad", "secamiento progresivo de ramas", "recuperar estructura productiva"),
    ("cochinilla radicular", "plagas", "plaga de raiz asociada a debilitamiento", "proteger sistema radicular"),
    ("minador de hoja", "plagas", "insecto que forma galerias en hoja", "evitar perdida fotosintetica"),
    ("nematodos", "plagas", "organismos de suelo que afectan raices", "reducir deterioro del lote"),
    ("control biologico", "plagas", "uso de organismos beneficos", "complementar manejo integrado"),
    ("control cultural", "plagas", "practicas de manejo que reducen incidencia", "prevenir problemas"),
    ("control preventivo", "general", "acciones antes de que aparezca dano severo", "disminuir riesgo de crisis"),
    # Cosecha y beneficio
    ("cereza madura", "cosecha", "fruto en punto optimo de cosecha", "maximizar calidad de taza"),
    ("cosecha selectiva", "cosecha", "recoleccion enfocada en frutos maduros", "mejorar uniformidad"),
    ("repase", "cosecha", "pase adicional para recoger frutos remanentes", "cortar ciclos de plaga y mejorar limpieza"),
    ("sobremaduro", "cosecha", "fruto pasado de punto", "evitar defectos de proceso"),
    ("fruto verde", "cosecha", "fruto sin madurez completa", "evitar defectos y baja calidad"),
    ("beneficio", "cosecha", "proceso de transformacion de cereza a pergamino", "preservar valor del lote"),
    ("despulpado", "cosecha", "retiro de pulpa de la cereza", "preparar fermentacion controlada"),
    ("mucilago", "cosecha", "capa azucarada del fruto", "manejar fermentacion con precision"),
    ("fermentacion", "cosecha", "proceso biologico para remover mucilago", "definir perfil de taza"),
    ("proceso lavado", "cosecha", "ruta de beneficio con lavado posterior", "buscar limpieza de taza"),
    ("proceso honey", "cosecha", "ruta con parte de mucilago en secado", "aportar dulzor y cuerpo"),
    ("proceso natural", "cosecha", "secado de fruto completo", "perfil frutal con mayor control"),
    ("fermentacion anaerobia", "cosecha", "fermentacion con bajo oxigeno", "diferenciar perfil sensorial"),
    ("secado", "cosecha", "reduccion de humedad del cafe pergamino", "asegurar almacenamiento seguro"),
    ("marquesina", "cosecha", "infraestructura para secado protegido", "mejorar control del proceso"),
    ("volteo de capa", "cosecha", "movimiento periodico durante secado", "uniformar humedad"),
    ("rehumectacion", "cosecha", "ganancia de humedad no deseada", "evitar deterioro de calidad"),
    ("humedad final", "cosecha", "contenido de agua al terminar secado", "definir seguridad de bodega"),
    ("actividad de agua", "cosecha", "indicador de agua disponible para microbios", "estabilidad del almacenamiento"),
    ("pergamino seco", "cosecha", "estado comercial posterior al secado", "base para almacenamiento y venta"),
    ("pasilla", "cosecha", "defecto fisico de grano", "afecta rendimiento y precio"),
    ("quaker", "cosecha", "grano inmaduro que afecta tueste", "reduce calidad de taza"),
    ("factor de rendimiento", "general", "indicador de conversion y calidad fisica", "evaluar resultado comercial"),
    ("almacenamiento en estibas", "cosecha", "sistema de bodega sobre base elevada", "evitar humedad por contacto"),
    ("trazabilidad de lote", "general", "seguimiento del lote desde campo a venta", "dar confianza comercial"),
    # Calidad y mercado
    ("catacion", "general", "evaluacion sensorial del cafe", "detectar fortalezas y defectos"),
    ("fragancia", "general", "aroma del cafe molido seco", "describir perfil de calidad"),
    ("aroma", "general", "componente olfativo de la taza", "caracterizar identidad del lote"),
    ("acidez", "general", "sensacion brillante en taza", "valorar equilibrio del perfil"),
    ("cuerpo", "general", "sensacion de peso y textura", "diferenciar experiencia sensorial"),
    ("dulzor", "general", "percepcion agradable asociada a buen proceso", "mejorar aceptacion"),
    ("balance", "general", "armonizacion de atributos de taza", "subir puntaje global"),
    ("retrogusto", "general", "sensacion posterior a tragar", "evaluar persistencia de calidad"),
    ("limpieza de taza", "general", "ausencia de sabores defectuosos", "indicar buen manejo"),
    ("consistencia de lote", "general", "repetibilidad de calidad en el tiempo", "construir confianza comercial"),
    ("micro lote", "general", "lote pequeno y diferenciado", "capturar valor en nichos"),
    ("cafe especial", "general", "cafe con calidad diferenciada", "acceder a mercados premium"),
    ("prima de calidad", "general", "precio adicional por mejor calidad", "aumentar rentabilidad"),
    ("negociacion basada en datos", "general", "venta apoyada en evidencia tecnica", "mejorar poder comercial"),
    ("costo por kilo", "general", "gasto total dividido por produccion vendida", "medir eficiencia real"),
    ("margen neto", "general", "resultado economico final", "evaluar sostenibilidad del negocio"),
    ("flujo de caja", "general", "movimiento de entradas y salidas de dinero", "planear continuidad operativa"),
    ("punto de equilibrio", "general", "nivel minimo para no perder dinero", "guiar decisiones de inversion"),
    ("retorno de inversion", "general", "ganancia frente al capital invertido", "priorizar mejoras"),
    ("cuello de botella", "general", "proceso que limita rendimiento global", "enfocar correcciones"),
    ("plan de mejora continua", "general", "ciclo de medir ajustar volver a medir", "subir resultados por iteracion"),
]


CARDS: list[dict[str, Any]] = []
for term, category, definition, purpose in TERMS:
    CARDS.append(
        card(
            category,
            [
                f"Que es {term} en cafe",
                f"Para que sirve {term} en cafetal",
                f"Como aplicar {term} en cultivo de cafe",
                f"Por que es importante {term} en cafe",
            ],
            f"En cafe, {term} se refiere a {definition}. "
            f"Su utilidad principal es {purpose} dentro de un manejo tecnico por lotes.",
            toks("cafe", term, definition, purpose, category),
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
    print(f"Entradas nuevas cafe (glossary): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()

