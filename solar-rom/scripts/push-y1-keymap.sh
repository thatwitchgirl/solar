#!/usr/bin/env bash
# Push Solar Y1 coexistence assets to a rooted device (no full ROM reflash).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCR="$ROOT/solar-rom/scripts"
adb wait-for-device
# ponytail: when two Y1 units share the same serial, target Solar by transport (usb path) or ANDROID_SERIAL.
if [ -z "${ANDROID_SERIAL:-}" ] && [ "$(adb devices | awk 'NR>1 && $2=="device"{print $1}' | sort -u | wc -l)" -gt 1 ]; then
    SOLAR_T="${SOLAR_ADB_TRANSPORT:-5}"
    ADB=(adb -t "$SOLAR_T")
else
    ADB=(adb)
fi
"${ADB[@]}" push "$SCR/Y1-Rockbox.kl" /data/local/tmp/Y1-Rockbox.kl
"${ADB[@]}" push "$SCR/switch-to-stock.sh" /data/local/tmp/switch-to-stock.sh
"${ADB[@]}" push "$SCR/switch-to-rockbox.sh" /data/local/tmp/switch-to-rockbox.sh
"${ADB[@]}" push "$SCR/sync-rockbox-libs.sh" /data/local/tmp/sync-rockbox-libs.sh
"${ADB[@]}" push "$SCR/sync-y1-keymap.sh" /data/local/tmp/sync-y1-keymap.sh
"${ADB[@]}" shell "su -c 'mount -o remount,rw /system
mkdir -p /system/etc/solar /data/data
cp /data/local/tmp/Y1-Rockbox.kl /system/etc/solar/Y1-Rockbox.kl
cp /data/local/tmp/switch-to-stock.sh /system/etc/solar/switch-to-stock.sh
cp /data/local/tmp/switch-to-rockbox.sh /system/etc/solar/switch-to-rockbox.sh
cp /data/local/tmp/sync-rockbox-libs.sh /system/etc/solar/sync-rockbox-libs.sh
cp /data/local/tmp/sync-y1-keymap.sh /system/etc/solar/sync-y1-keymap.sh
chmod 644 /system/etc/solar/Y1-Rockbox.kl
chmod 755 /system/etc/solar/switch-to-stock.sh /system/etc/solar/switch-to-rockbox.sh
chmod 755 /system/etc/solar/sync-rockbox-libs.sh /system/etc/solar/sync-y1-keymap.sh
cp /data/local/tmp/switch-to-stock.sh /data/data/switch-to-stock.sh
cp /data/local/tmp/switch-to-rockbox.sh /data/data/switch-to-rockbox.sh
chmod 755 /data/data/switch-to-stock.sh /data/data/switch-to-rockbox.sh
sh /system/etc/solar/sync-y1-keymap.sh
sh /system/etc/solar/sync-rockbox-libs.sh
echo \"Generic wheel:\"
grep -E \"^key (105|106)\" /system/usr/keylayout/Generic.kl
echo \"mtk-tpd-kpd side + wheel:\"
grep -E \"^key (105|106|163|165)\" /system/usr/keylayout/mtk-tpd-kpd.kl
md5sum /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/mtk-tpd-kpd.kl
md5sum /system/usr/keylayout/Generic.kl /system/usr/keylayout/Stock.kl /system/usr/keylayout/Rockbox.kl'"
echo "Rebooting so InputReader reloads keylayout..."
"${ADB[@]}" reboot
