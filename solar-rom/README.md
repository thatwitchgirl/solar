# Solar ROM builder (Innioasis Y1)

Builds flashable **type A** and **type B** firmware images from the [Rockbox-Y1](https://github.com/rockbox-y1/rockbox) stock bases, with **Solar** (`com.solar.launcher`) as the system launcher.

Release source: [github.com/thatwitchgirl/solar](https://github.com/thatwitchgirl/solar)

## Branches

| Branch | Releases | Versioning |
|--------|----------|------------|
| `nightly` | Every push | Tags `nightly-{build}` — APK, `rom.zip`, `rom_type_b.zip` |
| `main` | Stable | Tags `v0.1`, `v0.2`, … (+0.1 per release); first stable is **0.1** |

Day-to-day development targets **`nightly`**. Merge to **`main`** when cutting a stable release.

## Local build

```bash
./scripts/build.sh                    # signed app-release.apk
./solar-rom/scripts/build-rom.sh a --apk app/build/outputs/apk/release/app-release.apk rom.zip
./solar-rom/scripts/build-rom.sh b --apk app/build/outputs/apk/release/app-release.apk rom_type_b.zip
```

Requires `curl`, `unzip`, `zip`, `sudo`, and loop-mount support for ext4 (`e2fsprogs`).

Override release repo for ROM downloads: `SOLAR_GITHUB_REPO=thatwitchgirl/solar` (default).

## CI

`.github/workflows/build-release.yml` runs on pushes to **`main`** and **`nightly`**: signs the release APK (platform keys in repo secrets), builds both ROM zips, and publishes a GitHub release.

Required secrets: `SOLAR_PLATFORM_KEY_PK8_B64`, `SOLAR_PLATFORM_KEY_PEM_B64` (base64-encoded AOSP test platform key material).
