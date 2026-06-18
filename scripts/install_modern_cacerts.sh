#!/usr/bin/env bash
# Push Let's Encrypt / modern roots into /system/etc/security/cacerts on rooted Y1.
# Fixes "Trust anchor not found" for ALL apps (MediaPlayer HTTPS, stock URLConnection, etc.).
# Does NOT add TLS 1.3 to system OpenSSL — Solar still needs Conscrypt/OkHttp for that.
#
# Usage: install_modern_cacerts.sh [--no-reboot] [APK_PATH]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"
APK="${SOLAR_APK:-$ROOT/app/build/outputs/apk/release/app-release.apk}"
NO_REBOOT=0
for arg in "$@"; do
  case "$arg" in
    --no-reboot) NO_REBOOT=1 ;;
    *) APK="$arg" ;;
  esac
done

[[ -f "$APK" ]] || {
  echo "Missing $APK — run ./scripts/build.sh first or pass APK path" >&2
  exit 1
}

echo "== Waiting for device (120s) =="
timeout 120 adb wait-for-device
sleep 2

TLS_STAGING="$(mktemp -d)"
trap 'rm -rf "$TLS_STAGING"' EXIT
chmod +x "$ROOT/scripts/stage-y1-system-prep.sh" "$ROOT/scripts/push-y1-system-prep.sh"
"$ROOT/scripts/stage-y1-system-prep.sh" "$TLS_STAGING" "$APK" "$ROOT"
"$ROOT/scripts/push-y1-system-prep.sh" "$TLS_STAGING" --cacerts-only

echo "== Installed =="
adb shell "ls -la /system/etc/security/cacerts/6187b673.0 2>/dev/null" || true

if [[ "$NO_REBOOT" -eq 1 ]]; then
  echo "DONE: modern roots in /system/etc/security/cacerts (no reboot — caller will reboot)"
else
  echo "== Reboot required for all processes to reload trust store =="
  adb reboot
  echo "DONE: modern roots in /system/etc/security/cacerts — rebooting"
fi
