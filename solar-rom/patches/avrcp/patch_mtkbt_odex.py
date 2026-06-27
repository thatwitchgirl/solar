#!/usr/bin/env python3
"""
patch_mtkbt_odex.py — Java-side flag flips + cardinality NOPs in MtkBt.odex.

F1 — getPreferVersion(): return 14 instead of 10 (BlueAngel internal flag,
     unblocks the Java AVRCP dispatcher's 1.3+ command path).
F2 — BluetoothAvrcpService.disable(): reset sPlayServiceInterface so the
     second BT-toggle activation doesn't short-circuit to STATE_ENABLED +
     teardown before the peer CONNECT_IND arrives.
Cardinality NOPs (TRACK_CHANGED, PLAYBACK_STATUS_CHANGED) — Java's
     mRegisteredEvents BitSet is never populated because the JNI's TG
     layer uses native conn-state tracking. NOPing the if-eqz gates lets
     notificationTrackChangedNative / notificationPlayStatusChangedNative
     fire on every metachanged / playstatechanged broadcast.

Recomputes DEX adler32. Per-patch byte-level reference: docs/PATCHES.md.
"""

import argparse
import hashlib
import os
import struct
import sys
import zlib
from pathlib import Path

STOCK_MD5         = "11566bc23001e78de64b5db355238175"
OUTPUT_MD5        = "00cc642742044286966cbb7b01135ca7"

DEBUG_LOGGING     = os.environ.get("KOENSAYR_DEBUG", "") == "1"
OUTPUT_DEBUG_MD5  = OUTPUT_MD5

EXPECTED_OUTPUT_MD5 = OUTPUT_DEBUG_MD5 if DEBUG_LOGGING else OUTPUT_MD5

DEX_OFFSET     = 0x28
ADLER_FILE_OFF = 0x30

PATCHES = [
    {
        "name":   "[F1] getPreferVersion return value (BlueAngel internal 10 -> 14, unblocks 1.3+ dispatch)",
        "offset": 0x3e0ea,
        "before": bytes([0x0a]),
        "after":  bytes([0x0e]),
    },
    {
        "name":   "handleKeyMessage TRACK_CHANGED cardinality bypass (NOP if-eqz at 0x3c530)",
        "offset": 0x3c530,
        "before": bytes([0x38, 0x05, 0xda, 0xff]),  # if-eqz v5, +-38 (-> :cond_184)
        "after":  bytes([0x00, 0x00, 0x00, 0x00]),  # nop; nop
    },
    {
        "name":   "handleKeyMessage PLAYBACK_STATUS_CHANGED cardinality bypass (NOP if-eqz at 0x3c4fe)",
        # sswitch_18a / event 0x01 case in handleKeyMessage's nested
        # sparse-switch (same idiom as the TRACK_CHANGED NOP above).
        # Without this NOP the JNI's notificationPlayStatusChangedNative is
        # never invoked because the Java BitSet of registered events is
        # permanently empty (TG bookkeeping isn't updated by our
        # trampolines). With the NOP, the native fires on every
        # `com.android.music.playstatechanged` broadcast and lands in T9 via
        # the libextavrcp_jni.so hook at 0x3c88.
        "offset": 0x3c4fe,
        "before": bytes([0x38, 0x05, 0xf3, 0xff]),  # if-eqz v5, +-13 (-> :cond_184)
        "after":  bytes([0x00, 0x00, 0x00, 0x00]),  # nop; nop
    },
    {
        "name":   "BTAvrcpMusicAdapter$3.onReceive playstatechanged dedupe NOP",
        # NOP `if-eq v3, v2, :cond_50` (mPreviousPlayStatus dedupe) so every
        # playstatechanged broadcast posts msg=1 + msg=2. T5 / T9 internal
        # dedupes gate wire emits on actual edges; the broadcast-handler
        # dedupe was blocking the 1 Hz position tick, the papp CHANGED
        # loop, and PAUSED → STOPPED transitions.
        "offset": 0x3b310,
        "before": bytes([0x32, 0x23, 0xea, 0xff]),  # if-eq v3, v2, +0xffea
        "after":  bytes([0x00, 0x00, 0x00, 0x00]),  # nop; nop
    },
    {
        "name":   "[F2] disable() reset sPlayServiceInterface = false",
        "offset": 0x03f21a,
        "before": bytes([0x1a, 0x01, 0x02, 0x03,   # const-string v1, "EXT_AVRCP"
                         0x1a, 0x02, 0x21, 0x0b,   # const-string v2, "[BT][AVRCP] -disable"
                         0x71, 0x20, 0x86, 0x01,   # invoke-static Log::i
                         0x21, 0x00]),             #   {v1, v2}
        "after":  bytes([0x12, 0x10,               # const/4 v1, #0
                         0x6a, 0x01, 0xf3, 0x04,   # sput-byte v1, sPlayServiceInterface
                         0x00, 0x00,               # nop
                         0x00, 0x00,               # nop
                         0x00, 0x00,               # nop
                         0x00, 0x00]),             # nop
    },
]


def md5(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


def compute_adler32(data: bytes) -> int:
    dex_len = struct.unpack_from("<I", data, 12)[0]
    return zlib.adler32(data[DEX_OFFSET + 12: DEX_OFFSET + dex_len]) & 0xFFFFFFFF


def verify(data: bytes, mode: str) -> tuple[bool, list[dict]]:
    results = []
    for p in PATCHES:
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
        description="Patch stock MtkBt.odex for AVRCP 1.3+ Java-dispatcher unblock + BT toggle fix"
    )
    parser.add_argument("input", help="Path to stock MtkBt.odex")
    parser.add_argument("--output", "-o", default=None,
                        help="Output path (default: output/MtkBt.odex.patched)")
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

    if data[:4] != b"dey\n":
        print("ERROR: not an ODEX file (missing 'dey\\n' magic)")
        sys.exit(1)

    # Site-level verification is only informative when MD5 alone isn't
    # sufficient: alternate stock build (--skip-md5) or development mode
    # where the expected output MD5 isn't pinned yet. On the normal happy
    # path the input-MD5 and output-MD5 checks cover every byte in the file.
    show_sites = args.skip_md5 or EXPECTED_OUTPUT_MD5 is None

    if show_sites:
        pre_ok, pre_results = verify(data, "before")
        print_results("Pre-patch verification (stock)", pre_results, "before")

        if not pre_ok:
            post_ok, post_results = verify(data, "after")
            print_results("Already-patched check", post_results, "after")
            if post_ok:
                print("\nBinary is already fully patched. Nothing to do.")
                sys.exit(0)
            print("\nERROR: patch sites match neither stock nor fully-patched state.")
            sys.exit(1)

        # adler32 check on input — only meaningful in site-aware mode (mismatch
        # there could indicate the alternate-stock build has a different DEX
        # body than expected). On the normal happy path the input-MD5 already
        # validated the entire file, including the adler32 field.
        stored_adler   = struct.unpack_from("<I", data, ADLER_FILE_OFF)[0]
        computed_adler = compute_adler32(data)
        if stored_adler != computed_adler:
            print(f"\n  [WARN] 0x{ADLER_FILE_OFF:06x}  "
                  f"adler32 stored=0x{stored_adler:08x} computed=0x{computed_adler:08x}")
            print("  WARNING: adler32 mismatch on input — continuing anyway")

    if args.verify_only:
        print("\nVerify-only — no output written.")
        sys.exit(0)

    for p in PATCHES:
        data[p["offset"]: p["offset"] + len(p["after"])] = p["after"]

    # adler32 must always be recomputed and written back regardless of
    # verification mode — Dalvik refuses to load the DEX without it.
    new_adler = compute_adler32(data)
    struct.pack_into("<I", data, ADLER_FILE_OFF, new_adler)

    output_md5 = md5(data)
    output_md5_mismatch = EXPECTED_OUTPUT_MD5 is not None and output_md5 != EXPECTED_OUTPUT_MD5

    # Post-patch site verification fires either when we're already in a
    # site-aware mode (developer / alternate stock) or as a diagnostic when
    # the produced output doesn't hash to the pinned expected value.
    if show_sites or output_md5_mismatch:
        post_ok, post_results = verify(data, "after")
        print_results("Post-patch verification", post_results, "after")

        stored_after   = struct.unpack_from("<I", data, ADLER_FILE_OFF)[0]
        computed_after = compute_adler32(data)
        if stored_after != computed_after:
            print(f"  [FAIL] 0x{ADLER_FILE_OFF:06x}  "
                  f"adler32 stored=0x{stored_after:08x} computed=0x{computed_after:08x}")
            post_ok = False

        if not post_ok:
            print("\nERROR: post-patch verification failed — output not written.")
            sys.exit(1)

    if args.output:
        output_path = Path(args.output)
    else:
        output_dir = Path("output")
        output_dir.mkdir(exist_ok=True)
        output_path = output_dir / "MtkBt.odex.patched"

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
    print(f"  adb push {output_path} /system/app/MtkBt.odex")
    print(f"  adb shell chmod 644 /system/app/MtkBt.odex")
    print(f"  adb reboot")

    if output_md5_mismatch and not args.skip_md5:
        print("\nERROR: output MD5 doesn't match expected. Output was written but"
              " the patcher's expected hash is stale or the patch logic diverged."
              " Pass --skip-md5 to suppress.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
