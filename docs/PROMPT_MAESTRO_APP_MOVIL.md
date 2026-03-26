# Prompt Maestro Para Crear La App Movil Desde Cero

Este archivo contiene un prompt listo para pegar en un agente de desarrollo para construir la aplicacion desde cero, partiendo del objetivo de producto y no de una base de codigo existente.

## Prompt

```text
Actua como un equipo senior completo en una sola persona: arquitecto de software, ingeniero Android principal, diseniador UX para campo, product manager y especialista en IA aplicada. Tu tarea es crear desde cero una aplicacion movil real, lista para usarse en campo, no una demo.

Quiero que construyas una app movil Android llamada FarmifAI.

OBJETIVO DEL PRODUCTO
Construir una app movil para agricultores, especialmente caficultores en Colombia, que les permita consultar problemas reales del cultivo por voz o texto y recibir respuestas utiles, accionables y confiables, apoyadas en una base de conocimiento tecnica curada y en un LLM gratuito que funcione offline, sintetice la evidencia sin inventar, no copie texto literal y no suene robotico.

EL PROBLEMA QUE DEBE RESOLVER
- El agricultor necesita orientacion tecnica clara mientras esta en campo.
- La respuesta debe ayudar a decidir que hacer hoy.
- La app debe generar confianza, no confusion.
- La app no debe sentirse como un chatbot generico.
- La app no debe responder con texto pegado de documentos.
- La app debe admitir cuando no tiene evidencia suficiente.
- El nucleo del producto debe poder operar sin depender de APIs pagas.

USUARIO OBJETIVO
- Pequenos y medianos productores.
- Tecnicos de extension o asistencia agricola.
- Personas con alfabetizacion digital variable.
- Usuarios en zonas rurales con conectividad limitada o intermitente.

VISION DEL PRODUCTO
La app debe sentirse como un asesor tecnico de confianza en el bolsillo del agricultor:
- cercana,
- clara,
- rapida,
- accionable,
- honesta,
- util en la practica.

SALIDA ESPERADA DEL AGENTE
No quiero solo ideas. Quiero que construyas la aplicacion completa y funcional. Debes entregar:
- arquitectura propuesta,
- codigo implementado,
- estructura de proyecto,
- modelos de datos,
- flujos principales,
- interfaz movil,
- integracion de IA,
- build funcional,
- APK generada,
- y una breve explicacion de decisiones clave.

REQUISITOS NO NEGOCIABLES
1. La app debe ser Android nativa.
2. Debe tener una interfaz simple y usable en campo.
3. Debe permitir consultas por texto.
4. Debe permitir consultas por voz.
5. Debe integrar un LLM gratuito y offline como parte central del producto.
6. Ese LLM debe poder correr localmente en el dispositivo o en un paquete descargable local, sin requerir una API de pago para funcionar.
7. Debe usar una base de conocimiento estructurada como fuente de verdad.
8. El sistema de IA debe responder sintetizando evidencia, no pegando texto literal.
9. No debe haber plantillas roboticas hardcodeadas como salida por defecto.
10. Si falta evidencia, debe decirlo claramente y no inventar.
11. La salida debe ser apta para agricultores reales, no para investigadores.
12. No incluyas analisis de imagen en esta fase.

COMPORTAMIENTO QUE DEBE TENER LA APP

A. CONSULTA POR TEXTO
- El usuario escribe una pregunta natural.
- La app interpreta la intencion.
- Busca evidencia relevante.
- Genera una respuesta corta, clara y accionable.

B. CONSULTA POR VOZ
- El usuario habla.
- La app transcribe la consulta.
- Responde en texto y tambien en voz.
- El flujo debe sentirse comodo para uso manos libres.

C. RETROALIMENTACION RAPIDA
- Cada respuesta debe poder marcarse como:
  - clara o no clara,
  - util o no util,
  - la aplicaria hoy o no.
- Esto debe servir para mejora continua del producto.

COMO DEBEN SER LAS RESPUESTAS
Las respuestas deben parecer dadas por un asesor tecnico humano, no por un sistema.

Deben cumplir estas reglas:
- empezar por la recomendacion principal,
- incluir una razon breve,
- incluir pasos concretos o que observar,
- evitar relleno,
- evitar frases genericas repetidas,
- evitar lenguaje interno como modelo, sistema, RAG, embeddings, base de conocimiento o contexto,
- evitar copiar literalmente el contenido fuente,
- evitar inventar cifras, normas o diagnosticos no respaldados.

TONO DE RESPUESTA
- humano,
- practico,
- cercano,
- profesional,
- breve,
- confiable.

NO QUIERO RESPUESTAS QUE SUENEN ASI
- "Con lo que me cuentas..."
- "Ademas, te puede ayudar..."
- "Si me compartes cultivo, etapa..."
- o cualquier equivalente fijo repetido siempre.

Si faltan datos, el sistema puede pedirlos, pero debe hacerlo de forma contextual a la consulta y sin usar una plantilla universal.

ALCANCE TECNICO

1. PLATAFORMA
- Android nativo
- Kotlin
- Jetpack Compose para UI moderna y mantenible

2. ARQUITECTURA
Propone una arquitectura limpia, mantenible y escalable.
Puedes usar una combinacion como:
- UI en Compose
- ViewModel
- repositorios
- fuentes de datos locales
- motor de retrieval
- motor de generacion
- modulos de voz
- runtime local para LLM
- modulo de feedback

3. BASE DE CONOCIMIENTO
La app debe poder trabajar con una base de conocimiento rica y estructurada, idealmente en JSONL o formato similar, donde cada registro pueda incluir:
- titulo,
- tema,
- aliases,
- retrieval_hints,
- statement,
- accion,
- condicion,
- efecto esperado,
- riesgo si se ignora,
- datos cuantitativos,
- fuente,
- metadatos tecnicos.

La base de conocimiento debe ser utilizable sin perder riqueza estructural.
No conviertas esa base a un formato mas pobre si eso elimina contexto o campos relevantes.

4. SISTEMA DE RECUPERACION
Construye retrieval semantico o hibrido para encontrar la evidencia mas relevante para cada consulta.

Debe:
- indexar preguntas y pistas de recuperacion,
- soportar sinonimos y lenguaje natural,
- devolver contexto relevante,
- evitar falsos positivos excesivos,
- permitir grounding de respuestas.

5. SISTEMA DE GENERACION
La generacion de respuesta debe:
- usar la evidencia recuperada,
- sintetizar con palabras propias,
- responder como asesor tecnico,
- abstenerse si no hay evidencia suficiente,
- evitar alucinaciones,
- priorizar utilidad practica.

Si disenias un pipeline RAG, hazlo bien. Primero recuperar, luego razonar y redactar.

6. LLM GRATUITO OFFLINE
El producto debe integrar un LLM gratuito y offline.

Requisitos para ese LLM:
- debe usar pesos abiertos o de uso gratuito,
- debe poder ejecutarse localmente en Android,
- debe poder correr cuantizado si hace falta,
- debe ser suficientemente pequeno para una experiencia movil razonable,
- debe estar orientado a instrucciones,
- debe redactar con lenguaje natural y no solo repetir contexto.

No dependas de una API remota paga como requisito central del producto.
Si ofreces un modo online opcional para mejorar calidad, debe ser secundario. La app debe seguir teniendo valor real offline.

7. SOPORTE OFFLINE Y ONLINE
La app debe estar pensada para conectividad rural.
Define una estrategia realista de funcionamiento:
- offline-first,
- cache local,
- uso local de la KB,
- uso local del LLM gratuito offline,
- actualizaciones descargables cuando haya conectividad,
- y un modo online opcional solo como mejora, no como dependencia.

8. EXPERIENCIA DE USUARIO
La UX debe estar optimizada para uso en campo:
- tipografia grande y legible,
- botones claros,
- poco ruido visual,
- estados visibles,
- respuesta rapida,
- buena experiencia bajo luz exterior,
- minimo numero de pasos para consultar.

No construyas una interfaz generica de chatbot bonita pero inutil.
Construye una interfaz funcional para agricultores.

9. HISTORIAL Y CONTINUIDAD
La app debe poder recordar el ultimo caso conversado para que el usuario pueda decir:
- "continua",
- "explica mas",
- "y que hago despues",
- "y si llueve",
y la app mantenga el contexto.

10. FEEDBACK Y VALIDACION
La app debe generar datos utiles para visitas de campo y mejora del producto:
- claridad,
- utilidad,
- confianza,
- aplicabilidad inmediata.

11. APK Y DESPLIEGUE
Debes dejar la app compilable y con APK funcional.

CRITERIOS DE ACEPTACION
Considera el trabajo correcto solo si se cumple todo esto:

1. La app se puede compilar.
2. La app produce una APK instalable.
3. Tiene flujo de texto funcional.
4. Tiene flujo de voz funcional.
5. Integra un LLM gratuito offline funcional.
6. Usa una KB estructurada sin perder riqueza.
7. Recupera evidencia antes de responder.
8. Responde sintetizando, no copiando.
9. No usa plantillas roboticas globales.
10. No inventa cuando no tiene evidencia.
11. Tiene UX adecuada para uso en campo.
12. Permite recoger feedback rapido por respuesta.
13. No depende de analisis de imagen en esta fase.

QUE QUIERO QUE HAGAS PASO A PASO
1. Define la arquitectura completa.
2. Propone la estructura de carpetas y modulos.
3. Implementa la app desde cero.
4. Disenia la UI principal de chat y voz.
5. Implementa el modelo de datos para la base de conocimiento.
6. Implementa el retrieval de evidencia.
7. Integra un LLM gratuito offline apto para Android.
8. Implementa el pipeline de generacion de respuesta.
9. Implementa feedback rapido por respuesta.
10. Implementa manejo de errores y estados vacios.
11. Compila la app y genera APK.

SI NECESITAS TOMAR DECISIONES DE DISENO
Toma decisiones razonables que favorezcan:
- confianza,
- utilidad,
- claridad,
- mantenibilidad,
- funcionamiento rural,
- y fidelidad a la evidencia.

SI ALGO NO ES POSIBLE
No lo ocultes. Explica exactamente:
- que si se pudo construir,
- que no se pudo,
- por que no se pudo,
- que faltaria para completarlo,
- y cual seria la mejor alternativa.

IMPORTANTE
No me entregues una demo linda pero superficial.
No me entregues un chatbot agricola generico.
No me entregues respuestas que parezcan copiadas de un PDF.
No me entregues una app que hable mucho y ayude poco.
No me entregues una app que invente.
No me entregues una app dependiente de una API paga para funcionar.
No incluyas analisis de imagen en esta fase.

Tu trabajo es construir una app movil real para agricultores, especialmente caficultores, que sirva en campo, use evidencia tecnica de forma confiable, integre un LLM gratuito offline y responda como un asesor humano competente.
```

## Uso recomendado

Este prompt esta pensado para darselo a un agente constructor de software que pueda:

- crear un proyecto Android desde cero,
- decidir arquitectura,
- implementar interfaces,
- integrar IA,
- integrar un LLM gratuito offline,
- compilar,
- y generar una APK.

Si quieres, el siguiente paso puede ser que te prepare una version todavia mas fuerte para una herramienta concreta, por ejemplo `Codex`, `Claude Code`, `Cursor` o `ChatGPT Projects`.
