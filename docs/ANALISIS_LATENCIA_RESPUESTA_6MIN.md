# Analisis profundo de latencia (~6 minutos)

## 1) Resumen ejecutivo

Con los logs y el codigo actual, el cuello de botella principal no esta en RAG ni en carga de MindSpore. El tiempo se va casi completo en generacion del LLM local.

Hallazgos clave:

- El modelo local **no se carga en cada pregunta**. Se carga una vez al inicio.
- En el caso reportado, el flujo RAG tarda ~1.36 s, pero la fase LLM tarda ~348.14 s.
- La app fuerza presupuestos de salida muy altos (`maxTokens >= 1200`) cuando hay contexto KB.
- El modelo por defecto es pesado (`Qwen2.5-1.5B-Instruct-Q4_K_M.gguf`, ~1 GB).
- Se usa respuesta completa bloqueante (`sendComplete`), no streaming.
- Encima, al final se rechazo la respuesta por guard (`noInfo=true`), o sea se pago casi 6 min y se descarto.

---

## 2) Evidencia temporal del log

Linea de tiempo (caso compartido):

- `09:25:24.782` inicio busqueda RAG.
- `09:25:26.143` decision `LLM_WITH_KB` y armado de contexto.
- `09:31:14.287` `LLM guard reject`.
- `09:31:14.326` respuesta final por fallback KB.

Duraciones aproximadas:

- Retrieval + routing: **~1.36 s**
- Generacion LLM + postproceso hasta guard: **~348.14 s**
- Fallback + calidad + entrega: **<0.05 s**

Conclusion: la demora de ~6 min viene casi toda de generacion local.

---

## 3) Verificacion: el modelo se carga en cada pregunta?

**No**.

Evidencia en codigo:

- Carga inicial LLM al arrancar app:
  - `MainActivity.initializeLlama()` y `loadLlamaModel()`
  - `LlamaService.load()` -> `llama.load(...)`
- En cada pregunta se llama solo generacion:
  - `findResponseWithMeta()` -> `llamaService.generateAgriResponse(...)`
  - `LLamaAndroid.sendComplete(...)`
- Liberacion ocurre al cerrar actividad:
  - `onDestroy()` -> `llamaService.unload()`

Por lo tanto, no hay recarga del GGUF por pregunta como causa principal de esos 6 min.

---

## 4) Causas tecnicas priorizadas

### Causa A (principal): presupuesto de salida excesivo

Actualmente, para consultas con KB se obliga en practica a `maxTokens >= 1200`.

Impacto:

- Si el dispositivo va a 3-4 tokens/s, 1200 tokens toman ~300-400 s.
- Eso cuadra casi perfecto con los ~348 s observados.

### Causa B (principal): modelo local pesado por defecto

Se prioriza `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (~1 GB). Es util para calidad, pero mas lento en CPU movil que variantes 0.5B/1B o cuantizaciones mas agresivas.

### Causa C (alta): demasiadas consultas van por `LLM_WITH_KB`

El `KB_DIRECT` exige umbrales muy altos (ej. similitud >= 0.88). Preguntas que podrian resolverse directo con KB terminan yendo al LLM local (costoso).

### Causa D (alta): respuesta bloqueante sin streaming

Se usa `sendComplete`, asi que el usuario no ve nada hasta el final. Aunque el primer token salga rapido, la UX percibe todo el tiempo como espera total.

### Causa E (media): guard posterior descarta salida larga

En el caso mostrado, el guard marco `noInfo=true` y rechazo al final una respuesta de longitud 5274 chars. Esto no aumenta el tiempo de generacion, pero si desperdicia el costo y empeora la percepcion.

Nota: el detector incluye `"no se"` como patron global; puede dar falsos positivos en frases validas tipo "si no se aplica...".

### Causa F (media): encoder semantico desactivado por desalineacion

`Tokenizer/KB desalineados` desactiva el encoder MindSpore y usa fallback textual. No explica los 6 min, pero puede afectar routing/precision y empujar mas preguntas al LLM.

---

## 5) Opciones para reducir drasticamente latencia

## Opcion 1 (quick win, mayor ROI inmediato)

Reducir `maxTokens` de forma agresiva.

- Pasar default de 1200 a 200-320.
- Quitar el minimo forzado de 1200 cuando hay KB.
- Definir objetivos de salida: 4-8 bullets max, 120-220 tokens en preguntas factuales.

Impacto esperado:

- 3x a 6x menos tiempo en LLM local, segun dispositivo.

## Opcion 2 (quick win)

Cambiar routing para favorecer `KB_DIRECT` en consultas factuales con match decente.

- Bajar exigencia de umbral para respuesta directa en preguntas tipo FAQ.
- Mantener LLM solo para reformulacion opcional (o en segundo paso bajo demanda).

Impacto esperado:

- Muchas preguntas pasan de minutos a ~1-2 s.

## Opcion 3 (quick win UX)

Usar streaming en UI (`send`) en vez de esperar `sendComplete`.

- Reduce tiempo a primer token percibido.
- Mejora UX incluso cuando el total sigue siendo alto.

Impacto esperado:

- UX mucho mejor (TTFT bajo), aunque tiempo total no cambie tanto.

## Opcion 4 (quick+medio)

Modelo local mas pequeno por defecto.

- Priorizar `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` o 1B rapido.
- Mantener 1.5B como opcion de "alta calidad".

Impacto esperado:

- 1.5x a 3x mas rapido segun hardware.

## Opcion 5 (medio)

Timeout + fallback progresivo.

- Si LLM excede p.ej. 15-25 s, cortar y devolver:
  - respuesta KB directa, o
  - resumen parcial generado hasta ese punto.

Impacto esperado:

- Elimina outliers de 3-6 min.

## Opcion 6 (medio)

Corregir guard para evitar falsos rechazos al final.

- Hacer `isNoInfoStyleResponse` mas estricto (frases completas, no substring "no se" suelto).
- Evaluar guard en primeras lineas antes de invalidar toda la salida.

Impacto esperado:

- Menos desperdicio de computo y menos respuestas descartadas tras espera larga.

## Opcion 7 (medio)

Reactivar encoder semantico alineando tokenizer + embeddings.

- Regenerar `kb_embeddings.npy` y mapping con el mismo tokenizer/modelo en runtime.
- Reducir desalineacion para mejorar retrieval y routing.

Impacto esperado:

- Mejor precision de match y menos llamadas innecesarias al LLM.

## Opcion 8 (medio/alto)

Exponer tuning de runtime nativo (hilos, batch, etc.) para llama.cpp.

- Permitir ajustar threads segun CPU del dispositivo.
- Medir tokens/s por dispositivo para presets automaticos.

Impacto esperado:

- Mejora adicional de throughput, depende del SoC.

## Opcion 9 (estrategica)

Modo hibrido online/offline.

- Si hay internet/API, usar Groq para respuestas rapidas.
- Dejar local como fallback offline.

Impacto esperado:

- Respuestas de segundos en modo online.

---

## 6) Ruta recomendada (orden sugerido)

Fase 1 (1-2 dias, mayor impacto):

1. Bajar `maxTokens` default a 256 y quitar piso de 1200 para KB.
2. Activar `KB_DIRECT` para casos factuales de alta confianza practica (no tan estricta como 0.88).
3. Corregir guard `noInfo` para evitar falso positivo por "no se".
4. Activar streaming para mostrar salida incremental.

Fase 2 (2-4 dias):

1. Cambiar modelo default a 0.5B rapido (dejar 1.5B opcional).
2. Agregar timeout/fallback para capar cola larga de latencia.
3. Instrumentar metricas: `promptTokens`, `genTokens`, `tokens/s`, `TTFT`, `latencia total`.

Fase 3 (si se busca maxima calidad+velocidad):

1. Re-alinear encoder/embeddings.
2. Presets por dispositivo (threads/modelo/contexto).
3. Estrategia hibrida online/offline automatica.

---

## 7) Meta realista de tiempos

Sin cambiar arquitectura, la latencia de LLM local se aproxima a:

`latencia ~= prefill + (tokens_generados / tokens_por_segundo)`

Con tu caso:

- `tokens_generados` muy altos (por `maxTokens` alto)
- `tokens/s` moderado (modelo 1.5B en CPU movil)

Para bajar de minutos a segundos, necesitas combinar al menos:

- menos tokens generados,
- mas `KB_DIRECT`, y/o
- modelo mas pequeno/online.

