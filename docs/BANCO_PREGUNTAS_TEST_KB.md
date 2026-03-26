# Banco De Preguntas Para Testear La App (Basado En KB Nueva)

Este banco se construyo a partir de `kb nueva/extract/*.records.jsonl`, usando preguntas literales de `retrieval_hints` y `aliases`.

## Uso Recomendado

- Ejecuta al menos 3 rondas: smoke, regresion y estres.
- Mide: pertinencia, completitud, alucinacion, y tono no-robotico.
- Marca cada prueba como `OK`, `Parcial`, o `Fallo`.

## Set 1: Smoke Test Rapido (36 preguntas)

Usa estas primero para validar que todo el pipeline responde bien tras cada cambio.

1. que es un sistema
2. definicion de sistema de produccion
3. definicion de sistema
4. cuantos anos dura un cafetal
5. vida comercial de la planta de cafe
6. vida util del cafeto
7. que es la productividad del cafetal
8. como se define la productividad en cafe
9. kg cps por recurso
10. cuanto tiempo dura un cafetal
11. que debe definirse al establecer un cafetal
12. planificar el cafetal
13. que es una arvense
14. diferencia entre arvense y maleza
15. maleza
16. que es competencia entre plantas
17. definicion de competencia en cafetales
18. competencia vegetal
19. de que depende la produccion anual del cafe
20. que determina la produccion de una planta de cafe
21. base de la produccion del cafeto
22. que es la agroforesteria
23. definicion de sistema agroforestal en cafe
24. sistema agroforestal
25. que es un nutrimento esencial
26. diferencia entre elemento esencial y benefico
27. elementos esenciales
28. que es un cafe especial
29. definicion de cafe especial
30. specialty coffee
31. que es un sistema intercalado
32. definicion de asocio de cultivos con cafe
33. asocio de cultivos
34. que son las buenas practicas agricolas
35. definicion de BPA en caficultura
36. BPA

## Set 2: Banco Completo Por Capitulo

### Capitulo 1: Fundamentos sistemas de produccion
Origen: `kb nueva/extract/01_fundamentos_sistemas_de_produccion.records.jsonl`

1. que es un sistema
2. definicion de sistema de produccion
3. definicion de sistema
4. sistema productivo
5. unidades relacionadas
6. que es la fitotecnia
7. definicion de fitotecnia en cafe
8. tecnologia de la produccion agricola
9. disciplina fitotecnica
10. factores que sustentan la fitotecnia
11. factores edaficos climaticos y biologicos
12. factores de produccion vegetal

### Capitulo 2: Crecimiento y desarrollo planta de cafe
Origen: `kb nueva/extract/02_crecimiento_y_desarrollo_planta_de_cafe.records.jsonl`

1. cuantos anos dura un cafetal
2. vida comercial de la planta de cafe
3. vida util del cafeto
4. duracion comercial del cafe
5. cuando empieza a producir el cafe
6. a que edad produce mas el cafeto
7. edad productiva del cafeto
8. pico productivo del cafe
9. cuando florece el cafe
10. en que epoca se forman hojas y frutos del cafeto
11. fenologia del cafeto
12. floracion al final de la sequia

### Capitulo 3: Factores que determinan productividad cafetal
Origen: `kb nueva/extract/03_factores_que_determinan_productividad_cafetal.records.jsonl`

1. que es la productividad del cafetal
2. como se define la productividad en cafe
3. kg cps por recurso
4. definicion de productividad
5. de que depende la produccion potencial del cafe
6. que determina la produccion potencial del cafetal
7. potencial de produccion
8. determinantes de la produccion potencial
9. cuales son los niveles de productividad agricola
10. produccion potencial alcanzable y actual
11. niveles de productividad
12. brecha productiva

### Capitulo 4: Establecimiento y administracion del cafetal
Origen: `kb nueva/extract/04_establecimiento_y_administracion_del_cafetal.records.jsonl`

1. cuanto tiempo dura un cafetal
2. que debe definirse al establecer un cafetal
3. planificar el cafetal
4. duracion del ciclo del cafetal
5. como seleccionar semilla de cafe
6. de donde sacar semilla de Caturra o Borbon
7. seleccion de semilla
8. semilla de arboles sanos
9. por que no usar desmucilaginador en semilla de cafe
10. que dano causa el desmucilaginador a la semilla
11. dano al embrion de la semilla
12. beneficio de semilla

### Capitulo 5: Las arvenses y su manejo en los cafetales
Origen: `kb nueva/extract/05_las_arvenses_y_su_manejo_en_los_cafetales.records.jsonl`

1. que es una arvense
2. diferencia entre arvense y maleza
3. maleza
4. planta acompanante
5. que pasa si se controlan las arvenses indiscriminadamente
6. riesgos ambientales del manejo de arvenses
7. control indiscriminado
8. manejo no selectivo
9. que es la interferencia de arvenses
10. que incluye la interferencia de malezas
11. competencia de arvenses
12. alelopatia

### Capitulo 6: Densidad de siembra y productividad de los cafetales
Origen: `kb nueva/extract/06_densidad_de_siembra_y_productividad_de_los_cafetales.records.jsonl`

1. que es competencia entre plantas
2. definicion de competencia en cafetales
3. competencia vegetal
4. competencia entre plantas
5. que tipos de competencia ocurren en el cafe
6. competencia intra e interespecifica en cafe
7. tipos de competencia
8. competencia del cafe
9. que pasa con el cafe en alta densidad
10. como cambia la planta de cafe con mas densidad
11. efecto de la densidad
12. respuesta del cafeto a densidad alta

### Capitulo 7: Renovacion y administracion de los cafetales para estabilizar la produccion de la finca
Origen: `kb nueva/extract/07_renovacion_y_administracion_de_los_cafetales_para_estabilizar_la_produccion_de_la_finca.records.jsonl`

1. de que depende la produccion anual del cafe
2. que determina la produccion de una planta de cafe
3. base de la produccion del cafeto
4. ramas y nudos productivos
5. despues de cuantas cosechas se agota el cafe
6. cuando baja la formacion de nudos productivos
7. caida de la formacion de ramas
8. agotamiento del cafeto
9. que pasa si no se renueva un cafetal
10. por que cae la produccion del cafe viejo
11. riesgo de no renovar
12. declinacion del cafetal

### Capitulo 8: Produccion de cafe en sistemas agroforestales
Origen: `kb nueva/extract/08_produccion_de_cafe_en_sistemas_agroforestales.records.jsonl`

1. que es la agroforesteria
2. definicion de sistema agroforestal en cafe
3. sistema agroforestal
4. SAF
5. de que depende el efecto de los arboles de sombra sobre el cafe
6. las interacciones arbol cafe siempre son positivas
7. interacciones arbol cafe
8. efecto del sombrio
9. que porcentaje del cafe en Colombia se cultiva con sombra
10. cuanto cafe tiene sombrio en Colombia
11. porcentaje de cafe bajo sombra
12. adopcion del sombrio

### Capitulo 9: Consideraciones sobre nutricion mineral y organica en sistemas de produccion
Origen: `kb nueva/extract/09_consideraciones_sobre_nutricion_mineral_y_organica_en_sistemas_de_produccion.records.jsonl`

1. que es un nutrimento esencial
2. diferencia entre elemento esencial y benefico
3. elementos esenciales
4. elementos beneficos
5. cuales son los macronutrimentos del cafe
6. cuales son los micronutrimentos esenciales del cafeto
7. macro y microelementos
8. nutrimentos del cafe
9. como se clasifica el estado nutricional de una planta
10. que es el rango critico de un nutrimento
11. estado nutricional
12. niveles de nutrimentos

### Capitulo 10: Cafes especiales
Origen: `kb nueva/extract/10_cafes_especiales.records.jsonl`

1. que es un cafe especial
2. definicion de cafe especial
3. specialty coffee
4. cafe diferenciado
5. por que pagan mas por un cafe especial
6. que diferencia a un cafe especial en taza
7. valor del cafe especial
8. taza diferenciada
9. cuales son los segmentos de cafes especiales
10. tipos de cafe especial segun SCAA
11. segmentos de specialty coffee
12. tipos de cafe especial

### Capitulo 11: Produccion de cafe en sistemas intercalados
Origen: `kb nueva/extract/11_produccion_de_cafe_en_sistemas_intercalados.records.jsonl`

1. que es un sistema intercalado
2. definicion de asocio de cultivos con cafe
3. asocio de cultivos
4. arreglo interespecifico
5. intercalamiento
6. como diseñar un sistema intercalado con cafe
7. como reducir la competencia en cultivos asociados con cafe
8. diseño del asocio
9. arreglo de intercalados
10. manejo de competencia
11. que es el indice equivalente de terreno
12. como se calcula el IET en cafe intercalado

### Capitulo 12: Las buenas practicas agricolas en la caficultura
Origen: `kb nueva/extract/12_las_buenas_practicas_agricolas_en_la_caficultura.records.jsonl`

1. que son las buenas practicas agricolas
2. definicion de BPA en caficultura
3. BPA
4. buenas practicas
5. buenas practicas en cafe
6. cuales son los objetivos de las BPA
7. para que sirven las buenas practicas agricolas
8. objetivos de las BPA
9. para que sirven las BPA
10. que aspectos cubren las BPA
11. ejes de buenas practicas agricolas en cafe
12. componentes de las BPA

## Set 3: Pruebas De Continuidad (Multi-turn)

Estas pruebas son para verificar que la app no corte respuestas y mantenga contexto en seguimiento.

1. Explica a detalle como seleccionar semilla de cafe y que errores debo evitar.
2. Continua con mas detalle tecnico y pasos concretos.
3. Ahora resume que debo hacer hoy y que debo revisar en 30 dias.
4. Cuales son los objetivos de las BPA en caficultura y como aplicarlos en finca pequena.
5. Sigue y agrega riesgos si no se cumplen esas BPA.
6. Explica competencia entre plantas en cafe y como afecta productividad.
7. Continua con recomendaciones de manejo segun densidad de siembra.
8. Que es un sistema intercalado en cafe y como se diseña para evitar competencia.
9. Sigue con un ejemplo practico para una hectarea.
10. Como influye la nutricion mineral y organica en rendimiento del cafetal.
11. Continua y ordena las acciones por prioridad (alta, media, baja).
12. Como planificar renovacion del cafetal para estabilizar produccion anual.
13. Sigue con errores frecuentes y como prevenirlos.

## Set 4: Plantilla De Registro De Resultados

| ID | Pregunta | Pertinencia (1-5) | Completa (Si/No) | Alucinacion (Si/No) | Tono humano (1-5) | Resultado | Observacion |
|---|---|---:|---|---|---:|---|---|
| 1 |  |  |  |  |  |  |  |
| 2 |  |  |  |  |  |  |  |
| 3 |  |  |  |  |  |  |  |
