# Y1-Rockbox.kl — unified Generic.kl / Stock.kl / Rockbox.kl on Solar ROM

**Runtime fact (Y1 MT6572):** `dumpsys input` binds both `mtk-kpd` and `mtk-tpd-kpd` to **`Generic.kl`**, not `mtk-kpd.kl`. Side-button scancodes 163/165 must map to MEDIA in this file.

| Scancode | Y1-Rockbox.kl | Android | Role |
|----------|---------------|---------|------|
| 105 wheel CCW | **MEDIA_PLAY** | **126** | Rockbox + Solar scroll up |
| 106 wheel CW | **MEDIA_PAUSE** | **127** | Rockbox + Solar scroll down |
| 165 prev btn | **MEDIA_PREVIOUS** | **88** | Rockbox skip back / Solar prev |
| 163 next btn | **MEDIA_NEXT** | **87** | Rockbox skip forward / Solar next |

`mtk-kpd.kl` / `mtk-tpd-kpd.kl` are kept as patched phone maps (mirror of mtk-kpd) for firmware that does load per-device layouts.

ROM build keeps `org.rockbox.apk` + `librockbox.so` from the rockbox-y1 base image
(codec `.so` files live inside the APK under `lib/armeabi/`). Audit requires ≥35 native libs.

Test without reflash: `solar-rom/scripts/push-y1-keymap.sh`

**Codec lib sync:** `sync-rockbox-libs.sh` re-extracts `lib/armeabi/*.so` from `org.rockbox.apk` into `/data/data/org.rockbox/lib/` when stale. Solar runs this on boot and before every Switch to Rockbox (`RockboxLibSync.java`). See `.cursor/rules/rockbox-y1-coexistence.mdc`.
