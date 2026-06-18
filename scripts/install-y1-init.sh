#!/usr/bin/env bash
# Push Solar boot init script to /system/etc/init.d/ on a rooted Y1.
# Usage: install-y1-init.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INIT_SRC="$ROOT/solar-rom/system/99SolarInit.sh"
INIT_DST="/system/etc/init.d/99SolarInit.sh"

[[ -f "$INIT_SRC" ]] || { echo "Missing $INIT_SRC" >&2; exit 1; }

# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

run_su() {
  adb shell "su -c '$*'" 2>/dev/null || adb shell "$*"
}

echo "== Root + remount /system =="
adb root 2>/dev/null || true
sleep 1
adb remount 2>/dev/null || run_su "mount -o remount,rw /system" || true

echo "== Push $INIT_DST =="
adb push "$INIT_SRC" "$INIT_DST"
run_su "chmod 755 $INIT_DST"

echo "== Installed Solar boot init =="
