# Solar-branded boot assets (placeholder)

Drop custom files here when ready. Same layout as `../innioasis-boot/`:

| File | Destination |
|------|-------------|
| `logo.bin` | LOGO partition — `rom.zip` root (early static splash) |
| `boot.img` | BOOTIMG partition — `rom.zip` root (Linux kernel, **not** animation) |
| `bootanimation.zip` | `/system/media/bootanimation.zip` inside `system.img` |
| `bootanimation` | `/system/bin/bootanimation` inside `system.img` |

Build with:

```bash
SOLAR_BOOT_ASSETS=solar-rom/assets/solar-boot ./solar-rom/scripts/build-rom.sh a --apk ...
```

Until then, `../innioasis-boot/` supplies stock Innioasis 3.0.7 boot media.

See `../innioasis-boot/README.md` and `../../scripts/mtk-y1-layout.sh` for MTK flash layout.
