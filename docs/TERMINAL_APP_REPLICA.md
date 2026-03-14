# Replica de la app en terminal (RAG + Llama)

Este entorno permite probar el flujo del chat sin reinstalar APK en cada cambio.

## Scripts

- `scripts/run_llama_app_replica.py`: motor principal (replica de pipeline).
- `scripts/test_llama_pipeline.sh`: wrapper rapido.

## Flujo que replica

1. Recupera Top-K en KB (RAG) con la misma logica de umbrales.
2. Si hay match alto: responde directo desde KB (`KB_DIRECT`).
3. Si no: construye contexto (`KB` + `HISTORIAL`) y llama al LLM local.
4. Si LLM falla o responde muy corto: fallback a KB.

## Requisitos para probar con Llama real

Necesitas un servidor Llama compatible HTTP (por ejemplo `llama.cpp server`) corriendo localmente.

Puedes usar el helper incluido:

```bash
./scripts/start_llama_server.sh /ruta/a/tu_modelo.gguf 8080
```

O ejecutar manualmente:

```bash
llama-server -m /ruta/a/tu_modelo.gguf --port 8080
```

## Uso

### 1) Modo interactivo

```bash
./scripts/test_llama_pipeline.sh
```

Comandos:

- `/config` ver configuracion activa.
- `/help` ayuda.
- `/exit` salir.

### 2) Consulta unica

```bash
./scripts/test_llama_pipeline.sh --query "Como tratar el tizon norteno" --debug
```

### 3) Solo RAG/KB (sin Llama)

```bash
./scripts/test_llama_pipeline.sh --no-llama --query "Como tratar el tizon norteno" --debug
```

### 4) Forzar uso de LLM incluso con match alto

```bash
./scripts/test_llama_pipeline.sh --use-llm-for-all --query "Como tratar el tizon norteno" --debug
```

### 5) Cambiar endpoint/modelo HTTP

```bash
./scripts/test_llama_pipeline.sh \
  --llama-base-url http://127.0.0.1:8080 \
  --llama-model local-model
```

## Parametros utiles

- `--max-tokens` (default: `450`)
- `--context-length` (default: `1800`)
- `--similarity-threshold` (default: `0.45`)
- `--kb-fast-threshold` (default: `0.70`)
- `--context-relevance-threshold` (default: `0.50`)
- `--debug` imprime fuente de respuesta y top matches

## Nota de fidelidad

Esta replica implementa el comportamiento de `MainActivity + LlamaService + SemanticSearchHelper` para el flujo chat/RAG.

En desktop el retrieval se ejecuta en modo textual (sin inferencia MindSpore Android), por lo que no depende de instalar la app para iterar en prompts, umbrales y grounding de KB.
