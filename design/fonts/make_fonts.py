"""Cuts Google Sans Flex (variable) into the four static Latin weights the app uses.

Usage: python -I make_fonts.py <variable.ttf> <res/font dir>
Axes are pinned to their defaults except weight, so the result looks like plain Google Sans Flex.
"""
import sys
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

src, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
out_dir.mkdir(parents=True, exist_ok=True)

probe = TTFont(src)
axes = {a.axisTag: (a.minValue, a.defaultValue, a.maxValue) for a in probe["fvar"].axes}
print("axes:", axes)

# Basic Latin, Latin-1 (includes NBSP and the middle dot), Latin Extended-A, general punctuation
# (curly quotes, dashes, ellipsis), currency symbols, arrows (→ in strings) and the minus sign.
UNICODES = [*range(0x20, 0x7F), *range(0xA0, 0x180), *range(0x2000, 0x2070), *range(0x20A0, 0x20C1),
            *range(0x2190, 0x2200), 0x2212, 0xFFFD]

WEIGHTS = {"regular": 400, "medium": 500, "semibold": 600, "bold": 700}
for name, wght in WEIGHTS.items():
    font = TTFont(src)
    pins = {tag: default for tag, (_, default, _) in axes.items()}
    pins["wght"] = wght
    static = instantiateVariableFont(font, pins, inplace=False)
    options = Options()
    options.layout_features = ["*"]  # keep kerning, ligatures and tabular figures
    options.name_IDs = ["*"]
    options.notdef_outline = True
    subsetter = Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(static)
    target = out_dir / f"google_sans_flex_{name}.ttf"
    static.save(target)
    print(f"{target.name}: {target.stat().st_size:,} bytes")
