#!/usr/bin/env bash
set -euo pipefail

if ! command -v llama-server >/dev/null 2>&1; then
  echo "ERROR: no se encontro 'llama-server' en PATH." >&2
  echo "Instala llama.cpp con binario server o ejecuta tu servidor equivalente." >&2
  exit 1
fi

MODEL_PATH="${1:-}"
PORT="${2:-8080}"

if [[ -z "$MODEL_PATH" ]]; then
  echo "Uso: $0 /ruta/modelo.gguf [puerto]" >&2
  exit 2
fi

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "ERROR: modelo no encontrado en '$MODEL_PATH'" >&2
  exit 3
fi

echo "Iniciando llama-server en puerto $PORT"
echo "Modelo: $MODEL_PATH"
exec llama-server -m "$MODEL_PATH" --port "$PORT"
