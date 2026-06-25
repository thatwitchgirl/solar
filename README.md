<p align="center">
  <img src="app/src/main/assets/logo/square_full_logo_colour.png" alt="Solar" width="160">
</p>

# Solar

Custom firmware and launcher for the **Innioasis Y1** — a full interface replacement with podcast downloads, a global quick menu, **Reach** (Deezer + Soulseek), Y1 theme support, and **Rockbox-Y1** on the same ROM (switch without reboot).

## Features

- **Reach** — search and download music from Deezer and Soulseek; messaging, chat rooms, and peer browse
- **Podcasts** — search, subscribe, and download episodes to SD
- **Quick menu** — global context menu for playback, queue, Wi‑Fi, Bluetooth, and more from any screen
- **Y1 themes** — install and apply themes from the original Y1 custom-firmware gallery
- **Rockbox-Y1** — co-installed; switch launchers from Settings (unified keymap, no reboot)

## Screenshots

| | |
|:---:|:---:|
| ![About Solar](screenshots/About%20Solar.png) | ![Reach](screenshots/Reach.png) |
| ![Reach browse](screenshots/Reach%202.png) | ![Search for music](screenshots/Search%20for%20Music.png) |
| ![Soulseek messaging](screenshots/Soulseek%20Messaging.png) | ![Podcasts](screenshots/Podcasts.png) |
| ![Podcasts detail](screenshots/Podcasts%202.png) | ![Artists view](screenshots/Artists%20View.png) |
| ![Quick controls](screenshots/Quick%20Controls.png) | ![ACmp3 theme on Solar](screenshots/ACmp3%20theme%20%20on%20Solar.png) |

## Build & install

See [solar-rom/README.md](solar-rom/README.md) for ROM builds and flash instructions. Day-to-day development targets the **`nightly`** branch; stable releases ship from **`main`**.

```bash
./scripts/build.sh
./solar-rom/scripts/build-rom.sh a --apk app/build/outputs/apk/release/app-release.apk rom.zip
```

Release artifacts: signed APK and Y1 ROM zips from [GitHub Releases](https://github.com/thesolarproject/solar/releases).
