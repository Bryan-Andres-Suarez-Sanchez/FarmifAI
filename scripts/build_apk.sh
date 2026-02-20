#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_TYPE="${1:-debug}"

case "$BUILD_TYPE" in
    debug)
        TASK=":app:assembleDebug"
        APK_DIR="$PROJECT_DIR/app/build/outputs/apk/debug"
        ;;
    release)
        TASK=":app:assembleRelease"
        APK_DIR="$PROJECT_DIR/app/build/outputs/apk/release"
        ;;
    *)
        echo "Uso: $0 [debug|release]"
        exit 1
        ;;
esac

echo "Compilando APK ($BUILD_TYPE)..."
cd "$PROJECT_DIR"
./gradlew "$TASK" -Dkotlin.compiler.execution.strategy=in-process --no-daemon

APK_PATH="$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -n1 || true)"
if [ -z "$APK_PATH" ]; then
    echo "Error: no se encontró APK en $APK_DIR"
    exit 2
fi

echo "APK generado: $APK_PATH"
