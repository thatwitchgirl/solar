"""
Trampoline assembly for libextavrcp_jni.so.

Builds the dynamically-assembled trampoline blob at vaddr 0xac54 in the
LOAD #1 page-padding area. Per-trampoline behaviour: docs/PATCHES.md.
Stack-frame / calling convention: docs/ARCHITECTURE.md. On-disk file
schema: docs/BT-COMPLIANCE.md §4. PLT inventory: docs/BT-COMPLIANCE.md §3.

read(2) and clock_gettime(2) aren't in the PLT — issued via SVC #0 with
r7 = NR_read (3) / NR_clock_gettime (263).
"""

import os

from _thumb2asm import Asm

# Placeholder for future native-side trace edits; no trampoline emits Log
# calls today.
DEBUG_LOGGING = os.environ.get("KOENSAYR_DEBUG", "") == "1"

# ---------------------------------------------------------------- constants
# Blob base address (start of LOAD #1 page-padding region). Named T4_VADDR
# for historical reasons — T4 used to be the first trampoline in the blob
# before T1 was relocated in. The current first trampoline at this vaddr is
# T1_extended.
T4_VADDR = 0xac54

PLT_open                       = 0x363c
PLT_close                      = 0x33d8
PLT_strlen                     = 0x34d4
PLT_memset                     = 0x33fc
PLT_write                      = 0x3630
# Resolved via liblog.so DT_NEEDED. Only emitted under build(debug=True).
PLT_android_log_print          = 0x3300
PLT_get_capabilities_rsp       = 0x35dc
PLT_get_element_attributes_rsp = 0x3570
PLT_track_changed_rsp          = 0x3384
# Inform PDUs (CT→TG informational acks).
PLT_inform_charsetset_rsp      = 0x3588
PLT_battery_status_rsp         = 0x357c
# GetPlayStatus.
PLT_get_playstatus_rsp         = 0x3564
# RegisterNotification dispatcher (events ≠ 0x02).
PLT_reg_notievent_playback_rsp        = 0x339c
PLT_reg_notievent_reached_end_rsp     = 0x3378
PLT_reg_notievent_reached_start_rsp   = 0x336c
PLT_reg_notievent_pos_changed_rsp     = 0x3360
PLT_reg_notievent_battery_status_rsp  = 0x3354
PLT_reg_notievent_system_status_rsp   = 0x3348
PLT_reg_notievent_player_appsettings_rsp = 0x345c
# 1.4 event-ID response builders (T8 INTERIM-only, zero/empty payload —
# advertised in T1 to unblock strict CT metadata-pane render).
PLT_reg_notievent_now_playing_content_rsp = 0x330c
PLT_reg_notievent_uids_changed_rsp        = 0x3318
PLT_reg_notievent_availplayers_rsp        = 0x3324
PLT_reg_notievent_addredplayer_rsp        = 0x3330

# PlayerApplicationSettings PDUs 0x11-0x16 (T_papp).
PLT_list_player_attrs_rsp        = 0x35d0
PLT_list_player_values_rsp       = 0x35c4
PLT_get_curplayer_value_rsp      = 0x35b8
PLT_set_player_value_rsp         = 0x3594
PLT_get_player_attr_text_rsp     = 0x35ac
PLT_get_player_value_text_rsp    = 0x35a0

# Function-internal landmarks in saveRegEventSeqId.
EPILOGUE          = 0x712a   # mov r9,#1; canary check; pop {r4-r9, sl, fp, pc}
UNKNOW_INDICATION = 0x65bc   # original "unknow indication" path

# Returns BluetoothAvrcpService's per-conn struct (conn buffer at +8 inside);
# same helper called by notificationTrackChangedNative at file offset 0x3bda.
JNI_GET_AVRCP_STATE = 0x36c0

# T4 stack frame (post-SUB SP by T4_FRAME): args[0..15], state[16..31] (mirrors
# the .bss trampoline state at G_Y1_TRAMPOLINE_STATE_VADDR), file_buf[32..1135]
# (y1-track-info image; schema in docs/BT-COMPLIANCE.md §4).
T4_FRAME           = 1136
T4_FILE_SIZE       = 1104
T4_OFF_ARGS        = 0
T4_OFF_STATE       = 16
T4_OFF_FILE        = 32
T4_OFF_FILE_TID    = T4_OFF_FILE          # file_buf[0..7] = current track_id
T4_OFF_FILE_TITLE  = T4_OFF_FILE + 8      # file_buf[8..263]
T4_OFF_FILE_ARTIST = T4_OFF_FILE + 264    # file_buf[264..519]
T4_OFF_FILE_ALBUM  = T4_OFF_FILE + 520    # file_buf[520..775]
T4_OFF_FILE_TRACK_NUM   = T4_OFF_FILE + 800  # file_buf[800..815]
T4_OFF_FILE_TOTAL_NUM   = T4_OFF_FILE + 816  # file_buf[816..831]
T4_OFF_FILE_PLAY_TIME   = T4_OFF_FILE + 832  # file_buf[832..847]
T4_OFF_FILE_GENRE       = T4_OFF_FILE + 848  # file_buf[848..1103]

# Caller-relative offsets shift by T4_FRAME after our SUB SP.
T4_TRANSID_OFF = 368 + T4_FRAME           # 1176
T4_PDU_OFF_ENTRY  = 382                   # before SUB SP (entry pre-check)
T4_LR_CANARY_OFF_ENTRY = 374              # before SUB SP (epilogue restore)
# Inbound GetElementAttributes request body (AVRCP wire layout):
#   caller_sp + 382 = PDU (0x20), 383 = PT, 384..385 = ParamLen BE u16,
#   386..393 = Identifier (8 B, 0x0=PLAYING),
#   394 = NumAttributes (1 B), 395+ = AttributeID[N] (4 B BE each).
# Post-SUB-SP, these slots are at sp + offset + T4_FRAME.
T4_NUMATTR_OFF = 394 + T4_FRAME           # 1530 - inbound NumAttributes byte
T4_ATTRIDS_OFF = 395 + T4_FRAME           # 1531 - inbound AttributeID[0] base

# extended_T2 frame: 16 B for [track_id (8) || transId (1) || pad (7)].
T2_FRAME = 16
T2_OFF_TID = 0
T2_OFF_TRANSID = 8
T2_TRANSID_CALLER_OFF = 368 + T2_FRAME    # 384
T2_EVENT_ID_OFF_ENTRY = 386               # before SUB SP

# T6 (GetPlayStatus): 16 B args + 800 B file_buf. Reads y1-track-info offsets
# 776/780/784/792 (duration/pos/state_time BE u32 + playing_flag u8).
T6_FRAME           = 816
T6_OFF_ARGS        = 0
T6_OFF_FILE        = 16
T6_OFF_FILE_DURATION   = T6_OFF_FILE + 776   # 792 - duration_ms
T6_OFF_FILE_POS        = T6_OFF_FILE + 780   # 796 - position_at_state_change
T6_OFF_FILE_STATE_TIME = T6_OFF_FILE + 784   # 800 - state_change_time_ms u32 BE
T6_OFF_FILE_PLAYFLAG   = T6_OFF_FILE + 792   # 808 - playing_flag

# Stash struct timespec in unused outgoing-args slack so we can call
# clock_gettime(CLOCK_BOOTTIME, &timespec) from inside T6 to live-extrapolate
# the playback position. The outgoing-args region (sp+0..15) is reserved for
# the response builder's stack args, but only sp[0] (1-byte play_status) is
# actually consumed; sp+8..15 is unused and can hold the 8-byte timespec
# without growing T6_FRAME.
T6_OFF_TIMESPEC      = 8
T6_OFF_TIMESPEC_SEC  = T6_OFF_TIMESPEC + 0   # 8 - tv_sec u32
T6_OFF_TIMESPEC_NSEC = T6_OFF_TIMESPEC + 4   # 12 - tv_nsec u32 (we don't use it)

# T8 (RegisterNotification INTERIM dispatch for events ≠ 0x02) frame:
# 800 B file_buf at sp+0. None of the reg_notievent_*_rsp calls T8 makes
# need stack args (all 4 ARM args fit in r0 / r1 / r2 / r3), so no outgoing args
# region is reserved. Caller's event_id slot is at sp+T8_EVENT_ID_OFF
# after our SUB SP.
T8_FRAME           = 808                   # 800 file_buf + 8 timespec
T8_OFF_FILE        = 0
T8_OFF_FILE_POS      = T8_OFF_FILE + 780   # 780 - pos_at_state_change_ms
T8_OFF_FILE_STATE_TIME = T8_OFF_FILE + 784 # 784 - state_change_time_ms (BE u32)
T8_OFF_FILE_PLAYFLAG = T8_OFF_FILE + 792   # 792 - playing_flag (= AVRCP play_status)
T8_OFF_FILE_BATTERY  = T8_OFF_FILE + 794   # 794 - battery_status u8 (AVRCP §5.4.2
                                            #       Tbl 5.34/5.35 enum: 0=NORMAL,
                                            #       1=WARNING, 2=CRITICAL, 3=EXTERNAL,
                                            #       4=FULL_CHARGE)
T8_OFF_FILE_REPEAT   = T8_OFF_FILE + 795   # 795 - repeat_avrcp (AVRCP §5.2.4 Tbl 5.20)
T8_OFF_FILE_SHUFFLE  = T8_OFF_FILE + 796   # 796 - shuffle_avrcp (AVRCP §5.2.4 Tbl 5.21)
# Timespec for clock_gettime(CLOCK_BOOTTIME) — used by event 0x05 INTERIM to
# live-extrapolate position so a fresh CT subscribe doesn't see stale
# pos_at_state_change_ms. Same magic-multiply nsec-to-ms math T6/T9 use.
T8_OFF_TIMESPEC      = T8_OFF_FILE + 800   # 800 - struct timespec
T8_OFF_TIMESPEC_SEC  = T8_OFF_TIMESPEC + 0
T8_OFF_TIMESPEC_NSEC = T8_OFF_TIMESPEC + 4
T8_EVENT_ID_OFF    = 386 + T8_FRAME        # caller-frame event_id, post-SUB-SP

# T9 (proactive PLAYBACK_STATUS_CHANGED + BATT_STATUS_CHANGED + PLAYBACK_POS
# + PLAYER_APPLICATION_SETTING_CHANGED) frame:
#   sp+0..7    = outgoing-args region (only reg_notievent_player_appsettings_
#                changed_rsp uses stack args — its 5th + 6th are at sp[0]/sp[4])
#   sp+8..23   = state buf (16 B; mirrors the .bss trampoline state schema)
#   sp+24..823 = y1-track-info file buf (800 B)
#   sp+824..831 = struct timespec for clock_gettime(CLOCK_BOOTTIME)
#
# State byte usage (13 B in-memory, 4-byte aligned to 16 B in the .bss
# slot; short reads zero-fill):
#   [0..7]   last_seen track_id (T5)
#   [8]      last RegisterNotification transId (T5)
#   [9]      last_play_status (T9 edge)
#   [10]     last_battery_status (T9 edge)
#   [11]     last_repeat_avrcp (T9 papp edge)
#   [12]     last_shuffle_avrcp (T9 papp edge)
#
# Per-event subscription state lives in the JNI's g_avrcp_req_event_database
# global at vaddr 0xd2b5 (.bss, session-scope) — see
# _emit_event_subscribed_subroutine docstring.
#
# Single-writer regions (no read-modify-write race): T9 writes [9..12]
# (4-B block at off 9), T5 writes [0..8] (9-B block at off 0).
T9_FRAME              = 840        # 8 args + 24 state + 800 file_buf + 8 timespec
T9_OFF_ARGS           = 0
T9_OFF_STATE          = 8
T9_OFF_FILE           = 32          # 24 B state buf (13 B data + 11 B align padding)
T9_OFF_FILE_DURATION   = T9_OFF_FILE + 776   # duration_ms (BE u32, T6 reads same)
T9_OFF_FILE_POS        = T9_OFF_FILE + 780   # pos_at_state_change_ms (BE u32)
T9_OFF_FILE_STATE_TIME = T9_OFF_FILE + 784   # state_change_time_ms (BE u32)
T9_OFF_FILE_PLAYFLAG   = T9_OFF_FILE + 792   # playing_flag inside file_buf
T9_OFF_FILE_BATTERY    = T9_OFF_FILE + 794   # battery_status inside file_buf
T9_OFF_FILE_REPEAT     = T9_OFF_FILE + 795   # repeat_avrcp (AVRCP §5.2.4 Tbl 5.20)
T9_OFF_FILE_SHUFFLE    = T9_OFF_FILE + 796   # shuffle_avrcp (AVRCP §5.2.4 Tbl 5.21)
T9_STATE_LAST_PS_OFF      = T9_OFF_STATE + 9   # last_play_status
T9_STATE_LAST_BATT_OFF    = T9_OFF_STATE + 10  # last_battery_status
T9_STATE_LAST_REPEAT_OFF  = T9_OFF_STATE + 11  # last_repeat_avrcp (papp edge)
T9_STATE_LAST_SHUFFLE_OFF = T9_OFF_STATE + 12  # last_shuffle_avrcp (papp edge)
# T9's position-emit block needs a struct timespec for clock_gettime(CLOCK_BOOTTIME)
# to live-extrapolate the playback position (same arithmetic T6 does for
# GetPlayStatus). Place the 8 B timespec immediately after the file buf.
T9_OFF_TIMESPEC      = T9_OFF_FILE + 800     # struct timespec
T9_OFF_TIMESPEC_SEC  = T9_OFF_TIMESPEC + 0
T9_OFF_TIMESPEC_NSEC = T9_OFF_TIMESPEC + 4

# T5 (proactive TRACK_CHANGED + TRACK_REACHED_END / START 3-tuple) frame:
# 24 B state buf at sp+0..23 (holds the 13-byte trampoline state mirror plus
# 11 B zero-padding for 4-byte alignment) + 800 B y1-track-info file buf at
# sp+24..823. Same shape as T9. T5 reads enough of y1-track-info to see the
# natural-end flag at offset 793 (= sp + T5_OFF_FILE_NATURAL_END).
T5_FRAME              = 824
T5_OFF_STATE          = 0
T5_OFF_FILE           = 24
T5_OFF_FILE_TID       = T5_OFF_FILE          # track_id (8 B) at file[0..7]
T5_OFF_FILE_NATURAL_END = T5_OFF_FILE + 793  # previous_track_natural_end u8
                                              #   at file[793] (set by the
                                              #   music app before the
                                              #   metachanged broadcast that
                                              #   lands here).

# T_papp (PApp Settings PDUs 0x11-0x16) frame:
#   sp+0..23  : outgoing args region (24 B; max-of-needs is 5 stack args =
#               20 B for get_player_value_text_rsp, rounded to 24 for alignment)
# Caller's inbound AVRCP param body sits at sp+386+ (= entry-relative;
# post-SUB-SP offset is +PAPP_FRAME).
PAPP_FRAME            = 24
PAPP_OFF_ARGS         = 0
PAPP_PARAM_OFF_ENTRY  = 386                # caller-relative; first byte of param body
                                            # (PDU=sp+382, pkt_type=sp+383,
                                            # param_length BE=sp+384..385)
PAPP_PARAM_OFF        = PAPP_PARAM_OFF_ENTRY + PAPP_FRAME

# AVRCP 1.3 §5.2 PlayerApplicationSettings:
#   §5.2.1 attribute IDs (Tbl 5.18):
#     0x01 Equalizer ON/OFF
#     0x02 Repeat Mode Status
#     0x03 Shuffle ON/OFF
#     0x04 Scan ON/OFF
#   §5.2.4 Repeat-mode values (Tbl 5.20):
#     0x01 OFF, 0x02 SINGLE TRACK, 0x03 ALL TRACK, 0x04 GROUP
#   §5.2.4 Shuffle values (Tbl 5.21):
#     0x01 OFF, 0x02 ALL TRACK, 0x03 GROUP
# We expose Repeat (id=2) + Shuffle (id=3) — the universal pair (Equalizer
# and Scan are out of scope on Y1 hardware).
PAPP_ATTR_REPEAT      = 0x02
PAPP_ATTR_SHUFFLE     = 0x03
PAPP_REPEAT_OFF       = 0x01
PAPP_SHUFFLE_OFF      = 0x01


# AVRCP 1.3 §5.4.2 (RegisterNotification, Tables 5.34 + 5.36) canned-value
# defaults.
# - BATT_STATUS_CHANGED: real data wired through y1-track-info[794]
#   (battery_status u8). T8 INTERIM reads byte 794; T9 emits CHANGED-on-edge
#   when file[794] differs from .bss state[10] (last_battery_status).
#   The music app's BatteryReceiver maps Android `Intent.ACTION_BATTERY_CHANGED`
#   (level + plugged-state) to the AVRCP enum on every bucket transition and
#   fires `playstatechanged` so T9 picks up the change. Spec values:
#   0=NORMAL, 1=WARNING, 2=CRITICAL, 3=EXTERNAL, 4=FULL_CHARGE.
#   BATT_STATUS_NORMAL is retained as the default value when y1-track-info
#   is shorter than 800 B — T8 / T9 memset to zero before the read, so a
#   short read leaves byte 794 = 0 = NORMAL, a benign default.
# - SYSTEM_STATUS_CHANGED: 0x00 POWERED_ON — we run only when the device is
#   on, so this is always correct. (Spec: 0=POWERED_ON, 1=POWERED_OFF,
#   2=UNPLUGGED.)
BATT_STATUS_NORMAL    = 0x00
SYSTEM_STATUS_POWERED = 0x00

# AVRCP RegisterNotification response AV/C ctype codes. The JNI helpers
# in `libextavrcp.so` marshal the caller's r2 (this byte) into IPC
# payload[8]; mtkbt's per-event response builder at `fcn.0x121d8` reads
# ipc[8] and emits it as the wire AV/C ctype byte (M1 + M6 in
# `patch_mtkbt.py` make this dispatch a pure pass-through for any value
# the JNI side sets, beyond the original INTERIM-only special case).
REASON_INTERIM         = 0x0F   # AV/C ctype 0x0F INTERIM
REASON_CHANGED         = 0x0D   # AV/C ctype 0x0D CHANGED
REASON_NOT_IMPLEMENTED = 0x08   # AV/C ctype 0x08 NOT_IMPLEMENTED (requires M6)

# open(2) flags & modes (bionic / Linux generic).
O_RDONLY = 0x0000
O_WRONLY = 0x0001
O_CREAT  = 0x0040
O_TRUNC  = 0x0200
MODE_0666 = 0o666

# Linux ARM EABI syscall numbers.
NR_read = 3
NR_lseek = 19
NR_clock_gettime = 263
SEEK_SET = 0

# Linux clock IDs. CLOCK_BOOTTIME mirrors Android's SystemClock.elapsedRealtime
# (monotonic, includes time spent in suspend) — same source the music app's
# TrackInfoWriter uses when stamping mStateChangeTime, so subtracting the two
# yields the wall-clock seconds elapsed since the last play / pause edge.
CLOCK_BOOTTIME = 7

# ---------------------------------------------------------------- builder

# Advertised AVRCP event IDs (per AVRCP 1.3 §5.4.2 + 1.4 NCC/UIDS/etc.):
#   0x01 PLAYBACK_STATUS, 0x02 TRACK_CHANGED, 0x05 PLAYBACK_POS,
#   0x08 PLAYER_APPLICATION_SETTING_CHANGED, 0x09 NCC, 0x0a AVAILABLE_PLAYERS,
#   0x0b ADDRESSED_PLAYER, 0x0c UIDS — 8 events total, all INTERIM-acked.
T1_ADVERTISED_EVENTS = bytes([0x01, 0x02, 0x05, 0x08, 0x09, 0x0a, 0x0b, 0x0c])


def _emit_t1_extended(a: Asm) -> None:
    """T1_extended: GetCapabilities trampoline body, relocated from the
    in-JNI testparmnum slot at 0x7308.

    The in-JNI slot is now a thin 4-byte `b.w T1_extended` bridge. Moving
    the body here frees us from the 40-byte testparmnum budget and lets us
    splice in a `clear_event_database` call at the GetCapabilities entry
    — without that clear, the .bss-backed per-event subscription gates
    leak across CT disconnect/reconnect (the com.android.bluetooth
    process stays alive, so .bss survives CT churn). GetCapabilities is
    the canonical first CMD on every CT→TG connection per AVRCP 1.3
    §5.4.1 setup flow, so clearing the database here is a reliable
    "fresh session" signal.

    Entry: r0 = PDU (set by stock dispatcher); r5 = avrcp_state ptr (conn
    at r5+8). Falls through to extended_T2 via b.w for non-GetCaps PDUs.
    """
    a.label("T1_extended")

    # Re-read PDU from sp+382 (caller's stack frame; same offset T4 uses).
    a.ldrb_w(0, 13, 382)
    a.cmp_imm8(0, 0x10)
    a.bne("t1ext_not_getcaps")

    # GetCapabilities path. Clear the per-event database first so any
    # subscriptions cached from a previous CT connection don't gate-pass
    # T5/T9 emits in this fresh session.
    a.bl_w("clear_event_database")

    # btmtk_avrcp_send_get_capabilities_rsp(conn, 0, count, *events_array)
    a.add_imm_t3(0, 5, 8)                     # r0 = conn (= r5 + 8)
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, len(T1_ADVERTISED_EVENTS)) # r2 = 8
    a.adr_w(3, "t1_events_table")             # r3 = &events_table
    a.blx_imm(PLT_get_capabilities_rsp)

    # Jump back to the stock dispatcher's response-epilogue.
    a.labels["t1ext_epilogue_target"] = EPILOGUE
    a.b_w("t1ext_epilogue_target")

    a.label("t1ext_not_getcaps")
    # Non-GetCapabilities PDUs fall through to extended_T2 for further
    # dispatch (RegisterNotification / GetElementAttributes / etc.).
    a.b_w("extended_T2")

    a.align(4)
    a.label("t1_events_table")
    a.raw(T1_ADVERTISED_EVENTS)
    a.align(4)


def _emit_t4(a: Asm) -> None:
    """T4: universal non-RegNotif entry (GetElementAttributes / GetPlayStatus /
    Charset / Battery / PApp / Continuation). Lives in-blob immediately after
    T1_extended; reached via extended_T2's `b.w T4` for any PDU != 0x10/0x31.

    Entry conditions:
      - r5 holds JNI instance struct (conn buffer at r5+8)
      - r0 may be PDU or trashed (we re-read from sp+382)
      - lr canary still at caller's sp+374
    """
    a.label("T4")

    # AVRCP §3.3.5 strict TID echo for non-RegNotif PDUs. Stock
    # libextavrcp_jni.so writes conn[+0x11] = inbound seq_id before invoking
    # any response builder; the rsp builders read [conn, #0x11] to pack the
    # AVCTP transaction label on outbound frames. R1's redirect at 0x6538
    # bypasses stock's write, so without this prologue conn[+0x11] would
    # stay pinned at whatever value the previous response left there.
    # RegNotif paths refresh conn[+0x11] via the per-event database
    # (save_event_seq_id at extended_T2 + restore_conn_tid at every emit
    # site). Non-RegNotif paths (GetEA, GetPlayStatus, Charset, Battery,
    # PApp, Continuation) all dispatch through this T4 entry, so the
    # single ldrb+strb here covers them universally. GetCap (handled at
    # T1_extended:0xac5c) is unaffected — stock JNI's pre-R1 path writes
    # conn[+0x11] before our hijack takes effect.
    # Cost: 6 B unconditional; r1 clobbered (downstream PDU dispatch
    # arms re-load it before invoking their rsp builder).
    a.ldrb_w(1, 13, 0x171)                    # r1 = inbound seq_id (TID) at sp+0x171
    # strb r1, [r5, #0x19]  — struct[+0x19] = conn[+0x11] = TID
    # T1 STRB imm5 encoding: 0111 0 imm5 Rn Rt = 0x7000 | (0x19<<6) | (5<<3) | 1
    hw = 0x7000 | (0x19 << 6) | (5 << 3) | 1
    a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))

    # ---- pre-check: dispatch on PDU ----
    # PDU 0x20 → GetElementAttributes (T4 main body)
    # PDU 0x17 → InformDisplayableCharacterSet (T_charset)
    # PDU 0x18 → InformBatteryStatusOfCT (T_battery)
    # PDU 0x30 → GetPlayStatus (T6)
    # else     → restore lr canary + r0 and fall through to "unknow indication"
    a.ldrb_w(0, 13, T4_PDU_OFF_ENTRY)         # r0 = PDU
    if DEBUG_NATIVE_LOG:
        _emit_native_log_u32(a, "log_fmt_t1pdu", 0)
    a.cmp_imm8(0, 0x20)
    a.beq("t4_main")
    # PDU 0x17 / 0x18 / 0x30 dispatch via bne+b.w because T_charset / T_battery
    # / T6 live past the end of the T4 body (~600+ B forward), beyond beq's
    # ±256 B range.
    a.cmp_imm8(0, 0x17)
    a.bne("t4_after_charset")
    a.b_w("T_charset")
    a.label("t4_after_charset")
    a.cmp_imm8(0, 0x18)
    a.bne("t4_after_battery")
    a.b_w("T_battery")
    a.label("t4_after_battery")
    a.cmp_imm8(0, 0x30)
    a.bne("t4_after_playstatus")
    a.b_w("T6")
    a.label("t4_after_playstatus")
    # PDU 0x40 RequestContinuingResponse / 0x41 AbortContinuingResponse.
    # Routed through an explicit T_continuation handler that emits the spec-
    # acceptable NOT_IMPLEMENTED reject via the same UNKNOW_INDICATION path.
    # See _emit_t_continuation for rationale.
    a.cmp_imm8(0, 0x40)
    a.bne("t4_after_continuation_40")
    a.b_w("T_continuation")
    a.label("t4_after_continuation_40")
    a.cmp_imm8(0, 0x41)
    a.bne("t4_after_continuation_41")
    a.b_w("T_continuation")
    a.label("t4_after_continuation_41")
    # PDUs 0x11..0x16 (PlayerApplicationSettings) all route through T_papp.
    # Per AVRCP 1.3 ICS Table 7 C.14, supporting any one PApp PDU makes all
    # of 0x11..0x16 + event 0x08 Mandatory — handled together in T_papp.
    a.cmp_imm8(0, 0x11)
    a.blt("t4_after_papp")
    a.cmp_imm8(0, 0x17)                       # >= 0x17: not in our range
    a.bge("t4_after_papp")
    a.b_w("T_papp")
    a.label("t4_after_papp")
    # Anything else: restore lr canary and fall through to original
    # "unknow indication" path (which expects r0 = conn).
    a.ldrh_w(14, 13, T4_LR_CANARY_OFF_ENTRY)  # ldrh.w lr, [sp, #374]
    a.add_imm_t3(0, 5, 8)                     # add.w r0, r5, #8 (= conn)
    a.b_w("t4_to_unknown")

    a.label("t4_main")
    # ---- allocate stack frame ----
    a.subw(13, 13, T4_FRAME)                  # sub.w sp, sp, #1136

    # ---- zero-init state buffer (16 B) ----
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T4_OFF_STATE + 0)
    a.str_sp_imm(0, T4_OFF_STATE + 4)
    a.str_sp_imm(0, T4_OFF_STATE + 8)
    a.str_sp_imm(0, T4_OFF_STATE + 12)

    # ---- memset(file_buf, 0, FILE_SIZE) ----
    a.add_sp_imm(0, T4_OFF_FILE)              # r0 = sp+32
    a.movs_imm8(1, 0)                         # r1 = 0
    a.movw(2, T4_FILE_SIZE)                   # r2 = 1104
    a.blx_imm(PLT_memset)

    # ---- copy active slot from y1-track-info into file_buf ----
    # read_track_info handles mmap-cache + active-slot dispatch + byte-copy.
    # On failure (file missing, mmap rejects), returns 0 and leaves file_buf
    # zeroed from the memset above — downstream code sees "no track, no edge"
    # and skips emits naturally. No explicit branch needed.
    a.add_sp_imm(0, T4_OFF_FILE)              # r0 = dst = sp+T4_OFF_FILE
    a.movw(1, T4_FILE_SIZE)                   # r1 = nbytes = 1104
    a.movs_imm8(2, 0)                         # r2 = slot_offset = 0 (read from slot start)
    a.bl_w("read_track_info")

    a.label("t4_skip_track_read")

    # ---- copy trampoline state from .bss to sp+T4_OFF_STATE ----
    # T4 only inspects state[0..7] (track_id) but copies the full 13-byte
    # state window; bytes 8..12 are T9-owned or unused and end up zero-
    # padded in the state buf anyway.
    a.add_sp_imm(0, T4_OFF_STATE)
    a.movs_imm8(1, Y1_TRAMPOLINE_STATE_SIZE)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("read_state_block")

    a.label("t4_skip_state_read")

    # ---- compare track_id (file[0..7] vs state[0..7]) ----
    a.ldr_sp_imm(0, T4_OFF_FILE_TID + 0)
    a.ldr_sp_imm(1, T4_OFF_STATE   + 0)
    a.cmp_w(0, 1)
    a.bne("t4_track_changed")
    a.ldr_sp_imm(0, T4_OFF_FILE_TID + 4)
    a.ldr_sp_imm(1, T4_OFF_STATE   + 4)
    a.cmp_w(0, 1)
    a.beq("t4_no_change")

    a.label("t4_track_changed")
    # track_changed_rsp(conn, 0, REASON_CHANGED, &selected_track_id)
    # r1=0 takes the response builder's spec-correct path; r1!=0 hits the
    # reject-shape path that omits the event payload. r3 → 8 zero bytes per
    # AVRCP 1.6 §6.7.2 Table 6.32: "For TG conforming to AVRCP 1.3, the Identifier
    # shall always be set to 0x00...00." (Non-zero is a 1.4+ Browseable
    # Player UID; strict 1.3 parsers silently drop CHANGED with non-zero
    # Identifier and fall back to polling.) state[0..7] still tracks
    # audio_id for trampoline-side edge detection — only the wire payload
    # is constrained to spec.
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x02, "t4_tc")
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, REASON_CHANGED)
    a.adr_w(3, "selected_track_id")           # r3 = &0x00*8 (§5.14.1 SELECTED)
    a.blx_imm(PLT_track_changed_rsp)

    # Update state in-memory: state[0..7] = file[0..7]
    a.ldr_sp_imm(0, T4_OFF_FILE_TID + 0)
    a.str_sp_imm(0, T4_OFF_STATE   + 0)
    a.ldr_sp_imm(0, T4_OFF_FILE_TID + 4)
    a.str_sp_imm(0, T4_OFF_STATE   + 4)

    # ---- write updated state[0..7] (track_id) back to .bss ----
    # T4 only touches the first 8 bytes (track_id) — narrow write keeps
    # T9-owned bytes 9..12 unaffected.
    a.add_sp_imm(0, T4_OFF_STATE)
    a.movs_imm8(1, 8)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("write_state_block")

    a.label("t4_no_change")

    # N× get_element_attributes_rsp(conn, 0, idx, total, [attr_id, charset, len, ptr]).
    # Response builder (libextavrcp.so:0x2188) emits the packed frame when
    # idx+1 == total && total != 0; we accumulate and emit on the final call.
    #
    # AVRCP 1.3 §5.3.1 Table 5.24: TG returns exactly the requested attribute IDs
    # in the requested order (NumAttributes=0 means all); unsupported attributes
    # emit with AttributeValueLength=0. Attribute IDs are listed in AVRCP 1.3
    # Appendix E (0x01=Title, 0x02=Artist, 0x03=Album, 0x04=TrackNumber,
    # 0x05=TotalNumberOfTracks, 0x06=Genre, 0x07=PlayingTime). Supported attrs
    # 0x01..0x07 mapped via the inline t4_attr_offset_table; zero-length emit
    # relies on patch_libextavrcp.py E1.

    # ---- read NumAttributes from inbound request ----
    a.ldrb_w(7, 13, T4_NUMATTR_OFF)           # r7 = N (CT-requested count)
    a.cmp_imm8(7, 0)
    a.beq_w("t4_emit_all")                    # N==0 -> §6.6.1 "return all"

    # Request-driven loop. r5=JNI base, r6=i, r7=N, r9=attr_id, r10=str_offset
    # (r9/r10 callee-saved per AAPCS; survive the strlen + rsp calls).
    a.movs_imm8(6, 0)                         # r6 = i = 0

    a.label("t4_req_loop")
    # Compute pointer to AttributeID[i]: r4 = sp + T4_ATTRIDS_OFF + 4*i
    a.addw(4, 13, T4_ATTRIDS_OFF)             # r4 = sp + T4_ATTRIDS_OFF
    a.mov_lo_lo(0, 6)                         # r0 = i
    a.lsls_imm5(0, 0, 2)                      # r0 = i * 4
    a.add_reg(4, 0)                           # r4 += i*4 (now r4 = &AttrIDs[i])

    # Load BE u32 attr_id, byte-reverse to LE for compare.
    a.ldr_w(0, 4, 0)                          # r0 = BE u32 attr_id
    a.rev_lo_lo(0, 0)                         # r0 = LE attr_id

    # Save attr_id to r9 (preserved across strlen + rsp calls).
    a.mov_lo_lo(9, 0)

    # If attr_id is 0 or >= 8: unsupported. AVRCP 1.6 §26 Table 26.1 marks 0
    # as "Not Used" and 0x8-0xFFFFFFFF as Reserved.
    a.cmp_imm8(0, 0)
    a.beq("t4_req_unsup")
    a.cmp_imm8(0, 8)
    a.bhs("t4_req_unsup")                     # attr_id >= 8 → unsupported

    # Look up table[attr_id] → r4 (= sp-relative file_buf offset).
    a.adr_w(4, "t4_attr_offset_table")        # r4 = table base
    a.lsls_imm5(0, 0, 2)                      # r0 = attr_id * 4
    a.add_reg(4, 0)                           # r4 = &table[attr_id]
    a.ldr_w(4, 4, 0)                          # r4 = table[attr_id]
    a.b_w("t4_req_have_off")

    a.label("t4_req_unsup")
    # Unsupported attr: sentinel str_offset = 0 → response builder gets length=0.
    a.movs_imm8(4, 0)

    a.label("t4_req_have_off")
    a.mov_lo_lo(10, 4)                        # save str_offset across strlen/rsp

    # strlen(sp + str_offset). Short-circuit unsupported (r4 == 0) to avoid
    # measuring whatever's in the args region.
    a.cmp_imm8(4, 0)
    a.beq("t4_req_skip_strlen")
    a.mov_lo_lo(0, 13)                        # r0 = sp
    a.add_reg(0, 4)                           # r0 = sp + str_offset
    a.blx_imm(PLT_strlen)                     # r0 = strlen
    a.b_w("t4_req_have_strlen")

    a.label("t4_req_skip_strlen")
    a.movs_imm8(0, 0)                         # r0 = 0 (no value to measure)

    a.label("t4_req_have_strlen")
    # Pack response args: sp[0]=attr_id, sp[4]=charset, sp[8]=strlen, sp[12]=ptr
    a.str_sp_imm(0, T4_OFF_ARGS + 8)          # sp[8]  = strlen

    a.mov_lo_lo(0, 9)                         # r0 = attr_id
    a.str_sp_imm(0, T4_OFF_ARGS + 0)          # sp[0]  = attr_id

    a.movs_imm8(0, 0x6A)
    a.str_sp_imm(0, T4_OFF_ARGS + 4)          # sp[4]  = charset (UTF-8)

    a.mov_lo_lo(0, 13)                        # r0 = sp
    a.add_reg(0, 10)                          # r0 = sp + str_offset
    a.str_sp_imm(0, T4_OFF_ARGS + 12)         # sp[12] = ptr

    # Call get_element_attributes_rsp(conn, 0, i, N).
    a.add_imm_t3(0, 5, 8)                     # r0 = conn (= r5+8)
    a.movs_imm8(1, 0)                         # r1 = 0
    a.mov_lo_lo(2, 6)                         # r2 = i
    a.mov_lo_lo(3, 7)                         # r3 = N
    # No per-attribute debug log here; GEA wire-size diagnostics live on
    # the mtkbt side via `tools/btlog-parse.py --avrcp`.
    a.blx_imm(PLT_get_element_attributes_rsp)

    # i++; if i < N: loop.
    a.add_imm_t3(6, 6, 1)
    a.cmp_w(6, 7)
    a.blt_w("t4_req_loop")
    a.b_w("t4_req_done")

    # ---- N==0 fallback: emit all 7 supported attrs per §6.6.1 ----
    a.label("t4_emit_all")
    attr_table = (
        ("title",       0x01, T4_OFF_FILE_TITLE),
        ("artist",      0x02, T4_OFF_FILE_ARTIST),
        ("album",       0x03, T4_OFF_FILE_ALBUM),
        ("track_num",   0x04, T4_OFF_FILE_TRACK_NUM),
        ("total_num",   0x05, T4_OFF_FILE_TOTAL_NUM),
        ("genre",       0x06, T4_OFF_FILE_GENRE),
        ("play_time",   0x07, T4_OFF_FILE_PLAY_TIME),
    )
    total_attrs = len(attr_table)
    for idx, (label_suffix, attr_id, str_offset) in enumerate(attr_table):
        a.label(f"t4_reply_{label_suffix}")
        a.add_sp_imm(0, str_offset)           # r0 = sp + str_offset
        a.blx_imm(PLT_strlen)                 # r0 = strlen
        a.mov_lo_lo(6, 0)                     # r6 = strlen

        a.add_imm_t3(0, 5, 8)                 # r0 = conn
        a.movs_imm8(1, 0)
        a.movs_imm8(2, idx)
        a.movs_imm8(3, total_attrs)
        a.movs_imm8(4, attr_id)
        a.str_sp_imm(4, T4_OFF_ARGS + 0)      # sp[0]  = attr_id
        a.movs_imm8(4, 0x6A)
        a.str_sp_imm(4, T4_OFF_ARGS + 4)      # sp[4]  = charset
        a.str_sp_imm(6, T4_OFF_ARGS + 8)      # sp[8]  = strlen
        a.add_sp_imm(4, str_offset)
        a.str_sp_imm(4, T4_OFF_ARGS + 12)     # sp[12] = ptr
        a.blx_imm(PLT_get_element_attributes_rsp)

    # ---- restore stack and tail-call the function epilogue ----
    a.label("t4_req_done")
    a.addw(13, 13, T4_FRAME)
    a.ldrh_w(14, 13, T4_LR_CANARY_OFF_ENTRY)
    a.b_w("t4_to_epilogue")

    # ---- Inline data: attr_id → file_buf-relative offset lookup ----
    # Indexed by AVRCP 1.6 §26 Table 26.1 attribute ID (1..7).
    # Index 0 is unused (attr_id 0 = "Not Used"; bounds check above redirects
    # to the unsupported path before reaching this table).
    a.align(4)
    a.label("t4_attr_offset_table")
    a._word(0)                                # attr_id 0 (Not Used)
    a._word(T4_OFF_FILE_TITLE)                # attr_id 1
    a._word(T4_OFF_FILE_ARTIST)               # attr_id 2
    a._word(T4_OFF_FILE_ALBUM)                # attr_id 3
    a._word(T4_OFF_FILE_TRACK_NUM)            # attr_id 4
    a._word(T4_OFF_FILE_TOTAL_NUM)            # attr_id 5
    a._word(T4_OFF_FILE_GENRE)                # attr_id 6
    a._word(T4_OFF_FILE_PLAY_TIME)            # attr_id 7


def _emit_extended_t2(a: Asm) -> None:
    """extended_T2: RegisterNotification(TRACK_CHANGED) handler.

    T2 stub at 0x72d4 jumps here unconditionally (b.w extended_T2). We dispatch
    PDU / event-id internally and fall through to T4 if it's a GetElementAttributes
    that somehow reached us, or to UNKNOW_INDICATION otherwise.
    """
    a.label("extended_T2")

    # r0 contains PDU at entry (set by T1's bridge, which loads PDU and dispatches)
    a.cmp_imm8(0, 0x31)
    a.bne("ext2_check_get_attrs")             # not RegisterNotification → maybe T4

    # Save the inbound AVCTP TID into g_avrcp_req_event_database[event_id].
    # Stock JNI normally does this via saveRegEventSeqId at 0x6d26 in its
    # CMD dispatcher, but our R1 redirect hijacks the path upstream of that
    # call. Without this save, the database stays zero and the matching
    # restore_conn_tid in T5/T8/T9 reads 0 → wire frame ships c39=0 → CT
    # drops on §3.3.5 TID-echo mismatch. The TID lives at sp+0x171 (pre-
    # SUB-SP, verified empirically — see commit 2765ebd's T2d= probe).
    # Done once here, covering every RegNotif event_id: extended_T2's
    # TRACK_CHANGED arm + T8's 11 other arms all reach this point.
    a.ldrb_w(0, 13, T2_EVENT_ID_OFF_ENTRY)    # r0 = event_id (preserved by save)
    if DEBUG_NATIVE_LOG:
        # Confirm extended_T2 actually reached for each inbound RegNotif.
        # Log emitted BEFORE save_event_seq_id so the "T2reg ev=N" line
        # is the strongest possible signal: even if save fails, this line
        # confirms the CT sent RegisterNotification(ev=N) to us.
        _emit_native_log_u32(a, "log_fmt_t2reg", 0)
    a.ldrb_w(1, 13, 0x171)                    # r1 = inbound seq_id
    a.bl_w("save_event_seq_id")               # database[event_id] = seq_id
    a.cmp_imm8(0, 0x02)                       # TRACK_CHANGED?
    a.beq("ext2_track_changed")

    # PDU 0x31 but event ≠ 0x02 → T8 handles events 0x01/0x03/0x04/0x05/
    # 0x06/0x07. T8 returns NOT_IMPLEMENTED for any other event_id.
    a.b_w("T8")

    a.label("ext2_check_get_attrs")
    # PDU != 0x31. If it's 0x20 (GetElementAttributes), let T4 handle it; the
    # T4 entry re-reads PDU from sp+382 so it doesn't matter that r0 is stale.
    a.b_w("T4")

    a.label("ext2_track_changed")
    # ---- allocate small frame: stack scratch for the .bss state write ----
    # sp+0..7  : track_id (read from y1-track-info)
    # sp+8     : transId (caller-supplied)
    # sp+9..15 : unused (write_state_block writes only bytes 0..7 — see below)
    a.subw(13, 13, T2_FRAME)                  # sub.w sp, sp, #16

    # Default sp+0..7 to zero (defensive — track-info read might fail).
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T2_OFF_TID + 0)
    a.str_sp_imm(0, T2_OFF_TID + 4)

    # ---- copy first 8 B of active slot (track_id) into sp+T2_OFF_TID ----
    a.add_sp_imm(0, T2_OFF_TID)
    a.movs_imm8(1, 8)
    a.movs_imm8(2, 0)                         # slot_offset = 0
    a.bl_w("read_track_info")

    a.label("ext2_after_track_read")

    # ---- store track_id (file[0..7]) back into .bss state[0..7] ----
    # The transId-at-state[8] mirror is dead (per-event TIDs live in
    # g_avrcp_req_event_database; no consumer reads state[8] anymore), so
    # we narrow the write to just the 8-byte track_id. T9-owned bytes
    # 9..12 stay untouched.
    a.add_sp_imm(0, T2_OFF_TID)               # src = sp+0 (track_id)
    a.movs_imm8(1, 8)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("write_state_block")

    a.label("ext2_after_state_write")

    # ---- reply track_changed_rsp INTERIM ----
    # Before the rsp call: write database[2] → conn[+0x11] so the rsp
    # builder's `ldrb r3, [r4, 0x11]` picks up the inbound RegNotif TID
    # for §3.3.5 strict echo. r5 = struct ptr; conn = r5+8.
    a.add_imm_t3(0, 5, 8)                     # r0 = conn (also restore site target)
    _emit_restore_conn_tid_from_db(a, 0, 0x02, "ext2_tc")
    # r1=0 takes the spec-correct path. Disassembly of the response builder
    # at libextavrcp.so:0x2458 shows `cbnz r5, reject_path` on r1; r1==0 is
    # the spec-correct path that emits reasonCode + event_id + identifier;
    # r1!=0 writes a reject-shape frame that omits the event payload.
    # r3 → 8 zero bytes per AVRCP 1.6 §6.7.2 Table 6.32: 1.3 TGs shall emit
    # Identifier=0x00...00 ("selected track"). Non-zero values are a 1.4+
    # Browseable Player UID extension; strict 1.3 parsers reject those.
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, REASON_INTERIM)
    a.adr_w(3, "selected_track_id")           # r3 = &0x00*8 (§5.14.1 SELECTED)
    a.blx_imm(PLT_track_changed_rsp)

    # No separate "arm" write needed: save_event_seq_id (called at the top
    # of extended_T2 before this rsp builder) already wrote
    # database[2] = TID + 1, which is itself the subscription gate. T5's
    # TRACK_CHANGED emit path checks event_subscribed(2) which reads the
    # same database byte.

    a.label("ext2_epilogue")
    # Restore stack and branch to epilogue.
    a.addw(13, 13, T2_FRAME)
    a.b_w("t4_to_epilogue")


def _emit_t5(a: Asm) -> None:
    """T5: proactive CHANGED emit on track-edge.

    Entered via `b.w T5` from `notificationTrackChangedNative` (jni 0x3bc0).
    Returns jboolean=1 (caller ignores it). On track-edge (state[0..7] !=
    file[0..7]) emits the §5.4.2 track-edge 3-tuple: 0x03 TRACK_REACHED_END
    (gated on file[793] natural-end flag), 0x02 TRACK_CHANGED with SELECTED
    payload, 0x04 TRACK_REACHED_START. Each emit is per-event gated; see
    state[13..20] in the schema above.
    """
    a.label("T5")

    # ---- prologue: save callee-saves we'll trash ----
    # Thumb T1 push: encoding 0xB400 | (LR<<8) | regs[r0..r7]
    # We need r4 + r5 + lr saved.  push {r4, r5, lr} = 0xB430.
    a.raw(bytes([0x30, 0xB5]))                # push {r4, r5, lr}

    # ---- get the BluetoothAvrcpService internal struct ----
    # The helper at JNI_GET_AVRCP_STATE expects r0=env, r1=this — both still
    # set up from the Java native ABI when we entered.
    a.bl_w("jni_get_avrcp_state")             # r0 = struct ptr
    a.mov_lo_lo(4, 0)                         # r4 = struct ptr (preserved)

    # ---- allocate locals: 24 B state buf @ sp+0..23 + 800 B file buf @ sp+24..823 ----
    a.subw(13, 13, T5_FRAME)                  # sub.w sp, sp, #824

    # ---- memset(file_buf, 0, 800) ----
    # Default everything to 0 so a partial read (file shorter than 800 B —
    # e.g. an older writer where file[793] is just a zero pad byte) gives
    # natural_end=0, which means T5 only emits 0x02 + 0x04 (no spurious 0x03
    # emission). Same shape T9 uses for safe defaults.
    a.add_sp_imm(0, T5_OFF_FILE)              # r0 = sp+24
    a.movs_imm8(1, 0)
    a.movw(2, 800)
    a.blx_imm(PLT_memset)

    # ---- copy active slot of y1-track-info into file_buf ----
    a.add_sp_imm(0, T5_OFF_FILE)
    a.movw(1, 800)
    a.movs_imm8(2, 0)                         # slot_offset = 0
    a.bl_w("read_track_info")

    a.label("t5_skip_track_read")

    # ---- zero-init state buf (24 B) before reading 13 B from .bss state ----
    # Zero-fill covers bytes 13..23 (state bytes beyond Y1_TRAMPOLINE_STATE_SIZE
    # plus 4-B alignment padding) so any reads past offset 12 return zero.
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T5_OFF_STATE + 0)
    a.str_sp_imm(0, T5_OFF_STATE + 4)
    a.str_sp_imm(0, T5_OFF_STATE + 8)
    a.str_sp_imm(0, T5_OFF_STATE + 12)
    a.str_sp_imm(0, T5_OFF_STATE + 16)
    a.str_sp_imm(0, T5_OFF_STATE + 20)

    # ---- copy .bss trampoline state into sp+T5_OFF_STATE (13 bytes) ----
    a.add_sp_imm(0, T5_OFF_STATE)
    a.movs_imm8(1, Y1_TRAMPOLINE_STATE_SIZE)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("read_state_block")

    a.label("t5_skip_state_read")

    # ---- compare state[0..7] vs file[0..7] ----
    a.ldr_sp_imm(0, T5_OFF_STATE + 0)         # state[0..3]
    a.ldr_sp_imm(1, T5_OFF_FILE_TID + 0)      # file[0..3]
    a.cmp_w(0, 1)
    a.bne("t5_changed")
    a.ldr_sp_imm(0, T5_OFF_STATE + 4)         # state[4..7]
    a.ldr_sp_imm(1, T5_OFF_FILE_TID + 4)      # file[4..7]
    a.cmp_w(0, 1)
    a.beq_w("t5_no_change")                   # wide-form: extended T5 body
                                              #   exceeds 254 B branch range

    a.label("t5_changed")

    # ---- Emit order matches a reference 1.3-as-TG implementation's
    # observed wire order: PPC=0 → TC → NPCC. Position reset arrives
    # first so the CT zeroes the playhead before processing the identity
    # change; TC arrives second so the CT registers the new track ID
    # before NPCC's now-playing refresh hits. NPCC-first ordering causes
    # the now-playing refresh to land while the CT still considers the
    # OLD track the "selected" one, briefly holding the OLD position on
    # screen until TC arrives and forces a re-query.
    #
    # TR_END / TR_START are emitted AFTER the reference triple. The
    # reference implementation doesn't advertise them (its GetCap Events
    # list = 01/02/05/08/09/0a/0b/0c, no 03/04), so order-relative-to-
    # reference is undefined. Keeping them where they are now (post-TC)
    # maintains backward compat for any strict-1.3 CT that has subscribed
    # to them via T8 INTERIM.

    # ---- emit PLAYBACK_POS_CHANGED (event 0x05) on track edge ----
    # Carries the current position (= duration_ms on natural end, = 0 on
    # NEXT / PREV). Reads file[780..783] BE → host. Gate on database[5].
    _emit_check_event_subscribed(a, 0x05, "t5_skip_pos_changed")

    a.add_imm_t3(0, 4, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x05, "t5_pos")
    a.ldr_sp_imm(3, T5_OFF_FILE + 780)        # r3 = file[780..783] (BE)
    a.rev_lo_lo(3, 3)                         # → host order
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.blx_imm(PLT_reg_notievent_pos_changed_rsp)

    a.label("t5_skip_pos_changed")

    # ---- emit TRACK_CHANGED (event 0x02) — gated on subscription ----
    # AVRCP 1.3 §5.4.2 Table 5.30 + §6.7.2: 1.3 TGs shall emit
    # Identifier=0x00...00 ("selected track"). Non-zero values are a 1.4+
    # Browseable Player UID extension; strict 1.3 parsers silently drop
    # CHANGED with non-zero Identifier and fall back to polling-only
    # metadata refresh. ICS Table 7 row 24 (Mandatory wire-level).
    # Gate on database[2] != 0 (CT sent RegisterNotification(ev=02) this
    # session). Database persistence semantic: zeroed on every
    # libextavrcp_jni.so load (.bss), so ghost-arms from previous sessions
    # don't fire spurious CHANGEDs.
    _emit_check_event_subscribed(a, 0x02, "t5_skip_track_changed")

    a.add_imm_t3(0, 4, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x02, "t5_tc")
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, REASON_CHANGED)
    a.adr_w(3, "selected_track_id")           # r3 = &0x00*8 (§5.14.1 SELECTED)
    a.blx_imm(PLT_track_changed_rsp)

    # database[2] (TRACK_CHANGED subscription gate) stays armed across the
    # CHANGED emit per AVRCP 1.3 §5.4.2 (CHANGED on every value update
    # without requiring CT re-registration). A stricter single-shot-per-
    # registration reading gated CHANGEDs on prompt CT re-RegisterNotif
    # after each emit, which several CTs didn't reliably do — the first
    # CHANGED would be received, metadata fetched, but the CT wouldn't
    # re-RegisterNotification(ev=02) before the next track edge, so Y1's
    # second-track CHANGED would be gated out and the pane stayed frozen
    # on the first track. Set-once-by-RegNotif-INTERIM matches
    # extended_T2's INTERIM arm; per-event TID echo correctness is
    # preserved via the database read in _emit_restore_conn_tid_from_db.

    a.label("t5_skip_track_changed")

    # ---- emit NowPlayingContentChanged (event 0x09) ----
    # NowPlayingContent CHANGED on every track edge. Gate on database[9] !=
    # 0 (CT sent RegisterNotification(ev=09) this session). Database
    # is in .bss (wiped on every libextavrcp_jni.so load), so ghost-arms
    # from previous sessions can't trigger spurious CHANGEDs after reboot
    # or process restart. event_subscribed also clobbers r0 (= database
    # byte) — we re-load r0 = conn after the gate.
    _emit_check_event_subscribed(a, 0x09, "t5_skip_now_playing")
    a.add_imm_t3(0, 4, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x09, "t5_ncc")
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.blx_imm(PLT_reg_notievent_now_playing_content_rsp)

    a.label("t5_skip_now_playing")

    # ---- emit TRACK_REACHED_END (event 0x03) only if natural AND subscribed ----
    # AVRCP 1.3 §5.4.2 Table 5.31. ICS Table 7 row 25 (Optional). Two gates:
    #   1. previous-track-natural-end flag at file[793] (set by music app
    #      before metachanged broadcast)
    #   2. database[3] (event_subscribed check below — armed by T8's
    #      INTERIM ack for ev=03; stays armed across CHANGEDs).
    a.ldrb_w(0, 13, T5_OFF_FILE_NATURAL_END)
    a.cmp_imm8(0, 0)
    a.beq("t5_skip_reached_end")
    _emit_check_event_subscribed(a, 0x03, "t5_skip_reached_end")

    # reg_notievent_reached_end_rsp(conn, 0, REASON_CHANGED)
    a.add_imm_t3(0, 4, 8)                     # r0 = r4 + 8 (conn)
    _emit_restore_conn_tid_from_db(a, 0, 0x03, "t5_re_end")
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, REASON_CHANGED)
    a.blx_imm(PLT_reg_notievent_reached_end_rsp)

    a.label("t5_skip_reached_end")

    # ---- emit TRACK_REACHED_START (event 0x04) — gated on subscription ----
    # AVRCP 1.3 §5.4.2 Table 5.32. ICS Table 7 row 26 (Optional).
    _emit_check_event_subscribed(a, 0x04, "t5_skip_reached_start")

    a.add_imm_t3(0, 4, 8)                     # r0 = r4 + 8 (conn)
    _emit_restore_conn_tid_from_db(a, 0, 0x04, "t5_re_start")
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.movs_imm8(2, REASON_CHANGED)
    a.blx_imm(PLT_reg_notievent_reached_start_rsp)

    a.label("t5_skip_reached_start")

    # ---- update state in-memory: state[0..7] = file[0..7] ----
    a.ldr_sp_imm(0, T5_OFF_FILE_TID + 0)
    a.str_sp_imm(0, T5_OFF_STATE + 0)
    a.ldr_sp_imm(0, T5_OFF_FILE_TID + 4)
    a.str_sp_imm(0, T5_OFF_STATE + 4)

    # ---- write updated state[0..7] (track_id) back to .bss ----
    # state[8] is unused; T9-owned bytes 9..12 stay untouched because we
    # narrow the write to 8 bytes.
    a.add_sp_imm(0, T5_OFF_STATE)
    a.movs_imm8(1, 8)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("write_state_block")

    a.label("t5_no_change")
    # ---- epilogue: return jboolean true ----
    a.movs_imm8(0, 1)
    a.addw(13, 13, T5_FRAME)
    # pop {r4, r5, pc} — Thumb T1 pop: 0xBC00 | (PC<<8) | regs[r0..r7]
    # PC bit is bit 8.  pop {r4, r5, pc} = 0xBD30.
    a.raw(bytes([0x30, 0xBD]))


def _emit_t_charset(a: Asm) -> None:
    """T_charset: PDU 0x17 reject with AV/C NOT_IMPLEMENTED via UNKNOW_INDICATION.

    Spec-permissible per AVRCP 1.3 §5.2.7 (Optional). Acking via
    inform_charsetset_rsp stalls at least one strict CT into a 3 s wait
    between 0x17 and the first RegisterNotification; reject lets the
    subscription burst land in <10 ms. We keep sending UTF-8 either way —
    §5.2.7 doesn't couple the TG's outbound charset to the CT's advertised
    set.
    """
    a.label("T_charset")
    a.ldrh_w(14, 13, T4_LR_CANARY_OFF_ENTRY)  # ldrh.w lr, [sp, #374]
    a.add_imm_t3(0, 5, 8)                     # add.w r0, r5, #8 (= conn)
    a.b_w("t4_to_unknown")


def _emit_t_battery(a: Asm) -> None:
    """T_battery: PDU 0x18 InformBatteryStatusOfCT — ack via battery_status_rsp.

    The CT advertises its battery state to us; we ack but surface it nowhere
    (no CT-battery API on Y1).
    """
    a.label("T_battery")
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    a.movs_imm8(1, 0)                         # r1 = 0 (success)
    a.blx_imm(PLT_battery_status_rsp)
    a.b_w("t4_to_epilogue")


def _emit_t_continuation(a: Asm) -> None:
    """T_continuation: PDU 0x40 / 0x41 explicit reject.

    Spec strict-reject is AV/C INVALID_PARAMETER (§6.15.2); UNKNOW_INDICATION
    emits AV/C NOT_IMPLEMENTED — both are reject frames and the CT abandons
    continuation either way. T4 never fragments (mtkbt fragments below at
    AVCTP), so a spec-conforming CT never sends 0x40 against us.
    """
    a.label("T_continuation")
    # Restore lr canary + r0 to match UNKNOW_INDICATION's expected entry state.
    a.ldrh_w(14, 13, T4_LR_CANARY_OFF_ENTRY)  # ldrh.w lr, [sp, #374]
    a.add_imm_t3(0, 5, 8)                     # add.w r0, r5, #8 (= conn)
    a.b_w("t4_to_unknown")


def _emit_t6(a: Asm) -> None:
    """T6: PDU 0x30 GetPlayStatus.

    Calls btmtk_avrcp_send_get_playstatus_rsp(conn, 0, dur_ms, pos_ms,
    play_status). When playing_flag == 1, position is live-extrapolated via
    clock_gettime(CLOCK_BOOTTIME) and `live_pos = pos_at_state_change +
    (now_ms - state_change_ms)` — same monotonic-since-boot epoch as the
    music app's SystemClock.elapsedRealtime, so subtraction is bit-exact.
    tv_nsec/1e6 uses the standard GCC reciprocal magic-multiply 0x431BDE83.
    Stopped/paused freezes at the saved position.

    Reads (BE u32 / u8): file[776..779] dur_ms; [780..783] pos_at_state_change;
    [784..787] state_change_time; [792] playing_flag.
    """
    a.label("T6")

    # ---- allocate stack frame ----
    a.subw(13, 13, T6_FRAME)                  # sub.w sp, sp, #816

    # ---- memset(file_buf, 0, 800) ----
    # Default everything to 0 so a partial read (file shorter than 800 B,
    # e.g. an older writer that hasn't been rebuilt for the current schema)
    # gives play_status=0 (STOPPED) and duration / position = 0 rather than
    # uninitialized stack garbage.
    a.add_sp_imm(0, T6_OFF_FILE)              # r0 = sp+16
    a.movs_imm8(1, 0)
    a.movw(2, 800)
    a.blx_imm(PLT_memset)

    # ---- copy active slot of y1-track-info into file_buf ----
    a.add_sp_imm(0, T6_OFF_FILE)
    a.movw(1, 800)
    a.movs_imm8(2, 0)                         # slot_offset = 0
    a.bl_w("read_track_info")

    a.label("t6_skip_track_read")

    # ---- assemble args for get_playstatus_rsp(conn, 0, dur, pos, play_status) ----
    # sp[0] (caller stack arg slot 0) = play_status byte.
    a.ldrb_w(0, 13, T6_OFF_FILE_PLAYFLAG)     # r0 = playing_flag (0/1/2)
    a.strb_w(0, 13, T6_OFF_ARGS)              # sp[0] = play_status

    # Live position extrapolation.
    # If playing_flag == 1 (PLAYING):
    #   live_pos = saved_pos_ms + (now_ms - state_change_ms)
    #   now_ms   = tv_sec * 1000 + tv_nsec / 1e6
    # Else (STOPPED / PAUSED):
    #   live_pos = saved_pos  (the position field IS the freeze point for
    #                          paused / stopped, which is what CTs expect)
    # AVRCP 1.3 §5.4.1 Table 5.26 specifies SongPosition as "the current
    # position of the playing in milliseconds elapsed". A static position
    # that doesn't advance during playback violates that semantic — CTs
    # that visualize playback progress expect the value to advance with
    # playback, and some interpret a stuck-across-polls position as "no
    # position info" and hide the playback-progress display.
    a.cmp_imm8(0, 1)                          # r0 still = playing_flag
    a.bne("t6_position_static")

    # ---- clock_gettime(CLOCK_BOOTTIME, &timespec) ----
    # Default the timespec to zero so a syscall failure (extremely unlikely
    # — clock_gettime can't really fail with valid args) yields a bounded
    # fallback: now_ms collapses to 0, delta_ms wraps to (-state_change_ms)
    # mod 2^32, position lurches once. Better than uninit garbage.
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T6_OFF_TIMESPEC_SEC)
    a.str_sp_imm(0, T6_OFF_TIMESPEC_NSEC)

    a.movs_imm8(0, CLOCK_BOOTTIME)            # r0 = clk_id = 7
    a.add_sp_imm(1, T6_OFF_TIMESPEC)          # r1 = &timespec
    a.movw(7, NR_clock_gettime)               # r7 = 263
    a.svc(0)

    # ---- now_ms = tv_sec * 1000 + tv_nsec / 1_000_000 ----
    # tv_nsec/1e6 via magic-multiply: result = (tv_nsec * 0x431BDE83) >> 50,
    # equivalent to taking the high half of the 64-bit product then >>18.
    # 0x431BDE83 is GCC's standard reciprocal for unsigned div-by-1e6 on
    # a u32 input bounded by 1e9 (tv_nsec < 1_000_000_000). Verified
    # bit-exact for the full input range — see trampoline header comment.
    a.ldr_sp_imm(2, T6_OFF_TIMESPEC_SEC)      # r2 = tv_sec
    a.movw(0, 1000)
    a.muls_lo_lo(2, 0)                        # r2 = tv_sec * 1000
    a.ldr_sp_imm(0, T6_OFF_TIMESPEC_NSEC)     # r0 = tv_nsec
    a.movw(1, 0xDE83)
    a.movt(1, 0x431B)                         # r1 = 0x431BDE83 (magic)
    a.umull(4, 3, 0, 1)                       # r3:r4 = tv_nsec * magic; r3 = high half
    a.lsrs_imm5(3, 3, 18)                     # r3 = high >> 18 = tv_nsec / 1e6
    a.adds_lo_lo(2, 2, 3)                     # r2 = now_ms (= tv_sec*1000 + tv_nsec/1e6)

    # ---- delta_ms = now_ms - state_change_ms ----
    # state_change_time field at file[784..787] is now u32 ms-since-boot
    # (was sec-since-boot). u32 modular subtraction; correct under wrap
    # provided both endpoints are in the same domain. u32 ms wraps after
    # ~49.7 days uptime, well past any Y1 reboot cycle.
    a.ldr_sp_imm(0, T6_OFF_FILE_STATE_TIME)   # r0 = state_change_ms (BE)
    a.rev_lo_lo(0, 0)                         # → host order
    a.subs_lo_lo(2, 2, 0)                     # r2 = delta_ms

    # ---- live_pos = saved_pos + delta_ms ----
    a.ldr_sp_imm(3, T6_OFF_FILE_POS)          # r3 = saved_pos (BE)
    a.rev_lo_lo(3, 3)                         # → host order
    a.adds_lo_lo(3, 3, 2)                     # r3 = saved_pos + delta_ms

    a.b_w("t6_emit_response")

    a.label("t6_position_static")
    a.ldr_sp_imm(3, T6_OFF_FILE_POS)          # r3 = saved_pos (BE)
    a.rev_lo_lo(3, 3)                         # → host order

    a.label("t6_emit_response")

    # r2 = duration_ms (BE in file → REV → host order)
    a.ldr_sp_imm(2, T6_OFF_FILE_DURATION)
    a.rev_lo_lo(2, 2)

    # r0 = conn buffer (r5+8); r1 = 0 (success); r3 = position (already set)
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)
    # T6 fires on every CT GetPlayStatus poll (high-frequency, low-signal
    # for diagnostics); no debug log emit here. The same play_status /
    # position values surface in T9pos at the 1 Hz cadence.
    a.blx_imm(PLT_get_playstatus_rsp)

    # ---- restore stack and tail-call epilogue ----
    a.addw(13, 13, T6_FRAME)
    a.b_w("t4_to_epilogue")


def _emit_t_papp(a: Asm) -> None:
    """T_papp: PlayerApplicationSettings PDUs 0x11..0x16.

    Branched from T4's pre-check when the inbound PDU byte is in [0x11..0x16].
    Per AVRCP 1.3 ICS Table 7 C.14, supporting any single PApp PDU makes the
    full 7-row group (PDUs 0x11..0x16 + event 0x08) Mandatory — they all
    ship together.

    Y1 supports Repeat (id=2, three values OFF/SINGLE/ALL) + Shuffle (id=3,
    two values OFF/ALL_TRACK). Live values come from y1-track-info[795..796]
    written by the music app's PappStateBroadcaster on every
    musicRepeatMode/musicIsShuffle SharedPreferences edge. Set PDU (0x14)
    writes 2 bytes (attr_id, value) to y1-papp-set; the music app's
    PappSetFileObserver picks the write up and applies it via
    SharedPreferencesUtils so settings round-trip to the Android media
    session.

    Inbound AVRCP frame layout (caller's stack, post-T_papp SUB SP shifts
    by PAPP_FRAME):
      sp + 382  PDU
      sp + 383  packet_type
      sp + 384  param_length BE u16
      sp + 386  param body (PDU-specific):
        0x11 ListAttrs        : 0 bytes
        0x12 ListValues       : 1 byte attr_id
        0x13 GetCurrent       : 1 byte n + n attr_ids
        0x14 Set              : 1 byte n + n×{attr_id, value}
        0x15 AttrText         : 1 byte n + n attr_ids
        0x16 ValueText        : 1 byte attr_id + 1 byte n + n value_ids

    Builder calling conventions (see ARCHITECTURE.md "PlayerApplicationSettings
    response builders" + docs/INVESTIGATION.md for the disassembly).
    """
    a.label("T_papp")

    # ---- allocate stack frame for outgoing args ----
    a.subw(13, 13, PAPP_FRAME)

    # ---- dispatch on PDU ----
    a.ldrb_w(0, 13, T4_PDU_OFF_ENTRY + PAPP_FRAME)   # r0 = PDU
    a.cmp_imm8(0, 0x11)
    a.beq("papp_list_attrs")
    a.cmp_imm8(0, 0x12)
    a.beq("papp_list_values")
    a.cmp_imm8(0, 0x13)
    a.beq("papp_get_current")
    a.cmp_imm8(0, 0x14)
    a.beq_w("papp_set")
    a.cmp_imm8(0, 0x15)
    a.beq_w("papp_attr_text")
    # The only remaining PDU in the dispatch range is 0x16; fall through.

    # ---- 0x16 GetPlayerApplicationSettingValueText ----
    # btmtk_avrcp_send_get_player_value_text_value_rsp(
    #     conn, reject, idx, total, attr_id, value_id, charset, length, *str)
    # Accumulator: emits AVRCP_SendMessage on (idx+1==total).
    #
    # Param layout: sp+386 = attr_id (1 B), sp+387 = n (1 B),
    # sp+388..387+n = value_ids.
    #
    # Switch on (attr_id, first value_id) and emit the matching label.
    # We only handle the FIRST requested value (single-emit, idx=0/total=1)
    # — adequate for the CTs in our test matrix; multi-emit AttrText is
    # the spec-compliant extension and could be added if a future CT
    # requires it. Unsupported (attr_id, value_id) pairs jump to
    # papp_done with no emission (AVRCP layer sees no response, peer
    # times out / falls back).
    a.label("papp_value_text")
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 0)   # r6 = attr_id
    a.ldrb_w(7, 13, PAPP_PARAM_OFF + 2)   # r7 = first requested value_id

    a.cmp_imm8(6, PAPP_ATTR_REPEAT)
    a.beq("papp_vt_repeat")
    a.cmp_imm8(6, PAPP_ATTR_SHUFFLE)
    a.beq("papp_vt_shuffle")
    a.b_w("papp_done")

    a.label("papp_vt_repeat")
    # Repeat values: 0x01 OFF, 0x02 SINGLE, 0x03 ALL.
    a.cmp_imm8(7, 0x01)
    a.beq("papp_vt_emit_off")
    a.cmp_imm8(7, 0x02)
    a.beq("papp_vt_emit_single")
    a.cmp_imm8(7, 0x03)
    a.beq("papp_vt_emit_all")
    a.b_w("papp_done")

    a.label("papp_vt_shuffle")
    # Shuffle values: 0x01 OFF, 0x02 ALL_TRACK.
    a.cmp_imm8(7, 0x01)
    a.beq("papp_vt_emit_off")
    a.cmp_imm8(7, 0x02)
    a.beq("papp_vt_emit_all")
    a.b_w("papp_done")

    # Each emit block builds the 5 stack args + 4 reg args and tail-jumps
    # to papp_done. Common shape:
    #   r0 = conn, r1 = 0 (success), r2 = idx 0, r3 = total 1
    #   sp[0] = attr_id (r6), sp[4] = value_id (r7), sp[8] = charset 0x6A,
    #   sp[12] = length, sp[16] = &str
    a.label("papp_vt_emit_off")
    a.movs_imm8(2, 3)                     # "Off" length
    a.str_sp_imm(2, PAPP_OFF_ARGS + 12)
    a.adr_w(2, "papp_text_off")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 16)
    a.b_w("papp_vt_emit_common")

    a.label("papp_vt_emit_single")
    a.movs_imm8(2, 12)                    # "Single Track" length
    a.str_sp_imm(2, PAPP_OFF_ARGS + 12)
    a.adr_w(2, "papp_text_single")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 16)
    a.b_w("papp_vt_emit_common")

    a.label("papp_vt_emit_all")
    a.movs_imm8(2, 10)                    # "All Tracks" length
    a.str_sp_imm(2, PAPP_OFF_ARGS + 12)
    a.adr_w(2, "papp_text_all")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 16)
    # fall through

    a.label("papp_vt_emit_common")
    a.str_sp_imm(6, PAPP_OFF_ARGS + 0)    # sp[0] = attr_id
    a.str_sp_imm(7, PAPP_OFF_ARGS + 4)    # sp[4] = value_id
    a.movs_imm8(2, 0x6A)
    a.str_sp_imm(2, PAPP_OFF_ARGS + 8)    # sp[8] = charset UTF-8
    a.add_imm_t3(0, 5, 8)                 # r0 = conn
    a.movs_imm8(1, 0)                     # r1 = success
    a.movs_imm8(2, 0)                     # r2 = idx 0
    a.movs_imm8(3, 1)                     # r3 = total 1
    a.blx_imm(PLT_get_player_value_text_rsp)
    a.b_w("papp_done")

    # ---- 0x11 ListPlayerApplicationSettingAttributes ----
    # Returns: [Repeat=0x02, Shuffle=0x03], n=2.
    a.label("papp_list_attrs")
    a.add_imm_t3(0, 5, 8)                 # r0 = conn
    a.movs_imm8(1, 0)                     # r1 = success
    a.movs_imm8(2, 2)                     # r2 = n_attrs
    a.adr_w(3, "papp_attr_ids")           # r3 = &[2, 3]
    a.blx_imm(PLT_list_player_attrs_rsp)
    a.b_w("papp_done")

    # ---- 0x12 ListPlayerApplicationSettingValues ----
    # Inbound: 1 byte attr_id at sp+386. Switch on attr_id:
    #   2 → [1,2,3]   (Repeat: OFF / SINGLE / ALL — Y1 has no GROUP)
    #   3 → [1,2]     (Shuffle: OFF / ALL_TRACK — Y1 has no GROUP)
    #   else → reject
    # Honest advertisement: only the values Y1 can actually honor. Stock
    # advertised the full Tbl 5.20 / 5.21 sets including GROUP, so a CT
    # could Set 0x04 (Repeat GROUP) or 0x03 (Shuffle GROUP); T_papp 0x14
    # ACKed success but the music app's enum mapper rejected → CT-side
    # state diverged from Y1-side state.
    a.label("papp_list_values")
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 0)   # r6 = attr_id
    a.cmp_imm8(6, PAPP_ATTR_REPEAT)
    a.beq("papp_lv_repeat")
    a.cmp_imm8(6, PAPP_ATTR_SHUFFLE)
    a.beq("papp_lv_shuffle")
    # Unsupported attr_id → reject. arg5 still has to be passed (function
    # loads from stack regardless), so set sp[0] = 0.
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 1)                     # r1 != 0 → reject path
    a.movs_imm8(2, 0)
    a.movs_imm8(3, 0)
    a.blx_imm(PLT_list_player_values_rsp)
    a.b_w("papp_done")

    a.label("papp_lv_repeat")
    a.adr_w(0, "papp_repeat_values")
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)    # sp[0] = &[1,2,3]
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)                     # success
    a.movs_imm8(2, PAPP_ATTR_REPEAT)
    a.movs_imm8(3, 3)                     # n_values (OFF / SINGLE / ALL)
    a.blx_imm(PLT_list_player_values_rsp)
    a.b_w("papp_done")

    a.label("papp_lv_shuffle")
    a.adr_w(0, "papp_shuffle_values")
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)    # sp[0] = &[1,2]
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)
    a.movs_imm8(2, PAPP_ATTR_SHUFFLE)
    a.movs_imm8(3, 2)                     # n_values (OFF / ALL_TRACK)
    a.blx_imm(PLT_list_player_values_rsp)
    a.b_w("papp_done")

    # ---- 0x13 GetCurrentPlayerApplicationSettingValue ----
    # Inbound: 1 byte n + n attr_ids. Per AVRCP 1.3 §5.2.3, "The TG returns
    # the current value(s) of the player application setting(s) requested by
    # the CT" — strict CTs reject a response whose n field doesn't match the
    # request and close the AVCTP channel. Honor the spec by branching on
    # the inbound n: n==1 → return only the requested attr; otherwise fall
    # through to the existing two-attr response (kept for the n==2 case +
    # permissive CTs that send n==0 to mean "all").
    #
    # Live values: open y1-track-info, lseek to byte 795, read 2 bytes
    # ([repeat_avrcp, shuffle_avrcp]) into the outgoing-args region, pass
    # the live pointer as the values pointer. On I/O failure fall back to
    # the static OFF/OFF table at papp_current_values (n==2) or to the
    # single-byte 0x01 OFF default (n==1).
    a.label("papp_get_current")

    # Honor V13 §6.12 — branch to n==1 handler if inbound n is 1.
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 0)   # r6 = inbound n (caller's sp+386)
    a.cmp_imm8(6, 1)
    a.beq_w("papp_gc_n1")

    # ---- n != 1: existing two-attr path ----
    # Read live repeat (slot[795]) + shuffle (slot[796]) bytes from the
    # active slot via the mmap-backed read_track_info subroutine. sp+8
    # is in the outgoing-args region — we pass sp+0 as the values
    # pointer; sp+8..9 is unused by the response builder's stack args,
    # so it's safe scratch for the 2-byte read.
    a.add_sp_imm(0, PAPP_OFF_ARGS + 8)        # r0 = dst = sp+8
    a.movs_imm8(1, 2)                         # r1 = nbytes = 2
    a.movw(2, 795)                            # r2 = slot_offset = 795 (repeat+shuffle)
    a.bl_w("read_track_info")
    a.cmp_imm8(0, 0)
    a.beq("papp_gc_static_fallback")          # read_track_info returns 0 on mmap fail

    # Pass sp+8 as the live values pointer
    a.add_sp_imm(0, PAPP_OFF_ARGS + 8)
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)
    a.b_w("papp_gc_emit")

    a.label("papp_gc_static_fallback")
    a.adr_w(0, "papp_current_values")
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)

    a.label("papp_gc_emit")
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)
    a.movs_imm8(2, 2)                     # n_pairs
    a.adr_w(3, "papp_attr_ids")           # &[2, 3]
    a.blx_imm(PLT_get_curplayer_value_rsp)
    a.b_w("papp_done")

    # ---- n == 1: single-attr response ----
    # Outgoing-args layout for this path (PAPP_OFF_ARGS == 0). All buffer
    # base addresses are 4-byte aligned so add_sp_imm can reach them; the
    # 1-byte payloads live at the start of each 4-byte slot:
    #   sp+ 0..3  = stack arg slot for values_ptr (= sp+12)
    #   sp+ 8..11 = attr_ids buffer (sp+8 = requested attr_id byte)
    #   sp+12..15 = values buffer   (sp+12 = picked value byte)
    #   sp+16..19 = read scratch    (sp+16 = repeat byte, sp+17 = shuffle byte)
    a.label("papp_gc_n1")

    # Read inbound attr_id at caller's sp+387; validate as Repeat (0x02) or
    # Shuffle (0x03). Anything else → reject with V13 §6.15.2 status 0x05
    # INVALID_PARAMETER.
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 1)   # r6 = requested attr_id
    a.cmp_imm8(6, PAPP_ATTR_REPEAT)
    a.beq("papp_gc_n1_open")
    a.cmp_imm8(6, PAPP_ATTR_SHUFFLE)
    a.bne_w("papp_gc_n1_reject")

    a.label("papp_gc_n1_open")
    # Read live repeat (slot[795]) + shuffle (slot[796]) bytes from the
    # active slot via the mmap-backed read_track_info subroutine into
    # sp+16..17 (aligned scratch; sp+16 = repeat, sp+17 = shuffle).
    a.add_sp_imm(0, PAPP_OFF_ARGS + 16)
    a.movs_imm8(1, 2)                     # nbytes = 2
    a.movw(2, 795)                        # slot_offset = 795 (repeat+shuffle)
    a.bl_w("read_track_info")
    a.cmp_imm8(0, 0)
    a.beq("papp_gc_n1_static")            # 0 → mmap unavailable, use static OFF

    # Pick the byte matching the requested attr_id: Repeat at +16, Shuffle at +17.
    a.cmp_imm8(6, PAPP_ATTR_SHUFFLE)
    a.beq("papp_gc_n1_use_shuffle")
    a.ldrb_w(7, 13, PAPP_OFF_ARGS + 16)   # r7 = repeat byte
    a.b_w("papp_gc_n1_emit")

    a.label("papp_gc_n1_use_shuffle")
    a.ldrb_w(7, 13, PAPP_OFF_ARGS + 17)   # r7 = shuffle byte
    a.b_w("papp_gc_n1_emit")

    a.label("papp_gc_n1_static")
    # File I/O failed. PAPP_REPEAT_OFF and PAPP_SHUFFLE_OFF are both 0x01 per
    # V13 Tbl 5.20 / 5.21, so a single OFF default covers either attr_id.
    a.movs_imm8(7, PAPP_REPEAT_OFF)

    a.label("papp_gc_n1_emit")
    # Pack response: attr_ids[0] = r6 at sp+8, values[0] = r7 at sp+12.
    a.strb_w(6, 13, PAPP_OFF_ARGS + 8)
    a.strb_w(7, 13, PAPP_OFF_ARGS + 12)

    # Stack arg sp[0] = values_ptr (= sp + PAPP_OFF_ARGS + 12).
    a.add_sp_imm(0, PAPP_OFF_ARGS + 12)
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)

    # get_curplayer_value_rsp(conn, 0 success, n=1, &attr_ids).
    a.add_imm_t3(0, 5, 8)                 # r0 = conn
    a.movs_imm8(1, 0)
    a.movs_imm8(2, 1)                     # n_pairs = 1
    a.add_sp_imm(3, PAPP_OFF_ARGS + 8)    # r3 = &attr_ids
    a.blx_imm(PLT_get_curplayer_value_rsp)
    a.b_w("papp_done")

    a.label("papp_gc_n1_reject")
    # V13 §6.15.2 status 0x05 INVALID_PARAMETER. The response builder still
    # requires the n/ids/values triple; pass n=0 with dummy buffers at the
    # aligned slots.
    a.movs_imm8(0, 0)
    a.strb_w(0, 13, PAPP_OFF_ARGS + 8)
    a.strb_w(0, 13, PAPP_OFF_ARGS + 12)
    a.add_sp_imm(0, PAPP_OFF_ARGS + 12)
    a.str_sp_imm(0, PAPP_OFF_ARGS + 0)
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 5)                     # INVALID_PARAMETER
    a.movs_imm8(2, 0)                     # n=0
    a.add_sp_imm(3, PAPP_OFF_ARGS + 8)
    a.blx_imm(PLT_get_curplayer_value_rsp)
    a.b_w("papp_done")

    # ---- 0x14 SetPlayerApplicationSettingValue ----
    # Parse first (attr_id, value) pair from inbound param body at caller's
    # sp+387/+388 (= our sp+0x19b/+0x19c after the PAPP_FRAME shift), write
    # the 2 bytes to /data/data/com.innioasis.y1/files/y1-papp-set, ACK the
    # peer with success. The music app's PappSetFileObserver picks up the
    # file write and forwards the change to setMusicRepeatMode /
    # setMusicIsShuffle via SharedPreferencesUtils.
    #
    # Multi-pair Sets (n > 1) apply only the first pair. AVRCP 1.3 §5.2.4
    # lets a TG that supports a subset of attributes acknowledge any Set
    # whose listed attributes it can honor.
    a.label("papp_set")
    # Validate (attr_id, value) against the values we ACTUALLY advertise via
    # 0x12 ListValues. AVRCP 1.6 §6.15.3 defines status 0x05 INVALID_PARAMETER
    # for "the parameter is invalid" — appropriate when the CT sets a value
    # outside the supported set.
    #   attr_id 0x02 (Repeat): valid values 0x01..0x03 (OFF / SINGLE / ALL)
    #   attr_id 0x03 (Shuffle): valid values 0x01..0x02 (OFF / ALL_TRACK)
    # Any other attr_id is unsupported.
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 1)         # r6 = attr_id (caller's sp+387)
    a.ldrb_w(7, 13, PAPP_PARAM_OFF + 2)         # r7 = value   (caller's sp+388)

    # attr_id == Repeat → check value in [1..3]
    a.cmp_imm8(6, PAPP_ATTR_REPEAT)
    a.bne("papp_set_check_shuffle")
    a.cmp_imm8(7, 1)
    a.blt("papp_set_reject")
    a.cmp_imm8(7, 3)
    a.bgt("papp_set_reject")
    a.b_w("papp_set_validated")

    a.label("papp_set_check_shuffle")
    a.cmp_imm8(6, PAPP_ATTR_SHUFFLE)
    a.bne("papp_set_reject")
    a.cmp_imm8(7, 1)
    a.blt("papp_set_reject")
    a.cmp_imm8(7, 2)
    a.bgt("papp_set_reject")

    a.label("papp_set_validated")

    # Pack [attr_id, value] into the outgoing-args region (sp+0..1) as
    # the 2-byte write payload. set_player_value_rsp later in this arm
    # doesn't consume any stack args, so sp+0..1 is free scratch.
    a.strb_w(6, 13, PAPP_OFF_ARGS + 0)
    a.strb_w(7, 13, PAPP_OFF_ARGS + 1)

    # open(path_papp_set, O_WRONLY|O_TRUNC, 0) — TrackInfoWriter.prepareFiles()
    # in the music app pre-creates it at process start; if it's somehow gone,
    # skip the write but still ACK (the peer's UI shouldn't get stuck because
    # of a transient writer-side outage). No O_CREAT — same rationale as
    # other writes in this module: trampoline opens files non-creating; the
    # music app owns file creation.
    a.adr_w(0, "path_papp_set")
    a.movw(1, O_WRONLY | O_TRUNC)
    a.movs_imm8(2, 0)
    a.blx_imm(PLT_open)
    a.cmp_imm8(0, 0)
    a.blt("papp_set_skip_write")
    a.mov_lo_lo(4, 0)                            # r4 = fd

    a.mov_lo_lo(0, 4)
    a.add_sp_imm(1, PAPP_OFF_ARGS)               # r1 = &scratch[0]
    a.movs_imm8(2, 2)                            # 2 bytes: attr_id + value
    a.blx_imm(PLT_write)

    a.mov_lo_lo(0, 4)
    a.blx_imm(PLT_close)

    a.label("papp_set_skip_write")

    # ACK the peer with success. set_player_value_rsp(conn, 0) emits the
    # spec-correct success reply per AVRCP 1.3 §5.2.4 / §6.15.2.
    a.add_imm_t3(0, 5, 8)                        # r0 = conn
    a.movs_imm8(1, 0)                            # r1 = 0 (success ACK)
    a.blx_imm(PLT_set_player_value_rsp)
    a.b_w("papp_done")

    a.label("papp_set_reject")
    # Reject path: emit set_player_value_rsp(conn, 0x05 INVALID_PARAMETER).
    # Per V13 §6.15.2 Tbl 6.2 status 0x05 = "The parameter is invalid".
    # The peer's UI typically falls back to its previous value, keeping
    # CT-side and Y1-side state in sync.
    a.add_imm_t3(0, 5, 8)                        # r0 = conn
    a.movs_imm8(1, 5)                            # r1 = 0x05 INVALID_PARAMETER
    a.blx_imm(PLT_set_player_value_rsp)
    a.b_w("papp_done")

    # ---- 0x15 GetPlayerApplicationSettingAttributeText ----
    # Inbound: 1 byte n + n attr_ids at sp+386..386+n.
    # btmtk_avrcp_send_get_player_attr_text_rsp(
    #     conn, reject, idx, total, attr_id, charset, length, *str)
    #
    # Walk the inbound list, set wantRepeat / wantShuffle flags, then emit
    # text only for requested attrs (V13 §5.2.5).
    a.label("papp_attr_text")
    a.ldrb_w(6, 13, PAPP_PARAM_OFF + 0)   # r6 = n (count of attr_ids)
    a.cmp_imm8(6, 0)
    a.beq_w("papp_done")                  # n=0: nothing to emit

    # Walk attr_ids[0..n-1] and accumulate flags in r4 (wantRepeat) / r5
    # (wantShuffle). Loop variable in r3.
    a.movs_imm8(4, 0)                     # r4 = wantRepeat = 0
    a.movs_imm8(5, 0)                     # r5 = wantShuffle = 0
    a.movs_imm8(3, 0)                     # r3 = i = 0
    # Base pointer to attr_ids[0] = sp + PAPP_PARAM_OFF + 1. addw supports
    # 12-bit immediates (PAPP_PARAM_OFF+1 = 411 fits).
    a.addw(2, 13, PAPP_PARAM_OFF + 1)     # r2 = &attr_ids[0]

    a.label("papp_at_loop")
    a.cmp_w(3, 6)
    a.bge("papp_at_loop_done")
    a.ldrb_reg(0, 2, 3)                   # r0 = attr_ids[i]
    a.cmp_imm8(0, PAPP_ATTR_REPEAT)
    a.beq("papp_at_set_repeat")
    a.cmp_imm8(0, PAPP_ATTR_SHUFFLE)
    a.beq("papp_at_set_shuffle")
    a.b_w("papp_at_loop_next")

    a.label("papp_at_set_repeat")
    a.movs_imm8(4, 1)
    a.b_w("papp_at_loop_next")

    a.label("papp_at_set_shuffle")
    a.movs_imm8(5, 1)

    a.label("papp_at_loop_next")
    a.addw(3, 3, 1)                       # i++
    a.b_w("papp_at_loop")

    a.label("papp_at_loop_done")
    # total = wantRepeat + wantShuffle in r0
    a.adds_lo_lo(0, 4, 5)
    a.cmp_imm8(0, 0)
    a.beq_w("papp_done")                  # neither requested → no emit

    # Save total in r6 (which we no longer need for n — done iterating)
    a.mov_lo_lo(6, 0)

    # If wantRepeat: emit (idx=0, total=r6, attr_id=2, "Repeat", 6 B)
    a.cmp_imm8(4, 0)
    a.beq("papp_at_skip_repeat")
    a.movs_imm8(2, PAPP_ATTR_REPEAT)
    a.str_sp_imm(2, PAPP_OFF_ARGS + 0)
    a.movs_imm8(2, 0x6A)
    a.str_sp_imm(2, PAPP_OFF_ARGS + 4)
    a.movs_imm8(2, 6)                     # strlen("Repeat")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 8)
    a.adr_w(2, "papp_text_repeat")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 12)
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)
    a.movs_imm8(2, 0)                     # idx 0
    a.mov_lo_lo(3, 6)                     # total
    a.blx_imm(PLT_get_player_attr_text_rsp)

    a.label("papp_at_skip_repeat")
    # If wantShuffle: emit (idx = wantRepeat ? 1 : 0, total=r6, attr_id=3, "Shuffle", 7 B)
    a.cmp_imm8(5, 0)
    a.beq_w("papp_done")
    a.movs_imm8(2, PAPP_ATTR_SHUFFLE)
    a.str_sp_imm(2, PAPP_OFF_ARGS + 0)
    a.movs_imm8(2, 0x6A)
    a.str_sp_imm(2, PAPP_OFF_ARGS + 4)
    a.movs_imm8(2, 7)                     # strlen("Shuffle")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 8)
    a.adr_w(2, "papp_text_shuffle")
    a.str_sp_imm(2, PAPP_OFF_ARGS + 12)
    a.add_imm_t3(0, 5, 8)
    a.movs_imm8(1, 0)
    a.mov_lo_lo(2, 4)                     # idx = wantRepeat (0 or 1)
    a.mov_lo_lo(3, 6)                     # total
    a.blx_imm(PLT_get_player_attr_text_rsp)
    a.b_w("papp_done")

    a.label("papp_done")
    a.addw(13, 13, PAPP_FRAME)
    a.b_w("t4_to_epilogue")


# g_avrcp_req_event_database is a 15-byte global at vaddr 0xd2b5 (in
# libextavrcp_jni.so's .bss). Stock JNI's inbound CMD dispatcher calls
# saveRegEventSeqId(event_id, seq_id) on every inbound RegisterNotification,
# which writes seq_id into g_avrcp_req_event_database[event_id]. Just before
# calling any reg_notievent_*_rsp builder, stock JNI also writes
# database[event_id] into conn[+0x11], which the rsp builders read as the
# AVCTP transId for §3.3.5 strict-echo response packing (see e.g. stock JNI
# at 0x3c06: `add ip, pc ; ldrb r2, [ip, #2] ; strb r2, [r8, 0x19]` for
# notificationTrackChangedNative).
#
# Our T-trampolines REPLACE the stock prologues of those notification natives
# (and dispatch RegisterNotification CMDs via extended_T2 / T8 before stock's
# inbound handler does its database→conn write), so the conn[+0x11] slot is
# never populated. Empirically: every outbound wire frame emitted by the
# rsp builders ships with chan+0x39 = 0, breaking the strict §3.3.5 echo
# and causing CT-side drops.
#
# Fix: at every rsp call site in our trampolines, replicate the stock
# database→conn write immediately before the rsp builder blx.
G_AVRCP_REQ_EVENT_DATABASE_VADDR = 0xd2b5

# 4-byte slot in .bss padding (between g_y1_avrcp_track_identifier ending at
# 0xd2cc and stock g_avrcp_auto_browse_connect at 0xd2d5). Holds the lazy-init
# mmap'd base pointer for y1-track-info. NULL until the first successful
# get_or_init_mmap call; populated thereafter so subsequent calls skip the
# open + mmap + close syscall chain.
#
# .bss is zeroed on every libextavrcp_jni.so load (process-scoped). When the
# bluetooth process restarts, ptr is NULL on first read and the lazy-init
# re-mmaps. The mmap'd region itself follows the file's inode — since the
# music app's TrackInfoWriter does in-place writes (no tmpfile + rename, which
# would create a fresh inode and orphan our mapping), the same ptr stays
# valid across every flush from the writer side.
G_Y1_TRACK_INFO_MMAP_BASE_VADDR = 0xd2cc

# 13-byte trampoline-state block in .bss padding at 0xd2d6..0xd2e2 — the
# 30-byte gap between stock `g_avrcp_auto_browse_connect` (0xd2d5, 1 B) and
# `g_avrcp_seq_id_database` (0xd2f4, 113 B). Per-byte radare2 cross-reference
# analysis (full `aaaa` pass with relocs applied) confirms no stock code
# accesses any byte in 0xd2d6..0xd2f3. Layout:
#
#   state[0..7]  last_seen track_id (T5 / T4 edge detection)
#   state[8]     unused (per-event TIDs live in g_avrcp_req_event_database)
#   state[9]     last_play_status        (T9 edge detection)
#   state[10]    last_battery_status     (T9 edge detection)
#   state[11]    last_repeat_avrcp       (T9 papp edge)
#   state[12]    last_shuffle_avrcp      (T9 papp edge)
#
# Process-scope (zero-init at every libextavrcp_jni.so load), same semantic
# as g_avrcp_req_event_database. After mtkbt restart, the next T5/T9 fire
# sees state[N] = 0 vs current file value → edge detected → one CHANGED per
# event emitted (gated by subscription database — harmless if CT hasn't
# re-subscribed yet because the gate skips).
#
# 0xd2d6 was verified clean (no stock .text xrefs land within the 13-byte
# range) via per-byte `axt` queries against the full radare2 analysis. The
# 0xd2a4..0xd2b4 range nearby is hostile — stripped statics near __bss_start
# cluster there. See docs/INVESTIGATION.md for the verification methodology.
G_Y1_TRAMPOLINE_STATE_VADDR = 0xd2d6
Y1_TRAMPOLINE_STATE_SIZE    = 13


# y1-track-info schema. Music app's TrackInfoWriter ships file shape:
#
#   file[0]       active_slot (0 or 1) — single-byte atomic flag
#   file[1..3]    RFA padding (4-B-align slot[0])
#   file[4..1107] slot[0] — 1104-byte track-info image (existing schema)
#   file[1108..2211] slot[1] — second 1104-byte copy
#   file[2212]    RFA padding
#
# Writer flow: pick inactive = 1 - active_slot, fill that slot, atomically
# update file[0] = inactive. Reader (us) reads file[0] once, dispatches to
# the slot, and copies into its existing 1104-byte file_buf at the same
# stack offset trampolines have always used. Old per-trampoline field offsets
# (T*_OFF_FILE_*) are unchanged — they index into the copied slot.
Y1_TRACK_INFO_FILE_SIZE  = 2213
Y1_TRACK_INFO_SLOT_SIZE  = 1104
Y1_TRACK_INFO_SLOT0_OFF  = 4
Y1_TRACK_INFO_SLOT1_OFF  = Y1_TRACK_INFO_SLOT0_OFF + Y1_TRACK_INFO_SLOT_SIZE

# mmap2 syscall number on ARM EABI (Linux arch/arm/include/asm/unistd.h).
NR_mmap2 = 192


def _emit_check_event_subscribed(a: Asm, event_id: int, skip_label: str) -> None:
    """Emit `movs r1, #event_id; bl event_subscribed; beq skip_label`.

    Per-call cost: 8 bytes (2 + 4 + 2). Wraps the subscription-gate check
    at every T5/T9 CHANGED emit. event_subscribed reads
    g_avrcp_req_event_database[event_id] (.bss, wiped on every .so load)
    and returns Z=1 if it's 0 (no RegisterNotification received this
    session); the beq then skips the CHANGED emit. r0 is clobbered (= the
    raw database byte); r1 / r2 / lr are clobbered by the subroutine
    itself.
    """
    a.movs_imm8(1, event_id)
    a.bl_w("event_subscribed")
    a.beq(skip_label)


def _emit_restore_conn_tid_from_db(a: Asm, conn_reg: int, event_id: int,
                                   tag: str) -> None:
    """Emit a call to the shared `restore_conn_tid` subroutine that writes
    g_avrcp_req_event_database[event_id] → [r0, #0x11].

    Caller contract:
      - r0 must hold the conn pointer (the subroutine writes through it).
      - event_id must fit in an 8-bit immediate (0..255; all AVRCP event_ids
        are 0x01..0x0d so this is fine).
      - The bl clobbers r1, r2, r3, lr per the subroutine body (and the
        Thumb-1 push/pop dance below). The caller's rsp-builder arg vector
        (r0..r3) must be set up AFTER this call returns. r0 is preserved.

    `conn_reg` and `tag` are accepted for backward-compat with earlier
    inline-helper callers but ignored — every site funnels into the same
    subroutine.

    Per-site cost: 6 bytes (movs r1, #imm8 + bl_w).
    """
    _check = lambda c, m: None if c else (_ for _ in ()).throw(AssertionError(m))
    _check(0 <= event_id <= 0xFF, f"event_id imm8 overflow: {event_id}")
    del conn_reg, tag                          # unused — subroutine reads r0 directly
    a.movs_imm8(1, event_id)
    a.bl_w("restore_conn_tid")


def _emit_restore_conn_tid_subroutine(a: Asm) -> None:
    """Emit the shared restore_conn_tid subroutine.

    Pre: r0 = conn pointer, r1 = event_id.
    Post: [r0, #0x11] = g_avrcp_req_event_database[event_id]; r0 preserved.
    Clobbers r1, r2, r3, lr.

    Pattern mirrors stock JNI's database read (e.g., 0x3c06 in
    notificationTrackChangedNative):
        add ip, pc           ; ip = &g_avrcp_req_event_database
        ldrb r2, [ip, evid]  ; r2 = database[event_id]
        strb r2, [conn, 0x11]
    We use r2/r3 instead of ip/r2 since the trampoline blob's Thumb-1
    forms have wider register support.

    14 B code + alignment + 4 B literal = 16 B total.
    """
    a.label("restore_conn_tid")
    a.ldr_lit_w(2, "restore_conn_tid_lit")
    a.label("restore_conn_tid_add_pc")
    a.add_reg(2, 15)                          # add r2, pc → r2 = absolute db vaddr
    # ldrb r3, [r2, r1]: LDRB (register) T1, 0101 110 Rm Rn Rt → 0x5C00
    # imm fields: Rm=r1, Rn=r2, Rt=r3 → hw = 0x5C00 | (1 << 6) | (2 << 3) | 3
    hw = 0x5C00 | (1 << 6) | (2 << 3) | 3
    a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))
    # subs r3, #1 (T2 SUB imm8) — undo the +1 session-arm encoding stored
    # by save_event_seq_id; r3 now holds the raw seq_id ready for the wire.
    a.raw(bytes([0x01, 0x3B]))
    # strb r3, [r0, #0x11]: STRB imm T1, 0111 0 imm5 Rn Rt
    hw = 0x7000 | (0x11 << 6) | (0 << 3) | 3
    a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))
    a.bx(14)                                  # bx lr
    a.align(4)
    a.label("restore_conn_tid_lit")
    def _emit_lit(_pc: int) -> bytes:
        offset = G_AVRCP_REQ_EVENT_DATABASE_VADDR - (a.labels["restore_conn_tid_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_lit, 4)


def _emit_save_event_seq_id_subroutine(a: Asm) -> None:
    """Emit the shared save_event_seq_id subroutine.

    Pre: r0 = event_id, r1 = seq_id.
    Post: g_avrcp_req_event_database[event_id] = seq_id + 1; r0 preserved.
    Clobbers r1, r2, r3, lr.

    Mirrors stock JNI's saveRegEventSeqId at 0x5ee4 — same database, same
    indexing — but called from our trampoline path since R1 hijacks the
    dispatcher upstream of stock's call site at 0x6d26. The +1 offset
    is our own encoding (not stock JNI's): database[event_id] = 0
    unambiguously means "no RegisterNotification received this session"
    (the .bss is zeroed on every libextavrcp_jni.so load). T5/T9 emit
    gates check database[event_id] != 0 before firing CHANGEDs, and
    restore_conn_tid subtracts 1 before writing to conn[+0x11] so the
    raw seq_id reaches the wire. Without this encoding, database[event_id]=1
    ghost-arms from previous-session subscriptions trigger unsolicited
    CHANGEDs in fresh sessions where the CT hasn't actually subscribed,
    which strict-§3.3.5 CTs reject and disengage over.

    Caller obligation: the `adds r1, #1` inside this subroutine SETS
    flags. If the next instruction in the caller is a conditional
    branch, that branch will test the adds-derived Z (= r1+1 == 0,
    only true if inbound seq_id was 0xFF — basically never for a valid
    seq_id). extended_T2 dodges this by doing a fresh `cmp r0, 0x02`
    after the call, which overwrites the flags.

    14 B code + alignment + 4 B literal = 18 B total.
    """
    a.label("save_event_seq_id")
    a.ldr_lit_w(2, "save_event_seq_id_lit")
    a.label("save_event_seq_id_add_pc")
    a.add_reg(2, 15)                          # add r2, pc → r2 = absolute db vaddr
    # adds r1, #1 (T2 ADD imm8) — encode "seq_id + 1" so 0 unambiguously
    # means "not subscribed this session"
    a.raw(bytes([0x01, 0x31]))
    # strb r1, [r2, r0]: STRB (register) T1, 0101 010 Rm Rn Rt → 0x5400
    # Rm=r0, Rn=r2, Rt=r1 → hw = 0x5400 | (0 << 6) | (2 << 3) | 1
    hw = 0x5400 | (0 << 6) | (2 << 3) | 1
    a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))
    a.bx(14)                                  # bx lr
    a.align(4)
    a.label("save_event_seq_id_lit")
    def _emit_lit(_pc: int) -> bytes:
        offset = G_AVRCP_REQ_EVENT_DATABASE_VADDR - (a.labels["save_event_seq_id_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_lit, 4)


def _emit_clear_event_database_subroutine(a: Asm) -> None:
    """Emit the shared clear_event_database subroutine.

    Pre: (none)
    Post: g_avrcp_req_event_database[0..14] = 0; r0..r3 / lr clobbered.

    Called from T1_extended whenever the CT issues a fresh GetCapabilities
    request — by the AVRCP 1.3 §5.4.1 connection-setup flow, that's the
    first CMD on every new CT→TG connection. Clearing the database here
    means subscriptions from a previous CT session don't leak forward.

    .bss being zeroed on process restart handles the cross-process case;
    this subroutine handles the within-process CT disconnect/reconnect
    case (com.android.bluetooth stays alive across CT churn, so .bss
    persists).

    4 × str (8 B) + ldr.w (4 B) + add (2 B) + movs (2 B) + bx (2 B) = 18 B
    + 2 B align + 4 B literal = 24 B total. Covers 16 bytes at
    0xd2b5..0xd2c4: 15 B database (0xd2b5..0xd2c3) + 1 B slop. No
    overlap with g_avrcp_auto_browse_connect at 0xd2d5.
    """
    a.label("clear_event_database")
    a.ldr_lit_w(2, "clear_event_database_lit")
    a.label("clear_event_database_add_pc")
    a.add_reg(2, 15)                          # add r2, pc → r2 = absolute db vaddr
    a.movs_imm8(0, 0)                         # r0 = 0
    # 4 word stores @ offsets 0, 4, 8, 12 from r2 (= 0xd2b5).
    # str (immediate) T1: 0110 0 imm5 Rn Rt — encoded inline.
    for word_off in (0, 4, 8, 12):
        imm5 = word_off >> 2
        hw = 0x6000 | (imm5 << 6) | (2 << 3) | 0
        a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))
    a.bx(14)                                  # bx lr
    a.align(4)
    a.label("clear_event_database_lit")
    def _emit_lit(_pc: int) -> bytes:
        offset = G_AVRCP_REQ_EVENT_DATABASE_VADDR - (a.labels["clear_event_database_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_lit, 4)


def _emit_event_subscribed_subroutine(a: Asm) -> None:
    """Emit the shared event_subscribed subroutine.

    Pre: r1 = event_id.
    Post: Z flag set iff g_avrcp_req_event_database[event_id] == 0 (= no
        RegisterNotification received this session). Other flags (N, C, V)
        are clobbered by the internal cmp; callers must only use beq / bne
        / cmp-derived predicates after this call. r0 returns the raw
        database byte (TID + 1 if subscribed, 0 if not). Clobbers r2, lr.

    Caller pattern at every T5/T9 CHANGED emit gate:
        movs r1, #event_id
        bl   event_subscribed
        beq  skip_emit_label
        ... emit code (eventually bl restore_conn_tid + blx *_rsp) ...

    CRITICAL invariant: the `beq` MUST be the very next instruction after
    the `bl`. The subroutine's `cmp r0, 0` is what sets Z; `bx lr` preserves
    it, but any intervening instruction that sets flags (movs, cmp, adds,
    or a PLT blx with __android_log_print) will overwrite Z and the gate
    fails open. Don't splice debug logs between the bl and the beq.

    12 B code + alignment + 4 B literal = 16 B total.
    """
    a.label("event_subscribed")
    a.ldr_lit_w(2, "event_subscribed_lit")
    a.label("event_subscribed_add_pc")
    a.add_reg(2, 15)                          # add r2, pc → r2 = absolute db vaddr
    # ldrb r0, [r2, r1]: LDRB (register) T1, 0101 110 Rm Rn Rt → 0x5C00
    # Rm=r1, Rn=r2, Rt=r0 → hw = 0x5C00 | (1 << 6) | (2 << 3) | 0
    hw = 0x5C00 | (1 << 6) | (2 << 3) | 0
    a.raw(bytes([hw & 0xFF, (hw >> 8) & 0xFF]))
    a.cmp_imm8(0, 0)                          # set Z if database[event_id] == 0
    a.bx(14)                                  # bx lr (does not affect flags)
    a.align(4)
    a.label("event_subscribed_lit")
    def _emit_lit(_pc: int) -> bytes:
        offset = G_AVRCP_REQ_EVENT_DATABASE_VADDR - (a.labels["event_subscribed_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_lit, 4)


def _emit_get_or_init_mmap_subroutine(a: Asm) -> None:
    """Lazy-init mmap of y1-track-info, cached at G_Y1_TRACK_INFO_MMAP_BASE_VADDR.

    Pre: none.
    Post: r0 = mmap base ptr (cached on success), or 0 on failure.
    Clobbers r1, r2, r3, r7, ip, lr. r4-r6 preserved (used internally
    across the push/pop window).

    No sticky failure flag — every cache-miss call retries open + mmap.
    The failure path (music app hasn't created the file yet, mmap2 rejects)
    is rare; retrying is cheap and recovers automatically.

    mmap2 ABI on ARM EABI Linux: r0=addr, r1=length, r2=prot, r3=flags,
    r4=fd, r5=pgoff (units of pages, 0 = file start), r7=NR_mmap2 (192),
    svc 0 → r0 = mapped vaddr (>= 0x10000) or -errno.
    """
    a.label("get_or_init_mmap")

    # Compute &g_y1_track_info_mmap_base from PC.
    a.ldr_lit_w(0, "get_or_init_mmap_lit")
    a.label("get_or_init_mmap_add_pc")
    a.add_reg(0, 15)                            # r0 = absolute vaddr of g_mmap_base

    # Load cached ptr.
    a.ldr_w(1, 0, 0)                            # r1 = *(g_mmap_base)
    a.cmp_imm8(1, 0)
    a.beq("mmap_do_init")
    # Cache hit — return cached ptr.
    a.mov_lo_lo(0, 1)
    a.bx(14)                                    # bx lr

    a.label("mmap_do_init")
    # push {r3, r4-r7, lr} — bitmap 0xf8, R=1, halfword 0xb5f8, LE bytes f8 b5.
    # 6 regs = 24 B, sp stays 8-aligned. r3 pushed only for alignment (caller-
    # saved, never restored in any meaningful sense — we pop into r3 to discard).
    a.raw(bytes([0xf8, 0xb5]))
    a.mov_lo_lo(6, 0)                           # r6 = &g_mmap_base (preserved across blx)

    # open(path_track_info, O_RDONLY, 0)
    a.adr_w(0, "path_track_info")
    a.movs_imm8(1, 0)                           # O_RDONLY
    a.movs_imm8(2, 0)
    a.blx_imm(PLT_open)
    a.cmp_imm8(0, 0)
    a.blt("mmap_init_fail_no_fd")
    a.mov_lo_lo(4, 0)                           # r4 = fd (callee-save, preserved across svc)

    # mmap2(NULL, 0x1000, PROT_READ, MAP_SHARED, fd, pgoff=0)
    a.movs_imm8(0, 0)                           # r0 = addr (NULL)
    a.movw(1, 0x1000)                           # r1 = length (4096, one page — covers 2213 B file)
    a.movs_imm8(2, 1)                           # r2 = PROT_READ
    a.movs_imm8(3, 1)                           # r3 = MAP_SHARED
    # r4 = fd already (set above).
    a.movs_imm8(5, 0)                           # r5 = pgoff
    a.movw(7, NR_mmap2)
    a.svc(0)                                    # r0 = ptr or -errno
    a.mov_lo_lo(7, 0)                           # r7 = mmap result (preserve across close)

    # close(fd) — Linux mmap'd region survives fd close.
    a.mov_lo_lo(0, 4)
    a.blx_imm(PLT_close)

    # Validate mmap result. ARM mmap2 returns -errno (small negative) on
    # failure; any valid mapped vaddr is well above 0 (PAGE_OFFSET-ish).
    a.cmp_imm8(7, 0)
    a.blt("mmap_init_fail")
    # Success: store ptr in cache. str.w r7, [r6, #0].
    #   Thumb-2 STR.W (immediate) T3:
    #   hw1 = 0xF8C0 | Rn ; hw2 = (Rt<<12) | imm12.
    #   r7→[r6,0]: hw1 = 0xF8C6, hw2 = 0x7000 → LE bytes c6 f8 00 70.
    a.raw(bytes([0xc6, 0xf8, 0x00, 0x70]))
    a.mov_lo_lo(0, 7)                           # r0 = mmap result (return value)
    # pop {r3, r4-r7, pc} — bitmap 0xf8, R=1, halfword 0xbdf8, LE bytes f8 bd.
    a.raw(bytes([0xf8, 0xbd]))

    a.label("mmap_init_fail")
    a.movs_imm8(0, 0)                           # return 0
    a.raw(bytes([0xf8, 0xbd]))                  # pop {r3, r4-r7, pc}

    a.label("mmap_init_fail_no_fd")
    # open() failed — already in the pushed-state, so pop and return 0.
    a.movs_imm8(0, 0)
    a.raw(bytes([0xf8, 0xbd]))                  # pop {r3, r4-r7, pc}

    a.align(4)
    a.label("get_or_init_mmap_lit")
    def _emit_lit(_pc: int) -> bytes:
        offset = G_Y1_TRACK_INFO_MMAP_BASE_VADDR - (a.labels["get_or_init_mmap_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_lit, 4)


def _emit_read_track_info_subroutine(a: Asm) -> None:
    """Copy nbytes from y1-track-info's active slot into caller's buffer.

    Pre: r0 = dst buffer (1104-aligned-or-not stack address), r1 = nbytes
        (1..1104; caller picks the trampoline's existing FILE_SIZE constant),
        r2 = slot_offset (0 to copy from slot start; any value 0..1103 to copy
        from a non-zero offset within the active slot — e.g., T_papp's
        GetCurrent passes 795 to read repeat+shuffle bytes).
    Post: r0 = nbytes copied on success, 0 on mmap unavailable. r4-r11
        preserved. Clobbers r1, r2, r3, ip, lr (and r7 transiently inside
        get_or_init_mmap).

    Reads file[0] as active_slot, computes
        src = mmap_base + 4 + (active * 1104) + slot_offset,
    byte-copies nbytes into dst. Slow path (mmap miss) returns 0; caller
    treats dst as unchanged (already memset-zeroed at trampoline entry).

    Byte-copy loop is O(nbytes) but executes in cache after the first
    iteration — ~1100 instructions for a full 1104-byte copy, well under a
    microsecond on Cortex-A7.
    """
    a.label("read_track_info")
    # push {r3, r4-r7, lr} — same alignment rationale as get_or_init_mmap.
    a.raw(bytes([0xf8, 0xb5]))

    a.mov_lo_lo(4, 0)                           # r4 = dst (preserved across bl)
    a.mov_lo_lo(5, 1)                           # r5 = nbytes (preserved)
    a.mov_lo_lo(6, 2)                           # r6 = slot_offset (preserved across bl)

    a.bl_w("get_or_init_mmap")                  # r0 = mmap base or 0
    a.cmp_imm8(0, 0)
    a.beq("read_track_info_fail")

    # r0 = mmap base. Read active_slot byte.
    a.ldrb_w(1, 0, 0)                           # r1 = base[0] (active slot in low bit)
    # r1 is 0 or 1 in the writer's contract. We don't mask here — a non-
    # 0/non-1 value would mean the writer crashed mid-flip or someone wrote
    # garbage; in that case dispatching to slot[active & whatever] still
    # reads consistent slot data (the previous flush's slot stays valid
    # because the writer always writes the OTHER slot first).
    a.movw(2, Y1_TRACK_INFO_SLOT_SIZE)
    a.muls_lo_lo(1, 2)                          # r1 = active * 1104
    # adds r1, #4 (T2 ADD imm8): 0011 0 Rdn imm8 → 0x3000 | (1<<8) | 4 = 0x3104.
    a.raw(bytes([0x04, 0x31]))
    a.adds_lo_lo(1, 1, 0)                       # r1 = mmap_base + 4 + active*1104
    a.adds_lo_lo(1, 1, 6)                       # r1 = src = above + slot_offset

    # Byte-copy loop: r3=i, r5=count, r1=src, r4=dst. Unrolled would be
    # faster but byte loop fits naturally and the bytes-per-call is small
    # enough that the win isn't load-bearing.
    #
    # Defensive: if nbytes==0, skip the loop. The cmp/bne tail-check would
    # otherwise spin forever (r3 increments past r5 since r3 starts == r5
    # and adds first). Cheap (+4 B) and future-proofs against a caller
    # ever passing 0.
    a.cmp_imm8(5, 0)
    a.beq("read_track_info_done")
    a.movs_imm8(3, 0)
    a.label("read_track_info_loop")
    a.ldrb_reg(0, 1, 3)                         # r0 = src[i]
    # strb r0, [r4, r3] — Thumb-1 STRB (register) T1: 0101 010 Rm Rn Rt.
    #   Rm=3, Rn=4, Rt=0 → 0x5400 | (3<<6) | (4<<3) | 0 = 0x54E0.
    a.raw(bytes([0xe0, 0x54]))
    # adds r3, #1 (T2 ADD imm8): 0x3000 | (3<<8) | 1 = 0x3301.
    a.raw(bytes([0x01, 0x33]))
    a.cmp_w(3, 5)
    a.bne("read_track_info_loop")

    a.label("read_track_info_done")
    a.mov_lo_lo(0, 5)                           # return nbytes (0 if guard taken)
    a.raw(bytes([0xf8, 0xbd]))                  # pop {r3, r4-r7, pc}

    a.label("read_track_info_fail")
    a.movs_imm8(0, 0)                           # return 0
    a.raw(bytes([0xf8, 0xbd]))


def _emit_read_state_block_subroutine(a: Asm) -> None:
    """Copy nbytes from G_Y1_TRAMPOLINE_STATE_VADDR + state_offset to caller's
    stack buffer.

    Pre: r0 = dst, r1 = nbytes (1..13), r2 = state_offset (0..12).
    Post: r0 = nbytes copied. r4..r11 preserved. Clobbers r1, r2, r3, lr.

    Loads the state-block absolute vaddr via PC-relative literal + add r,pc,
    then byte-copies the requested range into dst. Used by T4 / T5 / T8 /
    T9 in place of a per-call `open(path_state, O_RDONLY) + read + close`
    sequence; saves 3 syscalls + the FD-management thumb-2 sequence
    (~30 B per site) at each call site.
    """
    a.label("read_state_block")
    a.raw(bytes([0xf8, 0xb5]))                  # push {r3, r4-r7, lr}

    a.mov_lo_lo(4, 0)                           # r4 = dst
    a.mov_lo_lo(5, 1)                           # r5 = nbytes

    a.ldr_lit_w(1, "read_state_block_lit")
    a.label("read_state_block_add_pc")
    a.add_reg(1, 15)                            # r1 = absolute &state[0]
    a.adds_lo_lo(1, 1, 2)                       # r1 += state_offset

    a.cmp_imm8(5, 0)
    a.beq("read_state_block_done")
    a.movs_imm8(3, 0)
    a.label("read_state_block_loop")
    a.ldrb_reg(0, 1, 3)                         # r0 = state[i]
    # strb r0, [r4, r3] — 0x5400 | (3<<6) | (4<<3) | 0 = 0x54E0.
    a.raw(bytes([0xe0, 0x54]))
    a.raw(bytes([0x01, 0x33]))                  # adds r3, #1
    a.cmp_w(3, 5)
    a.bne("read_state_block_loop")

    a.label("read_state_block_done")
    a.mov_lo_lo(0, 5)                           # return nbytes
    a.raw(bytes([0xf8, 0xbd]))                  # pop {r3, r4-r7, pc}

    a.align(4)
    a.label("read_state_block_lit")
    def _emit_read_lit(_pc: int) -> bytes:
        offset = G_Y1_TRAMPOLINE_STATE_VADDR - (a.labels["read_state_block_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_read_lit, 4)


def _emit_write_state_block_subroutine(a: Asm) -> None:
    """Copy nbytes from caller's src buffer to G_Y1_TRAMPOLINE_STATE_VADDR +
    state_offset.

    Pre: r0 = src, r1 = nbytes (1..13), r2 = state_offset (0..12).
    Post: r0 = nbytes copied. r4..r11 preserved. Clobbers r1, r2, r3, lr.

    Mirror of read_state_block (source / dest reversed). Used by T5 / T9
    in place of a per-call `open(path_state, O_WRONLY) + write + close`
    sequence.
    """
    a.label("write_state_block")
    a.raw(bytes([0xf8, 0xb5]))                  # push {r3, r4-r7, lr}

    a.mov_lo_lo(4, 0)                           # r4 = src
    a.mov_lo_lo(5, 1)                           # r5 = nbytes

    a.ldr_lit_w(1, "write_state_block_lit")
    a.label("write_state_block_add_pc")
    a.add_reg(1, 15)                            # r1 = absolute &state[0]
    a.adds_lo_lo(1, 1, 2)                       # r1 += state_offset

    a.cmp_imm8(5, 0)
    a.beq("write_state_block_done")
    a.movs_imm8(3, 0)
    a.label("write_state_block_loop")
    a.ldrb_reg(0, 4, 3)                         # r0 = src[i]
    # strb r0, [r1, r3] — 0x5400 | (3<<6) | (1<<3) | 0 = 0x54C8.
    a.raw(bytes([0xc8, 0x54]))
    a.raw(bytes([0x01, 0x33]))                  # adds r3, #1
    a.cmp_w(3, 5)
    a.bne("write_state_block_loop")

    a.label("write_state_block_done")
    a.mov_lo_lo(0, 5)                           # return nbytes
    a.raw(bytes([0xf8, 0xbd]))                  # pop {r3, r4-r7, pc}

    a.align(4)
    a.label("write_state_block_lit")
    def _emit_write_lit(_pc: int) -> bytes:
        offset = G_Y1_TRAMPOLINE_STATE_VADDR - (a.labels["write_state_block_add_pc"] + 4)
        return (offset & 0xFFFFFFFF).to_bytes(4, "little")
    a._fixup(_emit_write_lit, 4)


def _emit_native_log_u32(a: Asm, fmt_label: str, value_reg: int) -> None:
    """Emit __android_log_print(INFO, "Y1T", fmt, value_reg) before a wire-side
    response blx. Used by build(debug=True) to record exactly what bytes the
    trampolines are about to ship to the CT.

    Insertion contract: caller has its full r0..r3 arg vector already
    loaded (r0=conn, r1=0, r2=REASON_CHANGED, r3=payload). The log call
    clobbers all of r0-r3 — AAPCS only promises r4-r11 callee-saved
    across the blx. So push/pop all four caller-arg registers around the
    call to preserve the emit's setup.

    Bytes: 22 (push + ldr if needed + movs/adr/mov + blx + pop). Format
    strings consolidated into the data block at end of blob. value_reg
    must be r0..r7 (low regs).
    """
    # push {r0, r1, r2, r3} = 0xB40F  →  bytes [0x0F, 0xB4]. After push,
    # sp -= 16, with sp[0]=r0, sp[4]=r1, sp[8]=r2, sp[12]=r3.
    a.raw(bytes([0x0F, 0xB4]))

    # If value_reg is one of r0..r3 we must move it into r3 BEFORE
    # clobbering r0/r1/r2 with prio/tag/fmt. For value_reg=r3 the mov
    # is a self-move (still encoded but harmless). For value_reg in
    # r4..r7 the source register is preserved across the push so a
    # direct mov_lo_lo works either way.
    if value_reg != 3:
        a.mov_lo_lo(3, value_reg)

    a.movs_imm8(0, 4)                         # r0 = ANDROID_LOG_INFO
    a.adr_w(1, "log_tag")                     # r1 = "Y1T"
    a.adr_w(2, fmt_label)                     # r2 = fmt string
    a.blx_imm(PLT_android_log_print)

    # pop {r0, r1, r2, r3} = 0xBC0F  →  bytes [0x0F, 0xBC].
    a.raw(bytes([0x0F, 0xBC]))


def _emit_t8(a: Asm) -> None:
    """T8: RegisterNotification INTERIM dispatch for events other than
    TRACK_CHANGED (0x02, handled by extended_T2).

    Branched from extended_T2's "PDU 0x31 + non-0x02 event" arm. Reads
    y1-track-info into a stack buffer (for events 0x01 and 0x05 which
    need play_status / position from the schema), then dispatches on
    event_id and emits an INTERIM via the appropriate
    `reg_notievent_*_rsp` PLT entry. All these response builders share
    the same calling convention as their TRACK_CHANGED sibling: r0=conn,
    r1=0 (success), r2=reasonCode, r3=event-specific payload (or unused).
    transId is auto-extracted from conn[17] inside each builder.

    Events handled (per AVRCP 1.3 §5.4.2 Tables 5.29/5.31/5.32/5.33/5.34/5.36):
      0x01 PLAYBACK_STATUS_CHANGED  — Table 5.29; INTERIM with 1-byte
                                      play_status (from y1-track-info[792])
      0x03 TRACK_REACHED_END        — Table 5.31; INTERIM, no payload
      0x04 TRACK_REACHED_START      — Table 5.32; INTERIM, no payload
      0x05 PLAYBACK_POS_CHANGED     — Table 5.33; INTERIM with 4-byte
                                      position_ms (BE in file → REV → host
                                      order)
      0x06 BATT_STATUS_CHANGED      — Table 5.34; INTERIM with 1-byte canned
                                      0x00 NORMAL (Table 5.35 enum)
      0x07 SYSTEM_STATUS_CHANGED    — Table 5.36; INTERIM with 1-byte canned
                                      0x00 POWER_ON

    Unknown event_id falls through to "unknow indication" (0x65bc) for the
    spec-correct NOT_IMPLEMENTED reject.

    T8 ships INTERIM-only; proactive CHANGED for event 0x01
    PLAYBACK_STATUS_CHANGED lives in T9 (paired with the cardinality NOP at
    sswitch_18a / 0x3c4fe in MtkBt.odex, which mirrors the TRACK_CHANGED
    cardinality bypass at 0x3c530). CTs that subscribe to events 0x03..0x07
    receive the immediate INTERIM and can re-subscribe periodically to
    refresh.

    Frame: 800 B file_buf at sp+0. None of the response builders need
    stack args (all 4 args fit in r0 / r1 / r2 / r3). Caller's event_id is
    accessed via T8_EVENT_ID_OFF (= 386 + frame).
    """
    a.label("T8")

    # ---- allocate stack frame ----
    a.subw(13, 13, T8_FRAME)                  # sub.w sp, sp, #800

    # ---- memset(file_buf, 0, 800) ----
    # Default everything to 0 so a partial read (file shorter than 800 B
    # — e.g. an older writer built against an earlier schema) gives
    # play_status=0 (STOPPED) and position=0 rather than uninit stack
    # garbage.
    a.add_sp_imm(0, T8_OFF_FILE)              # r0 = sp+0
    a.movs_imm8(1, 0)
    a.movw(2, 800)
    a.blx_imm(PLT_memset)

    # ---- copy active slot of y1-track-info into file_buf ----
    a.add_sp_imm(0, T8_OFF_FILE)
    a.movw(1, 800)
    a.movs_imm8(2, 0)                         # slot_offset = 0
    a.bl_w("read_track_info")

    a.label("t8_skip_track_read")

    # ---- dispatch on event_id (caller's sp+386, post-SUB-SP at T8_EVENT_ID_OFF) ----
    a.ldrb_w(0, 13, T8_EVENT_ID_OFF)          # r0 = event_id
    # M5wire c39= already identifies which inbound event the TID restore
    # writes for, and the CT RegNotif event set is already known (ev=01..09);
    # the redundant T8reg log site was dropped to free trampoline budget.
    a.cmp_imm8(0, 0x01)
    a.bne("t8_check_3")

    # 0x01 PLAYBACK_STATUS_CHANGED
    # reg_notievent_playback_rsp(conn, 0, REASON_INTERIM, play_status)
    a.add_imm_t3(0, 5, 8)                     # r0 = conn (also restore-site target)
    _emit_restore_conn_tid_from_db(a, 0, 0x01, "t8_e01")
    a.ldrb_w(3, 13, T8_OFF_FILE_PLAYFLAG)     # r3 = play_status (1=PLAYING / 2=PAUSED / 0=STOPPED)
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)                         # success
    a.blx_imm(PLT_reg_notievent_playback_rsp)
    a.b_w("t8_done")

    a.label("t8_check_3")
    a.cmp_imm8(0, 0x03)
    a.bne("t8_check_4")
    # 0x03 TRACK_REACHED_END
    # reg_notievent_reached_end_rsp(conn, 0, REASON_INTERIM)
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x03, "t8_e03")
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_reached_end_rsp)

    a.b_w("t8_done")

    a.label("t8_check_4")
    a.cmp_imm8(0, 0x04)
    a.bne("t8_check_5")
    # 0x04 TRACK_REACHED_START
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x04, "t8_e04")
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_reached_start_rsp)

    a.b_w("t8_done")

    a.label("t8_check_5")
    a.cmp_imm8(0, 0x05)
    a.bne("t8_check_6")
    # 0x05 PLAYBACK_POS_CHANGED — live-extrapolate position when PLAYING
    # so a fresh CT subscribe sees the actual current position, not the
    # last state-change anchor. AVRCP 1.3 §5.4.1 Tbl 5.26 SongPosition is
    # "the current position of the playing in milliseconds elapsed".
    # When STOPPED/PAUSED the position field IS the freeze point (saved_pos
    # is the right value).
    a.ldrb_w(0, 13, T8_OFF_FILE_PLAYFLAG)
    a.cmp_imm8(0, 1)                          # 1 = PLAYING
    a.bne("t8_pos_static")

    # ---- live extrapolation ----
    # Same magic-multiply math T6/T9 use:
    #   now_ms = tv_sec * 1000 + tv_nsec / 1e6
    #   live_pos = saved_pos_ms + (now_ms - state_change_ms)
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T8_OFF_TIMESPEC_SEC)
    a.str_sp_imm(0, T8_OFF_TIMESPEC_NSEC)
    a.movs_imm8(0, CLOCK_BOOTTIME)
    a.add_sp_imm(1, T8_OFF_TIMESPEC)
    a.movw(7, NR_clock_gettime)
    a.svc(0)

    a.ldr_sp_imm(2, T8_OFF_TIMESPEC_SEC)      # r2 = tv_sec
    a.movw(0, 1000)
    a.muls_lo_lo(2, 0)                        # r2 = tv_sec * 1000
    a.ldr_sp_imm(0, T8_OFF_TIMESPEC_NSEC)     # r0 = tv_nsec
    a.movw(1, 0xDE83)
    a.movt(1, 0x431B)                         # r1 = 0x431BDE83 (magic for /1e6)
    a.umull(4, 3, 0, 1)                       # r3:r4 = tv_nsec * magic
    a.lsrs_imm5(3, 3, 18)                     # r3 = tv_nsec / 1e6
    a.adds_lo_lo(2, 2, 3)                     # r2 = now_ms

    a.ldr_sp_imm(0, T8_OFF_FILE_STATE_TIME)   # r0 = state_change_ms (BE)
    a.rev_lo_lo(0, 0)                         # → host order
    a.subs_lo_lo(2, 2, 0)                     # r2 = delta_ms

    a.ldr_sp_imm(3, T8_OFF_FILE_POS)          # r3 = saved_pos (BE)
    a.rev_lo_lo(3, 3)                         # → host order
    a.adds_lo_lo(3, 3, 2)                     # r3 = live_pos
    a.b_w("t8_pos_emit")

    a.label("t8_pos_static")
    a.ldr_sp_imm(3, T8_OFF_FILE_POS)          # r3 = saved_pos (BE)
    a.rev_lo_lo(3, 3)                         # → host order

    a.label("t8_pos_emit")
    # reg_notievent_pos_changed_rsp(conn, 0, REASON_INTERIM, position_ms_u32)
    # r3 already holds the position from the live-extrapolation block above;
    # restore must use a register the helper preserves, so do conn first then
    # set up r2/r1 (helper clobbers r2/r3 — r3 will be re-loaded? NO, r3 is
    # already set up). Use a callee-saved stash: push/pop r3 across the helper.
    a.raw(bytes([0x08, 0xB4]))                # push {r3}
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x05, "t8_e05")
    a.raw(bytes([0x08, 0xBC]))                # pop {r3}
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_pos_changed_rsp)
    a.b_w("t8_done")

    a.label("t8_check_6")
    a.cmp_imm8(0, 0x06)
    a.bne("t8_check_7")
    # 0x06 BATT_STATUS_CHANGED.
    # reg_notievent_battery_status_changed_rsp(conn, 0, REASON_INTERIM, batt_status_u8)
    # batt_status read from y1-track-info[794], where the music app's
    # BatteryReceiver writes the AVRCP enum (0=NORMAL, 1=WARNING, 2=CRITICAL,
    # 3=EXTERNAL, 4=FULL_CHARGE) bucket-mapped from
    # Android `Intent.ACTION_BATTERY_CHANGED`. Stack is memset to 0 before the
    # read, so a short file gives BATT_STATUS_NORMAL — benign default.
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x06, "t8_e06")
    a.ldrb_w(3, 13, T8_OFF_FILE_BATTERY)
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_battery_status_rsp)

    a.b_w("t8_done")

    a.label("t8_check_7")
    a.cmp_imm8(0, 0x07)
    a.bne("t8_check_8")
    # 0x07 SYSTEM_STATUS_CHANGED
    # reg_notievent_system_status_changed_rsp(conn, 0, REASON_INTERIM, system_status_u8)
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x07, "t8_e07")
    a.movs_imm8(3, SYSTEM_STATUS_POWERED)
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_system_status_rsp)
    a.b_w("t8_done")

    a.label("t8_check_8")
    a.cmp_imm8(0, 0x08)
    a.bne("t8_check_9")
    # 0x08 PLAYER_APPLICATION_SETTING_CHANGED INTERIM.
    # reg_notievent_player_appsettings_changed_rsp(
    #     conn, 0, REASON_INTERIM, n, *attr_ids, *values)
    # Live values read from y1-track-info[795..796] (the music app's
    # PappStateBroadcaster writes both bytes on every musicRepeatMode /
    # musicIsShuffle SharedPreferences change). file_buf is already loaded
    # into sp+0..799 above. Storing the outgoing-args at sp[0]/sp[4]
    # clobbers file_buf[0..7] (track_id), but track_id isn't read by this
    # arm and the frame is freed at t8_done.
    a.adr_w(0, "papp_attr_ids")
    a.str_sp_imm(0, 0)                          # sp[0] = &[2, 3]
    a.addw(0, 13, T8_OFF_FILE_REPEAT)           # r0 = &file[795] (= [r, s])
    a.str_sp_imm(0, 4)                          # sp[4] = current values
    a.add_imm_t3(0, 5, 8)                       # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x08, "t8_e08")
    a.movs_imm8(1, 0)                           # success
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(3, 2)                           # n=2
    a.blx_imm(PLT_reg_notievent_player_appsettings_rsp)

    a.b_w("t8_done")

    # Events 0x09..0x0c — AVRCP 1.4+ event IDs, advertised in T1 because
    # a reference 1.3-as-TG implementation also advertises + INTERIM-acks
    # them on a 1.3-profile SDP record. ACK with INTERIM (empty / zero
    # payload depending on event). The 0x09 NowPlayingContentChanged
    # subscription is load-bearing: some CTs use NowPlaying CHANGED (not
    # TrackChanged CHANGED) as their primary metadata-refresh trigger —
    # without database[9] armed (= seq_id + 1 from the inbound RegNotif
    # CMD; written here by save_event_seq_id at extended_T2's entry),
    # they fall back to ~20 s polling. T5/T9's NowPlaying CHANGED arms
    # fire on every track/play edge gated on `event_subscribed(0x09)`.
    #
    # For 0x0a / 0x0b / 0x0c we ACK but emit no CHANGED — Y1 has no
    # multi-player / UID-database semantics, so the subscriptions stay
    # idle (matching the reference TG's observed behaviour: INTERIM-ack
    # at connection time, no CHANGED in steady state).
    a.label("t8_check_9")
    a.cmp_imm8(0, 0x09)
    a.bne("t8_check_a")
    # 0x09 NOW_PLAYING_CONTENT_CHANGED INTERIM ACK.
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x09, "t8_e09")
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_now_playing_content_rsp)
    a.b_w("t8_done")

    a.label("t8_check_a")
    a.cmp_imm8(0, 0x0A)
    a.bne("t8_check_b")
    # 0x0A AVAILABLE_PLAYERS_CHANGED INTERIM ACK (empty payload).
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x0A, "t8_e0a")
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_availplayers_rsp)
    a.b_w("t8_done")

    a.label("t8_check_b")
    a.cmp_imm8(0, 0x0B)
    a.bne("t8_check_c")
    # 0x0B ADDRESSED_PLAYER_CHANGED INTERIM ACK. PlayerID u16 in r3 = 0,
    # UidCounter u16 at sp[0] = 0 (Y1 has one player, no UID database).
    a.movs_imm8(3, 0)
    a.str_sp_imm(3, 0)                          # sp[0] = uid_counter (0)
    a.add_imm_t3(0, 5, 8)                       # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x0B, "t8_e0b")
    a.movs_imm8(3, 0)                           # re-set after restore clobber
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_addredplayer_rsp)
    a.b_w("t8_done")

    a.label("t8_check_c")
    a.cmp_imm8(0, 0x0C)
    a.bne("t8_unknown_event")
    # 0x0C UIDS_CHANGED INTERIM ACK. UidCounter u16 in r3 = 0.
    a.add_imm_t3(0, 5, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x0C, "t8_e0c")
    a.movs_imm8(3, 0)
    a.movs_imm8(2, REASON_INTERIM)
    a.movs_imm8(1, 0)
    a.blx_imm(PLT_reg_notievent_uids_changed_rsp)
    a.b_w("t8_done")

    a.label("t8_unknown_event")
    # event_id we don't handle → spec-correct NOT_IMPLEMENTED reject via
    # the original "unknow indication" path. Restore stack first so the
    # reject-path's stack-canary check sees the correct sp.
    a.addw(13, 13, T8_FRAME)
    a.ldrh_w(14, 13, T4_LR_CANARY_OFF_ENTRY)  # restore lr canary = SIZE
    a.add_imm_t3(0, 5, 8)                     # restore r0 = conn
    a.b_w("t4_to_unknown")

    a.label("t8_done")
    # ---- restore stack and tail-call epilogue ----
    a.addw(13, 13, T8_FRAME)
    a.b_w("t4_to_epilogue")


def _emit_t9(a: Asm) -> None:
    """T9: proactive PLAYBACK_STATUS_CHANGED + BATT_STATUS_CHANGED + PLAYBACK_POS_CHANGED.

    Entered via `b.w T9` from the patched libextavrcp_jni.so::
    notificationPlayStatusChangedNative stub at file offset 0x3c88. MtkBt's
    handleKeyMessage path -- with the cardinality if-eqz NOPed at
    sswitch_18a (file offset 0x3c4fe in MtkBt.odex; mirrors the
    sswitch_1a3 / TRACK_CHANGED NOP at 0x3c530) -- invokes the native
    method on every `playstatechanged` broadcast emitted by the music app,
    asynchronously to any inbound AVRCP RegisterNotification.

    Closes the AVRCP 1.3 §5.4.2 spec gap that T8 alone leaves: T8 handles
    events 0x01 / 0x05 / 0x06 INTERIM-only, never fires the spec-mandated
    CHANGED frame when the value actually flips. Without T9 a polling CT
    subscribes to event 0x01 / 0x05 / 0x06, gets the immediate INTERIM,
    then never sees CHANGED, so the car-side play / pause icon, scrub bar,
    and battery indicator stay stuck on their initial values even though
    Y1's audio toggles correctly via the PASSTHROUGH path.

    Battery and periodic position both piggyback on this same trampoline.
    The music app fires `playstatechanged` whenever ANY of the following
    occurs: actual play / pause edge, battery bucket transition, or 1 s
    tick (while playing). T9 unconditionally:

      1. play_status: emit PLAYBACK_STATUS_CHANGED CHANGED on file[792]
         vs state[9] edge.
      2. battery_status: emit BATT_STATUS_CHANGED CHANGED on file[794]
         vs state[10] edge. Stock MtkBt's
         BTAvrcpSystemListener.onBatteryStatusChange dispatch chain is
         dead (BTAvrcpMusicAdapter$2 overrides it with a log-only stub),
         so reusing `playstatechanged` as the trigger is the cheapest
         spec-compliant alternative.
      3. pos_changed: emit PLAYBACK_POS_CHANGED CHANGED if file[792] == 1
         (PLAYING), with live-extrapolated position from
         clock_gettime(CLOCK_BOOTTIME) — same arithmetic T6 does for
         GetPlayStatus. Emits at our 1 s cadence rather than the CT's
         RegisterNotification `playback_interval`; this is a
         spec-permissible floor (the spec mandates a maximum interval,
         not a minimum cadence).

    On entry (Java native ABI for `notificationPlayStatusChangedNative(byte,
    byte, byte)`):
      - r0 = JNIEnv*  (Java native arg 0)
      - r1 = jobject this  (BluetoothAvrcpService instance)
      - r2 = jbyte arg1  (ignored — Java passes 0)
      - r3 = jbyte arg2  (ignored — Java passes 0)
      - sp[0] = jbyte arg3 = current play_status from MtkBt's mPlayStatus
                              (we ignore this and read from y1-track-info[792]
                               for consistency with T8's INTERIM data source)
      - lr = caller's return address

    Returns: jboolean in r0 (always 1; the caller ignores it per the smali
    at sswitch_18a).

    Logic:
      1. Call JNI helper at 0x36c0 to obtain the BluetoothAvrcpService's
         per-conn struct (same helper T5 uses; conn buffer at struct + 8).
      2. Read y1-track-info into file_buf @ sp+16..815. file[792] = current
         play_status (AVRCP §5.4.1 Tbl 5.26 enum); file[794] = current
         battery_status (AVRCP §5.4.2 Tbl 5.35 enum).
      3. Read .bss trampoline state (13 B) into state_buf @ sp+0..15.
         state[9]  = last_play_status.
         state[10] = last_battery_status.
      4. play_status compare → emit reg_notievent_playback_rsp CHANGED on
         edge; update state[9].
      5. battery_status compare → emit
         reg_notievent_battery_status_changed_rsp CHANGED on edge; update
         state[10].
      6. If either changed, write the modified state bytes back to .bss.

    Race with T5: both read+modify+write the .bss trampoline state. Concurrent
    firings can lose one update. In practice T5 fires on `metachanged` and
    T9 fires on `playstatechanged` -- they overlap rarely, and the worst
    case is a single missed CHANGED that the next event recovers.
    """
    a.label("T9")

    # ---- prologue: save callee-saves we'll trash ----
    # push {r4, r5, lr} = 0xB430.
    a.raw(bytes([0x30, 0xB5]))

    # ---- get the BluetoothAvrcpService internal struct ----
    a.bl_w("jni_get_avrcp_state")             # r0 = struct ptr
    a.mov_lo_lo(4, 0)                         # r4 = struct ptr (preserved)

    # ---- allocate locals: 16 B state buf @ sp+0 + 800 B file buf @ sp+16 ----
    a.subw(13, 13, T9_FRAME)                  # sub.w sp, sp, #816

    # ---- memset(file_buf, 0, 800) ----
    # Default everything to 0 so a partial read (file shorter than 800 B
    # — e.g. an older writer built against an earlier schema) gives
    # play_status=0 (STOPPED) rather than uninit stack garbage.
    a.add_sp_imm(0, T9_OFF_FILE)
    a.movs_imm8(1, 0)
    a.movw(2, 800)
    a.blx_imm(PLT_memset)

    # ---- memset(state_buf, 0, 24) ----
    # State is 24 B in-memory (4-B aligned): bytes 0..12 = T5 / T9 track +
    # edge-tracking; bytes 13..20 = per-event subscription gates (see
    # T9_STATE_SUB_*_OFF); bytes 21..23 = padding. zero-fill defaults every
    # gate to "not subscribed" if the read returns fewer bytes.
    a.add_sp_imm(0, T9_OFF_STATE)
    a.movs_imm8(1, 0)
    a.movs_imm8(2, 24)
    a.blx_imm(PLT_memset)

    # ---- copy active slot of y1-track-info into file_buf ----
    a.add_sp_imm(0, T9_OFF_FILE)
    a.movw(1, 800)
    a.movs_imm8(2, 0)                         # slot_offset = 0
    a.bl_w("read_track_info")

    a.label("t9_skip_track_read")

    # ---- copy .bss trampoline state into sp+T9_OFF_STATE (13 bytes) ----
    a.add_sp_imm(0, T9_OFF_STATE)
    a.movs_imm8(1, Y1_TRAMPOLINE_STATE_SIZE)
    a.movs_imm8(2, 0)                         # state_offset = 0
    a.bl_w("read_state_block")

    a.label("t9_skip_state_read")

    # r5 was the fd in the read blocks above; both closes ran, so r5 is
    # dead here. Repurpose r5 as `any_change` accumulator: 1 if either
    # play_status or battery_status edge fired (so .bss state bytes get
    # written back). r5 is callee-save so PLT calls below preserve it.
    a.movs_imm8(5, 0)                         # r5 = any_change = 0

    # ---- play_status compare (file[792] vs state[9]) ----
    a.ldrb_w(0, 13, T9_OFF_FILE_PLAYFLAG)     # r0 = current play_status
    a.ldrb_w(1, 13, T9_STATE_LAST_PS_OFF)     # r1 = last_play_status
    a.cmp_w(0, 1)
    a.beq("t9_after_play_check")

    # Edge detected. Update state[9] = file[792] in-memory unconditionally
    # so we don't loop "edge detected" forever while un-subscribed; the
    # state-writeback below will persist this.
    a.strb_w(0, 13, T9_STATE_LAST_PS_OFF)
    a.movs_imm8(5, 1)                         # any_change = 1

    # Gate on database[1] != 0 (CT sent RegisterNotification(ev=01) this
    # session). See T5 TRACK_CHANGED gate for the .bss-backed session-scope
    # semantic.
    _emit_check_event_subscribed(a, 0x01, "t9_after_play_check")

    # ---- emit CHANGED via reg_notievent_playback_rsp ----
    # r0 = conn (= struct + 8); r1 = 0 success; r2 = REASON_CHANGED;
    # r3 = play_status (from file_buf[792]).
    a.add_imm_t3(0, 4, 8)                     # r0 = r4 + 8 (conn)
    _emit_restore_conn_tid_from_db(a, 0, 0x01, "t9_ps")
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.ldrb_w(3, 13, T9_OFF_FILE_PLAYFLAG)     # r3 = play_status
    if DEBUG_NATIVE_LOG:
        _emit_native_log_u32(a, "log_fmt_t9ps", 3)
    a.blx_imm(PLT_reg_notievent_playback_rsp)

    # database[1] (PLAYBACK_STATUS subscription gate) stays armed across
    # the CHANGED emit — universal §5.4.2 reading; per-event TID echo
    # correctness preserved via the database read in restore_conn_tid.

    # ---- emit NowPlayingContentChanged CHANGED on play-edge ----
    # Paired with PlaybackStatus + TrackChanged as a 3-frame burst on
    # play/pause edge. Gate on database[9] (session-scope).
    _emit_check_event_subscribed(a, 0x09, "t9_after_play_check")

    a.add_imm_t3(0, 4, 8)                     # r0 = conn
    _emit_restore_conn_tid_from_db(a, 0, 0x09, "t9_ncc")
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.blx_imm(PLT_reg_notievent_now_playing_content_rsp)

    a.label("t9_after_play_check")

    # ---- battery_status compare (file[794] vs state[10]) ----
    # AVRCP 1.3 §5.4.2 Tbl 5.34 (BATT_STATUS_CHANGED CHANGED) carries a
    # 1-byte battery_status payload (Tbl 5.35 enum). The music app's
    # BatteryReceiver bucket-maps Android `Intent.ACTION_BATTERY_CHANGED`
    # (level + plug state) to the AVRCP enum on every transition and writes
    # file[794] before firing `playstatechanged`. T9 then picks it up.
    a.ldrb_w(0, 13, T9_OFF_FILE_BATTERY)      # r0 = current battery_status
    a.ldrb_w(1, 13, T9_STATE_LAST_BATT_OFF)   # r1 = last_battery_status
    a.cmp_w(0, 1)
    a.beq("t9_after_batt_check")

    # Edge detected. Update state[10] = file[794] in-memory unconditionally
    # so we don't loop "edge detected, can't emit" forever while un-subscribed.
    a.strb_w(0, 13, T9_STATE_LAST_BATT_OFF)
    a.movs_imm8(5, 1)                         # any_change = 1

    # Gate on database[6] (session-scope).
    _emit_check_event_subscribed(a, 0x06, "t9_after_batt_check")

    # ---- emit CHANGED via reg_notievent_battery_status_changed_rsp ----
    a.add_imm_t3(0, 4, 8)                     # r0 = r4 + 8 (conn)
    _emit_restore_conn_tid_from_db(a, 0, 0x06, "t9_batt")
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.ldrb_w(3, 13, T9_OFF_FILE_BATTERY)      # r3 = battery_status
    a.blx_imm(PLT_reg_notievent_battery_status_rsp)

    a.label("t9_after_batt_check")

    # ---- papp settings compare (file[795] / file[796] vs state[11] / state[12]) ----
    # AVRCP 1.3 §5.4.2 Tbl 5.36 (PLAYER_APPLICATION_SETTING_CHANGED CHANGED).
    # The music app's PappStateBroadcaster writes y1-track-info[795] =
    # repeat_avrcp and [796] = shuffle_avrcp on every SharedPreferences change
    # to musicRepeatMode / musicIsShuffle and fires `playstatechanged` so T9
    # picks up the edge — same trigger pipeline as the play_status / battery
    # checks above.
    # Spec values: §5.2.4 Tbl 5.20 (Repeat: 0x01 OFF / 0x02 SINGLE / 0x03 ALL
    # / 0x04 GROUP); Tbl 5.21 (Shuffle: 0x01 OFF / 0x02 ALL / 0x03 GROUP).
    a.ldrb_w(0, 13, T9_OFF_FILE_REPEAT)       # r0 = current repeat
    a.ldrb_w(1, 13, T9_STATE_LAST_REPEAT_OFF) # r1 = last repeat
    a.cmp_w(0, 1)
    a.bne("t9_papp_emit")
    a.ldrb_w(0, 13, T9_OFF_FILE_SHUFFLE)
    a.ldrb_w(1, 13, T9_STATE_LAST_SHUFFLE_OFF)
    a.cmp_w(0, 1)
    a.beq("t9_after_papp_check")

    a.label("t9_papp_emit")
    # Edge detected. Update state[11] / state[12] in-memory unconditionally
    # so we don't loop "edge detected" forever while un-subscribed.
    a.ldrb_w(0, 13, T9_OFF_FILE_REPEAT)
    a.strb_w(0, 13, T9_STATE_LAST_REPEAT_OFF)
    a.ldrb_w(0, 13, T9_OFF_FILE_SHUFFLE)
    a.strb_w(0, 13, T9_STATE_LAST_SHUFFLE_OFF)
    a.movs_imm8(5, 1)                         # any_change = 1

    # Gate on database[8] (session-scope).
    _emit_check_event_subscribed(a, 0x08, "t9_after_papp_check")

    # ---- emit CHANGED via reg_notievent_player_appsettings_changed_rsp ----
    # (conn, 0, REASON_CHANGED, n=2, *attr_ids, *values)
    # *values = &file[795] — file_buf already holds [repeat, shuffle]
    # contiguously at offsets 795..796.
    a.adr_w(0, "papp_attr_ids")
    a.str_sp_imm(0, T9_OFF_ARGS + 0)          # sp[0] = &[2, 3]
    a.addw(0, 13, T9_OFF_FILE_REPEAT)         # r0 = &file[795] (= [r, s])
    a.str_sp_imm(0, T9_OFF_ARGS + 4)          # sp[4] = current values
    a.add_imm_t3(0, 4, 8)                     # r0 = conn (struct + 8)
    _emit_restore_conn_tid_from_db(a, 0, 0x08, "t9_papp")
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    a.movs_imm8(3, 2)                         # n
    if DEBUG_NATIVE_LOG:
        _emit_native_log_u32(a, "log_fmt_t9papp", 3)
    a.blx_imm(PLT_reg_notievent_player_appsettings_rsp)

    # database[8] (PApp subscription gate) stays armed across CHANGED.

    a.label("t9_after_papp_check")

    # ---- write T9's bytes (state[9..12] = 4 B) into .bss if any edge fired ----
    # Narrow write keeps T5-owned bytes 0..7 (track_id) untouched.
    a.cmp_imm8(5, 0)
    a.beq("t9_after_state_write")

    a.addw(0, 13, T9_STATE_LAST_PS_OFF)       # r0 = src = sp + state[9] offset
    a.movs_imm8(1, 4)
    a.movs_imm8(2, 9)                         # state_offset = 9
    a.bl_w("write_state_block")

    a.label("t9_after_state_write")

    # ---- emit PLAYBACK_POS_CHANGED CHANGED if playing ----
    # AVRCP 1.3 §5.4.2 Tbl 5.33. ICS Table 7 row 27 (Optional). Emit a
    # live-extrapolated position whenever T9 fires while file[792] == 1
    # (PLAYING). The music app runs a 1 s tick that fires the
    # `playstatechanged` broadcast — same trigger T9 already uses for the
    # play-status / battery checks above — so this gives the CT roughly 1 Hz
    # CHANGED frames while playing. Strictly the spec says the CT gets to
    # set its own `playback_interval` via the original RegisterNotification
    # command and we should emit at exactly that rate; honoring the
    # CT-supplied interval would require us to capture and persist it from
    # T8's INTERIM-time stack frame, which is more involved than the
    # current build budget. Emitting at our 1 s cadence is spec-permissible
    # because (1) the spec doesn't forbid emitting MORE frequently than
    # requested (`shall be emitted` defines a floor, not a ceiling), and
    # (2) the CT can simply ignore frames that arrive faster than its
    # display refresh rate.
    a.ldrb_w(0, 13, T9_OFF_FILE_PLAYFLAG)
    a.cmp_imm8(0, 1)                          # 1 = PLAYING (AVRCP §5.4.1 Tbl 5.26)
    a.bne("t9_done")

    # Gate on database[5] (session-scope). Wire-side POS_CHANGED rate
    # tracks the music app's playstatechanged broadcast cadence (~1 Hz
    # when playing).
    _emit_check_event_subscribed(a, 0x05, "t9_done")

    # ---- clock_gettime(CLOCK_BOOTTIME, &timespec) ----
    # Default the timespec to zero so a syscall failure yields a useless
    # but bounded fallback (delta_sec computed against now=0 is negative,
    # live_pos collapses to saved_pos minus a constant — CTs render a
    # static or rewinding value rather than uninit garbage).
    a.movs_imm8(0, 0)
    a.str_sp_imm(0, T9_OFF_TIMESPEC_SEC)
    a.str_sp_imm(0, T9_OFF_TIMESPEC_NSEC)

    a.movs_imm8(0, CLOCK_BOOTTIME)
    a.add_sp_imm(1, T9_OFF_TIMESPEC)          # r1 = &timespec
    a.movw(7, NR_clock_gettime)
    a.svc(0)

    # ---- now_ms = tv_sec * 1000 + tv_nsec / 1_000_000 ----
    # Same arithmetic T6 does for GetPlayStatus, including the magic-multiply
    # for tv_nsec/1e6. The music app's TrackInfoWriter writes
    # state_change_time_ms directly from SystemClock.elapsedRealtime() with no
    # /1000 truncation, so both endpoints carry full ms precision.
    # CLOCK_BOOTTIME parity with elapsedRealtime makes the subtraction exact.
    a.ldr_sp_imm(2, T9_OFF_TIMESPEC_SEC)      # r2 = tv_sec
    a.movw(0, 1000)
    a.muls_lo_lo(2, 0)                        # r2 = tv_sec * 1000
    a.ldr_sp_imm(0, T9_OFF_TIMESPEC_NSEC)     # r0 = tv_nsec
    a.movw(1, 0xDE83)
    a.movt(1, 0x431B)                         # r1 = 0x431BDE83 (magic)
    a.umull(5, 3, 0, 1)                       # r3:r5 = tv_nsec * magic; r3 = high half
    a.lsrs_imm5(3, 3, 18)                     # r3 = high >> 18 = tv_nsec / 1e6
    a.adds_lo_lo(2, 2, 3)                     # r2 = now_ms

    # ---- delta_ms = now_ms - state_change_ms ----
    # u32 modular subtraction; correct under wrap (u32 ms wraps at ~49.7
    # days uptime, well past Y1 reboot cadence).
    a.ldr_sp_imm(0, T9_OFF_FILE_STATE_TIME)   # r0 = state_change_ms (BE)
    a.rev_lo_lo(0, 0)                         # → host order
    a.subs_lo_lo(2, 2, 0)                     # r2 = delta_ms

    # ---- live_pos = saved_pos + delta_ms ----
    a.ldr_sp_imm(3, T9_OFF_FILE_POS)          # r3 = saved_pos (BE)
    a.rev_lo_lo(3, 3)                         # → host order
    a.adds_lo_lo(3, 3, 2)                     # r3 = live_pos

    # ---- emit reg_notievent_pos_changed_rsp(conn, 0, REASON_CHANGED, live_pos) ----
    # r3 holds live_pos from the math chain above; restore_conn_tid would
    # clobber r3, so push/pop around the bl.
    a.raw(bytes([0x08, 0xB4]))                # push {r3}
    a.add_imm_t3(0, 4, 8)                     # r0 = conn (= struct + 8)
    _emit_restore_conn_tid_from_db(a, 0, 0x05, "t9_pos")
    a.raw(bytes([0x08, 0xBC]))                # pop {r3}
    a.movs_imm8(1, 0)                         # success
    a.movs_imm8(2, REASON_CHANGED)
    if DEBUG_NATIVE_LOG:
        _emit_native_log_u32(a, "log_fmt_t9pos", 3)
    a.blx_imm(PLT_reg_notievent_pos_changed_rsp)

    # database[5] (PLAYBACK_POS subscription gate) stays armed across
    # CHANGED. Position CHANGED then fires at the music app's
    # playstatechanged broadcast rate (~1 Hz when playing).

    a.label("t9_done")
    # ---- epilogue: return jboolean true ----
    a.movs_imm8(0, 1)
    a.addw(13, 13, T9_FRAME)
    # pop {r4, r5, pc} = 0xBD30.
    a.raw(bytes([0x30, 0xBD]))


DEBUG_NATIVE_LOG = False  # toggled by build(debug=True) — controls log-call emission


def build(debug: bool = False) -> tuple[bytes, dict[str, int]]:
    """Build the LOAD-#1-padding trampoline code blob.

    Args:
        debug: if True, splice __android_log_print calls before T5/T6/T9
            wire-side response blx's. Logs go to logcat with tag "Y1T",
            grep-friendly format `<emit_id>=%08x` (e.g. T5emit, T6pos,
            T9pos, T9pstat). Adds ~160 B to the blob. Release builds
            (debug=False) keep blob byte-identical to current shipping.

    Returns:
        (bytes, label_addresses)
        - bytes: the full assembled blob to splice in at vaddr T4_VADDR
        - label_addresses: dict of name → vaddr (so the patcher can wire the
          T2 stub at 0x72d4 to extended_T2)
    """
    global DEBUG_NATIVE_LOG
    DEBUG_NATIVE_LOG = debug

    a = Asm(T4_VADDR)

    # External landmarks — pre-register so b_w / bl_w resolve to absolute targets.
    a.labels["t4_to_unknown"] = UNKNOW_INDICATION
    a.labels["t4_to_epilogue"] = EPILOGUE
    a.labels["jni_get_avrcp_state"] = JNI_GET_AVRCP_STATE

    _emit_t1_extended(a)
    _emit_t4(a)
    _emit_extended_t2(a)
    _emit_t5(a)
    _emit_t_charset(a)                        # Inform PDU 0x17
    _emit_t_battery(a)                        # Inform PDU 0x18
    _emit_t_continuation(a)                   # Continuation PDUs 0x40/0x41
    _emit_t6(a)                               # PDU 0x30 GetPlayStatus
    _emit_t_papp(a)                           # PApp PDUs 0x11..0x16
    _emit_t8(a)                               # PDU 0x31 RegisterNotification dispatch
    _emit_t9(a)                               # proactive PLAYBACK_STATUS_CHANGED + battery + position

    # Shared subroutines for per-event TID save / restore.
    # save_event_seq_id (called from extended_T2 entry post-PDU-check):
    #   database[event_id] = inbound seq_id from sp+0x171. Mirrors stock
    #   JNI's saveRegEventSeqId at 0x5ee4 which we bypass via R1.
    # restore_conn_tid (called from every *_rsp call site):
    #   conn[+0x11] = database[event_id]. Mirrors stock JNI's pattern at
    #   e.g. 0x3c06 in notificationTrackChangedNative.
    _emit_restore_conn_tid_subroutine(a)
    _emit_save_event_seq_id_subroutine(a)
    _emit_event_subscribed_subroutine(a)
    _emit_clear_event_database_subroutine(a)
    # mmap-backed reads of y1-track-info (writer-side ships double-buffer
    # 2213-byte schema; reader-side dispatches active slot and byte-copies
    # the chosen slot into the caller's existing file_buf stack region).
    _emit_get_or_init_mmap_subroutine(a)
    _emit_read_track_info_subroutine(a)
    # .bss-backed trampoline state. Process-scope, zero-init at
    # libextavrcp_jni.so load. First emit after process restart sees
    # state[N]=0 vs current file value, emits one CHANGED per event,
    # subscription gates filter out events with no current subscriber.
    _emit_read_state_block_subroutine(a)
    _emit_write_state_block_subroutine(a)

    # Path strings, 4-byte-aligned for clean ADR offsets.
    a.align(4)
    a.label("path_track_info")
    a.asciiz("/data/data/com.innioasis.y1/files/y1-track-info")
    a.align(4)
    a.label("path_papp_set")
    a.asciiz("/data/data/com.innioasis.y1/files/y1-papp-set")
    a.align(4)

    # TRACK_CHANGED Identifier — 8 zero bytes = AVRCP 1.6 §6.7.2 Table 6.32 SELECTED
    # ("the currently playing track, no specific UID"). Matches the wire
    # shape a reference 1.3-as-TG implementation ships when there's no
    # Browseable Player Now-Playing queue; AVRCP 1.3 §5.4.2 Table 5.30 is
    # silent on Identifier value, so the 1.6 strict reading applies cleanly.
    a.label("selected_track_id")
    a.raw(bytes([0] * 8))
    a.align(4)

    # PApp data tables (PDU 0x11..0x16). All AVRCP 1.3 §5.2 spec values.
    a.label("papp_attr_ids")
    a.raw(bytes([PAPP_ATTR_REPEAT, PAPP_ATTR_SHUFFLE]))
    a.align(4)
    a.label("papp_repeat_values")
    # Y1's musicRepeatMode int enum has 3 values (0=OFF, 1=ONE, 2=ALL) — no
    # GROUP. Spec V13 Tbl 5.20 also defines 0x04 GROUP but we'd be lying to
    # advertise it (T_papp 0x14 would ACK a Set-to-GROUP that Y1 can't honor).
    a.raw(bytes([0x01, 0x02, 0x03]))         # OFF, SINGLE, ALL
    a.align(4)
    a.label("papp_shuffle_values")
    # Y1's musicIsShuffle is a boolean (false/true). Spec V13 Tbl 5.21 also
    # defines 0x03 GROUP, omitted here for the same honesty reason.
    a.raw(bytes([0x01, 0x02]))               # OFF, ALL_TRACK
    a.align(4)
    a.label("papp_current_values")
    # Fallback OFF/OFF for T_papp 0x13 GetCurrent on file-I/O failure.
    # T8 0x08 INTERIM + T9 papp CHANGED read live values from
    # y1-track-info[795..796].
    a.raw(bytes([PAPP_REPEAT_OFF, PAPP_SHUFFLE_OFF]))
    a.align(4)

    # Native debug logging strings (only referenced when build(debug=True)).
    # Tag + per-emit-site format strings. Each fmt is a single %08x arg
    # so log lines look like `Y1T  : T9pos=0000a3f4` — grep-friendly,
    # zero-pad-aligned, and avoids variadic 64-bit packing rules.
    if DEBUG_NATIVE_LOG:
        a.align(4)
        a.label("log_tag")
        a.asciiz("Y1T")
        a.align(4)
        # Per-event emit markers. No %08x value — the event_id is implicit
        # in the call site. Used to verify each CHANGED actually fires: if a
        # given event's CHANGED never appears in a session that should
        # produce one, the database[N] gate never armed (T8 / extended_T2
        # INTERIM didn't run for that event).
        a.label("log_fmt_t9ps")
        a.asciiz("T9ps")
        a.align(4)
        a.label("log_fmt_t9papp")
        a.asciiz("T9papp")
        a.align(4)
        # Position CHANGED live_pos value (host-order u32 ms). Pair with
        # M5wire c39= for wire-emit confirmation. Verifies "frozen in time"
        # symptom = stale value shipped (vs CT-side render bug).
        a.label("log_fmt_t9pos")
        a.asciiz("T9pos=%08x")
        a.align(4)
        # Inbound RegisterNotification entry marker — confirms extended_T2
        # was reached on a PDU=0x31 CMD. value = event_id. Pair with the
        # outbound T5tc / T9ps / T9papp emits to disambiguate "CT didn't
        # subscribe to ev=N" (no T2reg ev=N) from "CT subscribed but our
        # CHANGED gate skipped" (T2reg ev=N present, no matching T5tc/T9ps).
        a.label("log_fmt_t2reg")
        a.asciiz("T2reg ev=%02x")
        a.align(4)
        # Inbound non-RegNotif CMD PDU dispatcher entry. RegNotif (0x31)
        # has T2reg already; T1pdu covers GetCap (0x10), PApp (0x11..0x16),
        # Charset (0x17), Battery (0x18), GetEA (0x20), GetPlayStatus
        # (0x30), Continuation (0x40/0x41). Tells us what CT sends after
        # a CHANGED emit.
        a.label("log_fmt_t1pdu")
        a.asciiz("T1pdu=%02x")
        a.align(4)

    # PApp UTF-8 attribute / value text strings (charset 0x006A).
    a.label("papp_text_repeat")
    a.raw(b"Repeat")                          # 6 B, no null terminator (length passed explicitly)
    a.align(4)
    a.label("papp_text_shuffle")
    a.raw(b"Shuffle")                         # 7 B
    a.align(4)
    a.label("papp_text_off")
    a.raw(b"Off")                             # 3 B
    a.align(4)
    a.label("papp_text_single")
    a.raw(b"Single Track")                    # 12 B
    a.align(4)
    a.label("papp_text_all")
    a.raw(b"All Tracks")                      # 10 B
    a.align(4)

    blob = a.resolve()
    addrs = {k: v for k, v in a.labels.items()
             if k not in ("t4_to_unknown", "t4_to_epilogue",
                          "jni_get_avrcp_state")}
    return blob, addrs


if __name__ == "__main__":
    # LOAD #2 starts at file 0xbc08 in stock libextavrcp_jni.so; we can
    # extend LOAD #1 up to but not into LOAD #2, so the padding budget is
    # 0xbc08 - T4_VADDR = 4020 bytes.
    LOAD2_OFFSET = 0xbc08
    PADDING_BUDGET = LOAD2_OFFSET - T4_VADDR
    blob, addrs = build()
    print(f"blob length: {len(blob)} bytes  (LOAD #1 padding budget: {PADDING_BUDGET} bytes; "
          f"{PADDING_BUDGET - len(blob)} free)")
    print(f"final vaddr: 0x{T4_VADDR + len(blob):x}")
    print()
    print("labels:")
    for name, addr in sorted(addrs.items(), key=lambda kv: kv[1]):
        print(f"  0x{addr:06x}  {name}")
