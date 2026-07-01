# Solar theming (Y1 themes + solarConfig)

Solar runs on Innioasis Y1/Y2 hardware and reuses the stock Y1 theme format (`config.json` + PNG assets). Solar adds an optional **`solarConfig`** block for features that do not exist on stock Y1 firmware.

Reference gallery themes: [InnioasisY1Themes](https://github.com/thesolarproject/InnioasisY1Themes) — same `homePageConfig`, `settingConfig`, `itemConfig`, etc.

## Precedence

For any icon or color Solar supports in both places:

1. **Active theme `solarConfig`** (if the key is set and the asset file exists in that theme folder)
2. **Stock Y1 block** in the **same** theme (`homePageConfig`, `settingConfig`, …)
3. **Nothing** — hide the preview icon (no Android drawable, no bundled Aura, no sibling-theme files)

Never use unrelated Y1 keys (e.g. `shuffleQuick`) as a stand-in for Solar-only shortcuts like Wi‑Fi Transfer.

`solarConfig` from the bundled Aura default theme is **never** merged into third-party themes for **icons** (only non-icon keys like colors may merge on Default upgrade).

## Open-ended keys

Theme authors add keys freely. Solar derives lookup names from **English UI labels** so keys stay stable when the user changes device language.

| Pattern | Used for | Example label | Key |
|---------|----------|---------------|-----|
| `app{Name}` | Home screen shortcut preview / editor | `Music` | `appMusic` |
| `app{Name}` | Solar-only shortcuts | `Get Music` | `appGet_Music` |
| `settings{Name}` | Settings right-pane preview | `About` | `settingsAbout` |

**Sanitization:** non-alphanumeric runs become `_`, leading/trailing `_` trimmed. Legacy app keys without underscores (e.g. `appPCUpload`) are still accepted as a fallback.

**Value:** filename of a PNG (or other image) in the theme folder, same as Y1 keys.

### Stock home shortcuts

Stock rows (Music, Bluetooth, Settings, …) check `solarConfig` **before** `homePageConfig`. Example: `appMusic` overrides `homePageConfig.music`.

### Solar-only shortcuts (explicit Y1 fallbacks only)

| Shortcut | solarConfig | homePageConfig fallback |
|----------|-------------|-------------------------|
| Get Music | `appGet_Music` | `music` |
| Deezer | `appDeezer` | `music` |
| Podcasts | `appPodcasts` | `audiobooks` |
| Photos | `appPhotos` | `photos` |
| Wi‑Fi Transfer | `appPC_Upload` / `appPCUpload` | *(none)* |

### Settings rows

Right-pane icons use `settings{Name}` from the **English** row title (`SolarTheming.englishSettingsRowLabel`), then Reach-specific keys (`soulseekSearch`, …), then `app{Name}` for app rows (e.g. Reach → `appReach`), then `settingConfig`.

#### Renamed labels (theme authors)

If you shipped icons under old English titles, update `solarConfig` keys:

| Settings row (current English) | solarConfig key | Was |
|-------------------------------|-----------------|-----|
| Connections | `settingsConnections` | *(new submenu parent)* |
| Device | `settingsDevice` | *(new submenu parent)* |
| Playback | `settingsPlayback` | *(was root — Shuffle/Repeat/EQ)* |
| Library | `settingsLibrary` | *(new submenu parent)* |
| Media | `settingsMedia` | *(new submenu parent)* |
| Power | `settingsPower` | *(new submenu parent)* |
| USB | `settingsUSB` | *(new submenu parent)* |
| Wi-Fi | `settingsWi_Fi` | `settingsWi_Fi_Setup` |
| Bluetooth | `settingsBluetooth` | `settingsBluetooth_Setup` |

Submenu parents use the same `settings{Name}` rule as top-level rows, e.g. bundled Aura uses `settingsAbout` for the About screen icon.

Example:

```json
"solarConfig": {
  "settingsAbout": "square_full_logo_colour.png",
  "settingsConnections": "Bluetooth_YS.png",
  "settingsWi_Fi": "wifi_icon.png"
}
```

## Settings defaults (`enable*` / `disable*` / `set*`)

Themes can ship recommended **Appearance** (and related) prefs inside `solarConfig`. When the user applies the theme, Solar writes these into app `SharedPreferences` so the launcher matches the author’s intent without manual toggling.

| Prefix | Meaning | JSON value |
|--------|---------|------------|
| `enable` | Turn the setting **on** | Ignored (`true`, `"true"`, or any placeholder) |
| `disable` | Turn the setting **off** | Ignored |
| `set` | Set an explicit value | Boolean `true`/`false`, or an **English option label** for multi-choice rows (see below) |

### Deriving `{MenuItemName}`

1. Take the **registered lookup label** for the settings row (see table below — usually the English title from `values/strings.xml`, but word order can differ).
2. Replace each run of non-alphanumeric characters with a single `_` (same rule as `app{Name}` / `settings{Name}`).
3. Prepend `enable`, `disable`, or `set`.

Solar strips the prefix, converts `_` → spaces, lowercases, and drops any `(` hint suffix, then matches against `SettingLookup`.

Examples:

| Settings row (UI) | solarConfig key |
|-------------------|-----------------|
| Match Now Playing to Theme | `enableMatch_Now_Playing_To_Theme` |
| LCD album art | `enableLCD_album_art` |
| Match Status Bar to Theme Font | `enableStatus_Bar_Match_Font` |
| Full Width Menus | `enableFull_Width_Menus` |
| Status Bar Text | `setStatus_Bar_Text` |

Parenthetical hints on the row (e.g. `Full Width Menus (experimental)`) are ignored for lookup — only the text before `(` counts.

### Supported boolean rows

Registered in `SettingLookup` today:

| Settings row (UI) | Lookup label | `enable*` / `disable*` key | SharedPreferences key |
|-------------------|--------------|---------------------------|------------------------|
| Full Width Menus | full width menus | `enableFull_Width_Menus` | `full_width_menus` |
| LCD album art | lcd album art | `enableLCD_album_art` | `now_playing_lcd_art` |
| Artwork perspective | artwork perspective | `setArtwork_Perspective` | `artwork_perspective` (+ syncs `now_playing_3d_album_art`) |
| Match Now Playing to Theme | match now playing to theme | `enableMatch_Now_Playing_To_Theme` | `now_playing_match_font` |
| Now Playing Backdrop | now playing backdrop | `enableNow_Playing_Backdrop` | `now_playing_backdrop` |
| Match Status Bar to Theme Font | status bar match font | `enableStatus_Bar_Match_Font` | `status_bar_match_font` |
| USB Auto-Connect | usb auto connect / auto-connect | `enableUSB_Auto_Connect` | `usb_auto_connect` |
| Skip Plug-In Prompt | skip plug-in prompt | `enableSkip_Plug_In_Prompt` | `usb_suppress_connect_prompt` |

Unknown labels are skipped silently. To support a new boolean row, add a `put(...)` entry in `SettingLookup` and document it here.

### Multi-choice rows (`set*`)

Some settings are not simple on/off toggles. Use `set{MenuItemName}` with the **English right-pane option label**:

| Settings row | Key | Allowed values |
|--------------|-----|----------------|
| Status Bar Text | `setStatus_Bar_Text` | `Clock`, `Title` |
| Artwork perspective | `setArtwork_Perspective` | `Flat`, `3D` (default **3D** — slanted cover in Now Playing and Flow handoff) |

(`Clock` shows the time on the home menu status bar; `Title` shows the current menu/screen title.)

### Aura default example

Bundled Aura (`app/src/main/assets/themes/default/config.json`):

```json
"solarConfig": {
  "setStatus_Bar_Text": "Clock",
  "enableMatch_Now_Playing_To_Theme": "true",
  "enableLCD_album_art": "false",
  "setArtwork_Perspective": "3D",
  "enableStatus_Bar_Match_Font": "true"
}
```

Parsed by `SettingLookup.applySolarConfigOverrides()` — see implementation map.

## Other solarConfig keys

Colors (`homeUnselectedColor`, `settingSelectedColor`, …), `button_radius`, `nowPlayingInfoBars`, status bar Wi‑Fi/battery frame arrays, gallery install metadata — see `ThemeManager.solarBlock()` and bundled `app/src/main/assets/themes/default/config.json`.

## Adding a new Solar feature

When you add a user-visible home shortcut or settings row:

1. Add the shortcut to `HomeMenuConfig` or row to `RowKeys` / settings builder.
2. Wire icon via `ThemeManager.getHomeMenuIcon()` or `ThemeManager.getSettingsRowIcon()` (do not hard-code drawable paths).
3. Document the English label → `app*` / `settings*` key in this file.
4. If the feature has a user-facing boolean or choice pref, register it in `SettingLookup` and document the `enable*` / `set*` key.
5. Optionally add a sample asset or default override to Aura `themes/default/config.json`.
6. Add a unit test in `ThemeManagerTest` or `SolarTheming.selfCheck()` if the key rule is non-obvious.

## Implementation map

| Concern | Code |
|---------|------|
| Key derivation | `ThemeManager.solarAppConfigKey`, `solarSettingsConfigKey` |
| English labels | `SolarTheming.englishString`, `englishSettingsRowLabel` |
| Home icons | `ThemeManager.getHomeMenuIcon` |
| Settings preview icons | `ThemeManager.getSettingsRowIcon` |
| Y1 stock home | `homePageConfig` via `ThemeManager.getHomeIcon` |
| Y1 stock settings | `settingConfig` via `ThemeManager.getSettingIcon` |
| Settings pref overrides | `SettingLookup.applySolarConfigOverrides` |
| Label → pref key map | `SettingLookup.prefKeyForLabel` |
