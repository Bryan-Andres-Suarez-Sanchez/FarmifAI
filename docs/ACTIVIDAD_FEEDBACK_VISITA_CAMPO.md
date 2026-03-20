# Actividad de Campo para Retroalimentacion de FarmifAI

## 1) Objetivo
Validar, con agricultores reales y en condiciones reales de parcela, si FarmifAI:
- se entiende facil,
- responde de forma util y accionable,
- genera confianza para tomar decisiones,
- y que ajustes concretos debemos priorizar antes de despliegue.

## 2) Idea central de la actividad
**Actividad propuesta: "Reto 3 Casos Reales"**

Cada agricultor prueba la app con 3 situaciones de su propio contexto:
1. una consulta de manejo (riego/fertilizacion),
2. una consulta de problema (plaga/enfermedad/sintoma),
3. una consulta abierta ("que haria usted en mi cultivo").

Comparamos utilidad percibida, claridad y tiempo de respuesta, y cerramos con retro cualitativa guiada.

## 3) Formato recomendado
- Duracion total: 90 minutos
- Participantes: 8 a 12 agricultores
- Modalidad: grupos de 2 personas por telefono (para fomentar conversacion y observacion)
- Equipo facilitador: 1 moderador + 1 observador + 1 apoyo tecnico
- Lugar: zona de cultivo o espacio comunitario cercano a parcela

## 4) Guion operativo (paso a paso)

## 4.1 Apertura (10 min)
- Presentacion breve: "no estamos evaluando al agricultor; estamos evaluando la app".
- Consentimiento de uso de respuestas para mejora del producto.
- Explicar dinamica y tiempo.

## 4.2 Calentamiento sin app (10 min)
- Preguntar: "Cuando tiene una duda tecnica, que hace hoy?"
- Registrar canales actuales (tecnico local, vecino, WhatsApp, prueba/error, etc.).

## 4.3 Reto 3 Casos Reales con app (45 min)
Cada pareja ejecuta los 3 casos.

Para cada caso:
1. Formular pregunta en lenguaje natural (como la dirian en campo).
2. Leer respuesta de la app.
3. Marcar evaluacion rapida (1 a 5):
   - claridad,
   - utilidad,
   - confianza para aplicar.
4. Decidir: "la aplicaria hoy si/no".
5. Si es "no", registrar por que en una frase.

## 4.4 Mini entrevista de cierre (15 min)
Preguntas cortas por participante:
- "Que fue lo mas util de la app?"
- "Que parte fue confusa o incomoda?"
- "Que cambiaria para usarla cada semana?"
- "En que momento del ciclo del cultivo la usaria mas?"

## 4.5 Priorizacion participativa (10 min)
Tarjetas o votacion simple (3 votos por persona) sobre mejoras.
Categorias sugeridas:
- lenguaje,
- confianza,
- rapidez,
- facilidad de uso,
- funciones faltantes.

## 5) Datos a capturar (minimo viable)

## 5.1 Cuantitativos
- Tiempo promedio para obtener respuesta util (segundos).
- Puntaje de claridad (1-5).
- Puntaje de utilidad (1-5).
- Puntaje de confianza (1-5).
- % de respuestas "la aplicaria hoy".
- % de abandonos o reintentos por caso.

## 5.2 Cualitativos
- Frases textuales de frustracion o confusion.
- Frases textuales de valor percibido.
- Palabras/expresiones que no usan naturalmente.
- Preguntas que la app no resolvio bien.

## 6) Criterios de exito de la visita
Considerar la visita exitosa si se cumple:
- claridad promedio >= 4.0/5,
- utilidad promedio >= 4.0/5,
- al menos 70% responde "la aplicaria hoy",
- y se identifican maximo 5 ajustes prioritarios concretos para el siguiente sprint.

## 7) Ajustes propuestos a la app (si queremos mejor retro y mejor adopcion)

## 7.1 Ajustes recomendados antes de la visita
1. **Botones de feedback por respuesta**: `Me sirvio` / `No me sirvio`.
2. **Pregunta de seguimiento automatica** cuando detecte falta de contexto: pedir solo 1-2 datos (cultivo, etapa, sintoma).
3. **Microencuesta de 2 toques** al final de cada consulta:
   - "Fue clara?" (si/no)
   - "La aplicaria hoy?" (si/no)
4. **Indicador de estado simple**: `Respuesta basada en tu caso` vs `Orientacion general` (sin tecnicismos).

## 7.2 Ajustes recomendados despues de la visita (segun resultados)
1. **Diccionario local de lenguaje campesino** (sinonimos regionales de sintomas/plagas/cultivos).
2. **Respuestas en formato accionable corto**:
   - que hacer hoy,
   - que observar en 48h,
   - cuando escalar a tecnico.
3. **Historial de recomendaciones por cultivo** para seguimiento semanal.
4. **Modo voz mas guiado** para usuarios con baja alfabetizacion digital.

## 8) Plantilla rapida de registro (para el equipo)

| Participante | Caso | Claridad (1-5) | Utilidad (1-5) | Confianza (1-5) | La aplicaria hoy (Si/No) | Comentario clave |
|---|---|---:|---:|---:|---|---|
| P1 | Manejo |  |  |  |  |  |
| P1 | Problema |  |  |  |  |  |
| P1 | Abierta |  |  |  |  |  |

## 9) Entregable de salida de la jornada
Al finalizar, producir un resumen de 1 pagina con:
- Top 5 dolores detectados,
- Top 5 elementos valorados,
- Top 5 ajustes priorizados (Impacto alto + esfuerzo bajo primero),
- decision: "listo para piloto ampliado" o "requiere iteracion corta de 1 semana".

## 10) Recomendacion final
Esta actividad es suficientemente simple para ejecutarla en una visita tecnica real y, al mismo tiempo, suficientemente estructurada para tomar decisiones de producto. Si quieren, el siguiente paso es que te deje una **ficha imprimible** (una hoja) para que el equipo la use en campo sin improvisar.
