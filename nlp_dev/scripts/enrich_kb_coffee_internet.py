#!/usr/bin/env python3
"""
Enriquece la KB de la app con entradas SOLO de café, basadas en fuentes técnicas
(FNC/Cenicafé, FAO, World Agroforestry).

- Agrega muchas preguntas prácticas para visita de campo.
- Evita duplicados por pregunta normalizada.
- Mantiene IDs consecutivos.
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


def e(category: str, questions: list[str], answer: str, keywords: list[str]) -> dict[str, Any]:
    return {
        "category": category,
        "questions": questions,
        "answer": answer,
        "keywords": keywords,
    }


COFFEE_ENTRIES: list[dict[str, Any]] = [
    e(
        "cultivo",
        [
            "¿Cuál es el clima ideal para café arábica?",
            "Clima óptimo para cultivar café",
            "Condiciones climáticas del café arábica",
            "Qué clima necesita el café",
        ],
        "Para café arábica funciona mejor un clima templado-húmedo: temperatura media de 14-28°C (óptimo), lluvia anual de 1.400-2.300 mm y suelos bien drenados. Si tienes extremos de frío/calor o lluvias muy irregulares, conviene ajustar sombra, nutrición y renovación para estabilizar producción.",
        ["cafe", "clima", "temperatura", "lluvia", "arabica", "produccion"],
    ),
    e(
        "cultivo",
        [
            "Temperatura recomendada para café",
            "Rango de temperatura del café",
            "¿A qué temperatura produce mejor el café?",
            "Temperatura mínima y máxima para café",
        ],
        "Como referencia técnica, arábica responde mejor entre 14 y 28°C; por debajo de 10°C y por encima de 34°C aumenta el estrés y cae el rendimiento. En fincas calientes, subir sombra y materia orgánica ayuda a amortiguar temperatura del suelo.",
        ["cafe", "temperatura", "estres", "sombra", "suelo"],
    ),
    e(
        "cultivo",
        [
            "¿Cuánta lluvia necesita el café?",
            "Precipitación ideal para café",
            "Lluvia anual cultivo de café",
            "Rango de lluvia café arábica",
        ],
        "El rango útil está alrededor de 1.400-2.300 mm/año (con distribución relativamente uniforme). Con sequías largas sube riesgo de aborto floral y broca; con excesos sin drenaje suben enfermedades radiculares.",
        ["cafe", "lluvia", "precipitacion", "sequias", "drenaje", "enfermedades"],
    ),
    e(
        "cultivo",
        [
            "Altitud recomendada para café arábica",
            "¿A qué altura se da bien el café?",
            "msnm ideal para café",
            "Altura de cultivo café",
        ],
        "En sistemas de arábica se reportan muy buenos resultados desde 1.300 m y frecuentemente entre 1.500-2.000 m, según zona. La altitud no trabaja sola: debe ir acompañada de buena nutrición, variedad adecuada y manejo de sombra.",
        ["cafe", "altitud", "msnm", "arabica", "sombra", "variedad"],
    ),
    e(
        "cultivo",
        [
            "pH ideal del suelo para café",
            "Acidez del suelo en cafetal",
            "¿Qué pH necesita el café?",
            "Rango de pH café",
        ],
        "Un objetivo técnico común para suelo cafetero está cerca de pH 5,5 a 6,0. Con pH muy bajo se limita la disponibilidad de nutrientes y puede aumentar toxicidad de aluminio; por eso conviene analizar suelo y corregir con enmiendas cuando aplique.",
        ["cafe", "ph", "suelo", "acidez", "enmiendas", "nutrientes"],
    ),
    e(
        "cultivo",
        [
            "Tipo de suelo ideal para café",
            "¿Qué suelos no sirven para café?",
            "Suelo recomendado cafetal",
            "Textura de suelo para café",
        ],
        "El café responde mejor en suelos profundos, con buena materia orgánica y drenaje. Suelos muy arenosos, muy superficiales o con arcillas pesadas compactadas suelen reducir desarrollo radicular y estabilidad productiva.",
        ["cafe", "suelo", "textura", "drenaje", "raiz", "materia", "organica"],
    ),
    e(
        "riego",
        [
            "¿El café tolera encharcamiento?",
            "Drenaje en cultivo de café",
            "Exceso de agua en cafetal",
            "Manejo de encharcamiento en café",
        ],
        "No conviene el encharcamiento en café. El exceso de agua daña raíces y predispone enfermedades. Prioriza drenajes funcionales, coberturas nobles y manejo de suelo para infiltrar agua sin asfixiar raíces.",
        ["cafe", "encharcamiento", "drenaje", "raices", "coberturas", "suelo"],
    ),
    e(
        "cultivo",
        [
            "¿El café necesita sombra?",
            "Sombra recomendada en cafetales",
            "Café a pleno sol o con sombra",
            "Porcentaje de sombra para café",
        ],
        "La sombra debe ser manejada, no extrema. En Colombia se recomienda regularla alrededor de 30% en sistemas con sombrío, con podas oportunas. Muy poca sombra sube estrés térmico; demasiada sombra baja aireación y puede favorecer enfermedades.",
        ["cafe", "sombra", "sombrio", "podas", "enfermedades", "estres"],
    ),
    e(
        "siembra",
        [
            "Distancia de siembra del café",
            "Marco de plantación café",
            "¿A qué distancia sembrar café?",
            "Separación entre matas de café",
        ],
        "Como guía técnica frecuente: plantas a 2-3 m y surcos de 3-5 m en sistemas de contorno, ajustando a variedad, pendiente y tipo de manejo. Lo importante es equilibrar densidad, aireación y facilidad de labores.",
        ["cafe", "siembra", "distancia", "densidad", "surcos", "pendiente"],
    ),
    e(
        "siembra",
        [
            "Época de siembra del café",
            "¿Cuándo sembrar café?",
            "Meses recomendados para sembrar café",
            "Siembra de café según lluvias",
        ],
        "La regla práctica es sembrar en periodo de lluvias de tu zona para asegurar prendimiento. Si llega verano fuerte, hay que proteger colinos y apoyar con riego de establecimiento.",
        ["cafe", "siembra", "epoca", "lluvias", "colinos", "establecimiento"],
    ),
    e(
        "siembra",
        [
            "¿Cómo seleccionar colinos de café para llevar al lote?",
            "Calidad de colinos de café",
            "Selección de plantas para siembra café",
            "Qué colino de café escoger",
        ],
        "Elige colinos sanos, vigorosos y bien formados. Llévalos al campo antes de deformaciones de raíz por bolsa y evita golpes en transporte. Una mala planta de arranque cuesta producción por varios años.",
        ["cafe", "colinos", "almacigo", "siembra", "raiz", "vigor"],
    ),
    e(
        "siembra",
        [
            "Sistema de café a sol",
            "Sistema de café con sombra",
            "Diferencia café al sol y bajo sombrío",
            "¿Qué sistema de producción usar en café?",
        ],
        "Puedes producir café a pleno sol o bajo sombrío, según ambiente y suelo. En ladera y alta erosión, el sistema con sombra bien manejada suele ayudar a conservar suelo y regular microclima.",
        ["cafe", "sol", "sombra", "sistema", "erosion", "microclima"],
    ),
    e(
        "cultivo",
        [
            "Sombrío transitorio en café",
            "Uso de plátano como sombra en café",
            "Sombra temporal para cafetal",
            "Sombrío con musáceas en café",
        ],
        "El sombrío transitorio (por ejemplo plátano/banano) protege al cafeto joven, aporta biomasa y puede dar ingreso adicional. Debe manejarse para que no compita en exceso por luz, agua y nutrientes.",
        ["cafe", "sombrio", "transitorio", "platano", "banano", "biomasa"],
    ),
    e(
        "cultivo",
        [
            "Sombrío permanente en café",
            "Árboles de sombra permanente para cafetal",
            "Distancias del sombrío permanente",
            "Guamo en café distancia",
        ],
        "En sombrío permanente se usan especies como guamo y otras compatibles. Referencias técnicas mencionan marcos de 12-15 m para árboles permanentes, con podas para mantener cerca de 30% de sombra efectiva.",
        ["cafe", "sombrio", "permanente", "guamo", "podas", "distancia"],
    ),
    e(
        "cultivo",
        [
            "Asocio de café con maíz o frijol",
            "Intercultivo en café joven",
            "¿Se puede sembrar frijol entre café?",
            "Asocio temporal en cafetal nuevo",
        ],
        "En los primeros años puede asociarse con cultivos como maíz o fríjol según diseño del lote. Esto ayuda al flujo de caja, pero exige ordenar competencia por agua/nutrientes y conservar cobertura del suelo.",
        ["cafe", "asocio", "maiz", "frijol", "cobertura", "suelo"],
    ),
    e(
        "cultivo",
        [
            "Renovación de cafetales por zoca",
            "Renovación por siembra en café",
            "¿Cuándo renovar un cafetal?",
            "Zoca o siembra en renovación",
        ],
        "Los dos sistemas clave son renovación por zoca y por siembra. Renovar mantiene cafetales jóvenes, mejora manejo de plagas/enfermedades y aumenta eficiencia de fertilización y labores.",
        ["cafe", "renovacion", "zoca", "siembra", "plagas", "productividad"],
    ),
    e(
        "fertilizacion",
        [
            "¿Por qué hacer análisis de suelo en café?",
            "Importancia del análisis de suelo cafetal",
            "Fertilización racional en café",
            "Cómo decidir fertilización del café",
        ],
        "La fertilización en café debe basarse en análisis de suelo (y de hojas cuando sea posible), no en recetas fijas. Así aplicas lo que realmente falta, reduces costos y evitas degradar el suelo.",
        ["cafe", "fertilizacion", "analisis", "suelo", "costos", "nutricion"],
    ),
    e(
        "fertilizacion",
        [
            "Muestreo de suelo para café",
            "¿Cómo tomar muestra de suelo en cafetal?",
            "Profundidad de muestra de suelo café",
            "Frecuencia análisis de suelo café",
        ],
        "Para muestreo representativo en café: toma submuestras en al menos 20 sitios por bloque (2-4 ha), profundidad cercana a 15 cm, mezcla bien y etiqueta. Idealmente hazlo una vez al año antes de floración.",
        ["cafe", "muestreo", "suelo", "floracion", "analisis", "bloque"],
    ),
    e(
        "fertilizacion",
        [
            "Errores al tomar muestras de suelo en café",
            "Qué no hacer en muestreo de suelo cafetal",
            "Mala toma de muestra suelo café",
            "Evitar errores análisis de suelo café",
        ],
        "Evita muestrear justo después de fertilizar, no tomes muestra pegada a árboles de sombra y no mezcles zonas con suelos muy diferentes. Esos errores distorsionan la recomendación de fertilización.",
        ["cafe", "muestreo", "suelo", "errores", "fertilizacion", "sombra"],
    ),
    e(
        "fertilizacion",
        [
            "Muestreo foliar en café",
            "¿Cómo tomar hojas para análisis en café?",
            "Cuántas hojas muestrear en cafetal",
            "Análisis foliar del café",
        ],
        "Para análisis foliar se recomienda muestrear hojas de la 3ra o 4ta pareja en ramas activas, en árboles promedio del lote. Como guía, al menos 40 árboles por bloque y mínimo 100 hojas por muestra compuesta.",
        ["cafe", "foliar", "hojas", "analisis", "nutricion", "muestreo"],
    ),
    e(
        "fertilizacion",
        [
            "Rangos nutricionales hoja de café",
            "Niveles de N P K en hoja de café",
            "Interpretación análisis foliar café",
            "Valores de referencia foliar café",
        ],
        "Como referencia técnica en hojas de café: N 2,5-3,0%, P 0,15-0,20%, K 2,1-2,6%, Ca 0,75-1,5%, Mg 0,25-0,40%. Interpreta siempre con un agrónomo local porque variedad, clima y edad cambian objetivos.",
        ["cafe", "analisis", "foliar", "nitrogeno", "fosforo", "potasio"],
    ),
    e(
        "fertilizacion",
        [
            "Parámetros de suelo objetivo en café",
            "Objetivo de pH y materia orgánica en cafetal",
            "Meta de conductividad en suelo café",
            "Valores guía de suelo para café",
        ],
        "Como guía general reportada para café: pH cercano a 5,5-6,0, materia orgánica 1-3% y conductividad baja (<0,2 dS/m). Úsalo como referencia inicial y ajusta con análisis de tu finca.",
        ["cafe", "suelo", "ph", "materia", "organica", "conductividad"],
    ),
    e(
        "fertilizacion",
        [
            "Función del nitrógeno en café",
            "Para qué sirve N en el cafeto",
            "Importancia del nitrógeno en café",
            "Nutriente N en café",
        ],
        "El nitrógeno impulsa crecimiento vegetativo, proteínas, enzimas y fotosíntesis. Si falta, la planta se debilita y baja el llenado de fruto; si sobra, puede disparar brotes tiernos y desbalancear el cultivo.",
        ["cafe", "nitrogeno", "fotosintesis", "crecimiento", "fruto"],
    ),
    e(
        "fertilizacion",
        [
            "Función del fósforo en café",
            "Para qué sirve P en cafeto",
            "Importancia del fósforo en café",
            "Nutriente fósforo café",
        ],
        "El fósforo participa en energía celular, formación de raíces, floración y maduración. Es clave en etapa de establecimiento y en lotes que buscan buena estructura radicular.",
        ["cafe", "fosforo", "raiz", "floracion", "maduracion"],
    ),
    e(
        "fertilizacion",
        [
            "Función del potasio en café",
            "Para qué sirve K en el café",
            "Importancia del potasio en cafetal",
            "Nutriente K en café",
        ],
        "El potasio es crítico para calidad de fruto, balance hídrico y tolerancia a estrés. También participa en resistencia general de la planta, por eso no conviene descuidarlo en lotes productivos.",
        ["cafe", "potasio", "calidad", "agua", "estres", "resistencia"],
    ),
    e(
        "fertilizacion",
        [
            "Función del calcio y magnesio en café",
            "Para qué sirven Ca y Mg en cafeto",
            "Calcio magnesio en cultivo de café",
            "Nutrientes secundarios café",
        ],
        "El calcio fortalece paredes celulares y raíces; el magnesio es núcleo de la clorofila. Ambos sostienen vigor, coloración y llenado. Cuando hay deficiencias, corrige con enmiendas según análisis.",
        ["cafe", "calcio", "magnesio", "clorofila", "enmiendas", "vigor"],
    ),
    e(
        "fertilizacion",
        [
            "Función del boro en café",
            "Boro en floración de café",
            "Para qué sirve el B en cafeto",
            "Micronutriente boro café",
        ],
        "El boro apoya crecimiento de brotes y raíces nuevas, floración y cuajado. Se maneja con dosis precisas; excesos de micronutrientes también causan daño.",
        ["cafe", "boro", "floracion", "cuajado", "micronutriente"],
    ),
    e(
        "fertilizacion",
        [
            "Programa NPK año 1 café",
            "Dosis de fertilizante primer año café",
            "Fertilización café recién sembrado",
            "Plan nutricional año 1 cafeto",
        ],
        "Una referencia técnica para arábica en establecimiento: año 1, alrededor de 30 g/planta de NPK 15-15-15 antes de finalizar lluvias. Ajusta siempre con análisis de suelo y recomendación local.",
        ["cafe", "npk", "ano1", "fertilizacion", "establecimiento"],
    ),
    e(
        "fertilizacion",
        [
            "Programa NPK año 2 café",
            "Dosis fertilización segundo año café",
            "Fertilizante café año dos",
            "Plan nutricional año 2 cafeto",
        ],
        "Como guía para año 2: tres aplicaciones de ~30 g/planta de NPK 15-15-15 (inicio de lluvias, mitad de año y septiembre) y, cuando aplique, dolomita ~500 g/planta.",
        ["cafe", "npk", "ano2", "dolomita", "fertilizacion"],
    ),
    e(
        "fertilizacion",
        [
            "Programa NPK año 3 café",
            "Dosis de fertilizante tercer año café",
            "Fertilizante para café en producción temprana",
            "Plan nutricional año 3 cafeto",
        ],
        "Referencia frecuente en año 3: ~60 g/planta de NPK 15-15-15 por aplicación, tres veces al año. Si el lote tiene potencial alto, se puede requerir ajuste al alza con soporte de análisis.",
        ["cafe", "npk", "ano3", "produccion", "analisis", "fertilizacion"],
    ),
    e(
        "fertilizacion",
        [
            "Programa NPK año 4 café",
            "Dosis fertilización cuarto año café",
            "Fertilizante café año 4",
            "Plan nutricional cafetal año 4",
        ],
        "Como referencia: ~90 g/planta de NPK 15-15-15 por aplicación (3 veces/año) y revisión de enmiendas según pH y saturación de bases.",
        ["cafe", "npk", "ano4", "ph", "enmiendas", "fertilizacion"],
    ),
    e(
        "fertilizacion",
        [
            "Programa NPK año 5 en adelante café",
            "Fertilización cafetal adulto",
            "Dosis en café de 5 años",
            "Plan nutricional café adulto",
        ],
        "Para cafetal adulto, una guía técnica usa ~120 g/planta de NPK 15-15-15 por aplicación, 3 veces/año. No es receta universal: confirma con análisis de suelo/hoja y carga esperada.",
        ["cafe", "npk", "adulto", "ano5", "carga", "fertilizacion"],
    ),
    e(
        "fertilizacion",
        [
            "Uso de dolomita en café",
            "¿Cuánta dolomita aplicar al cafeto?",
            "Cal y magnesio en café",
            "Corrección de acidez con dolomita café",
        ],
        "Una referencia práctica es aplicar dolomita (Ca+Mg) alrededor de 500 g/planta cada dos años, idealmente antes de terminar lluvias, para que se incorpore al suelo.",
        ["cafe", "dolomita", "calcio", "magnesio", "acidez", "suelo"],
    ),
    e(
        "fertilizacion",
        [
            "¿Dónde aplicar fertilizante en café?",
            "Ubicación del abono en cafeto",
            "Fertilización en banda café",
            "Aplicación al rededor de la gotera café",
        ],
        "Ubica el fertilizante en banda alrededor de la línea de gotera, no pegado al tallo, para favorecer absorción y reducir pérdidas. Complementa con cobertura orgánica y humedad adecuada.",
        ["cafe", "fertilizante", "gotera", "banda", "absorcion", "suelo"],
    ),
    e(
        "fertilizacion",
        [
            "¿Se puede usar pulpa fresca en café?",
            "Pulpa sin compostar en cafetal",
            "Riesgo de usar estiércol fresco en café",
            "Compostaje de subproductos café",
        ],
        "Evita aplicar pulpa o estiércol frescos directamente: pueden quemar plantas, inmovilizar nitrógeno y elevar riesgos sanitarios. Es mejor compostar y luego incorporar de forma racional.",
        ["cafe", "pulpa", "compost", "estiercol", "nitrogeno", "riesgo"],
    ),
    e(
        "fertilizacion",
        [
            "Materia orgánica en café",
            "Lombricompuesto para cafetal",
            "Uso de pulpa descompuesta en café",
            "Fuentes orgánicas para café",
        ],
        "La pulpa de café descompuesta y el lombricompuesto son fuentes útiles de materia orgánica. Mejoran estructura del suelo y eficiencia del fertilizante mineral cuando se usan con manejo técnico.",
        ["cafe", "materia", "organica", "pulpa", "lombricompuesto", "suelo"],
    ),
    e(
        "fertilizacion",
        [
            "Nutrientes que se van en la cosecha de café",
            "Reposición de nutrientes en cafetal",
            "Por qué fertilizar después de cosecha café",
            "Reciclaje de nutrientes en café",
        ],
        "Con la cosecha se exportan nutrientes del lote. Por eso hay que reponer con fertilización y reciclar residuos bien compostados (pulpa, podas, hojarasca) para sostener productividad y calidad.",
        ["cafe", "cosecha", "nutrientes", "reposicion", "podas", "compost"],
    ),
    e(
        "plagas",
        [
            "¿Qué es la broca del café?",
            "Broca del cafeto descripción",
            "Cómo es el insecto broca café",
            "Identificación de broca en café",
        ],
        "La broca es un escarabajo pequeño (aprox. 1,5 mm), negro, que perfora el fruto y se desarrolla dentro del grano. Es la plaga económica más importante del café en Colombia.",
        ["broca", "cafe", "insecto", "escarabajo", "fruto", "grano"],
    ),
    e(
        "plagas",
        [
            "Ciclo de vida de la broca del café",
            "Huevos y larvas de la broca",
            "Cuánto tarda la broca en desarrollarse",
            "Etapas biológicas de la broca",
        ],
        "En términos prácticos: la hembra oviposita dentro del fruto (12-33 huevos reportados), la eclosión depende de clima (días) y luego larvas y pupas se desarrollan dentro de la semilla. Por eso el control debe romper ese ciclo en campo y beneficio.",
        ["broca", "ciclo", "huevos", "larvas", "pupas", "control"],
    ),
    e(
        "plagas",
        [
            "Daño económico de la broca",
            "Cómo afecta la broca el precio del café",
            "Pérdidas por broca en café",
            "Defecto broca de punto",
        ],
        "La broca reduce peso y calidad, y puede generar descuentos en compra por defectos. Si sube la infestación, el caficultor pierde rendimiento y precio.",
        ["broca", "perdidas", "precio", "calidad", "defecto", "cafe"],
    ),
    e(
        "plagas",
        [
            "¿Cuándo aumenta la broca?",
            "Broca en época seca",
            "Clima y dinámica de broca café",
            "Sequía y broca del café",
        ],
        "En muchas zonas cafeteras la broca se acelera en periodos secos y con cosecha deficiente. Mantener recolección oportuna y repase reduce focos de multiplicación.",
        ["broca", "sequias", "cosecha", "repase", "focos", "cafe"],
    ),
    e(
        "plagas",
        [
            "Broca después del zoqueo",
            "Riesgo de zoquear con frutos brocados",
            "Manejo de broca en zoca",
            "Zoqueo y dispersión de broca",
        ],
        "Si zoqueas sin retirar frutos brocados, la plaga puede multiplicarse y dispersarse a lotes vecinos. Antes de zoca, retira y maneja adecuadamente frutos infestados.",
        ["broca", "zoca", "zoqueo", "frutos", "infestacion", "lotes"],
    ),
    e(
        "plagas",
        [
            "¿Cómo muestrear broca en café?",
            "Método de muestreo de broca por hectárea",
            "Cuántos sitios evaluar para broca",
            "Muestreo de rama para broca",
        ],
        "Guía usada en caficultura colombiana: 30 sitios por hectárea; en cada sitio, un árbol y una rama productiva con 30-100 frutos. Cuenta total de frutos y frutos brocados para estimar infestación.",
        ["broca", "muestreo", "hectarea", "rama", "infestacion", "frutos"],
    ),
    e(
        "plagas",
        [
            "Frecuencia de monitoreo de broca",
            "Cada cuánto evaluar broca en cafetal",
            "Monitoreo mensual de broca",
            "Seguimiento broca por lotes",
        ],
        "Evalúa broca al menos una vez al mes por lote. El dato de infestación solo sirve si se registra por lote y se usa para decidir controles a tiempo.",
        ["broca", "monitoreo", "mensual", "lotes", "control"],
    ),
    e(
        "plagas",
        [
            "Umbral de broca en cosecha",
            "Nivel máximo de broca permitido",
            "Qué porcentaje de broca tolerar",
            "Broca límite en cafetal",
        ],
        "Como referencia técnica: durante cosecha se busca no superar 5% de infestación en campo para proteger calidad comercial. En periodos entre cosechas, el objetivo recomendado es aún más bajo.",
        ["broca", "umbral", "cosecha", "infestacion", "calidad"],
    ),
    e(
        "plagas",
        [
            "Umbral de broca entre cosechas",
            "Broca en intercosecha",
            "Meta de infestación broca fuera de cosecha",
            "Nivel deseable broca cafetal",
        ],
        "Entre cosechas, la meta práctica es mantener broca por debajo de 2% para no llegar con presión alta al siguiente pico productivo.",
        ["broca", "intercosecha", "umbral", "infestacion", "cafetal"],
    ),
    e(
        "plagas",
        [
            "Posiciones A B C D de la broca",
            "Qué significan posiciones de broca",
            "Penetración de broca en fruto",
            "Lectura de posiciones broca",
        ],
        "Las posiciones A-B indican broca iniciando/perforando canal; C-D indican broca más interna o con descendencia. Esta lectura ayuda a decidir si una medida de control tendrá buen impacto.",
        ["broca", "posicion", "penetracion", "fruto", "control"],
    ),
    e(
        "plagas",
        [
            "¿Cuándo aplicar control químico para broca?",
            "Insecticida para broca según posiciones",
            "Momento técnico para asperjar broca",
            "Aplicación por focos broca",
        ],
        "El control químico se justifica cuando hay infestación relevante y proporción alta de broca en posiciones A/B (aún expuesta), priorizando focos o puntos calientes y productos de bajo riesgo toxicológico.",
        ["broca", "quimico", "posiciones", "focos", "asperjar", "toxicologico"],
    ),
    e(
        "plagas",
        [
            "Qué es el control cultural RE-RE en café",
            "Control cultural de broca",
            "Re-Re en cafetal",
            "Cómo hacer repase de broca",
        ],
        "El RE-RE combina recolección oportuna y repase para retirar frutos maduros, sobremaduros y secos en árbol/suelo. Es la base del control de broca y puede explicar gran parte del éxito sanitario del lote.",
        ["broca", "re-re", "repase", "recoleccion", "control", "cafe"],
    ),
    e(
        "plagas",
        [
            "¿Cuántas veces hacer repase para broca?",
            "Frecuencia de repase en café",
            "Repase de broca dos veces al año",
            "Repase después de cosecha",
        ],
        "Como práctica recomendada, el repase de broca se realiza dos veces al año al finalizar cosecha principal y mitaca, retirando frutos remanentes que sostienen la plaga.",
        ["broca", "repase", "mitaca", "cosecha", "plaga"],
    ),
    e(
        "plagas",
        [
            "Control biológico de broca con Beauveria",
            "Beauveria bassiana en broca del café",
            "Hongo para controlar broca",
            "Bioinsumo para broca café",
        ],
        "Beauveria bassiana es un aliado biológico clave contra broca. Funciona mejor integrado con monitoreo, recolección oportuna y manejo de focos.",
        ["broca", "beauveria", "biologico", "hongo", "focos"],
    ),
    e(
        "plagas",
        [
            "Parasitoides de la broca del café",
            "Avispitas para broca",
            "Prorops nasuta broca",
            "Phymastichus coffea control",
        ],
        "Dentro del control biológico se han usado parasitoides como Prorops nasuta, Cephalonomia stephanoderis y Phymastichus coffea. Su mayor efecto aparece cuando se integran con buen manejo cultural.",
        ["broca", "parasitoides", "prorops", "cephalonomia", "phymastichus"],
    ),
    e(
        "plagas",
        [
            "Evitar escape de broca en beneficio",
            "Broca sale del beneficiadero al cafetal",
            "Manejo de broca en poscosecha",
            "Control de broca en desagües",
        ],
        "Para evitar reinfestación: usa costales cerrados, procesa oportunamente, coloca mallas en desagües y maneja bien pulpas/subproductos. Si la broca escapa del beneficio, vuelve al lote.",
        ["broca", "beneficio", "desagues", "costales", "reinfestacion"],
    ),
    e(
        "plagas",
        [
            "Control de broca por focos",
            "Puntos calientes de broca",
            "Manejo localizado de broca",
            "Mapeo de focos broca",
        ],
        "La infestación de broca no suele ser uniforme. Identificar focos permite concentrar control (biológico o químico) donde más se necesita y bajar costo total.",
        ["broca", "focos", "puntos", "calientes", "control", "costo"],
    ),
    e(
        "plagas",
        [
            "Categoría toxicológica de insecticidas para broca",
            "Insecticidas de bajo riesgo en café",
            "Control químico seguro broca",
            "Toxicología en manejo de broca",
        ],
        "Cuando se requiera control químico, prioriza productos recomendados de baja categoría toxicológica (III-IV), con equipo calibrado y enfoque por foco para reducir riesgos y residuos.",
        ["broca", "insecticidas", "toxicologia", "seguridad", "calibracion"],
    ),
    e(
        "diagnostico",
        [
            "¿Qué es la roya del café?",
            "Hemileia vastatrix en cafetal",
            "Importancia de roya en café",
            "Roya cafeto explicación",
        ],
        "La roya del cafeto (Hemileia vastatrix) es la enfermedad más importante del café en Colombia. En variedades susceptibles y sin manejo puede causar pérdidas significativas de producción en varios ciclos de cosecha.",
        ["roya", "cafe", "hemileia", "enfermedad", "perdidas"],
    ),
    e(
        "diagnostico",
        [
            "Pérdidas por roya en café",
            "Cuánto puede bajar la producción por roya",
            "Impacto económico de roya del cafeto",
            "Daño productivo roya café",
        ],
        "En Colombia se han reportado pérdidas de 10-30% en susceptibles sin manejo, y en escenarios críticos pueden ser mayores. La caída de follaje reduce llenado de fruto y afecta cosechas futuras.",
        ["roya", "perdidas", "produccion", "follaje", "cafe"],
    ),
    e(
        "diagnostico",
        [
            "Variedades susceptibles a roya en Colombia",
            "Caturra roya",
            "Geisha susceptible a roya",
            "Qué variedad de café se enferma más de roya",
        ],
        "En alertas fitosanitarias recientes se mencionan como susceptibles, entre otras: Caturra, Típica, Borbón, Geisha, Maragogipe y algunos Catimores introducidos. En estas variedades el monitoreo debe ser estricto.",
        ["roya", "variedades", "susceptibles", "caturra", "geisha", "cafe"],
    ),
    e(
        "diagnostico",
        [
            "Variedades resistentes a roya recomendadas",
            "Castillo resistente roya",
            "Variedad Tabi café",
            "Qué sembrar para reducir roya",
        ],
        "Una estrategia central es usar variedades con resistencia genética, como líneas Castillo y Tabi en programas de renovación. Esto reduce presión de fungicidas y estabiliza productividad.",
        ["roya", "resistencia", "castillo", "tabi", "renovacion"],
    ),
    e(
        "diagnostico",
        [
            "¿Cuándo controlar roya del café?",
            "Roya se controla cuando no se ve",
            "Momento oportuno control roya",
            "Control preventivo roya café",
        ],
        "La recomendación práctica es anticiparse: controlar roya antes de que escale visualmente en lote, especialmente después de floraciones fuertes y en variedades susceptibles.",
        ["roya", "control", "preventivo", "floracion", "susceptibles"],
    ),
    e(
        "diagnostico",
        [
            "Manejo integrado de roya en café",
            "Estrategia MIE en cafetal",
            "Control genético cultural químico roya",
            "Cómo manejar roya integralmente",
        ],
        "Aplica manejo integrado: componente genético (variedad), cultural (nutrición, sombra, renovación), biológico y químico cuando corresponda. El éxito depende de oportunidad y seguimiento por lote.",
        ["roya", "manejo", "integrado", "genetico", "cultural", "quimico"],
    ),
    e(
        "diagnostico",
        [
            "Por qué son importantes las hojas sanas en café",
            "Defoliación por roya en cafeto",
            "Roya y llenado de frutos",
            "Follaje y producción de café",
        ],
        "Las hojas sanas sostienen la fotosíntesis que llena fruto y forma estructuras de cosechas futuras. Si la roya tumba follaje, baja producción actual y también la del siguiente ciclo.",
        ["cafe", "hojas", "fotosintesis", "roya", "produccion"],
    ),
    e(
        "diagnostico",
        [
            "Niveles de roya mayores al 5%",
            "Qué hacer si sube roya en el lote",
            "Alerta roya en cafetal",
            "Roya alta en café",
        ],
        "Con incidencias altas (por ejemplo >5% en monitoreos locales), acelera plan técnico por lote: revisión varietal, nutrición, sombra, y control fitosanitario en momento oportuno según recomendación de extensión.",
        ["roya", "incidencia", "alerta", "lote", "extension"],
    ),
    e(
        "diagnostico",
        [
            "Mancha de hierro en café",
            "Prevención de mancha de hierro cafeto",
            "Cercospora en café manejo nutricional",
            "Hojas y frutos con mancha de hierro",
        ],
        "La mancha de hierro se relaciona con desbalances nutricionales y estrés. En manejo práctico: fertilización oportuna, buena aireación y manejo de arvenses ayudan a reducir presión de la enfermedad.",
        ["mancha", "hierro", "cafe", "nutricion", "arvenses", "aireacion"],
    ),
    e(
        "diagnostico",
        [
            "Mal rosado del café manejo",
            "Corticium salmonicolor control",
            "Ramas rosadas en cafeto",
            "Poda para mal rosado",
        ],
        "Para mal rosado, una práctica clave es podar y retirar partes enfermas en época seca, desinfectar herramientas y mejorar ventilación del lote.",
        ["mal", "rosado", "corticium", "poda", "herramientas", "cafe"],
    ),
    e(
        "diagnostico",
        [
            "Llaga radical en café",
            "Rosellinia en cafetal",
            "Control de llaga negra café",
            "Hongos de raíz en café manejo",
        ],
        "En llagas radicales, el manejo incluye retirar y destruir material enfermo, mejorar aireación/drenaje y aplicar biocontroladores cuando el técnico lo recomiende.",
        ["llaga", "radical", "rosellinia", "drenaje", "biocontrol"],
    ),
    e(
        "diagnostico",
        [
            "Antracnosis y gotera en café",
            "Exceso de sombra enfermedad café",
            "Humedad alta y enfermedades café",
            "Cómo reducir antracnosis en cafetal",
        ],
        "Exceso de sombra y humedad favorecen varias enfermedades (como antracnosis y gotera). Ajusta sombrío, poda y circulación de aire para bajar presión sanitaria.",
        ["antracnosis", "gotera", "sombra", "humedad", "poda", "cafe"],
    ),
    e(
        "plagas",
        [
            "Palomilla de la raíz en café",
            "Dysmicocus brevipes control",
            "Raíces de café atacadas por palomilla",
            "Plaga de raíz cafeto",
        ],
        "Para palomilla de la raíz, se recomienda manejo sanitario del foco y evitar encharcamientos, junto con nutrición oportuna para sostener vigor del cultivo.",
        ["palomilla", "raiz", "dysmicocus", "encharcamiento", "cafe"],
    ),
    e(
        "plagas",
        [
            "Minador de la hoja en café",
            "Leucoptera coffeella control",
            "Galerías en hojas de café",
            "Plaga minador cafetal",
        ],
        "El minador perfora hojas desde dentro. En manejo integrado, el control biológico nativo y condiciones climáticas favorables suelen ayudar; evita aplicaciones indiscriminadas.",
        ["minador", "leucoptera", "hojas", "biologico", "cafe"],
    ),
    e(
        "plagas",
        [
            "Arañita roja en café",
            "Ácaros en cafetal en verano",
            "Oligonychus yothersi en café",
            "Control de araña roja café",
        ],
        "La arañita roja suele subir en sequía y polvo (bordes de carretera). Mejorar cobertura, reducir estrés hídrico y conservar enemigos naturales ayuda a regularla.",
        ["aranita", "roja", "acaros", "sequias", "cobertura", "cafe"],
    ),
    e(
        "cosecha",
        [
            "Recolección selectiva en café",
            "Cosechar solo maduros en café",
            "Porcentaje de frutos maduros recomendado",
            "Buenas prácticas de cosecha café",
        ],
        "Para calidad, prioriza recolección selectiva de frutos maduros (idealmente alta proporción de maduros) y evita mezclar mucho verde, sobremaduro o frutos dañados.",
        ["cafe", "cosecha", "selectiva", "maduros", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Clasificación hidráulica de café cereza",
            "¿Qué hacer con frutos flotes en café?",
            "Separar frutos dañados en beneficio",
            "Flotes en café manejo",
        ],
        "Antes del despulpado conviene clasificar y retirar flotes/frutos dañados. Esto mejora uniformidad del proceso y reduce defectos en secado y taza.",
        ["cafe", "flotes", "clasificacion", "cereza", "beneficio", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Secado al sol del café en capa",
            "Espesor de capa para secar café",
            "Kg por metro cuadrado secado café",
            "Cómo extender café en secador",
        ],
        "En secado solar se recomienda capa delgada (aprox. 2-3 cm), con volteo frecuente. Capas muy gruesas retrasan secado y elevan riesgo de moho y defectos de taza.",
        ["cafe", "secado", "capa", "sol", "moho", "taza"],
    ),
    e(
        "cosecha",
        [
            "Temperatura en secador mecánico de café",
            "¿A qué temperatura secar café pergamino?",
            "Secado mecánico café 47 50",
            "Control térmico en secado café",
        ],
        "En secadores mecánicos estáticos, una referencia técnica usa aire alrededor de 47-50°C con recambio de flujo. Temperaturas mal manejadas afectan calidad y vida útil del grano.",
        ["cafe", "secado", "mecanico", "temperatura", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Humedad final del café pergamino",
            "¿A cuánto dejar la humedad del café?",
            "Meta de humedad comercial café",
            "11% humedad café",
        ],
        "La meta de comercialización suele estar entre 10% y 12% de humedad (preferible cerca de 11%). Fuera de rango suben riesgos de deterioro o quiebre y pérdida de calidad.",
        ["cafe", "humedad", "pergamino", "secado", "comercializacion"],
    ),
    e(
        "cosecha",
        [
            "Cuánto tarda secar café en túnel solar",
            "Tiempo de secado café 53 a 10-12%",
            "Secado café 7 a 10 días",
            "Duración secado café pergamino",
        ],
        "En ensayos de secador tipo túnel, secar desde ~53% de humedad hasta 10-12% tomó alrededor de 7-10 días, dependiendo de radiación y manejo del proceso.",
        ["cafe", "secado", "tunel", "dias", "humedad", "radiacion"],
    ),
    e(
        "cosecha",
        [
            "Secado de café con capa de 2 cm",
            "13 kg m2 en secador de café",
            "Secado café ambiente seco y soleado",
            "Riesgo de moho capa delgada café",
        ],
        "Con capa cercana a 2 cm (aprox. 13 kg/m2) y buen sol, el secado puede lograrse en 6-8 días y con menor exposición a condiciones críticas para mohos.",
        ["cafe", "secado", "2cm", "13kg", "moho", "sol"],
    ),
    e(
        "cosecha",
        [
            "Secado de café con capas gruesas",
            "Qué pasa si seco café en 4 cm o más",
            "Capa gruesa en secado de café",
            "Riesgo por secado lento café",
        ],
        "Cuando la capa supera 4 cm, sobre todo en lluvias, el secado puede alargarse mucho (hasta semanas) y aumenta riesgo de fermentaciones indeseables, mohos y defectos sensoriales.",
        ["cafe", "secado", "capa", "gruesa", "fermentacion", "mohos"],
    ),
    e(
        "cosecha",
        [
            "Riesgo de OTA en secado de café",
            "Mochos y ocratoxina en café",
            "Cómo evitar OTA en pergamino",
            "Actividad de agua y calidad café",
        ],
        "El secado lento y heterogéneo incrementa riesgo de mohos y OTA. Para prevenir: clasifica bien, retira residuos, seca en capas delgadas, voltea el grano y cierra en humedad final segura.",
        ["cafe", "ota", "mohos", "secado", "actividad", "agua"],
    ),
    e(
        "cosecha",
        [
            "Almacenamiento de café pergamino seco",
            "Condiciones de bodega para café",
            "Temperatura y humedad para guardar café",
            "Cómo almacenar café por meses",
        ],
        "Para conservar calidad: café seco (10-12%), bodega limpia/ventilada, temperatura baja (<20°C) y humedad relativa de 65-70%. Usa estibas y evita contacto con paredes y techo.",
        ["cafe", "almacenamiento", "bodega", "humedad", "temperatura", "estibas"],
    ),
    e(
        "cosecha",
        [
            "Distancia de sacos a paredes en bodega de café",
            "Estibas para almacenar café",
            "Separación del café en almacenamiento",
            "Cómo arrumar sacos de café",
        ],
        "Arruma los sacos sobre estibas limpias y deja separación de seguridad con paredes/techo (referencias técnicas hablan de ~30 cm) para evitar humedad, contaminación y calentamiento.",
        ["cafe", "sacos", "estibas", "bodega", "paredes", "humedad"],
    ),
    e(
        "cosecha",
        [
            "Contaminación del café pergamino",
            "Qué no almacenar cerca del café",
            "Olores extraños en café almacenado",
            "Evitar contaminación química del café",
        ],
        "No almacenes café junto a pinturas, combustibles, abonos, insecticidas, maderas húmedas o animales. El grano absorbe olores y contaminantes que luego dañan la taza.",
        ["cafe", "contaminacion", "olores", "almacenamiento", "quimicos"],
    ),
    e(
        "cosecha",
        [
            "Defectos no admisibles en café pergamino",
            "Qué granos defectuosos rechazan en café",
            "Pasillas y defectos de calidad café",
            "Control de defectos en pergamino",
        ],
        "Debes retirar granos con moho visible, pulpa adherida, daños severos por insectos y defectos fuertes de olor/color. Mientras más limpio llegue el lote, mejor precio y consistencia de taza.",
        ["cafe", "defectos", "pergamino", "pasillas", "calidad", "precio"],
    ),
    e(
        "cosecha",
        [
            "Color del café pergamino de buena calidad",
            "Desviaciones de color en pergamino",
            "Pergamino amarillo claro café",
            "Cómo ver calidad por color café",
        ],
        "Un pergamino sano suele verse homogéneo y de color amarillo claro. Apariencias grisáceas, muy oscuras o manchadas suelen indicar problemas de secado, humedad o manejo.",
        ["cafe", "color", "pergamino", "secado", "humedad", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Transporte de café pergamino",
            "Cómo transportar café sin contaminar",
            "Llevar café en jeep o camioneta cubiertos",
            "Cuidado del café durante transporte",
        ],
        "Durante transporte protege el café de lluvia, humo, tierra, químicos y cargas contaminantes. Lo ideal es vehículo cubierto y sacos bien cerrados.",
        ["cafe", "transporte", "contaminacion", "lluvia", "sacos"],
    ),
    e(
        "cosecha",
        [
            "Beneficio ecológico del café",
            "Qué es el beneficio húmedo ecológico",
            "Ventajas ambientales del beneficio ecológico",
            "Reducción de contaminación en beneficio café",
        ],
        "El beneficio ecológico por vía húmeda busca transformar cereza a pergamino usando menos agua y menor impacto ambiental, manteniendo calidad y aprovechando subproductos.",
        ["cafe", "beneficio", "ecologico", "agua", "subproductos", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Calidad del agua para lavar café",
            "pH del agua en beneficio del café",
            "Agua potable para lavado de café",
            "Requisitos del agua en beneficio",
        ],
        "Para lavado y fermentación controlada del café se recomienda agua limpia; en estándares avanzados se busca agua potable con pH cercano a 6-8 y libre de contaminantes biológicos y metales pesados.",
        ["cafe", "agua", "lavado", "ph", "beneficio", "potable"],
    ),
    e(
        "cosecha",
        [
            "Fermentación controlada de café",
            "Tiempo de fermentación café",
            "pH y brix en fermentación de café",
            "Fermentación sólida vs sumergida café",
        ],
        "En fermentación controlada, el tiempo y la temperatura definen perfil sensorial. Referencias técnicas reportan rangos aproximados de 12-18 h (sólida) y 18-30 h (sumergida) a 18-23°C, con seguimiento de pH y °Brix.",
        ["cafe", "fermentacion", "tiempo", "ph", "brix", "calidad"],
    ),
    e(
        "cosecha",
        [
            "Secado por lotes de café",
            "¿Por qué no mezclar lotes de café?",
            "Separar lotes en secado y almacenamiento",
            "Trazabilidad del café en finca",
        ],
        "Secar y almacenar por lotes mejora trazabilidad, permite corregir fallas por parcela y evita que un lote defectuoso deteriore toda la producción.",
        ["cafe", "lotes", "trazabilidad", "secado", "almacenamiento"],
    ),
    e(
        "cultivo",
        [
            "Manejo de arvenses en café",
            "Coberturas nobles en cafetal",
            "¿Conviene desnudar el suelo en café?",
            "Control de malezas conservacionista café",
        ],
        "En café de ladera conviene manejo de arvenses con enfoque conservacionista: coberturas nobles, mínima labranza y control dirigido. Desnudar totalmente el suelo aumenta erosión y costos.",
        ["cafe", "arvenses", "coberturas", "erosion", "suelo", "manejo"],
    ),
    e(
        "cultivo",
        [
            "Sombra y erosión en cafetales de ladera",
            "Cómo proteger suelo en cafetal inclinado",
            "Café en pendientes fuertes manejo",
            "Conservación de suelos en café",
        ],
        "En pendientes fuertes, el sombrío bien diseñado y coberturas ayudan a reducir impacto de lluvia, escorrentía y pérdida de suelo. Esto protege productividad de largo plazo.",
        ["cafe", "ladera", "sombra", "erosion", "escorrentia", "suelo"],
    ),
    e(
        "cultivo",
        [
            "¿Qué árboles usar como sombra en café?",
            "Especies de sombrío recomendadas",
            "Guamo matarratón nogales en cafetal",
            "Selección de árboles de sombra café",
        ],
        "Selecciona especies de sombra compatibles que no compitan demasiado por agua/nutrientes y permitan manejo de altura/luz. En Colombia son comunes guamos y otras especies adaptadas por región.",
        ["cafe", "sombra", "especies", "guamo", "matarraton", "compatibles"],
    ),
    e(
        "cultivo",
        [
            "Poda del sombrío en café",
            "Descope del guamo en cafetal",
            "Manejo de altura de árboles de sombra",
            "Cómo mantener 30% de sombra café",
        ],
        "La poda del sombrío (incluido descope en especies altas) mantiene entrada de luz, reduce humedad excesiva y facilita labores. El objetivo es un equilibrio de sombra útil sin cerrar el lote.",
        ["cafe", "poda", "sombrio", "descope", "luz", "humedad"],
    ),
    e(
        "general",
        [
            "Checklist rápido de manejo de café",
            "Revisión semanal del cafetal",
            "Qué revisar cada semana en café",
            "Lista de control cafetal",
        ],
        "Checklist práctico semanal: 1) estado de broca y roya por lote, 2) frutos maduros/remanentes, 3) drenajes y coberturas, 4) sombra y aireación, 5) avance de secado y humedad del grano, 6) orden e higiene del beneficio.",
        ["cafe", "checklist", "broca", "roya", "secado", "beneficio"],
    ),
    e(
        "general",
        [
            "Checklist pre-cosecha en café",
            "Preparación de cosecha cafetera",
            "Qué alistar antes de cosechar café",
            "Plan de cosecha café finca",
        ],
        "Antes de cosecha: calibra mano de obra para recolectar maduro, define repase, limpia equipos de beneficio, asegura área de secado suficiente y organiza registro por lotes para trazabilidad.",
        ["cafe", "pre-cosecha", "recoleccion", "beneficio", "secado", "lotes"],
    ),
    e(
        "general",
        [
            "Cómo mejorar coherencia de respuestas de la app sobre café",
            "Qué preguntas hacer al agricultor sobre café",
            "Datos mínimos para recomendar en cafetal",
            "Entradas clave para diagnóstico café",
        ],
        "Para recomendaciones más precisas, pide datos mínimos del lote: variedad, edad del cafetal, altitud, clima reciente, % de broca o roya observado, estado de cosecha y método de secado. Con eso se evita consejo genérico.",
        ["cafe", "diagnostico", "variedad", "altitud", "broca", "roya"],
    ),
]


def main() -> None:
    kb = json.loads(KB_PATH.read_text(encoding="utf-8"))
    entries = kb.get("entries", [])
    categories = kb.get("categories", [])

    existing_q = {
        normalize(q)
        for entry in entries
        for q in entry.get("questions", [])
    }

    existing_answer = {
        normalize(entry.get("answer", ""))
        for entry in entries
        if entry.get("answer")
    }

    next_id = max((entry.get("id", 0) for entry in entries), default=0) + 1
    added = 0

    for item in COFFEE_ENTRIES:
        unique_questions: list[str] = []
        for q in item["questions"]:
            nq = normalize(q)
            if nq and nq not in existing_q:
                unique_questions.append(q)
        if not unique_questions:
            continue

        n_answer = normalize(item["answer"])
        if n_answer in existing_answer:
            # Evitar duplicar bloques completos de respuesta
            continue

        category = item["category"]
        if category not in categories:
            categories.append(category)

        entry = {
            "id": next_id,
            "category": category,
            "questions": unique_questions,
            "answer": item["answer"],
            "keywords": item["keywords"],
        }

        entries.append(entry)
        for q in unique_questions:
            existing_q.add(normalize(q))
        existing_answer.add(n_answer)

        next_id += 1
        added += 1

    kb["entries"] = entries
    kb["categories"] = categories
    KB_PATH.write_text(json.dumps(kb, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total_q = sum(len(e.get("questions", [])) for e in entries)
    print(f"Entradas nuevas de café: {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()
