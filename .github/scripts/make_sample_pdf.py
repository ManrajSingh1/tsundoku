#!/usr/bin/env python3
"""Writes a plain multi-page text PDF for the smoke test.

Usage: make_sample_pdf.py OUTPUT [PAGES]

It exists so CI has a file to open in both PDF engines without checking a binary into the
repository. It is small and text-only, so it says nothing about how either engine copes
with a large scan; that needs real files on a real phone.
"""
import sys


def build(pages: int) -> bytes:
    objects = {
        1: "<< /Type /Catalog /Pages 2 0 R >>",
        3: "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    }
    kids = []
    number = 3
    for page in range(1, pages + 1):
        page_id, content_id = number + 1, number + 2
        number += 2
        kids.append(f"{page_id} 0 R")
        lines = [f"TSUNDOKU sample PDF, page {page} of {pages}"]
        lines += [f"Line {n}: the quick brown fox jumps over the lazy dog." for n in range(1, 41)]
        stream = "BT /F1 14 Tf 50 790 Td 18 TL " + " ".join(f"({line}) Tj T*" for line in lines) + " ET"
        objects[page_id] = (
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_id} 0 R >>"
        )
        objects[content_id] = f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream"
    objects[2] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {pages} >>"

    out = bytearray(b"%PDF-1.4\n")
    offsets = {}
    for num in sorted(objects):
        offsets[num] = len(out)
        out += f"{num} 0 obj\n{objects[num]}\nendobj\n".encode("latin-1")
    xref_at = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode("latin-1")
    out += b"0000000000 65535 f \n"
    for num in sorted(objects):
        out += f"{offsets[num]:010d} 00000 n \n".encode("latin-1")
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF\n".encode("latin-1")
    return bytes(out)


if __name__ == "__main__":
    target = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 40
    with open(target, "wb") as handle:
        handle.write(build(count))
    print(f"Wrote {target}: {count} pages")
