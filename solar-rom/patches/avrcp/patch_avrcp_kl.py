#!/usr/bin/env python3
"""
patch_avrcp_kl.py — re-align stock AOSP AVRCP.kl with AVRCP 1.3 §4.6.1's
discrete PASSTHROUGH PAUSE semantics. Stock AOSP's 2010-era AVRCP.kl maps
Linux `KEY_PAUSECD` (201) to Android `KEYCODE_MEDIA_PLAY_PAUSE` (85) —
coalescing the discrete PAUSE keycode into the toggle key. AVRCP 1.3 §4.6.1
+ ICS Table 8 define `PASSTHROUGH 0x46 PAUSE` as a DISCRETE Optional command
distinct from `0x44 PLAY`; the AOSP coalescing predates Android's discrete
`KEYCODE_MEDIA_PAUSE` (127) and is a spec deviation visible to any CT that
sends `0x46` (e.g. CTs with separate play / pause UI buttons).

`libextavrcp_jni.so`'s `avrcp_input_sendkey` table at vaddr `0xccec` maps
`PASSTHROUGH 0x46` → Linux `KEY_PAUSECD` (201). Post-patch, AVRCP.kl row 201
routes to discrete `MEDIA_PAUSE` (127), which propagates through Patch H
(`BaseActivity.dispatchKeyEvent`'s media-key bypass) to
`PlayControllerReceiver`'s `cond_pause_strict` arm → `PlayerService.pause(
0x12, true)` — discrete pause, idempotent on repeat presses.

Row 200 (`KEY_PLAYCD → MEDIA_PLAY`) is unchanged, so CTs that send
`0x44 PLAY` as a toggle (the older convention) continue to route through
`cond_play_strict` (`if isPlaying: playOrPause() else play(true)`) as before.

The AVRCP.kl file is byte-identical across stock 3.0.2 and 3.0.7
(`366670c4f944150bd657d9377839463a`); `KNOWN_AVRCP_KL_MD5S` maps each
known firmware to its expected stock MD5 so a future build that diverges
gets a clean MD5-mismatch report rather than silent miscompare.
"""

import argparse
import hashlib
import os
import sys
from pathlib import Path

KNOWN_AVRCP_KL_MD5S = {
    "3af1d4ad8f955038186696950430ffda": "3.0.2",
    "fd2ce74db9389980b55bccf3d8f15660": "3.0.7",
    "366670c4f944150bd657d9377839463a": "3.0.2/3.0.7",
}

STOCK_MD5  = "366670c4f944150bd657d9377839463a"
OUTPUT_MD5 = "dfd9afd58e94c38fc6f92592674b4ef1"

PATCHES = [
    {
        "name":   "[K1] AVRCP.kl row 201: MEDIA_PLAY_PAUSE -> MEDIA_PAUSE (discrete PASSTHROUGH 0x46 PAUSE per AVRCP 1.3 §4.6.1)",
        "offset": 0x2ac,
        "before": b"key 201   MEDIA_PLAY_PAUSE    WAKE\n",
        "after":  b"key 201   MEDIA_PAUSE         WAKE\n",
    },
]


def md5(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


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
    if ok_count == total:
        print(f"\n{label}: {ok_count}/{total} sites OK")
        return
    print(f"\n{label}")
    print("-" * 72)
    for r in results:
        print(f"  [{'OK' if r['ok'] else 'FAIL'}] 0x{r['offset']:06x}  {r['name']}")
        if not r["ok"]:
            print(f"          expected ({mode}): {r[mode]!r}")
            print(f"          actual:            {r['actual']!r}")
    print("-" * 72)


def main():
    parser = argparse.ArgumentParser(
        description="AOSP AVRCP.kl discrete-PAUSE routing fix (row 201: MEDIA_PLAY_PAUSE -> MEDIA_PAUSE)"
    )
    parser.add_argument("input", help="Path to stock AVRCP.kl")
    parser.add_argument("--output", "-o", default=None,
                        help="Output path (default: output/AVRCP.kl)")
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

    if input_md5 == OUTPUT_MD5:
        print(f"Input:  {input_path}  ({len(data):,} bytes)")
        print(f"MD5:    {input_md5}  [OK — already at expected output]")
        print("Nothing to do.")
        return

    print(f"Input:  {input_path}  ({len(data):,} bytes)")
    md5_tag = ""
    if input_md5 == STOCK_MD5:
        version = KNOWN_AVRCP_KL_MD5S.get(input_md5, "unknown")
        md5_tag = f"[OK — matches stock ({version})]"
    else:
        md5_tag = f"[MISMATCH — expected {STOCK_MD5}]"
    print(f"MD5:    {input_md5}  {md5_tag}")

    if not args.skip_md5 and input_md5 != STOCK_MD5:
        known = ", ".join(f"{m}={v}" for m, v in KNOWN_AVRCP_KL_MD5S.items())
        print(f"ERROR: input MD5 {input_md5} doesn't match any known stock AVRCP.kl")
        print(f"       Known: {known}")
        print(f"       Pass --skip-md5 to bypass (diagnostic use only).")
        sys.exit(1)

    ok, results = verify(data, "before")
    if not ok:
        print_results("Pre-patch verification (looking for stock bytes)", results, "before")
        already_ok, _ = verify(data, "after")
        if already_ok:
            print("\nAll sites already match expected post-patch bytes — input is already patched.")
            return
        sys.exit(1)
    print_results("Pre-patch verification", results, "before")

    for p in PATCHES:
        data[p["offset"]: p["offset"] + len(p["before"])] = p["after"]

    ok, results = verify(data, "after")
    print_results("Post-patch verification", results, "after")
    if not ok:
        print("ERROR: post-patch verification failed; refusing to write output", file=sys.stderr)
        sys.exit(1)

    if args.verify_only:
        print("\n--verify-only: not writing output.")
        return

    out_path = Path(args.output) if args.output else Path("output") / "AVRCP.kl"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(bytes(data))
    output_md5 = md5(data)
    md5_tag = "[OK — matches expected]" if output_md5 == OUTPUT_MD5 else f"[MISMATCH — expected {OUTPUT_MD5}]"
    print(f"\nOutput: {out_path}  ({len(data):,} bytes)")
    print(f"MD5:    {output_md5}  {md5_tag}")
    if output_md5 != OUTPUT_MD5:
        print("\nERROR: output MD5 doesn't match expected. Output was written but the patcher's "
              "expected hash is stale or the patch logic diverged. Pass --skip-md5 to suppress.")
        sys.exit(1)


if __name__ == "__main__":
    main()
