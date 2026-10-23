# App font: Google Sans Flex

The app's text uses **Google Sans Flex**, the open version of Google Sans, released by Google under the SIL Open Font License 1.1 (`OFL.txt`). It has no Reserved Font Name. The names "Google", "Google Sans" and "Google Sans Flex" are Google trademarks: never use them in the app's name, store listing or marketing, or in a way that suggests Google endorses Picksmart.

## Source

- File: `GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf` (3,910,784 bytes), downloaded 2026-10-23 from `github.com/google/fonts`, folder `ofl/googlesansflex`.
- The original variable file is not committed; download it again from the same place to regenerate.

## What ships in the app

`make_fonts.py` cuts the variable font into four static weights in `app/src/main/res/font/` (about 88 KB each instead of 3.9 MB):

| File | Weight |
| --- | --- |
| `google_sans_flex_regular.ttf` | 400 |
| `google_sans_flex_medium.ttf` | 500 |
| `google_sans_flex_semibold.ttf` | 600 |
| `google_sans_flex_bold.ttf` | 700 |

Other axes stay at their defaults (optical size 18, width 100, no rounding, no slant). Only Latin text is kept: Basic Latin, Latin-1, Latin Extended-A, general punctuation, currency symbols and arrows. The font has no "→" glyph, so Android draws that character with the system font. Sinhala and Tamil text also falls back to the system font for those scripts.

The licence ships inside the app at `app/src/main/assets/licenses/google_sans_flex_OFL.txt`, as the OFL requires.

## Regenerate

```bash
pip install --user fonttools
python -P make_fonts.py "GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf" ../../app/src/main/res/font
```

Then rebuild and check the screens at 100% and 200% font size.
