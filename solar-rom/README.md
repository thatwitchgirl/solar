# Solar ROM builder (Innioasis Y1)

Builds flashable **type A** and **type B** firmware images from the [Rockbox-Y1](https://github.com/rockbox-y1/rockbox) stock bases, with **Solar** (`com.solar.launcher`) as the system launcher.

Release source: [github.com/thatwitchgirl/solar](https://github.com/thatwitchgirl/solar)

## Branches

| Branch | CI workflow | Versioning |
|--------|-------------|------------|
| `nightly` | **Nightly release** — every push | Tags `nightly-{build}` — APK, `rom.zip`, `rom_type_b.zip` (prerelease) |
| `main` | **Stable release** — every push | Tags `v0.1`, `v0.2`, … (+0.1 per release); first stable is **0.1** |

Day-to-day development pushes **`nightly`** and gets installable builds automatically — no merge required.

Push **`main`** only when intentionally cutting a semver stable release (cherry-picks or direct commits). Merging `nightly` into `main` is optional, not the normal build path.

## Local build

```bash
./scripts/build.sh                    # signed app-release.apk
./solar-rom/scripts/build-rom.sh a --apk app/build/outputs/apk/release/app-release.apk rom.zip
./solar-rom/scripts/build-rom.sh b --apk app/build/outputs/apk/release/app-release.apk rom_type_b.zip
```

Requires `curl`, `unzip`, `zip`, `openssl`, `sudo`, and loop-mount support for ext4 (`e2fsprogs`).

Each ROM includes, on the **system** partition:

| Path | Purpose |
|------|---------|
| `/system/app/com.solar.launcher.apk` | Solar launcher (platform-signed) |
| `/system/lib/libconscrypt_jni.so` | Conscrypt JNI for TLS 1.2+ (Reach, podcasts, themes via OkHttp) |
| `/system/etc/security/cacerts/*.0` | Modern CA roots (Let's Encrypt, etc.) for **MediaPlayer** HTTPS and all apps |
| `/system/etc/init.d/99SolarInit.sh` | Boot: create `Music` / `Podcasts` / `Themes` on SD; AVRCP track-info dir; log if TLS prep missing |
| `/system/app/Y1Bridge.apk` | AVRCP Binder host (when Koensayr patches applied) |
| Patched `mtkbt`, `libextavrcp*.so`, `AVRCP.kl` | Full AVRCP 1.3 metadata + car-stereo transport (Koensayr) |
| `logo.bin`, `boot.img` (rom.zip root) | LOGO + BOOTIMG partitions — early splash + kernel ([details](assets/innioasis-boot/README.md)) |
| `/system/media/bootanimation.zip`, `/system/bin/bootanimation` | Android boot animation inside `system.img` only |

These match what `./scripts/clean_install_system.sh` applies on a rooted device. Shared staging: `scripts/stage-y1-system-prep.sh` → `apply-y1-system-prep.sh` (ROM) or `push-y1-system-prep.sh` (adb). `SolarApplication` loads Conscrypt at boot; system cacerts are still required for stock HTTPS stacks (podcast streaming via MediaPlayer).

### AVRCP (Koensayr)

Full Bluetooth receiver metadata and transport controls require **native system patches** from [Koensayr](https://github.com/SeanathanVT/koensayr). OTA APK updates alone cannot install these — flash a Solar ROM built with Koensayr present.

```bash
git clone https://github.com/SeanathanVT/koensayr.git solar-rom/koensayr
# or: export KOENSAYR_DIR=/path/to/koensayr
./solar-rom/scripts/build-rom.sh a --apk app/build/outputs/apk/release/app-release.apk rom.zip
```

`build-rom.sh` runs `apply-koensayr-avrcp.sh` when `solar-rom/koensayr` exists. Solar writes track state to the legacy `y1-track-info` path expected by the patched BT stack.

### Boot splash (Innioasis stock)

Rockbox base firmware ships Rockbox-branded `logo.bin` (3 MiB LOGO partition fill), `boot.img`, and system `bootanimation.zip`. Solar ROM builds replace these with verified stock Innioasis 3.0.7 assets from [`solar-rom/assets/innioasis-boot/`](solar-rom/assets/innioasis-boot/) via `apply-innioasis-boot.sh` and [`mtk-y1-layout.sh`](solar-rom/scripts/mtk-y1-layout.sh).

- **Partition images** (`logo.bin`, `boot.img`) are copied to the rom staging directory and packaged flat beside `MT6572_Android_scatter.txt`.
- **Boot animation** (`bootanimation.zip` + `bootanimation` binary) is written into the mounted `system.img` only.

Future custom Solar boot media: [`solar-rom/assets/solar-boot/`](solar-rom/assets/solar-boot/) + `SOLAR_BOOT_ASSETS`.


## Device install (rooted, without full ROM flash)

```bash
./scripts/build.sh
./scripts/clean_install_system.sh    # full: remove old launchers + APK + TLS prep
# or quick update:
./scripts/install.sh --system
./scripts/install_modern_cacerts.sh  # cacerts only
```

Override release repo for ROM downloads: `SOLAR_GITHUB_REPO=thatwitchgirl/solar` (default).

## CI

| Workflow | Trigger |
|----------|---------|
| `.github/workflows/build-nightly.yml` | Push to **`nightly`** (or manual dispatch) |
| `.github/workflows/build-stable.yml` | Push to **`main`** (or manual dispatch); skips version-bump-only commits |

Both call `.github/workflows/release-build.yml`: sign the release APK (platform keys in repo secrets), build both ROM zips, publish a GitHub release, and update OTA.

CI caches Gradle dependencies (`gradle/actions/setup-gradle`), Android SDK packages, apt packages for ROM tools, and Rockbox-Y1 base firmware zips (`SOLAR_ROM_BASE_CACHE`). There is no npm/Node toolchain in this repo.

Required secrets: `SOLAR_PLATFORM_KEY_PK8_B64`, `SOLAR_PLATFORM_KEY_PEM_B64` (base64-encoded AOSP test platform key material).
