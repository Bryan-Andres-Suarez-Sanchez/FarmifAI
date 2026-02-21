#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB_BIN:-adb}"

TARGET_DEVICE="${1:-}"
PAIR_TARGET="${2:-${ADB_PAIR_TARGET:-}}"
PAIR_CODE="${3:-${ADB_PAIR_CODE:-}}"

usage() {
  cat <<EOF
Uso:
  $0 <ip:puerto_dispositivo> [ip:puerto_pairing] [codigo_pairing]

Ejemplo:
  $0 192.168.20.45:36431 192.168.20.45:35333 501106

Variables opcionales:
  ADB_BIN         Binario adb (default: adb)
  APK_PATH        Ruta de APK especifica a instalar
  ADB_PAIR_TARGET Alternativa para pairing target
  ADB_PAIR_CODE   Alternativa para pairing code
EOF
}

have_adb() {
  if [[ "$ADB_BIN" == */* ]]; then
    [[ -x "$ADB_BIN" ]]
  else
    command -v "$ADB_BIN" >/dev/null 2>&1
  fi
}

find_latest_apk() {
  ls -t "$PROJECT_DIR"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n1
}

if [[ -z "$TARGET_DEVICE" ]]; then
  usage
  exit 2
fi

if ! have_adb; then
  echo "ERROR: no se encontro adb. Define ADB_BIN o agrega adb al PATH." >&2
  exit 3
fi

APK_PATH="${APK_PATH:-$(find_latest_apk)}"

if [[ -z "${APK_PATH:-}" || ! -f "$APK_PATH" ]]; then
  echo "[*] No hay APK debug, compilando..."
  (
    cd "$PROJECT_DIR"
    ./gradlew :app:assembleDebug -Dkotlin.compiler.execution.strategy=in-process --no-daemon
  )
  APK_PATH="$(find_latest_apk)"
fi

if [[ -z "${APK_PATH:-}" || ! -f "$APK_PATH" ]]; then
  echo "ERROR: no se encontro APK en app/build/outputs/apk/debug/" >&2
  exit 4
fi

echo "[*] APK a instalar: $APK_PATH"

if [[ -n "$PAIR_TARGET" && -n "$PAIR_CODE" ]]; then
  echo "[*] Intentando pairing en $PAIR_TARGET ..."
  set +e
  PAIR_OUTPUT="$(printf '%s\n' "$PAIR_CODE" | "$ADB_BIN" pair "$PAIR_TARGET" 2>&1)"
  PAIR_STATUS=$?
  set -e
  echo "$PAIR_OUTPUT"
  if [[ $PAIR_STATUS -ne 0 ]]; then
    echo "[!] Pairing fallo o no fue necesario. Se intentara conectar de todos modos."
  fi
fi

echo "[*] Conectando a $TARGET_DEVICE ..."
CONNECT_OUTPUT="$("$ADB_BIN" connect "$TARGET_DEVICE" 2>&1 || true)"
echo "$CONNECT_OUTPUT"

if [[ "$CONNECT_OUTPUT" != *"connected to"* && "$CONNECT_OUTPUT" != *"already connected to"* ]]; then
  echo "[!] No se pudo conectar a $TARGET_DEVICE por connect directo. Intentando fallback mDNS..."

  TARGET_IP="${TARGET_DEVICE%%:*}"
  MDNS_TARGET="$("$ADB_BIN" mdns services 2>/dev/null | awk -v ip="$TARGET_IP" '$3 ~ ("^" ip ":") {print $3; exit}')"
  if [[ -n "$MDNS_TARGET" ]]; then
    echo "[*] Endpoint mDNS detectado: $MDNS_TARGET"
    MDNS_CONNECT_OUTPUT="$("$ADB_BIN" connect "$MDNS_TARGET" 2>&1 || true)"
    echo "$MDNS_CONNECT_OUTPUT"
    if [[ "$MDNS_CONNECT_OUTPUT" == *"connected to"* || "$MDNS_CONNECT_OUTPUT" == *"already connected to"* ]]; then
      TARGET_DEVICE="$MDNS_TARGET"
    fi
  fi

  if ! "$ADB_BIN" -s "$TARGET_DEVICE" get-state >/dev/null 2>&1; then
    FALLBACK_SERIAL="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    if [[ -n "$FALLBACK_SERIAL" ]]; then
      TARGET_DEVICE="$FALLBACK_SERIAL"
      echo "[*] Usando dispositivo ya conectado: $TARGET_DEVICE"
    else
      echo "ERROR: no fue posible conectar al dispositivo $TARGET_DEVICE" >&2
      exit 5
    fi
  else
    echo "[*] Dispositivo listo para instalar: $TARGET_DEVICE"
  fi
fi

echo "[*] Instalando APK ..."
"$ADB_BIN" -s "$TARGET_DEVICE" install -r "$APK_PATH"

echo "[✓] Instalacion completada en $TARGET_DEVICE"
