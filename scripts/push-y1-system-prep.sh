#!/usr/bin/env bash
# Push staged Y1 /system TLS prep (Conscrypt + cacerts) to a rooted device via adb.
# Usage: push-y1-system-prep.sh STAGING_DIR [--cacerts-only]
set -euo pipefail

STAGING="${1:-}"
CACERTS_ONLY=0
[[ "${2:-}" == "--cacerts-only" ]] && CACERTS_ONLY=1

[[ -n "$STAGING" && -d "$STAGING" ]] || {
  echo "usage: $0 STAGING_DIR [--cacerts-only]" >&2
  exit 1
}
if [[ "$CACERTS_ONLY" -eq 0 ]]; then
  [[ -f "$STAGING/lib/libconscrypt_jni.so" ]] || {
    echo "Missing $STAGING/lib/libconscrypt_jni.so — run stage-y1-system-prep.sh first" >&2
    exit 1
  }
fi
[[ -d "$STAGING/etc/security/cacerts" ]] || {
  echo "Missing $STAGING/etc/security/cacerts" >&2
  exit 1
}

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

run_su() {
  adb shell "su -c '$*'" 2>/dev/null || adb shell "$*"
}

echo "== Root + remount /system =="
adb root 2>/dev/null || true
sleep 1
adb remount 2>/dev/null || run_su "mount -o remount,rw /system" || true

if [[ "$CACERTS_ONLY" -eq 0 ]]; then
  echo "== Push libconscrypt_jni.so =="
  adb push "$STAGING/lib/libconscrypt_jni.so" /system/lib/libconscrypt_jni.so
  run_su "chmod 644 /system/lib/libconscrypt_jni.so"
fi

CACERTS_SRC="$STAGING/etc/security/cacerts"
SYSTEM_CACERTS="/system/etc/security/cacerts"
count="$(find "$CACERTS_SRC" -maxdepth 1 -type f | wc -l)"
echo "== Push $count cacert(s) to $SYSTEM_CACERTS =="
for f in "$CACERTS_SRC"/*; do
  [[ -f "$f" ]] || continue
  name="$(basename "$f")"
  adb push "$f" "${SYSTEM_CACERTS}/${name}"
  run_su "chmod 644 ${SYSTEM_CACERTS}/${name}"
done

echo "== Installed TLS prep on device =="

if [[ "${SOLAR_SKIP_INIT:-0}" != "1" ]]; then
  INIT_SRC="$ROOT/solar-rom/system/99SolarInit.sh"
  if [[ -f "$INIT_SRC" ]]; then
    echo "== Push /system/etc/init.d/99SolarInit.sh =="
    adb push "$INIT_SRC" /system/etc/init.d/99SolarInit.sh
    run_su "chmod 755 /system/etc/init.d/99SolarInit.sh"
  fi
fi
