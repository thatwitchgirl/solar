#!/usr/bin/env bash
# Apply boot splash assets for Innioasis Y1 (MT6572).
#
# Partition images (logo.bin, boot.img) → rom staging dir beside MT6572_Android_scatter.txt
#   LOGO partition    = logo.bin   (first static splash, before Android)
#   BOOTIMG partition = boot.img   (Linux kernel — not bootanimation)
#
# Android boot animation → inside system.img only:
#   /system/media/bootanimation.zip
#   /system/bin/bootanimation
#
# Usage: apply-innioasis-boot.sh <rom_staging_dir> <mounted_system>
set -euo pipefail

ROM_DIR="${1:-}"
MOUNT_SYS="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/mtk-y1-layout.sh"
ASSETS_DIR="${SOLAR_BOOT_ASSETS:-$SCRIPT_DIR/../assets/innioasis-boot}"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$ROM_DIR" ] && [ -d "$ROM_DIR" ] || die "usage: $0 <rom_staging_dir> <mounted_system>"
[ -n "$MOUNT_SYS" ] && [ -d "$MOUNT_SYS" ] || die "usage: $0 <rom_staging_dir> <mounted_system>"
[ -d "$ASSETS_DIR" ] || die "boot assets missing: $ASSETS_DIR"
[ -f "$ROM_DIR/$MTK_Y1_SCATTER" ] || die "scatter missing in $ROM_DIR — unzip base rom.zip first"

for f in "${MTK_Y1_BOOT_ASSET_FILES[@]}"; do
    [ -f "$ASSETS_DIR/$f" ] || die "missing asset $ASSETS_DIR/$f"
done

echo "==> Boot assets from $ASSETS_DIR"

# --- eMMC partition images (rom.zip root; flashed via scatter / mtkclient) ---
for part in "${MTK_Y1_BOOT_PARTITION_ASSETS[@]}"; do
    src="$ASSETS_DIR/$part"
    dest="$ROM_DIR/$part"
    case "$part" in
        logo.bin)
            mtk_y1_check_partition_image_size "$src" "$MTK_Y1_LOGO_MAX_BYTES" "LOGO" \
                || die "logo.bin too large for LOGO partition"
            echo "  LOGO partition  ← $part ($(stat -c%s "$src") bytes)"
            ;;
        boot.img)
            mtk_y1_check_partition_image_size "$src" "$MTK_Y1_BOOTIMG_MAX_BYTES" "BOOTIMG" \
                || die "boot.img too large for BOOTIMG partition"
            echo "  BOOTIMG (kernel) ← $part ($(stat -c%s "$src") bytes)"
            ;;
    esac
    cp "$src" "$dest"
done

# --- Android bootanimation (system.img contents only) ---
install_system_file() {
    local rel="$1"
    local src="$2"
    local dest="$MOUNT_SYS/$rel"
    sudo mkdir -p "$(dirname "$dest")"
    sudo cp "$src" "$dest"
    case "$rel" in
        bin/*) sudo chmod 755 "$dest" ;;
        *)     sudo chmod 644 "$dest" ;;
    esac
    sudo chown root:root "$dest"
    echo "  /system/$rel"
}

install_system_file "media/bootanimation.zip" "$ASSETS_DIR/bootanimation.zip"
install_system_file "bin/bootanimation" "$ASSETS_DIR/bootanimation"

echo "==> Boot assets applied (partition images in $ROM_DIR; animation in system.img)"
