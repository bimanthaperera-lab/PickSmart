# Brand assets

`picksmart-logo.jpeg` is the owner-supplied logo: the pin mark above the "Picksmart" wordmark on a dark background.

The app's vector drawables are traced from it, so they stay sharp at every size:

| Drawable | Use |
| --- | --- |
| `ic_launcher_foreground` | Adaptive launcher icon (pin only, inside the 66 dp safe zone) |
| `ic_launcher_monochrome` | Themed icon on Android 13+ (single colour) |
| `picksmart_mark` | Pin mark alone, small sizes on dark surfaces |
| `picksmart_logo` | Full logo, splash and onboarding on dark surfaces only |

The "Pick" part of the wordmark and the white P are off-white, so they disappear on a light or yellow surface. There, put the mark inside a near-black circle (CLAUDE.md section 9).

## Regenerating

If the owner supplies a new logo (ideally an SVG, which would replace tracing altogether):

```text
pip install potracer pillow numpy
python -I design/brand/trace_logo.py design/brand/picksmart-logo.jpeg <scratch_dir>
python -I design/brand/make_drawables.py <scratch_dir> app/src/main/res/drawable
```

`trace_logo.py` also writes `preview_mark.png` and `preview_word.png` into the scratch folder. Compare them with the source before committing.
