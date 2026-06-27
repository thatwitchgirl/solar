# patches

Byte-level and smali patchers for Innioasis Y1 firmware binaries. Invoked by the top-level [`apply.bash`](../../apply.bash); each patcher can also be run standalone for inspection.

## Files

| Patcher | Target | Wired by |
|---|---|---|
| **`patch_mtkbt.py`** | `mtkbt` — SDP shape (V1/V2/V3/V4/V7/V8/S1/P_PN0), activeVersion override (V6), force-PASSTHROUGH-emit op_code dispatch (P1), AVDTP sig 0x0c → sig 0x02 alias (V5), RegNotif response builder's INTERIM/CHANGED discriminator widen (M1 + M6 — wire ctype now matches the JNI's reasonCode for any AV/C ctype value), Path A chip-readiness gate elimination (M2 + M3 + M10 — sparse-re-registration CTs leave `chan+0xf2` stuck SET after the inbound RegisterNotification INTERIM callback; M10 NOPs the cbnz gate check so subsequent CHANGEDs ship regardless) + Path B list-contains NOP (M4 — covers `fcn.0x6d0f0`), the M5 TID-echo cave (24-byte LOAD #1 code-cave at `0xf3680` skips Path B's `chan[+0x29]` write on outbound packets — discriminator `packet[+0xd] == 0` (outbound, allocator-zeroed) skips, nonzero (inbound, per-channel stash) writes — preserving the per-event TID that the JNI trampoline writes via `restore_conn_tid` for outbound IPC packets), and the AVDTP-CLOSE survivor (M8 — NOPs `AVRCP_HandleA2DPInfo`'s info=1 disconnect call at `0xfa38` so the AVCTP control channel survives audio-stream open/close cycles per AVRCP 1.3 §4 transport independence). | `--avrcp` |
| **`patch_libextavrcp_jni.py`** | `libextavrcp_jni.so` — R1 redirect into the in-blob T1_extended (GetCapabilities advertises events `{0x01, 0x02, 0x05, 0x08, 0x09, 0x0a, 0x0b, 0x0c}`), T2-stub bridge into extended_T2 (RegisterNotification dispatch + `save_event_seq_id` of the inbound transaction id), and the trampoline-blob bodies T4 / T5 / T6 / T8 / T9 / T_charset / T_battery / T_continuation / T_papp plus four shared subroutines (`restore_conn_tid`, `save_event_seq_id`, `event_subscribed`, `clear_event_database`). T4's prologue writes `conn[+0x11] = sp[+0x171]` so non-RegNotif response builders ship with the inbound CMD's TID per AVRCP 1.3 §4.2.1 strict echo. Per-event subscription gating uses the JNI's `.bss` `g_avrcp_req_event_database` (vaddr 0xd2b5, encoded as `seq_id + 1` — 0 unambiguously means "not subscribed this session", session reset is on every CT GetCapabilities via `clear_event_database`). TRACK_CHANGED Identifier ships `0x0000000000000000` ("SELECTED" per AVRCP 1.6 §6.7.2 Table 6.32) — matches a reference 1.3-as-TG implementation's wire shape when no Browseable-Player Now-Playing queue is in use. Y1's SDP record advertises AVRCP 1.3 (which is silent on Identifier value in §5.4.2 Table 5.30), so the 1.6 strict reading applies cleanly. U1 NOPs the AVRCP-uinput `UI_SET_EVBIT(EV_REP)` ioctl to defang the kernel auto-repeat loop on dropped PASSTHROUGH RELEASEs. The blob extends LOAD #1 into a previously-zero-padding region; budget is hard-capped at 4020 bytes (patcher asserts on overflow). Built by `_trampolines.py` via `_thumb2asm.py`. | `--avrcp` |
| **`patch_libextavrcp.py`** | `libextavrcp.so` — E1: 2-byte CBZ→NOP inside `btmtk_avrcp_send_get_element_attributes_rsp` so unsupported attributes emit with `AttributeValueLength=0` per AVRCP 1.3 §5.3.1 Table 5.24 (stock silently drops them). | `--avrcp` |
| **`patch_mtkbt_odex.py`** | `MtkBt.odex` — F1 (`getPreferVersion()` flag), F2 (`disable()` resets `sPlayServiceInterface`), two cardinality NOPs that wake `notificationTrackChangedNative` / `notificationPlayStatusChangedNative` on every `metachanged` / `playstatechanged` broadcast. Recomputes DEX adler32. | `--avrcp` |
| **`patch_y1_apk.py`** | `com.innioasis.y1*.apk` — A/B/C (Artist→Album navigation), Patch E (discrete PASSTHROUGH PLAY/PAUSE/STOP/NEXT/PREVIOUS routing), Patch H (`BaseActivity.dispatchKeyEvent` propagates unhandled media keys). Uses androguard + apktool. | `--music-apk` (all of A/B/C/E/H ship together) |
| **`patch_libaudio_a2dp.py`** | `libaudio.a2dp.default.so` — single-byte cond-flip in `A2dpAudioStreamOut::standby_l` so AudioFlinger's silence-timeout standby leaves the AVDTP source stream alive (no SUSPEND on the wire). Matches AVDTP 1.3 §8.14 / §8.15. | `--avrcp` |
| **`patch_avrcp_kl.py`** | `usr/keylayout/AVRCP.kl` — K1: row 201 (`KEY_PAUSECD`) `MEDIA_PLAY_PAUSE → MEDIA_PAUSE` so stock AOSP's 2010-era coalescing of discrete `PASSTHROUGH 0x46 PAUSE` into the toggle keycode is undone. After K1, `0x46` propagates through Patch H to `PlayControllerReceiver`'s `cond_pause_strict` arm (`pause(0x12, true)`) — discrete pause per AVRCP 1.3 §4.6.1, idempotent on repeat presses. Row 200 (`KEY_PLAYCD → MEDIA_PLAY`) unchanged. | `--avrcp` |

Per-patch byte-level reference (offsets, before/after bytes, rationale, ICS row coverage, spec citations): [`../../docs/PATCHES.md`](../../docs/PATCHES.md).

## Common interface

Each byte patcher (mtkbt / mtkbt_odex / libextavrcp_jni) takes:

```
python3 patch_<name>.py <stock-binary> [--output PATH] [--verify-only] [--skip-md5]
```

- Validates the stock input MD5 against a hardcoded expected value
- Verifies every patch site matches its `before` bytes; refuses to write on mismatch
- Detects "already patched" inputs (every site matches `after`) and exits 0 without writing
- Default output: `output/<name>.patched`

`patch_y1_apk.py` is script-style (no `--output`; output lands in `output/` relative to CWD) — see its docstring for invocation details.

## Manual invocation

Run from this directory so `output/` and `_patch_workdir/` (apktool scratch) land here:

```bash
( cd .. && cd src/patches && python3 patch_mtkbt.py /path/to/stock/mtkbt )
# → src/patches/output/mtkbt.patched
```

The top-level bash always invokes patchers from `src/patches/`; for manual runs it's a convention worth following so the bash can pick up the output if you switch to `--avrcp` afterwards.

## Idempotency

The bash's `patch_in_place_bytes` helper detects "already patched" exit-0-without-output and skips the write-back. Re-running `--avrcp` against an already-patched mount is a no-op.

## Requirements

- Python 3.8+, stdlib only, for all byte patchers.
- `patch_y1_apk.py` additionally requires Java 11–21 (apktool 2.9.3's bundled smali assembler can silently drop patches on Java 22+ — the patcher refuses to write if its DEX-signature check fails) and `androguard` (`pip install androguard`). apktool is downloaded once into `tools/apktool-2.9.3.jar` and reused. Decoded smali + rebuilt DEX persist under `staging/y1-apk/` (`--clean-staging` forces a fresh decode). MD5 pinned to stock 3.0.2 / 3.0.7; `--skip-md5` bypasses.

## Debug logging

`apply.bash --debug` (or `KOENSAYR_DEBUG=1` in the env) routes diagnostic logging through three independent tags:

- **`Y1Patch :`** — Java-side. `patch_y1_apk.py` injects `Log.d("Y1Patch", …)` at every metadata-relevant entry point and inline `_dbgKV(key, long)` at diagnostic-critical sites in `TrackInfoWriter` + `PlaybackStateBridge.onPlayValue`. Tail with `adb logcat -s Y1Patch:*`.
- **`Y1T :`** — Native trampoline-side. `patch_libextavrcp_jni.py` splices `__android_log_print(INFO, "Y1T", fmt, value)` at two inbound CMD entry markers (`T1pdu=%02x` — T4 reached on every non-RegNotif AV/C CMD; `T2reg ev=%02x` — extended_T2 reached on every inbound `RegisterNotification`) and three outbound CHANGED emit markers: `T9ps` (PLAYBACK_STATUS), `T9papp` (PLAYER_APPLICATION_SETTING_CHANGED), `T9pos=%08x` (PlaybackPositionChanged live_pos in ms). Pairs of `T2reg ev=N` + matching outbound marker disambiguate "CT didn't subscribe to ev=N" from "CT subscribed but our CHANGED gate skipped." Gating uses `g_avrcp_req_event_database[event_id] != 0`, the session-scope subscription state populated by `save_event_seq_id` on every inbound `RegisterNotification`. `patch_mtkbt.py` adds mtkbt-side logs hooked into the AVCTP wire-frame builder and the M5 TID-echo cave: `M5wire c39=` (chan+0x39 at wire emit) plus `M5dbg p8 / pd` (packet[+8], packet[+0xd] at cave exit). Tail with `adb logcat -s Y1T:*`.
- **mtkbt's own xlog stream (btlog).** Not affected by `--debug` — these are stock mtkbt internals (`avctpCB`, `[AVCTP] chid:`, `avrcp: sbunit type:`). Capture with `tools/dual-capture.sh` and parse via [`../../tools/btlog-parse.py`](../../tools/btlog-parse.py) (use `--avrcp` for the AVRCP/AVCTP-only preset that pairs cleanly with the `Y1T` logcat trace).

Release builds are byte-identical without the env var. Coverage list: [`../../docs/PATCHES.md`](../../docs/PATCHES.md) §"`--debug` instrumentation".

## Status

Active patchers (wired into the bash):
- `patch_mtkbt.py`, `patch_mtkbt_odex.py`, `patch_libextavrcp_jni.py`, `patch_libextavrcp.py`, `patch_libaudio_a2dp.py`, `patch_avrcp_kl.py`, `patch_y1_apk.py`

Root escalation is handled by [`../su/`](../su/) (setuid `/system/xbin/su`).

## See also

- [`../../README.md`](../../README.md) — project overview
- [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) — **AVRCP metadata proxy architecture**: data-path diagram, trampoline chain, response-builder calling conventions, ELF program-header surgery, code-cave inventory. Read this first if extending the trampoline chain or adding new PDU handlers.
- [`../../docs/PATCHES.md`](../../docs/PATCHES.md) — per-patch byte-level reference (offsets, before/after bytes, rationale)
- [`../../docs/INVESTIGATION.md`](../../docs/INVESTIGATION.md) — chronological investigation history (gdbserver capture work, dead-end paths, hypothesis evolution)
- [`../../CHANGELOG.md`](../../CHANGELOG.md) — top-level changelog
