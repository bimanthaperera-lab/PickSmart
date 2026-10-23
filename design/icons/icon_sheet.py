"""Draws every res/drawable/ic_*.xml on one HTML page to compare them. Usage: python icon_sheet.py <res/drawable dir> <out.html>"""
import sys, re, glob, os, html
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
src, out = sys.argv[1], sys.argv[2]
cells=[]
for f in sorted(glob.glob(os.path.join(src,'ic_*.xml'))):
    name=os.path.basename(f)[:-4]
    if 'launcher' in name: continue
    r=ET.parse(f).getroot()
    vw,vh=r.get(A+'viewportWidth'),r.get(A+'viewportHeight')
    parts=[]
    for el in r.iter():
        if el.tag=='path':
            d=el.get(A+'pathData')
            sw=el.get(A+'strokeWidth'); sc=el.get(A+'strokeColor'); fc=el.get(A+'fillColor')
            cap=el.get(A+'strokeLineCap','butt'); join=el.get(A+'strokeLineJoin','miter')
            fill='currentColor' if fc else 'none'
            stroke='currentColor' if sc else 'none'
            rule='evenodd' if el.get(A+'fillType')=='evenOdd' else 'nonzero'
            parts.append(f'<path d="{d}" fill-rule="{rule}" fill="{fill}" stroke="{stroke}" stroke-width="{sw or 0}" stroke-linecap="{cap}" stroke-linejoin="{join}"/>')
    svg=f'<svg viewBox="0 0 {vw} {vh}" width="72" height="72">{"".join(parts)}</svg>'
    small=f'<svg viewBox="0 0 {vw} {vh}" width="24" height="24">{"".join(parts)}</svg>'
    cells.append(f'<div class="c">{svg}{small}<span>{name[3:]}</span></div>')
open(out,'w').write('<!doctype html><meta charset=utf-8><style>body{background:#0E1113;color:#EDEDED;font:12px sans-serif;margin:16px}.g{display:grid;grid-template-columns:repeat(9,1fr);gap:12px}.c{display:flex;flex-direction:column;align-items:center;gap:6px;padding:10px;background:#1C2022;border-radius:12px}.c svg:first-child{color:#FBBB23}</style><div class=g>'+''.join(cells)+'</div>')
print(len(cells))
