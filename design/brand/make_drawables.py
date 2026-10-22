"""Build Android VectorDrawables from the traced logo paths.

Usage: python make_drawables.py <trace_dir> <res_drawable_dir>
"""
import json
import re
import sys
from pathlib import Path

trace, res = Path(sys.argv[1]), Path(sys.argv[2])
boxes = json.loads((trace / "bboxes.json").read_text())
p = {k: re.sub(r"(\d+\.\d)\d+", r"\1", (trace / f"{k}.txt").read_text())
     for k in ("mark_yellow", "mark_white", "word_yellow", "word_white")}

YELLOW, OFF_WHITE, MONO = "@color/brand_yellow", "@color/off_white", "#FF000000"
HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
NOTE = "<!-- Generated from design/brand/picksmart-logo.jpeg (see design/brand/README.md). Do not edit by hand. -->\n"


def paths(keys_colours, indent="        "):
    return "".join(
        f'{indent}<path\n{indent}    android:fillColor="{c}"\n{indent}    android:fillType="evenOdd"\n'
        f'{indent}    android:pathData="{p[k]}" />\n'
        for k, c in keys_colours
    )


def vector(width_dp, height_dp, vw, vh, tx, ty, s, body):
    return (
        HEADER + NOTE
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width_dp}dp"\n    android:height="{height_dp}dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n'
        f'    <group\n        android:scaleX="{s:.5f}"\n        android:scaleY="{s:.5f}"\n'
        f'        android:translateX="{tx:.3f}"\n        android:translateY="{ty:.3f}">\n'
        + body
        + "    </group>\n</vector>\n"
    )


mx0, my0, mx1, my1 = boxes["mark"]
mw, mh = mx1 - mx0, my1 - my0

# Launcher foreground: 108 x 108 canvas, pin centred and kept well inside the 66 dp safe circle.
MARK_H = 58
s = MARK_H / mh
tx, ty = 54 - s * (mx0 + mw / 2), 54 - s * (my0 + mh / 2)
mark_colour = [("mark_yellow", YELLOW), ("mark_white", OFF_WHITE)]
(res / "ic_launcher_foreground.xml").write_text(vector(108, 108, 108, 108, tx, ty, s, paths(mark_colour)))
# Themed icon: one colour; the dark gaps in the mark keep the white P readable.
mono = [("mark_yellow", MONO), ("mark_white", MONO)]
(res / "ic_launcher_monochrome.xml").write_text(vector(108, 108, 108, 108, tx, ty, s, paths(mono)))

# Pin mark alone, for small in-app use on dark surfaces.
vw, vh = round(mw, 1), round(mh, 1)
(res / "picksmart_mark.xml").write_text(vector(round(48 * mw / mh, 1), 48, vw, vh, -mx0, -my0, 1, paths(mark_colour)))

# Full logo (mark over wordmark), for splash and onboarding on dark surfaces.
wx0, wy0, wx1, wy1 = boxes["word"]
lx0, ly0, lx1, ly1 = min(mx0, wx0), my0, max(mx1, wx1), wy1
lw, lh = round(lx1 - lx0, 1), round(ly1 - ly0, 1)
full = mark_colour + [("word_yellow", YELLOW), ("word_white", OFF_WHITE)]
(res / "picksmart_logo.xml").write_text(vector(240, round(240 * lh / lw, 1), lw, lh, -lx0, -ly0, 1, paths(full)))

for f in ("ic_launcher_foreground", "ic_launcher_monochrome", "picksmart_mark", "picksmart_logo"):
    print(f, (res / f"{f}.xml").stat().st_size, "bytes")
