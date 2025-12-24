#!/usr/bin/env bash
set -euo pipefail

# Run Waydroid restart / install / log capture reliably.
# Usage:
#   sudo scripts/run_waydroid_reinstall.sh    # recommended
#   scripts/run_waydroid_reinstall.sh --no-sudo  # run without sudo (will try, may fail)

NO_SUDO=0
for arg in "$@"; do
  case "$arg" in
    --no-sudo) NO_SUDO=1 ;;
  esac
done

need_sudo() {
  if [ "$NO_SUDO" -eq 1 ]; then
    return 1
  fi
  # Check if sudo is usable non-interactively
  if sudo -n true 2>/dev/null; then
    return 0
  fi
  return 2
}

SUDO_CMD=""
if need_sudo; then
  SUDO_CMD="sudo"
elif [ "$NO_SUDO" -eq 1 ]; then
  SUDO_CMD=""
else
  echo "This script needs sudo to control Waydroid. Re-run with sudo or pass --no-sudo to attempt without sudo."
  echo "Example: sudo $0"
  exit 1
fi

echo "== Waydroid reinstall helper starting (logs -> /tmp) =="
echo "Using sudo: ${SUDO_CMD:+yes}" 

LOGDIR=/tmp
JOURNAL=$LOGDIR/waydroid-container.journal
SESSION_LOG=$LOGDIR/waydroid-session.log
UI_LOG=$LOGDIR/waydroid-ui.log
INSTALL_LOG=$LOGDIR/waydroid-install.log
LOGCAT_DUMP=$LOGDIR/waydroid-reinstall2-logcat.dump

echo "-- restarting waydroid-container"
${SUDO_CMD:-} systemctl restart waydroid-container || true
echo "-- saving recent journal to $JOURNAL"
# Use a pipe+tee so the redirection is performed by tee (avoids sudo+redirect permission issues)
${SUDO_CMD:-} journalctl -u waydroid-container --no-pager -n 200 2>&1 | tee "$JOURNAL" >/dev/null || true

echo "-- stopping any existing session"
${SUDO_CMD:-} waydroid session stop >/dev/null 2>&1 || true

echo "-- starting waydroid session (background). Logs: $SESSION_LOG"
# Start session and pipe output to tee to avoid shell redirection permission errors
nohup ${SUDO_CMD:-} waydroid session start 2>&1 | tee "$SESSION_LOG" >/dev/null &
sleep 3

echo "-- waiting for Android boot (sys.boot_completed) up to 120s"
for i in $(seq 1 120); do
  v=$(${SUDO_CMD:-} waydroid shell -- getprop sys.boot_completed 2>/dev/null || true)
  echo "boot_completed=$v (try $i/120)"
  if [ "$v" = "1" ]; then
    echo "Android reported boot completed"
    break
  fi
  sleep 1
done

echo "-- attempting to show full UI (background). UI logs: $UI_LOG"
nohup ${SUDO_CMD:-} waydroid show-full-ui 2>&1 | tee "$UI_LOG" >/dev/null &
sleep 2

APK_PATH="$(ls -t app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n1 || true)"
if [ -z "$APK_PATH" ]; then
  echo "ERROR: no APK found in app/build/outputs/apk/debug/"
  exit 2
fi
echo "-- APK found: $APK_PATH"

PKG=edu.unicauca.app.agrochat

echo "-- uninstalling existing package (ignore errors)"
${SUDO_CMD:-} waydroid app uninstall "$PKG" >/dev/null 2>&1 || true

echo "-- installing APK (log -> $INSTALL_LOG)"
${SUDO_CMD:-} waydroid app install "$APK_PATH" 2>&1 | tee "$INSTALL_LOG" || true

echo "-- launching $PKG/.MainActivity"
${SUDO_CMD:-} waydroid shell -- am start -n ${PKG}/.MainActivity || true
sleep 4

echo "-- dumping logcat to $LOGCAT_DUMP"
# Pipe to tee so file is written by tee (avoids sudo redirection issues)
${SUDO_CMD:-} waydroid shell -- logcat -d 2>&1 | tee "$LOGCAT_DUMP" >/dev/null || true

echo "-- filtering for likely errors"
grep -i -n -E 'mindspore|helper|UnsatisfiedLinkError|dlopen|Fallo|fail|Exception|ERROR' "$LOGCAT_DUMP" || true

echo "-- done. Important logs:"
ls -lh "$JOURNAL" "$SESSION_LOG" "$UI_LOG" "$INSTALL_LOG" "$LOGCAT_DUMP" || true

echo "If the script requires sudo and you prefer to run it without interactive password, run:"
echo "  sudo $0"

exit 0
