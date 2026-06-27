"""
Tiny Thumb-2 assembler — only the instructions used by the AVRCP
trampoline blob (T-family) in patch_libextavrcp_jni.py.

Two-pass: emit() builds the instruction stream with placeholders; resolve()
patches in pc-relative offsets once every label has a known address.

NOT a general-purpose assembler. Only the encodings I actually need are
implemented.
"""

from typing import Callable


def _check(cond: bool, msg: str) -> None:
    if not cond:
        raise ValueError(msg)


class Asm:
    def __init__(self, base_addr: int) -> None:
        self.base = base_addr
        self.buf = bytearray()
        self.labels: dict[str, int] = {}
        self.fixups: list[tuple[int, Callable[[int], bytes]]] = []

    # ------------------------------------------------------------------ basic

    @property
    def cur(self) -> int:
        return self.base + len(self.buf)

    def label(self, name: str) -> None:
        _check(name not in self.labels, f"duplicate label {name}")
        self.labels[name] = self.cur

    def _hw(self, hw: int) -> None:
        _check(0 <= hw <= 0xFFFF, f"bad hw {hw:#x}")
        self.buf += bytes([hw & 0xFF, (hw >> 8) & 0xFF])

    def _word(self, w: int) -> None:
        self.buf += bytes([(w >> i) & 0xFF for i in (0, 8, 16, 24)])

    def raw(self, b: bytes) -> None:
        self.buf += b

    def asciiz(self, s: str) -> None:
        self.buf += s.encode("utf-8") + b"\x00"

    def align(self, n: int) -> None:
        while len(self.buf) % n != 0:
            self.buf.append(0)

    def _fixup(self, fn: Callable[[int], bytes], size: int) -> None:
        self.fixups.append((len(self.buf), fn))
        self.buf += b"\x00" * size

    # ------------------------------------------------------------------ T1 (16-bit)

    def movs_imm8(self, rd: int, imm8: int) -> None:
        # T1: 00100 Rd imm8
        _check(0 <= rd <= 7 and 0 <= imm8 <= 0xFF, "movs_imm8 bad operands")
        self._hw(0x2000 | (rd << 8) | imm8)

    def cmp_imm8(self, rn: int, imm8: int) -> None:
        # T2: 00101 Rn imm8
        _check(0 <= rn <= 7 and 0 <= imm8 <= 0xFF, "cmp_imm8 bad operands")
        self._hw(0x2800 | (rn << 8) | imm8)

    def add_sp_imm(self, rd: int, imm: int) -> None:
        # T1 add Rd, SP, #imm   (imm = imm8 * 4, range 0..1020)
        _check(0 <= rd <= 7, "add_sp_imm rd")
        _check(0 <= imm <= 1020 and (imm & 3) == 0, f"add_sp_imm bad imm {imm}")
        self._hw(0xA800 | (rd << 8) | (imm >> 2))

    def str_sp_imm(self, rt: int, imm: int) -> None:
        # T2 str Rt, [SP, #imm]   (imm8 * 4, range 0..1020)
        _check(0 <= rt <= 7, "str_sp_imm rt")
        _check(0 <= imm <= 1020 and (imm & 3) == 0, f"str_sp_imm bad imm {imm}")
        self._hw(0x9000 | (rt << 8) | (imm >> 2))

    def ldr_sp_imm(self, rt: int, imm: int) -> None:
        # T2 ldr Rt, [SP, #imm]   (imm8 * 4)
        _check(0 <= rt <= 7, "ldr_sp_imm rt")
        _check(0 <= imm <= 1020 and (imm & 3) == 0, f"ldr_sp_imm bad imm {imm}")
        self._hw(0x9800 | (rt << 8) | (imm >> 2))

    def mov_lo_lo(self, rd: int, rm: int) -> None:
        # T1 (mov Rd, Rm with low regs is encoded via T2 mov register form):
        # T2: 0100 0110 D Rm[3..0] Rd[2..0], where D is bit3 of Rd.
        _check(0 <= rd <= 15 and 0 <= rm <= 15, "mov_lo_lo")
        d = (rd >> 3) & 1
        self._hw(0x4600 | (d << 7) | (rm << 3) | (rd & 7))

    def bx(self, rm: int) -> None:
        # T1: 0100 0111 0 Rm[3..0] 000
        _check(0 <= rm <= 15, "bx")
        self._hw(0x4700 | (rm << 3))

    def svc(self, imm8: int) -> None:
        # T1: 1101 1111 imm8
        _check(0 <= imm8 <= 0xFF, "svc")
        self._hw(0xDF00 | imm8)

    def rev_lo_lo(self, rd: int, rm: int) -> None:
        # REV T1 (byte-reverse word): 1011 1010 00 Rm[2..0] Rd[2..0]
        _check(0 <= rd <= 7 and 0 <= rm <= 7, "rev_lo_lo bad regs")
        self._hw(0xBA00 | (rm << 3) | rd)

    def ldrb_reg(self, rt: int, rn: int, rm: int) -> None:
        # LDRB (register) T1: 0101 1100 Rm[2..0] Rn[2..0] Rt[2..0]
        # Loads Rt = byte at [Rn + Rm], zero-extended. Low regs only.
        _check(0 <= rt <= 7 and 0 <= rn <= 7 and 0 <= rm <= 7, "ldrb_reg bad regs")
        self._hw(0x5C00 | (rm << 6) | (rn << 3) | rt)

    def adds_lo_lo(self, rd: int, rn: int, rm: int) -> None:
        # ADDS T1 3-reg form: 0001 100 Rm[2..0] Rn[2..0] Rd[2..0]  (low regs only)
        _check(0 <= rd <= 7 and 0 <= rn <= 7 and 0 <= rm <= 7, "adds_lo_lo bad regs")
        self._hw(0x1800 | (rm << 6) | (rn << 3) | rd)

    def subs_lo_lo(self, rd: int, rn: int, rm: int) -> None:
        # SUBS T1 3-reg form: 0001 101 Rm[2..0] Rn[2..0] Rd[2..0]  (low regs only)
        _check(0 <= rd <= 7 and 0 <= rn <= 7 and 0 <= rm <= 7, "subs_lo_lo bad regs")
        self._hw(0x1A00 | (rm << 6) | (rn << 3) | rd)

    def muls_lo_lo(self, rdm: int, rn: int) -> None:
        # MULS T1: 0100 0011 01 Rn[2..0] Rdm[2..0]  (Rdm = Rdm * Rn; low regs only)
        _check(0 <= rdm <= 7 and 0 <= rn <= 7, "muls_lo_lo bad regs")
        self._hw(0x4340 | (rn << 3) | rdm)

    def lsrs_imm5(self, rd: int, rm: int, imm5: int) -> None:
        # LSR (immediate) T1: 00001 imm5 Rm[2..0] Rd[2..0]
        # imm5=0 encodes shift=32; imm5=1..31 = shift amount (low regs only).
        _check(0 <= rd <= 7 and 0 <= rm <= 7 and 0 <= imm5 <= 31, "lsrs_imm5 bad")
        self._hw(0x0800 | (imm5 << 6) | (rm << 3) | rd)

    def lsls_imm5(self, rd: int, rm: int, imm5: int) -> None:
        # LSL (immediate) T1: 00000 imm5 Rm[2..0] Rd[2..0]
        # imm5=1..31 = shift amount; imm5=0 is MOVS (just register copy).
        _check(0 <= rd <= 7 and 0 <= rm <= 7 and 0 <= imm5 <= 31, "lsls_imm5 bad")
        self._hw(0x0000 | (imm5 << 6) | (rm << 3) | rd)

    def bcond(self, cond: int, label: str) -> None:
        # T1: 1101 cond imm8 (range ±256 bytes from PC)
        _check(0 <= cond <= 14, "bcond cond")  # cond=14 unconditional reserved -> 15 = SVC
        cur = self.cur
        op = 0xD000 | (cond << 8)

        def emit(pc: int) -> bytes:
            target = self.labels[label]
            offset = target - (pc + 4)
            _check(-256 <= offset <= 254 and (offset & 1) == 0,
                   f"bcond out of range to {label}: offset {offset}")
            imm8 = (offset >> 1) & 0xFF
            inst = op | imm8
            return bytes([inst & 0xFF, (inst >> 8) & 0xFF])

        self._fixup(emit, 2)

    def beq(self, label: str) -> None: self.bcond(0, label)
    def bne(self, label: str) -> None: self.bcond(1, label)
    def blt(self, label: str) -> None: self.bcond(11, label)
    def bge(self, label: str) -> None: self.bcond(10, label)
    def bgt(self, label: str) -> None: self.bcond(12, label)
    def ble(self, label: str) -> None: self.bcond(13, label)

    def _bcond_w(self, cond_skip: int, label: str) -> None:
        """Wide-range conditional via inverted-short + b.w. 6 bytes total."""
        skip = f"_bcw_{len(self.fixups)}_{len(self.buf)}"
        self.bcond(cond_skip, skip)
        self.b_w(label)
        self.label(skip)

    # Wide-range conditional branches. cond_skip = inverted condition for the
    # short-form skip. Range: ±1 MB (the underlying b.w T4).
    def beq_w(self, label: str) -> None: self._bcond_w(1, label)   # NE skips
    def bne_w(self, label: str) -> None: self._bcond_w(0, label)   # EQ skips
    def blt_w(self, label: str) -> None: self._bcond_w(10, label)  # GE skips
    def bge_w(self, label: str) -> None: self._bcond_w(11, label)  # LT skips
    def bgt_w(self, label: str) -> None: self._bcond_w(13, label)  # LE skips
    def ble_w(self, label: str) -> None: self._bcond_w(12, label)  # GT skips

    # ------------------------------------------------------------------ T3/T4 (32-bit)

    def ldrb_w(self, rt: int, rn: int, imm: int) -> None:
        # LDRB (immediate) T3: 1111 1000 1001 Rn / Rt imm12
        _check(0 <= rn <= 14 and 0 <= rt <= 14 and 0 <= imm <= 0xFFF,
               "ldrb_w bad operands")
        self._hw(0xF890 | rn)
        self._hw((rt << 12) | imm)

    def ldrh_w(self, rt: int, rn: int, imm: int) -> None:
        # LDRH (immediate) T2: 1111 1000 1011 Rn / Rt imm12
        _check(0 <= rn <= 14 and 0 <= rt <= 14 and 0 <= imm <= 0xFFF,
               "ldrh_w bad operands")
        self._hw(0xF8B0 | rn)
        self._hw((rt << 12) | imm)

    def ldr_w(self, rt: int, rn: int, imm: int) -> None:
        # LDR (immediate) T3: 1111 1000 1101 Rn / Rt imm12
        _check(0 <= rn <= 14 and 0 <= rt <= 14 and 0 <= imm <= 0xFFF,
               "ldr_w bad operands")
        self._hw(0xF8D0 | rn)
        self._hw((rt << 12) | imm)

    def ldr_lit_w(self, rt: int, label: str) -> None:
        """LDR (literal) T2: load Rt from align(PC, 4) + imm12 (or minus imm12).
        PC = inst_addr + 4. Used for fetching a 4-byte word literal from a
        named label elsewhere in the blob (e.g., a constant offset pool).
        """
        _check(0 <= rt <= 14, "ldr_lit_w rt")

        def emit(pc: int) -> bytes:
            target = self.labels[label]
            offset = target - ((pc + 4) & ~3)
            U = 1 if offset >= 0 else 0
            imm = abs(offset)
            _check(imm <= 0xFFF, f"ldr_lit_w out of range to {label}: {offset}")
            # Encoding: 11111000 U101 1111 Rt imm12
            hw1 = 0xF85F | (U << 7)
            hw2 = (rt << 12) | imm
            return bytes([hw1 & 0xFF, hw1 >> 8, hw2 & 0xFF, hw2 >> 8])

        self._fixup(emit, 4)

    def add_reg(self, rdn: int, rm: int) -> None:
        # ADD (register) T2: 0100 0100 DN Rm[3..0] Rdn[2..0]; DN = bit 3 of Rdn.
        # Rdn = Rdn + Rm; no flag change; any reg in {0..15}.
        _check(0 <= rdn <= 15 and 0 <= rm <= 15, "add_reg bad regs")
        d = (rdn >> 3) & 1
        self._hw(0x4400 | (d << 7) | (rm << 3) | (rdn & 7))

    def bhi(self, label: str) -> None: self.bcond(8, label)
    def bls(self, label: str) -> None: self.bcond(9, label)
    def bcs(self, label: str) -> None: self.bcond(2, label)
    def bcc(self, label: str) -> None: self.bcond(3, label)
    def bhs(self, label: str) -> None: self.bcond(2, label)  # alias for bcs
    def blo(self, label: str) -> None: self.bcond(3, label)  # alias for bcc

    def bhi_w(self, label: str) -> None: self._bcond_w(9, label)   # LS skips
    def bhs_w(self, label: str) -> None: self._bcond_w(3, label)   # LO skips

    def strb_w(self, rt: int, rn: int, imm: int) -> None:
        # STRB (immediate) T3: 1111 1000 1000 Rn / Rt imm12
        _check(0 <= rn <= 14 and 0 <= rt <= 14 and 0 <= imm <= 0xFFF,
               "strb_w bad operands")
        self._hw(0xF880 | rn)
        self._hw((rt << 12) | imm)

    def addw(self, rd: int, rn: int, imm12: int) -> None:
        # ADD (immediate) T4 (ADDW): raw 12-bit imm, no flags. Rn=15 → ADR (add).
        _check(0 <= rd <= 14 and 0 <= rn <= 15 and 0 <= imm12 <= 0xFFF,
               f"addw bad operands rd={rd} rn={rn} imm={imm12}")
        i = (imm12 >> 11) & 1
        imm3 = (imm12 >> 8) & 0x7
        imm8 = imm12 & 0xFF
        self._hw(0xF200 | (i << 10) | rn)
        self._hw((imm3 << 12) | (rd << 8) | imm8)

    def subw(self, rd: int, rn: int, imm12: int) -> None:
        # SUB (immediate) T4 (SUBW): raw 12-bit imm.
        _check(0 <= rd <= 14 and 0 <= rn <= 15 and 0 <= imm12 <= 0xFFF,
               f"subw bad operands rd={rd} rn={rn} imm={imm12}")
        i = (imm12 >> 11) & 1
        imm3 = (imm12 >> 8) & 0x7
        imm8 = imm12 & 0xFF
        self._hw(0xF2A0 | (i << 10) | rn)
        self._hw((imm3 << 12) | (rd << 8) | imm8)

    def add_imm_t3(self, rd: int, rn: int, imm12: int, set_flags: bool = False) -> None:
        """ADD (immediate) T3 — uses ThumbExpandImm encoding for small constants."""
        # Only call with values in {1, 2, 4, 8, 16, 32, ...} that work as i:imm3:imm8 directly.
        # For simplicity we only support imm12 < 256 (imm8 only).
        _check(0 <= imm12 <= 0xFF, "add_imm_t3 only supports imm < 256 here")
        s = 1 if set_flags else 0
        self._hw(0xF100 | (s << 4) | rn)
        self._hw((rd << 8) | imm12)

    def cmp_w(self, rn: int, rm: int) -> None:
        # CMP (register) T3: 1110 1011 1011 Rn / 0000 1111 0000 Rm
        _check(0 <= rn <= 14 and 0 <= rm <= 14, "cmp_w bad")
        self._hw(0xEBB0 | rn)
        self._hw(0x0F00 | rm)

    def movw(self, rd: int, imm16: int) -> None:
        # MOV (immediate) T3 (MOVW): 1111 0 i 100100 imm4 / 0 imm3 Rd imm8
        _check(0 <= rd <= 14 and 0 <= imm16 <= 0xFFFF, "movw bad")
        i = (imm16 >> 11) & 1
        imm4 = (imm16 >> 12) & 0xF
        imm3 = (imm16 >> 8) & 0x7
        imm8 = imm16 & 0xFF
        self._hw(0xF240 | (i << 10) | imm4)
        self._hw((imm3 << 12) | (rd << 8) | imm8)

    def movt(self, rd: int, imm16: int) -> None:
        # MOVT T1: 1111 0 i 101100 imm4 / 0 imm3 Rd imm8 — set upper halfword.
        _check(0 <= rd <= 14 and 0 <= imm16 <= 0xFFFF, "movt bad")
        i = (imm16 >> 11) & 1
        imm4 = (imm16 >> 12) & 0xF
        imm3 = (imm16 >> 8) & 0x7
        imm8 = imm16 & 0xFF
        self._hw(0xF2C0 | (i << 10) | imm4)
        self._hw((imm3 << 12) | (rd << 8) | imm8)

    def umull(self, rdlo: int, rdhi: int, rn: int, rm: int) -> None:
        # UMULL T1: 1111 1011 1010 Rn / RdLo RdHi 0000 Rm
        # RdHi:RdLo = (u32)Rn * (u32)Rm. RdLo != RdHi.
        _check(0 <= rdlo <= 12 and 0 <= rdhi <= 12 and 0 <= rn <= 12 and 0 <= rm <= 12,
               "umull bad regs")
        _check(rdlo != rdhi, "umull RdLo == RdHi")
        self._hw(0xFBA0 | rn)
        self._hw((rdlo << 12) | (rdhi << 8) | rm)

    def mvn_imm(self, rd: int, imm: int) -> None:
        # MVN (immediate) T1: 1111 0 i 0 0011 1 1111 / 0 imm3 Rd imm8
        # Used for mvn rd, #0 → rd = -1.
        _check(0 <= rd <= 14, "mvn_imm")
        # Use ThumbExpandImm for imm=0 → rd = ~0 = -1
        if imm == 0:
            self._hw(0xF06F)
            self._hw((rd << 8) | 0x00)
            return
        raise NotImplementedError("only mvn_imm rd, #0 supported")

    def adr_w(self, rd: int, label: str) -> None:
        """ADR (32-bit, ADD form): result = align(PC, 4) + imm12. PC = inst_addr + 4."""
        _check(0 <= rd <= 14, "adr_w rd")
        cur = self.cur

        def emit(pc: int) -> bytes:
            target = self.labels[label]
            offset = target - ((pc + 4) & ~3)
            if offset >= 0:
                _check(offset <= 0xFFF, f"adr_w out of range to {label}: {offset}")
                imm = offset
                # ADD form: 1111 0 i 10000 0 1111 / 0 imm3 Rd imm8
                i = (imm >> 11) & 1
                imm3 = (imm >> 8) & 0x7
                imm8 = imm & 0xFF
                hw1 = 0xF20F | (i << 10)
                hw2 = (imm3 << 12) | (rd << 8) | imm8
            else:
                imm = -offset
                _check(imm <= 0xFFF, f"adr_w out of range to {label}: {-imm}")
                # SUB form: 1111 0 i 10101 0 1111 / 0 imm3 Rd imm8
                i = (imm >> 11) & 1
                imm3 = (imm >> 8) & 0x7
                imm8 = imm & 0xFF
                hw1 = 0xF2AF | (i << 10)
                hw2 = (imm3 << 12) | (rd << 8) | imm8
            return bytes([hw1 & 0xFF, hw1 >> 8, hw2 & 0xFF, hw2 >> 8])

        self._fixup(emit, 4)

    def b_w(self, label: str) -> None:
        """B.W (T4): unconditional 32-bit branch, range ±16MB."""
        cur = self.cur

        def emit(pc: int) -> bytes:
            target = self.labels[label]
            offset = target - (pc + 4)
            return _encode_t4_branch(offset, kind="b")

        self._fixup(emit, 4)

    def bl_w(self, label: str) -> None:
        """BL (T1): 32-bit Thumb-mode call, range ±16MB."""
        cur = self.cur

        def emit(pc: int) -> bytes:
            target = self.labels[label]
            offset = target - (pc + 4)
            return _encode_t4_branch(offset, kind="bl")

        self._fixup(emit, 4)

    def blx_imm(self, target_addr: int) -> None:
        """BLX (immediate) T2: 32-bit call to ARM-mode target. target must be 4-aligned."""
        _check((target_addr & 3) == 0, f"blx target {target_addr:#x} not 4-aligned")
        cur = self.cur

        def emit(pc: int) -> bytes:
            pc_aligned = (pc + 4) & ~3
            offset = target_addr - pc_aligned
            return _encode_t4_branch(offset, kind="blx")

        self._fixup(emit, 4)

    # ------------------------------------------------------------------ resolution

    def resolve(self) -> bytes:
        for off, emit in self.fixups:
            pc = self.base + off
            chunk = emit(pc)
            self.buf[off:off + len(chunk)] = chunk
        return bytes(self.buf)


def _encode_t4_branch(offset: int, kind: str) -> bytes:
    """Encode a T4 branch given a signed offset from current PC.

    kind: "b"  → B.W   (uncond 32-bit branch)
          "bl" → BL    (32-bit branch w/ link, Thumb target)
          "blx"→ BLX   (32-bit branch w/ link to ARM target — last bit of imm
                        forced to 0)
    """
    _check(-(1 << 24) <= offset < (1 << 24),
           f"branch offset {offset:#x} out of ±16MB range")
    if kind == "blx":
        _check((offset & 1) == 0, "blx imm offset must be even")
    if offset < 0:
        u = offset + (1 << 25)
    else:
        u = offset
    s = (u >> 24) & 1
    i1 = (u >> 23) & 1
    i2 = (u >> 22) & 1
    j1 = (1 - i1) ^ s
    j2 = (1 - i2) ^ s
    if kind == "blx":
        imm10h = (u >> 12) & 0x3FF
        imm10l = (u >> 2) & 0x3FF
        hw1 = 0xF000 | (s << 10) | imm10h
        hw2 = 0xC000 | (j1 << 13) | (j2 << 11) | (imm10l << 1)
    else:
        imm10 = (u >> 12) & 0x3FF
        imm11 = (u >> 1) & 0x7FF
        hw1 = 0xF000 | (s << 10) | imm10
        if kind == "bl":
            hw2 = 0xD000 | (j1 << 13) | (j2 << 11) | imm11
        else:  # b
            hw2 = 0x9000 | (j1 << 13) | (j2 << 11) | imm11
    return bytes([hw1 & 0xFF, hw1 >> 8, hw2 & 0xFF, hw2 >> 8])


# Quick self-test against known-good encodings from the existing patcher.
if __name__ == "__main__":
    # `b.w 0x712a` from instruction at 0x72f0 → bytes FF F7 1B BF
    a = Asm(0x72f0)
    a.label("epilogue_target")
    a.labels["dst"] = 0x712a
    a.b_w("dst")
    out = a.resolve()
    assert out == bytes.fromhex("FFF71BBF"), out.hex()
    # `add.w r0, r5, #8` → 05 F1 08 00
    a = Asm(0)
    a.add_imm_t3(0, 5, 8)
    assert a.resolve() == bytes.fromhex("05F10800"), a.resolve().hex()
    # `ldrb.w r0, [sp, #382]` → 9D F8 7E 01
    a = Asm(0)
    a.ldrb_w(0, 13, 382)
    assert a.resolve() == bytes.fromhex("9DF87E01"), a.resolve().hex()
    # `subw sp, sp, #784` → AD F2 10 3D (LE for hw1=0xF2AD hw2=0x3D10).
    a = Asm(0)
    a.subw(13, 13, 784)
    assert a.resolve() == bytes.fromhex("ADF2103D"), a.resolve().hex()
    # `movw r2, #0x308` (776) → 40 F2 08 32
    a = Asm(0)
    a.movw(2, 776)
    assert a.resolve() == bytes.fromhex("40F20832"), a.resolve().hex()
    print("Self-tests passed.")
