# App icons

The app's icons are outline vectors on a 24-unit grid with a 2-unit stroke and round caps and joins, tinted by Compose (CLAUDE.md section 13a). Most were written by hand in `app/src/main/res/drawable/ic_*.xml`.

`make_icons.py` writes the ones added on 2026-10-23: the vehicle types (`ic_vehicle_*`), `ic_gauge`, `ic_edit`, `ic_shield`, the redrawn `ic_nav_history` and `ic_toll`, and the filled bottom-bar icons for the selected tab (`ic_nav_*_filled`). Change them there and run it again rather than editing the XML:

```bash
python design/icons/make_icons.py app/src/main/res/drawable
```

`icon_sheet.py` draws every `ic_*.xml` on one HTML page, at large and real size, to check that a new icon matches the set:

```bash
python design/icons/icon_sheet.py app/src/main/res/drawable icons.html
```
