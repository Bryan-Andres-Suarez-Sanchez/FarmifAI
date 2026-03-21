#!/usr/bin/env python3
"""
Enriquecimiento ULTRA de la KB con contenido SOLO de cafe.
Objetivo: cubrir consultas abiertas de caficultura con mayor amplitud tematica.
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


def card(category: str, questions: list[str], answer: str, keywords: list[str]) -> dict[str, Any]:
    return {
        "category": category,
        "questions": questions,
        "answer": answer,
        "keywords": keywords,
    }


CARDS: list[dict[str, Any]] = []

# ---------------------------------------------------------------------------
# 1) Entradas manuales de alto valor conversacional
# ---------------------------------------------------------------------------
CARDS.extend([
    card(
        "general",
        [
            "Quiero empezar en cafe pero no se por donde arrancar",
            "Plan inicial para productor nuevo de cafe",
            "Como comenzar un proyecto de cafe desde cero",
            "Primeros pasos para una finca cafetera",
        ],
        "Empieza con tres decisiones base: zonificacion del lote (clima y suelo), variedad objetivo y plan de manejo anual por lotes. Luego arma un cronograma simple de vivero, establecimiento, nutricion, sanidad, cosecha y registro de costos para no improvisar durante el ciclo.",
        ["cafe", "plan", "inicio", "lote", "variedad", "cronograma"],
    ),
    card(
        "general",
        [
            "Como organizar una finca cafetera para tomar mejores decisiones",
            "Gestion tecnica de una finca de cafe",
            "Orden de trabajo en la finca de cafe",
            "Como administrar mejor el cafetal",
        ],
        "Organiza la finca por lotes homogeneos y define indicadores por lote: productividad, costo por kilo, incidencia sanitaria y calidad. Cuando cada lote tiene historial, las decisiones de fertilizar, podar o renovar se vuelven mas precisas y rentables.",
        ["cafe", "gestion", "lotes", "indicadores", "rentabilidad"],
    ),
    card(
        "general",
        [
            "Como hacer un diagnostico rapido de un lote de cafe",
            "Revision tecnica rapida de cafetal",
            "Chequeo de campo para cafe",
            "Evaluacion inicial de un lote de cafe",
        ],
        "En una visita rapida revisa cinco frentes: vigor vegetativo, carga de frutos, estado de suelo-cobertura, focos de plagas/enfermedades y uniformidad del lote. Con ese barrido priorizas acciones para las siguientes dos semanas y evitas dispersar recursos.",
        ["cafe", "diagnostico", "lote", "vigor", "priorizacion"],
    ),
    card(
        "cultivo",
        [
            "Como mejorar la uniformidad del cafetal",
            "Que hacer cuando un lote de cafe esta muy disparejo",
            "Uniformidad de plantas en cafe",
            "Como emparejar un lote de cafe",
        ],
        "La uniformidad mejora cuando corriges fallas temprano, nivelas manejo nutricional por edad y eliminas competencia excesiva de arvenses. Tambien ayuda separar sublotes con problemas distintos para que cada bloque tenga un manejo propio.",
        ["cafe", "uniformidad", "sublotes", "nutricion", "arvenses"],
    ),
    card(
        "cultivo",
        [
            "Como evitar alternancia de produccion en cafe",
            "Cafe con anos muy altos y anos muy bajos",
            "Bianualidad en cafe como manejarla",
            "Como estabilizar cosechas de cafe",
        ],
        "Para reducir alternancia, combina poda oportuna, nutricion ajustada a carga, control sanitario temprano y cosecha completa con repase. La idea es evitar agotamiento del arbol en anos de alta carga y sostener brotacion productiva para el siguiente ciclo.",
        ["cafe", "bianualidad", "poda", "nutricion", "cosecha"],
    ),
    card(
        "cultivo",
        [
            "Como leer el estado fenologico del cafe",
            "Etapas fenologicas del cafeto para manejo",
            "Fenologia del cafe aplicada",
            "Manejo segun etapa del cafe",
        ],
        "Usa la fenologia para calendarizar labores: prefloracion, floracion, llenado de fruto y poscosecha. Cuando sincronizas fertilizacion, control sanitario y manejo de agua con esas etapas, el cultivo responde mejor que con fechas fijas.",
        ["cafe", "fenologia", "floracion", "llenado", "poscosecha"],
    ),
    card(
        "siembra",
        [
            "Como calcular cuantas plantas de cafe caben por hectarea",
            "Densidad de siembra de cafe por hectarea",
            "Formula para densidad de cafe",
            "Numero de plantas en un cafetal",
        ],
        "La densidad aproximada se calcula dividiendo 10.000 m2 por el area ocupada por planta (distancia entre surcos por distancia entre plantas). Luego ajusta por pendientes, caminos y areas de proteccion para no sobreestimar el numero real de plantas.",
        ["cafe", "densidad", "hectarea", "siembra", "plantas"],
    ),
    card(
        "siembra",
        [
            "Conviene sembrar cafe en curvas a nivel",
            "Trazado en contorno para cafe",
            "Siembra de cafe en ladera",
            "Curvas de nivel en cafetal",
        ],
        "En laderas, sembrar siguiendo curvas a nivel reduce velocidad del escurrimiento y protege suelo. Esta practica, junto con barreras vivas y cobertura, baja erosion y mejora infiltracion de agua en el perfil.",
        ["cafe", "curvas", "ladera", "erosion", "infiltracion"],
    ),
    card(
        "cultivo",
        [
            "Como manejar barreras vivas en cafe",
            "Barreras para conservar suelo en cafetal",
            "Que barreras usar en laderas con cafe",
            "Control de erosion con barreras vivas en cafe",
        ],
        "Las barreras vivas deben ubicarse en linea de contorno y mantenerse podadas para no sombrear en exceso. Su funcion es frenar sedimentos, mejorar infiltracion y proteger fertilidad en lotes con pendiente.",
        ["cafe", "barreras", "suelo", "pendiente", "conservacion"],
    ),
    card(
        "cultivo",
        [
            "Como manejar cobertura del suelo en cafe",
            "Coberturas nobles para cafetal",
            "Suelo desnudo en cafe que hacer",
            "Manejo de cobertura viva en cafe",
        ],
        "Mantener cobertura vegetal regulada reduce erosion, conserva humedad y mejora actividad biologica del suelo. Evita dejar suelo desnudo por periodos largos, especialmente en laderas y epocas de lluvia intensa.",
        ["cafe", "cobertura", "suelo", "humedad", "erosion"],
    ),
    card(
        "riego",
        [
            "Como decidir si debo regar el cafe",
            "Criterios para riego en cafetal",
            "Cuando aplicar riego suplementario en cafe",
            "Riego de apoyo en cultivo de cafe",
        ],
        "El riego suplementario se justifica cuando hay deficit hidrico en etapas sensibles como floracion y llenado. Decide con observacion de humedad en suelo, pronostico local y estado de la planta, no solo por calendario.",
        ["cafe", "riego", "deficit", "floracion", "llenado"],
    ),
    card(
        "riego",
        [
            "Que tipo de riego conviene en cafe",
            "Riego por goteo en cafetal",
            "Microaspersión para cafe",
            "Comparacion de sistemas de riego en cafe",
        ],
        "En muchas fincas cafeteras, goteo o microaspersión permiten uso mas eficiente del agua frente a sistemas menos controlados. La eleccion depende de topografia, disponibilidad de agua, costo de operacion y capacidad de mantenimiento.",
        ["cafe", "riego", "goteo", "microaspersion", "eficiencia"],
    ),
    card(
        "fertilizacion",
        [
            "Como planear fertilizacion por lotes en cafe",
            "Fertilizacion diferenciada por lote cafetal",
            "Plan nutricional por ambientes en cafe",
            "Nutricion variable en cafe",
        ],
        "No todos los lotes requieren la misma formula. Define fertilizacion por ambiente productivo, edad y analisis de suelo/hoja para evitar subdosificar lotes exigentes o sobredosificar lotes de bajo potencial.",
        ["cafe", "fertilizacion", "lotes", "analisis", "nutricion"],
    ),
    card(
        "fertilizacion",
        [
            "Como fraccionar fertilizante en cafe",
            "Aplicaciones divididas de abono en cafetal",
            "Fraccionamiento de NPK en cafe",
            "Mejor estrategia de aplicacion de fertilizante en cafe",
        ],
        "Fraccionar aplicaciones durante el ano mejora eficiencia y reduce perdidas por lavado o volatilizacion. El fraccionamiento debe coincidir con lluvias efectivas y etapas de mayor demanda fisiologica del cultivo.",
        ["cafe", "fertilizante", "fraccionamiento", "lluvia", "eficiencia"],
    ),
    card(
        "fertilizacion",
        [
            "Como usar bioinsumos en cafe sin perder productividad",
            "Biofertilizantes en cafetal",
            "Integrar organico y mineral en cafe",
            "Estrategia de nutricion mixta para cafe",
        ],
        "Los bioinsumos funcionan mejor como complemento de un plan tecnico, no como sustituto ciego de toda la nutricion mineral. Combina fuentes organicas y minerales segun analisis para sostener rendimiento y salud de suelo.",
        ["cafe", "bioinsumos", "nutricion", "organico", "mineral"],
    ),
    card(
        "plagas",
        [
            "Como priorizar control de plagas en cafe",
            "Manejo integrado de plagas en cafetal",
            "Plan MIP para cafe",
            "Control preventivo de plagas en cafe",
        ],
        "Prioriza monitoreo periodico, umbrales de accion, manejo cultural y control biologico antes de medidas mas agresivas. Un MIP bien ejecutado reduce costos y evita dependencia de aplicaciones indiscriminadas.",
        ["cafe", "plagas", "mip", "monitoreo", "control"],
    ),
    card(
        "enfermedad",
        [
            "Como prevenir enfermedades en cafe de forma integral",
            "Manejo sanitario preventivo del cafetal",
            "Plan de sanidad en cafe",
            "Prevencion de hongos y bacterias en cafe",
        ],
        "La prevencion combina variedad adaptada, aireacion por poda, nutricion balanceada, manejo de sombra y vigilancia temprana de focos. Corregir predisponentes del lote suele ser mas efectivo que reaccionar tarde.",
        ["cafe", "enfermedad", "prevencion", "poda", "sombra"],
    ),
    card(
        "diagnostico",
        [
            "Como diferenciar problema nutricional de enfermedad en cafe",
            "Deficiencia o patogeno en cafeto",
            "Diagnostico diferencial en hojas de cafe",
            "Como saber si es plaga o nutricion en cafe",
        ],
        "Observa patron del dano: las deficiencias suelen verse mas uniformes por lote o estrato, mientras muchas enfermedades forman focos y lesiones tipicas. Para decidir bien, combina inspeccion de campo con historial de manejo y clima reciente.",
        ["cafe", "diagnostico", "deficiencia", "enfermedad", "patron"],
    ),
    card(
        "diagnostico",
        [
            "Que datos debo tomar cuando aparece un problema en cafe",
            "Ficha de diagnostico para cafetal",
            "Registro tecnico de incidencias en cafe",
            "Informacion minima para diagnosticar cafe",
        ],
        "Cuando aparezca un problema registra fecha, lote, edad de plantas, sintomas, distribucion del dano, clima reciente y labores aplicadas. Ese contexto reduce errores de diagnostico y acelera decisiones acertadas.",
        ["cafe", "diagnostico", "registro", "lote", "sintomas"],
    ),
    card(
        "cosecha",
        [
            "Como mejorar la seleccion de cereza madura en cafe",
            "Cosecha selectiva de cafe",
            "Recolectar solo cereza madura en cafetal",
            "Madurez ideal para cosecha de cafe",
        ],
        "Entrena recolectores para separar maduros de verdes y sobremaduros, y programa pases segun ritmo de maduracion del lote. Mejor seleccion en cosecha suele traducirse en mejor taza y menos defectos en poscosecha.",
        ["cafe", "cosecha", "madurez", "taza", "defectos"],
    ),
    card(
        "cosecha",
        [
            "Como reducir perdidas de cafe durante cosecha",
            "Perdidas en recoleccion de cafe",
            "Eficiencia de cosecha en cafetal",
            "Como mejorar rendimiento de recolecta de cafe",
        ],
        "Reduce perdidas con rutas de recoleccion claras, canastillas limpias, repase oportuno y control de derrame de frutos. Medir kilos cosechados por jornada y calidad entregada ayuda a ajustar el proceso.",
        ["cafe", "cosecha", "perdidas", "recoleccion", "calidad"],
    ),
    card(
        "cosecha",
        [
            "Como manejar fermentacion para no perder calidad en cafe",
            "Control de fermentacion de cafe",
            "Fermentacion limpia en beneficio de cafe",
            "Que cuidar en fermentacion de cafe",
        ],
        "Controla tiempo, temperatura, higiene y uniformidad de lote durante fermentacion. La meta es retirar mucilago sin generar sabores avinagrados o defectos por sobrefermentacion.",
        ["cafe", "fermentacion", "beneficio", "higiene", "calidad"],
    ),
    card(
        "cosecha",
        [
            "Como secar cafe pergamino de forma segura",
            "Secado correcto de cafe",
            "Secado en marquesina de cafe",
            "Buenas practicas de secado para cafe",
        ],
        "El secado debe ser gradual y uniforme hasta humedad comercial segura, evitando sobrecalentamiento y rehumectacion nocturna. Un secado estable conserva calidad fisica y sensorial del pergamino.",
        ["cafe", "secado", "pergamino", "humedad", "calidad"],
    ),
    card(
        "cosecha",
        [
            "Como almacenar cafe pergamino sin que se dañe",
            "Bodega para cafe pergamino",
            "Almacenamiento seguro de cafe",
            "Condiciones de bodega para cafe",
        ],
        "Guarda el pergamino en empaques limpios, sobre estibas y en bodega seca, ventilada y sin olores extraños. Evita contacto directo con piso o pared para reducir riesgo de humedad y contaminacion.",
        ["cafe", "almacenamiento", "bodega", "pergamino", "humedad"],
    ),
    card(
        "cosecha",
        [
            "Como preparar lotes de cafe para vender con mejor precio",
            "Presentacion comercial del cafe",
            "Requisitos basicos para comercializar cafe",
            "Como vender cafe con trazabilidad",
        ],
        "Para negociar mejor, entrega lotes trazables, con humedad controlada, buen factor de rendimiento y consistencia de calidad. Un registro claro de origen y proceso da confianza al comprador y mejora margen.",
        ["cafe", "venta", "trazabilidad", "humedad", "precio"],
    ),
    card(
        "general",
        [
            "Como adaptar el cafetal al cambio climatico",
            "Estrategias de resiliencia climatica en cafe",
            "Cafe frente a clima variable",
            "Manejo de riesgo climatico en caficultura",
        ],
        "La adaptacion combina sombra regulada, suelos con mas materia organica, cosecha de agua, variedades adaptadas y monitoreo meteorologico local. La resiliencia climatica se construye con varias practicas simultaneas, no con una sola medida.",
        ["cafe", "clima", "resiliencia", "sombra", "agua"],
    ),
    card(
        "general",
        [
            "Como bajar costos sin bajar calidad en cafe",
            "Eficiencia de costos en cafetal",
            "Reducir gastos en produccion de cafe",
            "Mejorar margen en finca cafetera",
        ],
        "Baja costos atacando ineficiencias: fertilizacion no ajustada, controles tardios, reprocesos en beneficio y perdidas en cosecha. Cuando ordenas esas fugas, mejora el margen sin comprometer calidad.",
        ["cafe", "costos", "eficiencia", "calidad", "margen"],
    ),
    card(
        "general",
        [
            "Como entrenar al equipo para mejorar resultados en cafe",
            "Capacitacion de recolectores y operarios en cafe",
            "Formacion del personal en finca cafetera",
            "Mejorar desempeno del equipo en cafetal",
        ],
        "Estandariza procedimientos en cosecha, beneficio, secado y registro de datos. Entrenar al equipo con criterios claros de calidad y tiempos reduce errores operativos y mejora consistencia del lote.",
        ["cafe", "equipo", "capacitacion", "calidad", "procesos"],
    ),
    card(
        "general",
        [
            "Como preparar una visita tecnica de cafe con productores",
            "Guion de visita de campo para cafetal",
            "Actividad tecnica en finca de cafe",
            "Checklist para asesoria en cafe",
        ],
        "Llega con un guion corto: diagnostico del lote, pregunta prioritaria del productor, recomendacion accionable y cierre con compromiso medible. Si dejas una accion concreta por visita, la adopcion mejora notablemente.",
        ["cafe", "visita", "diagnostico", "adopcion", "acciones"],
    ),
])


# ---------------------------------------------------------------------------
# 2) Deficiencias nutricionales especificas
# ---------------------------------------------------------------------------
NUTRIENTS = [
    {
        "name": "nitrogeno",
        "symbol": "N",
        "signal": "hojas viejas palidas y menor crecimiento vegetativo",
        "cause": "baja disponibilidad de N o perdidas por lavado",
        "action": "ajustar plan nitrogenado fraccionado y reforzar materia organica",
    },
    {
        "name": "fosforo",
        "symbol": "P",
        "signal": "desarrollo radicular pobre y floracion debil",
        "cause": "fijacion de P en suelos acidos o baja reposicion",
        "action": "corregir acidez y aplicar fuentes fosforadas segun analisis",
    },
    {
        "name": "potasio",
        "symbol": "K",
        "signal": "bordes necróticos en hojas y menor llenado de fruto",
        "cause": "alta extraccion por cosecha sin reposicion suficiente",
        "action": "fortalecer fertilizacion potasica y balance hidrico",
    },
    {
        "name": "calcio",
        "symbol": "Ca",
        "signal": "tejidos nuevos debiles y raices poco activas",
        "cause": "acidez alta con baja saturacion de bases",
        "action": "usar enmiendas calcicas y mejorar estructura del suelo",
    },
    {
        "name": "magnesio",
        "symbol": "Mg",
        "signal": "clorosis internerval en hojas maduras",
        "cause": "desbalance cationico o baja disponibilidad de Mg",
        "action": "incorporar fuentes magnesicas y revisar balance con K y Ca",
    },
    {
        "name": "azufre",
        "symbol": "S",
        "signal": "amarillamiento en hojas jovenes y bajo vigor",
        "cause": "suelos pobres en materia organica o fertilizacion incompleta",
        "action": "integrar fuentes con azufre y reciclar residuos organicos",
    },
    {
        "name": "boro",
        "symbol": "B",
        "signal": "deformacion de brotes y baja fecundacion floral",
        "cause": "deficit de micronutriente en suelos lavados",
        "action": "corregir con dosis bajas y precisas evitando excesos",
    },
    {
        "name": "zinc",
        "symbol": "Zn",
        "signal": "hojas pequenas y entrenudos cortos",
        "cause": "pH fuera de rango o disponibilidad limitada",
        "action": "aplicar fuentes de Zn segun diagnostico foliar",
    },
    {
        "name": "hierro",
        "symbol": "Fe",
        "signal": "clorosis en hojas jovenes con nervaduras mas verdes",
        "cause": "bloqueo por condiciones de suelo y raiz estresada",
        "action": "mejorar salud radicular y ajustar manejo de pH",
    },
    {
        "name": "manganeso",
        "symbol": "Mn",
        "signal": "moteado clorotico y menor actividad fotosintetica",
        "cause": "desbalance nutricional en suelos acidos o muy intervenidos",
        "action": "corregir balance de nutrientes y pH del lote",
    },
    {
        "name": "cobre",
        "symbol": "Cu",
        "signal": "brotacion debil y menor lignificacion de tejidos",
        "cause": "baja disponibilidad de Cu en suelo",
        "action": "suplementar micronutriente con criterio tecnico",
    },
    {
        "name": "molibdeno",
        "symbol": "Mo",
        "signal": "ineficiencia en metabolismo del nitrogeno",
        "cause": "deficit puntual en suelos con limitantes quimicas",
        "action": "ajustar micronutricion de precision segun analisis",
    },
]

for n in NUTRIENTS:
    CARDS.append(
        card(
            "fertilizacion",
            [
                f"Deficiencia de {n['name']} en cafe",
                f"Sintomas por falta de {n['symbol']} en cafeto",
                f"Como corregir carencia de {n['name']} en cafe",
                f"Que pasa si falta {n['name']} en cultivo de cafe",
            ],
            f"Cuando falta {n['name']} ({n['symbol']}) en cafe suele observarse {n['signal']}. Normalmente el problema se asocia a {n['cause']}. La correccion tecnica es {n['action']} y verificar respuesta del lote con seguimiento.",
            ["cafe", "deficiencia", n["name"], n["symbol"].lower(), "nutricion", "fertilizacion"],
        )
    )


# ---------------------------------------------------------------------------
# 3) Plagas especificas del cafeto
# ---------------------------------------------------------------------------
PESTS = [
    {
        "name": "minador de la hoja",
        "alias": "Leucoptera coffeella",
        "signal": "galerias translúcidas en hojas y reduccion de area fotosintetica",
        "risk": "epocas secas y lotes estresados",
        "management": "monitoreo frecuente, conservacion de enemigos naturales y manejo equilibrado de sombra y nutricion",
    },
    {
        "name": "cochinilla de la raiz",
        "alias": "cochinillas radiculares",
        "signal": "decaimiento progresivo, amarillamiento y raices debilitadas",
        "risk": "suelos compactados y plantas con bajo vigor",
        "management": "mejorar suelo, revisar hormigas asociadas y renovar focos severos",
    },
    {
        "name": "cochinilla verde",
        "alias": "escama blanda",
        "signal": "melaza, fumagina y debilitamiento de brotes",
        "risk": "sombras densas y baja ventilacion",
        "management": "regular sombra, podar para aireacion y fortalecer control biologico",
    },
    {
        "name": "escamas",
        "alias": "insectos chupadores",
        "signal": "debilidad general y presencia de costras en tallos/ramas",
        "risk": "plantas sombreadas y manejo nutricional deficiente",
        "management": "podas sanitarias, limpieza de focos y monitoreo continuo",
    },
    {
        "name": "trips",
        "alias": "Thysanoptera",
        "signal": "raspado en tejidos tiernos y deformacion foliar",
        "risk": "periodos secos y brotacion tierna abundante",
        "management": "monitorear brotes, reducir estres hidrico y preservar fauna benefica",
    },
    {
        "name": "acaro rojo",
        "alias": "acaros fitofagos",
        "signal": "bronceado foliar y perdida de vigor",
        "risk": "ambientes secos con polvo y baja humedad",
        "management": "disminuir estres, mejorar cobertura y monitorear en focos tempranos",
    },
    {
        "name": "arañita roja",
        "alias": "Tetranychus spp.",
        "signal": "punteado clorotico y telarañas finas en hojas",
        "risk": "sequias prolongadas",
        "management": "manejo de microclima, monitoreo y acciones tempranas por foco",
    },
    {
        "name": "nematodos agalladores",
        "alias": "Meloidogyne spp.",
        "signal": "agallas en raices y crecimiento restringido",
        "risk": "suelos infestados y material vegetal sin control",
        "management": "uso de material sano, mejoramiento de suelo y rotacion de practicas preventivas",
    },
    {
        "name": "hormiga arriera",
        "alias": "Atta spp.",
        "signal": "defoliacion rapida en plantas jovenes",
        "risk": "presencia de hormigueros activos cerca del lote",
        "management": "deteccion temprana de nidos y manejo integrado del foco",
    },
    {
        "name": "barrenador del tallo",
        "alias": "coleopteros barrenadores",
        "signal": "perforaciones en tallo y debilitamiento estructural",
        "risk": "arboles estresados o con heridas",
        "management": "podas higienicas, eliminacion de tejido afectado y vigilancia de focos",
    },
    {
        "name": "chicharritas",
        "alias": "cigarrillas",
        "signal": "succion de savia y reduccion del vigor",
        "risk": "malezas hospederas y desbalance ecologico",
        "management": "manejo de arvenses, monitoreo y equilibrio biologico del lote",
    },
    {
        "name": "orugas defoliadoras",
        "alias": "larvas comedoras de hojas",
        "signal": "consumo de follaje y perdida de area fotosintetica",
        "risk": "focos no detectados en etapas iniciales",
        "management": "inspeccion de follaje y control temprano de focos",
    },
    {
        "name": "picudo de ramas",
        "alias": "curculionidos",
        "signal": "danos en brotes y ramas jovenes",
        "risk": "lotes con residuos de poda sin manejo",
        "management": "higiene del lote, poda oportuna y seguimiento periodico",
    },
    {
        "name": "broca remanente postcosecha",
        "alias": "focos residuales de broca",
        "signal": "frutos secos o sobremaduros infestados en planta y suelo",
        "risk": "cosecha incompleta y falta de repase",
        "management": "repase riguroso, recoleccion sanitaria y corte de ciclo en beneficio",
    },
]

for p in PESTS:
    CARDS.append(
        card(
            "plagas",
            [
                f"Como manejar {p['name']} en cafe",
                f"Sintomas de {p['name']} en cafeto",
                f"Control de {p['alias']} en cafetal",
                f"Que hacer ante {p['name']} en cultivo de cafe",
            ],
            f"{p['name'].capitalize()} ({p['alias']}) puede causar {p['signal']}. El riesgo aumenta en {p['risk']}. El manejo recomendado es {p['management']} dentro de un enfoque integrado por lotes.",
            ["cafe", "plaga", p["name"], p["alias"], "monitoreo", "manejo"],
        )
    )


# ---------------------------------------------------------------------------
# 4) Enfermedades especificas del cafeto
# ---------------------------------------------------------------------------
DISEASES = [
    {
        "name": "roya del cafe",
        "agent": "Hemileia vastatrix",
        "signal": "manchas amarillas en haz y polvo anaranjado en enves",
        "context": "alta humedad y tejidos susceptibles",
        "management": "variedades adaptadas, nutricion balanceada, regulacion de sombra y control oportuno por focos",
    },
    {
        "name": "cercospora",
        "agent": "Cercospora coffeicola",
        "signal": "manchas de hierro en hojas y frutos, con deterioro de calidad",
        "context": "estres nutricional y exposicion ambiental desfavorable",
        "management": "fortalecer nutricion, reducir estres y vigilar focos en periodos criticos",
    },
    {
        "name": "ojo de gallo",
        "agent": "Mycena citricolor",
        "signal": "lesiones circulares en hojas con defoliacion",
        "context": "ambientes sombreados con alta humedad",
        "management": "regular sombra, mejorar aireacion y retirar focos severos",
    },
    {
        "name": "antracnosis",
        "agent": "Colletotrichum spp.",
        "signal": "lesiones oscuras en tejidos tiernos y frutos",
        "context": "lluvias frecuentes y tejido estresado",
        "management": "higiene de poda, manejo de humedad y vigilancia temprana",
    },
    {
        "name": "mal rosado",
        "agent": "Corticium salmonicolor",
        "signal": "costra rosada en ramas con secamiento progresivo",
        "context": "humedad alta y copas cerradas",
        "management": "podas sanitarias, apertura de copa y eliminacion de material afectado",
    },
    {
        "name": "llaga negra",
        "agent": "patogenos de suelo",
        "signal": "decaimiento, muerte de raices y perdida de anclaje",
        "context": "suelos encharcables y drenaje deficiente",
        "management": "mejorar drenaje, sanear focos y renovar plantas comprometidas",
    },
    {
        "name": "llaga estrellada",
        "agent": "patogenos radiculares",
        "signal": "marchitez y muerte regresiva en focos",
        "context": "suelos con exceso de humedad y compactacion",
        "management": "manejo de suelo, drenaje y aislamiento del foco",
    },
    {
        "name": "muerte descendente",
        "agent": "complejo fungico",
        "signal": "secamiento de puntas hacia ramas principales",
        "context": "estres acumulado y heridas de poda mal manejadas",
        "management": "poda limpia, desinfeccion de herramientas y fortalecimiento del vigor",
    },
    {
        "name": "pudricion de raiz",
        "agent": "hongos de suelo",
        "signal": "amarillamiento, marchitez y perdida de raices funcionales",
        "context": "drenaje pobre y materia organica mal descompuesta",
        "management": "recuperar estructura del suelo, mejorar drenaje y renovar donde sea necesario",
    },
    {
        "name": "mancha aureolada",
        "agent": "bacteriosis",
        "signal": "lesiones foliares con halo clorotico",
        "context": "heridas y condiciones de alta humedad",
        "management": "manejo preventivo, higiene de labores y reduccion de estres",
    },
    {
        "name": "necrosis de ramas",
        "agent": "complejo de hongos oportunistas",
        "signal": "secamiento localizado de ramas productivas",
        "context": "sombras densas y manejo tardio de focos",
        "management": "poda sanitaria y mejora de ventilacion del lote",
    },
    {
        "name": "moho en pergamino",
        "agent": "hongos de almacenamiento",
        "signal": "olor a humedad y deterioro fisico del grano",
        "context": "secado insuficiente o bodega humeda",
        "management": "secado uniforme y almacenamiento en condiciones secas y limpias",
    },
    {
        "name": "fermentacion no deseada",
        "agent": "microflora desbalanceada",
        "signal": "olores avinagrados y taza inconsistente",
        "context": "tiempos excesivos o higiene deficiente en beneficio",
        "management": "control estricto de tiempos, limpieza y separacion de lotes",
    },
    {
        "name": "contaminacion cruzada en beneficio",
        "agent": "mezcla de lotes y superficies sucias",
        "signal": "defectos repetitivos en taza y merma de calidad",
        "context": "flujo de proceso desordenado",
        "management": "estandarizar limpieza, separar lotes y trazabilidad por proceso",
    },
]

for d in DISEASES:
    CARDS.append(
        card(
            "enfermedad",
            [
                f"Como identificar {d['name']} en cafe",
                f"Sintomas de {d['name']} en cafeto",
                f"Manejo de {d['agent']} en cafetal",
                f"Que hacer frente a {d['name']} en cultivo de cafe",
            ],
            f"{d['name'].capitalize()} ({d['agent']}) suele mostrar {d['signal']}. Se favorece con {d['context']}. La estrategia recomendada es {d['management']} para reducir incidencia y dano productivo.",
            ["cafe", "enfermedad", d["name"], d["agent"], "sanidad", "manejo"],
        )
    )


# ---------------------------------------------------------------------------
# 5) Variedades y material vegetal
# ---------------------------------------------------------------------------
VARIETIES = [
    {
        "name": "Castillo",
        "profile": "material ampliamente usado por su enfoque de resistencia y adaptacion",
        "care": "ajustar nutricion, sombra y carga para sostener productividad",
    },
    {
        "name": "Cenicafe 1",
        "profile": "variedad mejorada para ambientes cafeteros con manejo tecnico",
        "care": "acompanar con buena renovacion y control sanitario preventivo",
    },
    {
        "name": "Colombia",
        "profile": "material historico de referencia en caficultura colombiana",
        "care": "mantener vigor de lote con podas y nutricion racional",
    },
    {
        "name": "Tabi",
        "profile": "variedad de porte mas alto con potencial de calidad y adaptacion",
        "care": "planear densidad y arquitectura para facilitar cosecha",
    },
    {
        "name": "Caturra",
        "profile": "material de porte bajo apreciado en sistemas de calidad",
        "care": "fortalecer manejo sanitario por su sensibilidad en ciertos ambientes",
    },
    {
        "name": "Bourbon",
        "profile": "linaje tradicional valorado por perfil de taza",
        "care": "requiere manejo fino de nutricion y sanidad para estabilidad",
    },
    {
        "name": "Typica",
        "profile": "material tradicional con importancia historica en cafe de calidad",
        "care": "necesita manejo agronomico cuidadoso y renovacion planificada",
    },
    {
        "name": "Geisha",
        "profile": "variedad orientada a nichos de alto valor sensorial",
        "care": "demanda trazabilidad estricta y poscosecha muy controlada",
    },
    {
        "name": "Catimor",
        "profile": "grupo varietal con enfoque de adaptacion y productividad",
        "care": "debe evaluarse por comportamiento local de calidad y sanidad",
    },
    {
        "name": "SL28",
        "profile": "material reconocido en ciertos origenes por calidad de taza",
        "care": "requiere ambiente adecuado y manejo agronomico exigente",
    },
    {
        "name": "Pacamara",
        "profile": "variedad usada en segmentos de cafe especial",
        "care": "beneficio y secado deben ser muy consistentes para sostener valor",
    },
    {
        "name": "Maragogipe",
        "profile": "material de grano grande con manejo particular",
        "care": "conviene planificar densidad y nutricion de acuerdo con vigor y mercado",
    },
]

for v in VARIETIES:
    CARDS.append(
        card(
            "cultivo",
            [
                f"Como manejar variedad {v['name']} en cafe",
                f"Recomendaciones tecnicas para cafe {v['name']}",
                f"Que considerar antes de sembrar {v['name']}",
                f"Perfil agronomico de {v['name']} en cafetal",
            ],
            f"{v['name']} es un {v['profile']}. Para que responda bien en campo conviene {v['care']} y validar su desempeno por lote antes de escalar grandes areas.",
            ["cafe", "variedad", v["name"].lower(), "material", "manejo", "calidad"],
        )
    )


# ---------------------------------------------------------------------------
# 6) Defectos de calidad y poscosecha
# ---------------------------------------------------------------------------
QUALITY_DEFECTS = [
    {
        "name": "grano negro",
        "cause": "frutos sobremaduros o mal manejo de secado",
        "impact": "deprecia factor y perfil de taza",
        "prevention": "seleccion de cereza y secado uniforme",
    },
    {
        "name": "grano agrio",
        "cause": "fermentacion descontrolada",
        "impact": "aporta acidez defectuosa en taza",
        "prevention": "control de tiempos e higiene de proceso",
    },
    {
        "name": "grano mohoso",
        "cause": "almacenamiento con humedad elevada",
        "impact": "genera olores y sabores no deseados",
        "prevention": "bodega seca y control de humedad del pergamino",
    },
    {
        "name": "pasilla por broca",
        "cause": "infestacion de broca no controlada",
        "impact": "reduce rendimiento y precio de venta",
        "prevention": "manejo integrado de broca y cosecha completa",
    },
    {
        "name": "grano vinagre",
        "cause": "sobrefermentacion",
        "impact": "deja notas avinagradas en taza",
        "prevention": "seguimiento estricto de fermentacion",
    },
    {
        "name": "olor a humo",
        "cause": "secado con exposicion a humo o combustibles",
        "impact": "contamina el perfil sensorial",
        "prevention": "secar en ambientes limpios y sin humo",
    },
    {
        "name": "grano reposado",
        "cause": "almacenamiento prolongado sin condiciones adecuadas",
        "impact": "disminuye frescura y expresion aromatica",
        "prevention": "rotacion de inventario y control ambiental",
    },
    {
        "name": "quaker",
        "cause": "recoleccion de frutos inmaduros",
        "impact": "tostion dispareja y menor calidad en taza",
        "prevention": "cosecha selectiva de maduros",
    },
    {
        "name": "grano mordido",
        "cause": "danos mecanicos o manipulacion brusca",
        "impact": "aumenta defectos fisicos",
        "prevention": "mejorar manejo en despulpado y transporte interno",
    },
    {
        "name": "contaminacion por olores",
        "cause": "almacenamiento junto a quimicos o combustibles",
        "impact": "transfiere olores extraños al cafe",
        "prevention": "separar bodega de fuentes de olor",
    },
    {
        "name": "secado disparejo",
        "cause": "capas muy gruesas o volteo insuficiente",
        "impact": "heterogeneidad de humedad y defectos",
        "prevention": "capa controlada y volteo programado",
    },
    {
        "name": "sobresecado",
        "cause": "exposicion excesiva a calor",
        "impact": "fragilidad del grano y perdida de calidad",
        "prevention": "monitorear humedad objetivo y temperatura",
    },
]

for qd in QUALITY_DEFECTS:
    CARDS.append(
        card(
            "cosecha",
            [
                f"Que es {qd['name']} en cafe",
                f"Como evitar {qd['name']} en cafetal",
                f"Causa de {qd['name']} en poscosecha de cafe",
                f"Impacto de {qd['name']} en calidad del cafe",
            ],
            f"El defecto {qd['name']} suele originarse por {qd['cause']}. Este problema {qd['impact']}. Para prevenirlo conviene {qd['prevention']} y mantener control del proceso por lotes.",
            ["cafe", "calidad", "defecto", qd["name"], "poscosecha", "taza"],
        )
    )


# ---------------------------------------------------------------------------
# 7) Temas avanzados de manejo y mercado
# ---------------------------------------------------------------------------
ADVANCED_TOPICS = [
    (
        "general",
        "Como construir un calendario anual de labores en cafe",
        [
            "Cronograma tecnico anual para cafetal",
            "Plan de trabajo por meses en cafe",
            "Organizar labores del cafe durante el ano",
        ],
        "Arma el calendario por fenologia y clima local: podas, nutricion, controles sanitarios, cosecha y poscosecha. Actualizarlo cada trimestre permite responder mejor a cambios de lluvia y presion sanitaria.",
        ["cafe", "calendario", "labores", "fenologia", "clima"],
    ),
    (
        "general",
        "Como definir metas productivas realistas en cafe",
        [
            "Objetivos de rendimiento para cafetal",
            "Metas por lote en cafe",
            "Plan de productividad cafetera",
        ],
        "Define metas por lote basadas en historial real, no en promedios externos. Une meta de produccion con meta de calidad y costo para evaluar rentabilidad completa, no solo volumen.",
        ["cafe", "metas", "productividad", "lotes", "rentabilidad"],
    ),
    (
        "general",
        "Como calcular costo por kilo de cafe producido",
        [
            "Costo unitario del cafe en finca",
            "Como sacar costos reales del cafetal",
            "Analisis economico por kilo de cafe",
        ],
        "Suma costos directos (mano de obra, insumos, cosecha, beneficio) y prorratea costos indirectos por lote. Dividir ese total entre kilos vendidos te muestra donde realmente se gana o se pierde dinero.",
        ["cafe", "costos", "kilo", "economia", "lote"],
    ),
    (
        "general",
        "Como mejorar trazabilidad del cafe desde el lote",
        [
            "Trazabilidad en finca cafetera",
            "Registro de origen por lote de cafe",
            "Control de trazabilidad para vender cafe",
        ],
        "Asigna codigo por lote y registra fecha de cosecha, proceso, secado y almacenamiento. La trazabilidad ordenada mejora control de calidad y abre puertas a mercados que pagan diferenciacion.",
        ["cafe", "trazabilidad", "lote", "calidad", "mercado"],
    ),
    (
        "general",
        "Como preparar lotes para cafe especial",
        [
            "Requisitos para cafe de especialidad",
            "Que cuidar para vender cafe especial",
            "Produccion de cafe con valor agregado",
        ],
        "Para cafe especial necesitas consistencia en madurez, proceso limpio, secado uniforme, almacenamiento correcto y trazabilidad completa. El valor agregado nace de repetir calidad, no de un solo lote bueno.",
        ["cafe", "especialidad", "valor", "calidad", "trazabilidad"],
    ),
    (
        "general",
        "Como evaluar si conviene fermentar diferente un lote de cafe",
        [
            "Experimentar procesos en cafe",
            "Pruebas de fermentacion en finca",
            "Cambios de proceso para cafe especial",
        ],
        "Haz pruebas pequenas con diseno controlado y compara contra un testigo tradicional. Solo escala procesos nuevos cuando muestren mejora repetible en taza sin aumentar defectos ni riesgo operativo.",
        ["cafe", "fermentacion", "pruebas", "taza", "riesgo"],
    ),
    (
        "general",
        "Como reducir huella hidrica en beneficio de cafe",
        [
            "Ahorro de agua en beneficio cafetero",
            "Uso eficiente de agua en poscosecha de cafe",
            "Menor consumo de agua en proceso de cafe",
        ],
        "Reduce huella hidrica optimizando equipos, recirculando cuando sea viable y evitando fugas en el proceso. Medir litros por kilo procesado ayuda a fijar metas reales de eficiencia.",
        ["cafe", "agua", "beneficio", "eficiencia", "sostenibilidad"],
    ),
    (
        "general",
        "Como manejar subproductos del cafe con enfoque de economia circular",
        [
            "Aprovechamiento de pulpa y mucilago de cafe",
            "Economia circular en finca cafetera",
            "Uso de residuos de cafe en la finca",
        ],
        "Pulpa, mucilago y podas pueden convertirse en compost u otras rutas de aprovechamiento con control tecnico. Gestionar subproductos reduce impactos ambientales y puede bajar costo de fertilizacion.",
        ["cafe", "subproductos", "economia", "circular", "compost"],
    ),
    (
        "general",
        "Como preparar la finca de cafe para auditorias o certificaciones",
        [
            "Checklist de certificacion para cafetal",
            "Orden documental para cafe certificado",
            "Buenas practicas para auditoria en cafe",
        ],
        "Mantener registros de trazabilidad, manejo ambiental, seguridad laboral y uso responsable de insumos facilita auditorias. La clave es que la practica en campo coincida con lo documentado.",
        ["cafe", "certificacion", "auditoria", "registros", "buenas practicas"],
    ),
    (
        "general",
        "Como planear renovacion escalonada en una finca cafetera",
        [
            "Renovacion por etapas del cafetal",
            "No detener ingresos al renovar cafe",
            "Estrategia de renovacion gradual en cafe",
        ],
        "Renueva por bloques anuales para evitar caidas bruscas de ingreso. Una renovacion escalonada permite sostener flujo de caja mientras se rejuvenece el parque cafetero.",
        ["cafe", "renovacion", "bloques", "ingresos", "planificacion"],
    ),
    (
        "general",
        "Como tomar decisiones rapidas cuando hay clima extremo en cafe",
        [
            "Respuesta a sequia o lluvias extremas en cafetal",
            "Protocolo de emergencia climatica para cafe",
            "Que priorizar en clima extremo en cafe",
        ],
        "En eventos extremos prioriza supervivencia de plantas y proteccion de suelo: agua, cobertura, drenaje funcional y control sanitario de focos. Luego ajusta nutricion y podas cuando el lote se estabilice.",
        ["cafe", "clima", "emergencia", "suelo", "sanidad"],
    ),
    (
        "general",
        "Como mejorar adopcion de recomendaciones tecnicas en productores de cafe",
        [
            "Extension rural efectiva para cafe",
            "Lograr que productores apliquen recomendaciones en cafe",
            "Metodologia de acompanamiento en caficultura",
        ],
        "La adopcion mejora cuando cada recomendacion se traduce en una accion concreta, simple y medible para la siguiente semana. Hacer seguimiento corto y mostrar resultados visibles acelera confianza del productor.",
        ["cafe", "extension", "adopcion", "seguimiento", "productor"],
    ),
]

for category, q0, qv, ans, kws in ADVANCED_TOPICS:
    CARDS.append(card(category, [q0] + qv, ans, kws))


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
    print(f"Entradas nuevas cafe (ultra): {added}")
    print(f"Total entradas KB: {len(entries)}")
    print(f"Total preguntas KB: {total_q}")


if __name__ == "__main__":
    main()
