#!/usr/bin/env bash
# MTK MT6572 Innioasis Y1 (g368_nyx) — where boot assets belong for SP Flash Tool / mtkclient.
#
# Early splash:  logo.bin  → LOGO partition   (rom.zip root, scatter file_name: logo.bin)
# Linux kernel:  boot.img  → BOOTIMG partition (rom.zip root — NOT boot animation)
# Android anim:  bootanimation.zip → /system/media/ inside system.img
#                bootanimation     → /system/bin/ inside system.img
#
# mtkclient example (from rockbox-y1 docs, BROM mode):
#   python mtk w logo,bootimg,android logo.bin,boot.img,system.img
# Full flash:
#   python mtk w logo,uboot,bootimg,recovery,android,usrdata \
#     logo.bin,lk.bin,boot.img,recovery.img,system.img,userdata.img
#
# This file is sourced by apply-innioasis-boot.sh and build-rom.sh — not executed directly.

MTK_Y1_SCATTER="MT6572_Android_scatter.txt"

# Partition max sizes from MT6572_Android_scatter.txt (g368_nyx / rockbox-y1 type-A base).
MTK_Y1_LOGO_MAX_BYTES=$((0x300000))      # 3 MiB
MTK_Y1_BOOTIMG_MAX_BYTES=$((0x600000))   # 6 MiB

# Files that must appear beside the scatter in a flashable rom.zip (flat layout).
# Order matches typical SP Flash Tool listing; all are required for a full flash package.
MTK_Y1_ROM_IMAGE_FILES=(
    "$MTK_Y1_SCATTER"
    preloader_g368_nyx.bin
    MBR
    EBR1
    lk.bin
    boot.img
    recovery.img
    logo.bin
    secro.img
    system.img
    cache.img
    userdata.img
)

# Partition images Solar may replace from SOLAR_BOOT_ASSETS (rom.zip root only).
MTK_Y1_BOOT_PARTITION_ASSETS=(
    logo.bin
    boot.img
)

# Paths inside mounted system.img (never loose files in rom.zip).
MTK_Y1_SYSTEM_BOOT_PATHS=(
    media/bootanimation.zip
    bin/bootanimation
)

# Asset filenames expected under innioasis-boot/ or solar-boot/.
MTK_Y1_BOOT_ASSET_FILES=(
    logo.bin
    boot.img
    bootanimation.zip
    bootanimation
)

mtk_y1_check_partition_image_size() {
    local file="$1"
    local max="$2"
    local label="$3"
    local size
    size="$(stat -c%s "$file" 2>/dev/null || echo 0)"
    if [ "$size" -gt "$max" ]; then
        echo "error: $label $(basename "$file") is ${size} bytes (max $max for Y1 scatter)" >&2
        return 1
    fi
    return 0
}
