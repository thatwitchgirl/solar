# Stock Innioasis 3.0.7 boot assets

Verified against [y1-community/y1-stock-rom Latest-3.0.7](https://github.com/y1-community/y1-stock-rom/releases/tag/Latest-3.0.7).

## Where each file goes (MT6572 / Innioasis Y1)

| Asset | Flash target | What it is |
|-------|----------------|------------|
| `logo.bin` | **LOGO** eMMC partition — loose file in `rom.zip` beside `MT6572_Android_scatter.txt` | First static splash (before Android). Edit with Logo Builder / mtklogo, flash via SP Flash or `mtk w logo logo.bin`. |
| `boot.img` | **BOOTIMG** partition — loose file in `rom.zip` | Linux **kernel** image (not boot animation). |
| `bootanimation.zip` | **`/system/media/bootanimation.zip`** inside `system.img` | Android boot animation zip (after logo). |
| `bootanimation` | **`/system/bin/bootanimation`** inside `system.img` | Executable that plays `bootanimation.zip`. |

There is **no** `bootanimation.img` partition. Do not put `bootanimation.zip` in `rom.zip` root — it only takes effect when baked into `system.img`.

### mtkclient (BROM mode, from unpacked rom folder)

```bash
# Splash + kernel + system (animation is inside system.img):
python mtk w logo,bootimg,android logo.bin,boot.img,system.img

# Full image set (rockbox-y1):
python mtk w logo,uboot,bootimg,recovery,android,usrdata \
  logo.bin,lk.bin,boot.img,recovery.img,system.img,userdata.img
```

## Checksums (stock 3.0.7)

| File | MD5 |
|------|-----|
| `boot.img` | `83b946d1799b4f0281ba8e808ed7911b` |
| `logo.bin` | `887a32d6ff4c7b1f04b6cfdf22fef532` |
| `bootanimation.zip` | `b65b1227350f4ded743a1fe61b0a4eb1` |
| `bootanimation` | `5ef02d5f39955e22f231618d7557b148` |

Applied by `solar-rom/scripts/apply-innioasis-boot.sh` during `build-rom.sh` (see `mtk-y1-layout.sh` for partition limits).

Custom Solar splash: use `../solar-boot/` and set `SOLAR_BOOT_ASSETS` to that directory.
