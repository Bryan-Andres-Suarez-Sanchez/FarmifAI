#!/bin/bash
# Script para desplegar APK en Waydroid
# Uso: ./scripts/deploy_waydroid.sh [ruta_apk]

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="edu.unicauca.app.agrochat.debug"

GREEN='\033[0;32m'; BLUE='\033[0;34m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${BLUE}=== Waydroid APK Deployer ===${NC}"

# Encontrar APK
find_apk() { ls -t "$PROJECT_DIR/app/build/outputs/apk/debug/"*.apk 2>/dev/null | head -1; }
APK="${1:-$(find_apk)}"

# Compilar si no existe
if [ ! -f "$APK" ]; then
    echo -e "${BLUE}[*]${NC} Compilando APK..."
    cd "$PROJECT_DIR" && ./gradlew assembleDebug -q
    APK=$(find_apk)
fi

[ ! -f "$APK" ] && echo -e "${RED}[!] APK no encontrado${NC}" && exit 1
echo -e "${BLUE}[*]${NC} APK: $(basename "$APK")"

# Verificar/iniciar Waydroid
if ! pgrep -f weston > /dev/null; then
    echo -e "${BLUE}[*]${NC} Iniciando Waydroid..."
    sudo systemctl start waydroid-container.service
    sleep 2
    weston --backend=x11-backend.so --width=480 --height=960 --shell=kiosk-shell.so -- waydroid show-full-ui &
    echo -e "${BLUE}[*]${NC} Esperando Android..."
    sleep 25
fi

# Conectar e instalar
echo -e "${BLUE}[*]${NC} Conectando ADB..."
adb connect 192.168.240.112:5555 >/dev/null 2>&1
sleep 2

echo -e "${BLUE}[*]${NC} Instalando APK..."
if adb install -r "$APK" 2>&1 | grep -q "Success"; then
    echo -e "${GREEN}[✓]${NC} APK instalado"
else
    echo -e "${RED}[!]${NC} Error instalando"
    exit 1
fi

# Lanzar app
echo -e "${BLUE}[*]${NC} Lanzando app..."
waydroid app launch "$PACKAGE" 2>/dev/null

echo -e "${GREEN}[✓]${NC} ¡ARApp ejecutándose en Waydroid!"
