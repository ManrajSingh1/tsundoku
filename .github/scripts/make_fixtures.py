#!/usr/bin/env python3
"""Builds small, valid sample books for the CI smoke test.

Usage: make_fixtures.py OUTPUT_DIR

Writes a manga volume with ComicInfo.xml, a comic with none, an exact copy of the first
(to show a duplicate being recognised), an EPUB and a PDF. They exist so no binary files
are checked into the repository. They are tiny and synthetic: they prove the import path
works, not that it copes with a large real library.
"""
import os
import struct
import sys
import zipfile
import zlib


def png(width, height, top, bottom, band):
    """A vertical gradient from `top` to `bottom` with a contrasting band across it."""
    rows = bytearray()
    for y in range(height):
        t = y / max(1, height - 1)
        colour = bytes(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        if height * 0.62 <= y < height * 0.74:
            colour = bytes(band)
        rows.append(0)  # filter type: none
        rows.extend(colour * width)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)  # 8-bit RGB
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(rows), 6)) + chunk(b"IEND", b"")


def cbz(path, pages, palette, comic_info=None):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for page in range(1, pages + 1):
            shade = 30 * page
            top = tuple(min(255, c + shade) for c in palette[0])
            archive.writestr(f"page_{page:03d}.png", png(600, 900, top, palette[1], palette[2]))
        if comic_info:
            archive.writestr("ComicInfo.xml", comic_info)


def epub(path, title, author):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        # The EPUB container format requires this entry first and uncompressed.
        archive.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        archive.writestr(
            "META-INF/container.xml",
            '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
            '<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
            "</rootfiles></container>",
        )
        archive.writestr(
            "OEBPS/content.opf",
            f"""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">urn:uuid:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee</dc:identifier>
    <dc:title>{title}</dc:title>
    <dc:creator>{author}</dc:creator>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>""",
        )
        archive.writestr(
            "OEBPS/nav.xhtml",
            '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
            'xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body>'
            '<nav epub:type="toc"><ol><li><a href="chapter1.xhtml">Chapter 1</a></li></ol></nav></body></html>',
        )
        archive.writestr(
            "OEBPS/chapter1.xhtml",
            '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head>'
            "<title>Chapter 1</title></head><body><h1>Chapter 1</h1>"
            + "<p>It was a quiet night on the shelf, and nobody was reading.</p>" * 40
            + "</body></html>",
        )
        archive.writestr("OEBPS/cover.png", png(600, 900, (150, 60, 45), (60, 20, 20), (240, 225, 200)))


def pdf(path, pages):
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
        lines = [f"Quarterly report, page {page} of {pages}"]
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
    with open(path, "wb") as handle:
        handle.write(out)


MANGA_COMIC_INFO = (
    '<?xml version="1.0" encoding="utf-8"?><ComicInfo><Title>The Third Volume</Title>'
    "<Series>Test Manga</Series><Number>3</Number><Writer>A. Author</Writer>"
    "<Manga>YesAndRightToLeft</Manga></ComicInfo>"
)

if __name__ == "__main__":
    target = sys.argv[1]
    os.makedirs(target, exist_ok=True)
    manga = os.path.join(target, "Test Manga v03 (Digital).cbz")
    cbz(manga, 6, ((20, 60, 110), (10, 20, 50), (235, 200, 80)), MANGA_COMIC_INFO)
    with open(manga, "rb") as source, open(os.path.join(target, "Test Manga v03 copy.cbz"), "wb") as copy:
        copy.write(source.read())
    cbz(os.path.join(target, "Quiet Shelf v07 [Scans].cbz"), 4, ((40, 110, 70), (10, 40, 30), (240, 240, 230)))
    epub(os.path.join(target, "download-4812.epub"), "A Test Novel", "Nora Novelist")
    pdf(os.path.join(target, "Quarterly report.pdf"), 40)
    for name in sorted(os.listdir(target)):
        print(f"{os.path.getsize(os.path.join(target, name)):>9}  {name}")
