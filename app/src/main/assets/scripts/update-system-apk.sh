#!/system/bin/sh
# Root: replace Solar at /system/app without touching app data (upgrade/downgrade/sidegrade).
# Usage: update-system-apk.sh /path/to/downloaded.apk
APK="$1"
DEST="/system/app/com.solar.launcher.apk"
[ -n "$APK" ] && [ -f "$APK" ] || exit 1
mount -o remount,rw /system 2>/dev/null || true
cp "$APK" "$DEST" || exit 1
chmod 644 "$DEST" || exit 1
sync
# Reboot in-shell immediately after replace — Java must not resume once this APK is overwritten.
reboot
