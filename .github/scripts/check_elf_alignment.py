#!/usr/bin/env python3
"""Reports whether the 64-bit native libraries in an APK can load on 16 KB page devices.

Usage: check_elf_alignment.py APK [--enforce]

`zipalign -c -P 16` only proves the libraries sit at 16 KB offsets inside the APK. A library
also has to be linked for 16 KB pages: every loadable segment needs an alignment of at
least 16384. This reads that straight from each library's ELF program headers.

Only 64-bit ABIs are checked; 32-bit devices do not use 16 KB pages. Without --enforce the
script always exits 0, so the spike can still ship a library that fails and report it.
"""
import struct
import sys
import zipfile

PAGE_16K = 16384
PT_LOAD = 1
ABIS_64 = ("lib/arm64-v8a/", "lib/x86_64/")


def load_alignments(data: bytes):
    if data[:4] != b"\x7fELF" or data[4] != 2:  # not ELF, or not 64-bit
        return None
    endian = "<" if data[5] == 1 else ">"
    (phoff,) = struct.unpack_from(endian + "Q", data, 0x20)
    phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x36)
    alignments = []
    for index in range(phnum):
        base = phoff + index * phentsize
        (p_type,) = struct.unpack_from(endian + "I", data, base)
        if p_type == PT_LOAD:
            (p_align,) = struct.unpack_from(endian + "Q", data, base + 48)
            alignments.append(p_align)
    return alignments


def main() -> int:
    apk = sys.argv[1]
    enforce = "--enforce" in sys.argv[2:]
    failures = 0
    checked = 0
    with zipfile.ZipFile(apk) as archive:
        for name in sorted(archive.namelist()):
            if not name.endswith(".so") or not name.startswith(ABIS_64):
                continue
            alignments = load_alignments(archive.read(name))
            if alignments is None:
                continue
            checked += 1
            smallest = min(alignments) if alignments else 0
            ok = smallest >= PAGE_16K
            failures += 0 if ok else 1
            print(f"{'OK  ' if ok else 'FAIL'} {name}: smallest LOAD alignment {smallest}")
    if checked == 0:
        print("No 64-bit native libraries in this APK.")
    elif failures:
        print(f"{failures} of {checked} libraries are not linked for 16 KB pages.")
    else:
        print(f"All {checked} libraries are linked for 16 KB pages.")
    return 1 if (failures and enforce) else 0


if __name__ == "__main__":
    sys.exit(main())
