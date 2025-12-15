#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Uso: $0 /ruta/al/modelo.gguf" >&2
  exit 2
fi

MODEL_PATH="$1"
PKG="edu.unicauca.app.agrochat"
DEST_DIR="/sdcard/Android/data/${PKG}/files"

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "No existe el archivo: $MODEL_PATH" >&2
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb no está disponible en PATH" >&2
  exit 2
fi

echo "Verificando dispositivo..."
adb devices -l

echo "Creando directorio destino: $DEST_DIR"
adb shell "mkdir -p '$DEST_DIR'"

BASENAME="$(basename "$MODEL_PATH")"

echo "Pusheando modelo ($BASENAME) al dispositivo... (puede tardar)"
adb push "$MODEL_PATH" "$DEST_DIR/$BASENAME"

echo "Verificando en el dispositivo:"
adb shell "ls -lh '$DEST_DIR/$BASENAME'"

echo "OK. Abre la app y entra al chat: Llama debería cargarse automáticamente."
