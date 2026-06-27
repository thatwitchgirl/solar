#!/usr/bin/env python3
"""
patch_libextavrcp_jni.py — AVRCP TG trampoline chain.

Patches libextavrcp_jni.so with a redirect (R1), a uinput auto-repeat NOP
(U1), and a dynamically-assembled Thumb-2 trampoline blob in the LOAD #1
page-padding code-cave at vaddr 0xac54. The blob handles GetCapabilities,
GetElementAttributes, GetPlayStatus, RegisterNotification (all events),
InformDisplayableCharacterSet, InformBatteryStatusOfCT, and the §5.4.2
track-edge 3-tuple — answering inbound 1.3 PDUs directly from native code
since the stock Java AVRCP layer is a no-op stub on this firmware.

The blob is built by _trampolines.py via the Thumb-2 assembler in
_thumb2asm.py. Per-trampoline behaviour: docs/PATCHES.md. Stack frame
and JNI calling convention: docs/ARCHITECTURE.md.

Pairs with patch_mtkbt.py's P1 (msg 519 size=9 routing) and
patch_libextavrcp.py's E1 (§5.3.1 Table 5.24 zero-length emit).
"""

import argparse
import hashlib
import os
import sys
from pathlib import Path

# Allow `from _trampolines import ...` when invoked from any cwd.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _trampolines import build as build_trampolines, T4_VADDR
from _thumb2asm import _encode_t4_branch  # noqa: F401 (used to build T2 stub)
from _thumb2asm import Asm

# Entry of notificationTrackChangedNative — replaced with `b.w T5`.
# Paired with MtkBt.odex's sswitch_1a3 cardinality NOP; runs on every
# `metachanged` broadcast.
NATIVE_TRACK_CHANGED_VADDR = 0x3bc0

# Entry of notificationPlayStatusChangedNative — replaced with `b.w T9`.
# Paired with MtkBt.odex's sswitch_18a cardinality NOP; runs on every
# `playstatechanged` broadcast.
NATIVE_PLAY_STATUS_CHANGED_VADDR = 0x3c88

STOCK_MD5         = "fd2ce74db9389980b55bccf3d8f15660"
OUTPUT_MD5        = "4ebd181976c1dbdd19b6a06112dce484"

# KOENSAYR_DEBUG=1: splices __android_log_print calls (tag "Y1T") into the
# inbound CMD dispatcher (T1pdu / T2reg markers) and T9's outbound emit
# sites (T9ps / T9papp / T9pos). Release builds remain byte-identical
# without the env var.
DEBUG_LOGGING     = os.environ.get("KOENSAYR_DEBUG", "") == "1"
OUTPUT_DEBUG_MD5  = "384f0c630feff36d43e62a122764bade"
EXPECTED_OUTPUT_MD5 = OUTPUT_DEBUG_MD5 if DEBUG_LOGGING else OUTPUT_MD5

# ---------------------------------------------------------------- T1

# T1 stub at 0x7308 overlays the unused `testparmnum` JNI debug method
# (40 bytes available). The stub is a 4-byte `b.w T1_extended` bridge —
# T1's GetCapabilities body lives in the trampoline blob (see
# `_emit_t1_extended` in `_trampolines.py`). Hosting the body in the
# blob gives T1's GetCapabilities path room to bl clear_event_database
# (resets the per-event subscription database on every fresh CT
# connection so ghost-arm subscriptions can't leak across CT
# disconnect/reconnect). The remaining 36 bytes of the testparmnum
# slot are zero-padded (never executed).
def _t1_bridge(t1_extended_vaddr: int) -> bytes:
    a = Asm(0x7308)
    a.labels["target"] = t1_extended_vaddr
    a.b_w("target")
    while len(a.buf) < 40:
        a.buf.append(0x00)
    return a.resolve()

# Stock testparmnum first 40 bytes.
TESTPARMNUM_STOCK = bytes([
    0x10, 0xB5, 0x04, 0x20, 0x07, 0x4C, 0x08, 0x4A,
    0x7C, 0x44, 0x21, 0x46, 0x7A, 0x44, 0xFB, 0xF7,
    0xF4, 0xEF, 0x06, 0x4A, 0x04, 0x20, 0x21, 0x46,
    0x00, 0x23, 0x7A, 0x44, 0xFB, 0xF7, 0xEC, 0xEF,
    0x00, 0x20, 0x10, 0xBD, 0x01, 0x07, 0x00, 0x00,
])
assert len(TESTPARMNUM_STOCK) == 40

# ---------------------------------------------------------------- T2 stub

# Stock classInitNative (48 bytes) — entry + body + literal pool.
CLASSINITNATIVE_STOCK = bytes([
    0x10, 0xB5, 0x04, 0x20, 0x07, 0x4C, 0x08, 0x4A,
    0x7C, 0x44, 0x21, 0x46, 0x7A, 0x44, 0xFC, 0xF7,
    0x10, 0xE8, 0x06, 0x4A, 0x04, 0x20, 0x21, 0x46,
    0x00, 0x23, 0x7A, 0x44, 0xFC, 0xF7, 0x08, 0xE8,
    0x00, 0x20, 0x10, 0xBD, 0x39, 0x07, 0x00, 0x00,
    0xCA, 0x12, 0x00, 0x00, 0xDD, 0x2C, 0x00, 0x00,
])
assert len(CLASSINITNATIVE_STOCK) == 48


def _t2_stub(extended_t2_vaddr: int) -> bytes:
    """Build the 48-byte block at 0x72d0:
        0x72d0: movs r0, #0; bx lr      (classInitNative `return 0` stub)
        0x72d4: b.w extended_T2         (the only T2 logic; everything else
                                         is dispatched inside extended_T2)
        0x72d8..0x72ff: zero filler (unreachable)
    """
    a = Asm(0x72d0)
    a.raw(bytes([0x00, 0x20, 0x70, 0x47]))   # movs r0, #0; bx lr
    a.labels["target"] = extended_t2_vaddr
    a.b_w("target")
    while len(a.buf) < 48:
        a.buf.append(0x00)
    return a.resolve()


# ---------------------------------------------------------------- LOAD #1 phdr

LOAD1_PHDR_OFFSET = 0x54
LOAD1_FILESZ_OFFSET = LOAD1_PHDR_OFFSET + 16
LOAD1_MEMSZ_OFFSET  = LOAD1_PHDR_OFFSET + 20
LOAD1_OLD_SIZE = 0xac54

# Hard ceiling for the trampoline blob. The stock ELF lays LOAD #2's
# file offset at 0xbc08 (its first byte is `.data` / `.got` and the
# dynamic linker reads it via mmap). Anything past 0xbc08 in the file
# silently clobbers LOAD #2's first bytes — GOT corruption produces a
# SIGSEGV during/after the next PLT call (typically classInitNative).
# The patcher's MD5 pin catches stable changes but a fresh debug build
# with new log sites would *match its own pinned MD5* even while
# silently corrupting LOAD #2; the assertion below makes that case
# fail loudly.
LOAD2_FILE_OFFSET = 0xbc08
TRAMPOLINE_BUDGET = LOAD2_FILE_OFFSET - LOAD1_OLD_SIZE   # 0xbc08 - 0xac54 = 4020

# ---------------------------------------------------------------- patch list builder


def _native_track_changed_stub(t5_vaddr: int) -> bytes:
    """Replace the first 4 bytes of notificationTrackChangedNative with
    `b.w T5`. The remaining 196 bytes of the original function body are
    unreachable but left in place (they form valid but dead code; harmless)."""
    a = Asm(NATIVE_TRACK_CHANGED_VADDR)
    a.labels["target"] = t5_vaddr
    a.b_w("target")
    return a.resolve()


def _native_play_status_changed_stub(t9_vaddr: int) -> bytes:
    """Replace the first 4 bytes of notificationPlayStatusChangedNative with
    `b.w T9`. The remaining bytes of the original function body are
    unreachable but left in place (valid dead code; harmless)."""
    a = Asm(NATIVE_PLAY_STATUS_CHANGED_VADDR)
    a.labels["target"] = t9_vaddr
    a.b_w("target")
    return a.resolve()


# Stock first 4 bytes of notificationTrackChangedNative — the prologue's
# `stmdb sp!, {r4, r5, r6, r7, r8, r9, sl, lr}` instruction.
NATIVE_TRACK_CHANGED_STOCK_PROLOGUE = bytes([0x2D, 0xE9, 0xF0, 0x47])

# Stock first 4 bytes of notificationPlayStatusChangedNative.
# Disassembled: stmdb sp!, {r0, r1, r4, r5, r6, r7, r8, lr} (reg list 0x41F3) --
# distinct from notificationTrackChangedNative's prologue (0x47F0) because the
# play_status native takes 3 jbyte args (Java arg3 = play_status arrives in r4
# per the AAPCS register / stack split for variadic-byte Java natives), and the
# stock body needs r0 / r1 (env, this) preserved for re-use after the call into
# the AVRCP service.  We don't care about the original body — overwriting the
# first 4 bytes with `b.w T9` short-circuits everything past it.
NATIVE_PLAY_STATUS_CHANGED_STOCK_PROLOGUE = bytes([0x2D, 0xE9, 0xF3, 0x41])


def build_patches() -> tuple[list[dict], int]:
    """Build the patch list. Returns (patches, new_load1_size)."""
    blob, addrs = build_trampolines(debug=DEBUG_LOGGING)
    extended_t2_vaddr = addrs["extended_T2"]
    t1_extended_vaddr = addrs["T1_extended"]
    t5_vaddr = addrs["T5"]
    t9_vaddr = addrs["T9"]
    new_load1_size = T4_VADDR + len(blob)

    if len(blob) > TRAMPOLINE_BUDGET:
        raise AssertionError(
            f"trampoline blob ({len(blob)} bytes) exceeds the LOAD #1 padding "
            f"budget ({TRAMPOLINE_BUDGET} bytes). Anything past file offset "
            f"0x{LOAD2_FILE_OFFSET:x} overwrites LOAD #2's .data/.got and the "
            f"binary will SIGSEGV during classInitNative on load. Shrink the "
            f"trampoline (drop debug log sites or consolidate format strings) "
            f"before re-running. debug={DEBUG_LOGGING}"
        )

    patches = [
        {
            "name": "R1: redirect bne.n 0x65bc → bl.w 0x7308 (T1) at 0x6538",
            "offset": 0x6538,
            "before": bytes([0x40, 0xD1, 0x09, 0x25]),  # bne.n 0x65bc; movs r5, #9
            "after":  bytes([0x00, 0xF0, 0xE6, 0xFE]),  # bl.w 0x7308
        },
        {
            # U1 — disable kernel auto-repeat on the AVRCP /dev/uinput device.
            # The init at 0x73c8 (called via exported avrcp_input_init, which
            # opens "/dev/uinput" @ 0xa849 and registers a keyboard with name
            # "AVRCP" @ 0x828b, BUS_BLUETOOTH=5) issues four UI_SET_EVBIT
            # ioctls in sequence: EV_KEY, EV_REL (vendor typo, harmless),
            # EV_REP (0x14), EV_SYN. Without EV_REP in the device's evbit,
            # input_register_device() in the kernel won't enable
            # input_enable_softrepeat(), so a dropped PASSTHROUGH RELEASE no
            # longer leads to a 25 Hz KEY_xxx REPEAT cascade and the haptic
            # loop on strict CTs goes away. NOPing the blx ioctl@plt at
            # 0x74e8 is the most surgical option — register / stack contracts
            # are untouched; the next ioctl reloads r1 / r2 cleanly. See
            # docs/INVESTIGATION.md "kernel auto-repeat" for the getevent(8)
            # trace that drove this.
            "name": "U1: NOP blx ioctl@plt for UI_SET_EVBIT(EV_REP) at 0x74e8 — disable kernel auto-repeat on AVRCP uinput",
            "offset": 0x74e8,
            "before": bytes([0xFC, 0xF7, 0xB4, 0xE8]),  # blx ioctl@plt
            "after":  bytes([0x00, 0xBF, 0x00, 0xBF]),  # nop ; nop (Thumb-2)
        },
        {
            "name": (
                f"T1: testparmnum → b.w T1_extended (0x{t1_extended_vaddr:x})"
                f" bridge at 0x7308"
            ),
            "offset": 0x7308,
            "before": TESTPARMNUM_STOCK,
            "after":  _t1_bridge(t1_extended_vaddr),
        },
        {
            "name": (
                f"notificationTrackChangedNative @ 0x{NATIVE_TRACK_CHANGED_VADDR:x}"
                f" → b.w T5 (0x{t5_vaddr:x}) — proactive CHANGED on track change"
            ),
            "offset": NATIVE_TRACK_CHANGED_VADDR,
            "before": NATIVE_TRACK_CHANGED_STOCK_PROLOGUE,
            "after":  _native_track_changed_stub(t5_vaddr),
        },
        {
            "name": (
                f"notificationPlayStatusChangedNative @"
                f" 0x{NATIVE_PLAY_STATUS_CHANGED_VADDR:x} → b.w T9 (0x{t9_vaddr:x})"
                f" — proactive PLAYBACK_STATUS_CHANGED on play / pause edge"
            ),
            "offset": NATIVE_PLAY_STATUS_CHANGED_VADDR,
            "before": NATIVE_PLAY_STATUS_CHANGED_STOCK_PROLOGUE,
            "after":  _native_play_status_changed_stub(t9_vaddr),
        },
        {
            "name": (
                f"T2 stub: classInitNative stub + b.w 0x{extended_t2_vaddr:x}"
                " (extended_T2) at 0x72d0"
            ),
            "offset": 0x72d0,
            "before": CLASSINITNATIVE_STOCK,
            "after":  _t2_stub(extended_t2_vaddr),
        },
        {
            "name": (
                f"trampoline blob @ 0x{T4_VADDR:x} ({len(blob)} bytes "
                f"in LOAD #1 padding; final vaddr 0x{new_load1_size:x})"
            ),
            "offset": T4_VADDR,
            "before": bytes([0x00] * len(blob)),  # stock LOAD #1 padding is zeros
            "after":  blob,
        },
        {
            "name": f"LOAD #1 filesz: 0x{LOAD1_OLD_SIZE:x} → 0x{new_load1_size:x}",
            "offset": LOAD1_FILESZ_OFFSET,
            "before": LOAD1_OLD_SIZE.to_bytes(4, "little"),
            "after":  new_load1_size.to_bytes(4, "little"),
        },
        {
            "name": f"LOAD #1 memsz: 0x{LOAD1_OLD_SIZE:x} → 0x{new_load1_size:x}",
            "offset": LOAD1_MEMSZ_OFFSET,
            "before": LOAD1_OLD_SIZE.to_bytes(4, "little"),
            "after":  new_load1_size.to_bytes(4, "little"),
        },
    ]
    return patches, new_load1_size


# ---------------------------------------------------------------- I/O helpers


def md5(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


def verify(data: bytes, mode: str, patches: list[dict]) -> tuple[bool, list[dict]]:
    results = []
    for p in patches:
        expected = p[mode]
        actual = bytes(data[p["offset"]: p["offset"] + len(expected)])
        results.append({**p, "actual": actual, "ok": actual == expected})
    return all(r["ok"] for r in results), results


def print_results(label: str, results: list[dict], mode: str) -> None:
    ok_count = sum(1 for r in results if r["ok"])
    total = len(results)
    # Quiet when everything verifies — print a one-line summary. The full
    # per-site listing is only needed for diagnosis when something fails.
    if ok_count == total:
        print(f"\n{label}: {ok_count}/{total} sites OK")
        return
    print(f"\n{label}")
    print("-" * 72)
    for r in results:
        n = len(r["before"])
        fmt = lambda b: b.hex(" ") if n <= 8 else b[:8].hex(" ") + " ..."
        print(f"  [{'OK' if r['ok'] else 'FAIL'}] 0x{r['offset']:06x}  {r['name']}")
        if not r["ok"]:
            print(f"          expected ({mode}): {fmt(r[mode])}")
            print(f"          actual:            {fmt(r['actual'])}")
    print("-" * 72)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Minimum JNI patch — route size-9 msg-519 frames to BT-SIG VENDOR path"
    )
    parser.add_argument("input", help="Path to stock libextavrcp_jni.so")
    parser.add_argument("--output", "-o", default=None,
                        help="Output path (default: output/libextavrcp_jni.so.patched)")
    parser.add_argument("--verify-only", action="store_true",
                        help="Check patch sites only, do not write output")
    parser.add_argument("--skip-md5", action="store_true",
                        help="Skip stock MD5 check (use for alternate stock builds)")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"ERROR: {input_path} not found", file=sys.stderr)
        sys.exit(1)

    data = bytearray(input_path.read_bytes())
    input_md5 = md5(data)

    # Already-at-expected-output fast path. MD5 over the whole file is
    # strictly stronger evidence than verifying a handful of patch sites,
    # so when the input already hashes to the expected output for the
    # current build mode (release or debug) there's nothing to do.
    if EXPECTED_OUTPUT_MD5 is not None and input_md5 == EXPECTED_OUTPUT_MD5:
        print(f"Input:  {input_path}  ({len(data):,} bytes)")
        print(f"MD5:    {input_md5}  [OK — already at expected output]")
        print("Nothing to do.")
        sys.exit(0)

    if args.skip_md5:
        md5_tag = "(stock check skipped)"
    elif input_md5 == STOCK_MD5:
        md5_tag = "[OK — matches stock]"
    else:
        md5_tag = f"[MISMATCH — expected {STOCK_MD5}]"

    print(f"Input:  {input_path}  ({len(data):,} bytes)")
    print(f"MD5:    {input_md5}  {md5_tag}")

    if not args.skip_md5 and input_md5 != STOCK_MD5:
        print("ERROR: input is not the expected stock build.")
        if EXPECTED_OUTPUT_MD5 is not None:
            print(f"       Expected stock ({STOCK_MD5}) or already-patched ({EXPECTED_OUTPUT_MD5}).")
        print("       Use --skip-md5 for alternate stock builds.")
        sys.exit(1)

    patches, new_load1_size = build_patches()

    # Site-level verification is only informative when MD5 alone isn't
    # sufficient: alternate stock build (--skip-md5) or development mode
    # where the expected output MD5 isn't pinned yet. On the normal happy
    # path the input-MD5 and output-MD5 checks cover every byte in the file.
    show_sites = args.skip_md5 or EXPECTED_OUTPUT_MD5 is None

    if show_sites:
        pre_ok, pre_results = verify(data, "before", patches)
        print_results("Pre-patch verification (stock)", pre_results, "before")

        if not pre_ok:
            post_ok, post_results = verify(data, "after", patches)
            print_results("Already-patched check", post_results, "after")
            if post_ok:
                print("\nBinary is already patched. Nothing to do.")
                sys.exit(0)
            print("\nERROR: patch site matches neither stock nor patched.")
            sys.exit(1)

    if args.verify_only:
        print("\nVerify-only — no output written.")
        sys.exit(0)

    for p in patches:
        data[p["offset"]: p["offset"] + len(p["after"])] = p["after"]

    output_md5 = md5(data)
    output_md5_mismatch = EXPECTED_OUTPUT_MD5 is not None and output_md5 != EXPECTED_OUTPUT_MD5

    # Post-patch site verification fires either when we're already in a
    # site-aware mode (developer / alternate stock) or as a diagnostic when
    # the produced output doesn't hash to the pinned expected value.
    if show_sites or output_md5_mismatch:
        post_ok, post_results = verify(data, "after", patches)
        print_results("Post-patch verification", post_results, "after")
        if not post_ok:
            print("\nERROR: post-patch verification failed — output not written.")
            sys.exit(1)

    if args.output:
        output_path = Path(args.output)
    else:
        output_dir = Path("output")
        output_dir.mkdir(exist_ok=True)
        output_path = output_dir / "libextavrcp_jni.so.patched"
    output_path.write_bytes(data)

    md5_var = "OUTPUT_DEBUG_MD5" if DEBUG_LOGGING else "OUTPUT_MD5"
    if EXPECTED_OUTPUT_MD5 is None:
        out_tag = f"[set {md5_var} = \"{output_md5}\"]"
    elif output_md5 == EXPECTED_OUTPUT_MD5:
        out_tag = "[OK — matches expected]"
    else:
        out_tag = f"[MISMATCH — expected {EXPECTED_OUTPUT_MD5}]"

    print(f"\nOutput: {output_path}  ({len(data):,} bytes)")
    print(f"MD5:    {output_md5}  {out_tag}")
    print(f"\nDeploy:")
    print(f"  adb push {output_path} /system/lib/libextavrcp_jni.so")
    print(f"  adb shell chmod 644 /system/lib/libextavrcp_jni.so")
    print(f"  adb reboot")
    print(f"  logcat | grep -E 'CMD_FRAME_IND|registerNotificationInd|cardinality|Y1Patch'")

    if output_md5_mismatch and not args.skip_md5:
        print("\nERROR: output MD5 doesn't match expected. Output was written but"
              " the patcher's expected hash is stale or the patch logic diverged."
              " Pass --skip-md5 to suppress.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
