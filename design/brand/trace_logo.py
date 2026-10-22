"""Trace picksmart-logo.jpeg into Android VectorDrawable path data.

Usage: python trace_logo.py <logo.jpeg> <out_dir>
Writes mark_yellow.txt, mark_white.txt, word_yellow.txt, word_white.txt (pathData, in source px
coordinates) plus bboxes.json, and preview PNGs rasterised from the traced paths for checking.
"""
import json
import sys
from pathlib import Path

import numpy as np
import potrace
from PIL import Image, ImageDraw

SCALE = 3  # upscale before thresholding so edges trace smoothly

src, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)

im = Image.open(src).convert("RGB")
big = im.resize((im.width * SCALE, im.height * SCALE), Image.BICUBIC)
a = np.asarray(big).astype(int)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
yellow = (r > 150) & (g > 100) & (b < 110) & (r - b > 90)
white = (r > 170) & (g > 170) & (b > 170) & (np.max(a, -1) - np.min(a, -1) < 45)

# Split mark from wordmark by the empty band of rows between them.
ink_rows = np.where((yellow | white).any(axis=1))[0]
gaps = np.where(np.diff(ink_rows) > 10 * SCALE)[0]
assert len(gaps) == 1, f"expected one gap between mark and wordmark, got {len(gaps)}"
split = (ink_rows[gaps[0]] + ink_rows[gaps[0] + 1]) // 2


def bbox(mask):
    ys, xs = np.where(mask)
    return [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]


def trace(mask):
    bm = potrace.Bitmap(~mask)  # potracer traces the False pixels
    plist = bm.trace(turdsize=40 * SCALE, alphamax=1.0, opticurve=True, opttolerance=0.2)
    f = lambda p: f"{p.x / SCALE:.2f},{p.y / SCALE:.2f}"
    parts, polys = [], []
    for curve in plist:
        sp = curve.start_point
        d = [f"M{f(sp)}"]
        poly, cur = [(sp.x, sp.y)], (sp.x, sp.y)
        for seg in curve.segments:
            if seg.is_corner:
                d.append(f"L{f(seg.c)}L{f(seg.end_point)}")
                poly += [(seg.c.x, seg.c.y), (seg.end_point.x, seg.end_point.y)]
            else:
                d.append(f"C{f(seg.c1)} {f(seg.c2)} {f(seg.end_point)}")
                for t in np.linspace(0, 1, 12)[1:]:
                    mt = 1 - t
                    pts = [cur, (seg.c1.x, seg.c1.y), (seg.c2.x, seg.c2.y), (seg.end_point.x, seg.end_point.y)]
                    poly.append(tuple(mt**3 * pts[0][i] + 3 * mt**2 * t * pts[1][i] + 3 * mt * t**2 * pts[2][i] + t**3 * pts[3][i] for i in range(2)))
            cur = (seg.end_point.x, seg.end_point.y)
        d.append("Z")
        parts.append("".join(d))
        polys.append(poly)
    return "".join(parts), polys


def raster(polys, size):
    """Even-odd fill: XOR every contour."""
    acc = np.zeros(size[::-1], bool)
    for poly in polys:
        layer = Image.new("1", size, 0)
        ImageDraw.Draw(layer).polygon([(x, y) for x, y in poly], fill=1)
        acc ^= np.asarray(layer, bool)
    return acc


boxes = {}
for region, rows in (("mark", slice(0, split)), ("word", slice(split, None))):
    region_masks = {}
    for colour, mask in (("yellow", yellow), ("white", white)):
        m = np.zeros_like(mask)
        m[rows] = mask[rows]
        region_masks[colour] = m
        d, polys = trace(m)
        (out / f"{region}_{colour}.txt").write_text(d)
        region_masks[colour + "_polys"] = polys
    union = region_masks["yellow"] | region_masks["white"]
    boxes[region] = [v / SCALE for v in bbox(union)]

    # Preview from the traced curves, to compare against the source.
    size = (big.width, big.height)
    prev = np.zeros((size[1], size[0], 3), np.uint8)
    prev[:] = (11, 13, 15)
    prev[raster(region_masks["yellow_polys"], size)] = (251, 187, 35)
    prev[raster(region_masks["white_polys"], size)] = (245, 245, 242)
    x0, y0, x1, y1 = bbox(union)
    pad = 20 * SCALE
    Image.fromarray(prev[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad]).save(out / f"preview_{region}.png")

(out / "bboxes.json").write_text(json.dumps(boxes, indent=1))
print(json.dumps(boxes))
for p in sorted(out.glob("*.txt")):
    print(p.name, len(p.read_text()), "chars,", p.read_text().count("M"), "contours")
