"""Writes Picksmart's hand-drawn 24-unit icons. Usage: python make_icons.py <res/drawable dir>"""
import sys, os

OUT = sys.argv[1]
STROKE = "stroke"   # 2-unit outline, round caps and joins
FILL = "fill"       # solid fill (with evenOdd holes)
BOTH = "both"       # filled and outlined, so it matches an outline's outer edge

def circle(cx, cy, r):
    return f"M{cx},{cy - r}A{r},{r} 0,1 1,{cx},{cy + r}A{r},{r} 0,1 1,{cx},{cy - r}Z"

def rrect(x0, y0, x1, y1, r):
    return (f"M{x0 + r},{y0}H{x1 - r}A{r},{r} 0,0 1,{x1},{y0 + r}V{y1 - r}A{r},{r} 0,0 1,{x1 - r},{y1}"
            f"H{x0 + r}A{r},{r} 0,0 1,{x0},{y1 - r}V{y0 + r}A{r},{r} 0,0 1,{x0 + r},{y0}Z")

def path(d, kind):
    if kind == STROKE:
        return (f'    <path\n        android:pathData="{d}"\n        android:strokeWidth="2"\n'
                f'        android:strokeColor="#FF000000"\n        android:strokeLineCap="round"\n'
                f'        android:strokeLineJoin="round" />')
    if kind == FILL:
        return (f'    <path\n        android:pathData="{d}"\n        android:fillColor="#FF000000"\n'
                f'        android:fillType="evenOdd" />')
    return (f'    <path\n        android:pathData="{d}"\n        android:fillColor="#FF000000"\n'
            f'        android:strokeWidth="2"\n        android:strokeColor="#FF000000"\n'
            f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />')

def write(name, comment, *paths):
    body = "\n".join(path(d, k) for d, k in paths)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f'<!-- {comment} -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp"\n    android:height="24dp"\n'
           '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
           f'{body}\n</vector>\n')
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)

OUTLINE = "Outline icon on a 24 grid, 2-unit stroke. Tinted by Compose."
FILLED = "Filled icon on a 24 grid, for the selected bottom-bar tab. Tinted by Compose."

# --- Vehicles -------------------------------------------------------------------------------
write("ic_vehicle_motorcycle", OUTLINE + " A scooter, the most common bike here.",
      (circle(6, 17, 2.5), STROKE),
      (circle(18, 17, 2.5), STROKE),
      # Rear body and seat, floor, front shield up to the handlebar.
      ("M3.5,14.5C3.5,12.6 5,11 7,11H11.5", STROKE),
      ("M8.5,17H12.5C14.5,17 15.5,15.5 15.5,13.5V9.5L14.5,5H16.5", STROKE),
      ("M15.5,11.5L18,14.5", STROKE))
write("ic_vehicle_three_wheeler",
      "Solid icon on a 24 grid: a three-wheeler (tuk-tuk) from the side, facing left, traced from "
      "the owner's reference: roof over the front, narrow front cowl, open door with the seat, "
      "rear window. Tinted by Compose.",
      # Body: roof, rounded back, rear wheel arch, floor, front wheel arch, front cowl up to the
      # windscreen, then the roof's front edge back up.
      ("M3,4H18.5C20,4 21,5.2 21,6.8V16.6C21,17.1 20.6,17.5 20.1,17.5"
       "A2.6,2.6 0,0 0,14.9,17.5H6.7A2.3,2.3 0,0 0,2.1,17.5"
       "L2.3,15.5C2.4,14.4 2.9,13.4 3.6,12.6L5.2,10.2L2.6,4.6C2.5,4.2 2.7,4 3,4Z"
       # Windscreen, door opening, rear window and the stripe on the back, cut out.
       "M4.9,6.1H8.6V9.6H6.5Z"
       "M10,6.1H15V15.6H10Z"
       + rrect(16.4, 6.1, 19.3, 9.8, 0.5) +
       "M16.4,12.3H19.3V13H16.4Z", FILL),
      # The seat back and cushion, seen through the door.
      ("M11.2,9.6H12.6V13.9H15V15.6H11.2Z", FILL),
      # Rear and front wheels, each with its hub cut out.
      (circle(17.5, 18.9, 1.9) + circle(17.5, 18.9, 0.7) + circle(4.4, 18.9, 1.7) + circle(4.4, 18.9, 0.6), FILL))
write("ic_vehicle_car", OUTLINE + " A car, from the side.",
      (circle(7, 16.5, 2), STROKE),
      (circle(17, 16.5, 2), STROKE),
      ("M5,16.5H3.5C3.2,16.5 3,16.3 3,16V12.5L5.1,8C5.4,7.4 6.1,7 6.8,7H14.6C15.3,7 16,7.4 16.4,8L18.5,11H20C20.6,11 21,11.4 21,12V16C21,16.3 20.8,16.5 20.5,16.5H19", STROKE),
      ("M9,16.5H15", STROKE),
      ("M3.5,11H18.5", STROKE),
      ("M11,7V11", STROKE))
write("ic_vehicle_other", OUTLINE + " A small van, for any other vehicle.",
      (circle(7, 17.5, 2), STROKE),
      (circle(17, 17.5, 2), STROKE),
      ("M5,17.5H3.5C3.2,17.5 3,17.3 3,17V6C3,5.4 3.4,5 4,5H14V17.5", STROKE),
      ("M14,9H17.6C18,9 18.4,9.2 18.6,9.6L20.8,13.4C20.9,13.6 21,13.8 21,14V17C21,17.3 20.8,17.5 20.5,17.5H19", STROKE),
      ("M9,17.5H15", STROKE))

# --- Small helpers for rates and notes -------------------------------------------------------
write("ic_gauge", OUTLINE + " A speedometer, for fuel efficiency (km per litre).",
      ("M4.6,18A8.5,8.5 0,1 1,19.4 18", STROKE),
      ("M12,14.5L15.5,10", STROKE),
      ("M12,14.5h0.01", STROKE))
write("ic_edit", OUTLINE + " A pencil, for Change / Edit.",
      ("M4,20H8L18.6,9.4C19.4,8.6 19.4,7.4 18.6,6.6L17.4,5.4C16.6,4.6 15.4,4.6 14.6,5.4L4,16Z", STROKE),
      ("M13,7L17,11", STROKE))
write("ic_shield", OUTLINE + " A shield with a tick, for the safety note.",
      ("M12,3L19,6V11.2C19,15.5 16.1,19.2 12,20.8C7.9,19.2 5,15.5 5,11.2V6Z", STROKE),
      ("M9,12L11,14L15,10", STROKE))

# --- Redrawn icons -----------------------------------------------------------------------------
write("ic_nav_history", OUTLINE + " A clock with a turn-back arrow.",
      ("M3.5,12A8.5,8.5 0,1 0,6 6L3.5,8.5", STROKE),
      ("M3.5,4.5V8.5H7.5", STROKE),
      ("M12,8V12L14.5,14.5", STROKE))
write("ic_toll", OUTLINE + " A toll booth with its barrier.",
      ("M3,21H21", STROKE),
      ("M5,21V9C5,8.4 5.4,8 6,8H10C10.6,8 11,8.4 11,9V21", STROKE),
      ("M7.5,11H8.5", STROKE),
      ("M11,12.5H20C20.6,12.5 21,12.9 21,13.5V14.5C21,15.1 20.6,15.5 20,15.5H11", STROKE),
      ("M15,12.5L14,15.5M18.5,12.5L17.5,15.5", STROKE))

# --- Filled bottom-bar icons for the selected tab ---------------------------------------------
write("ic_nav_home_filled", FILLED,
      ("M4,10.5L12,4L20,10.5V20H14.5V14.5H9.5V20H4Z", BOTH))
write("ic_nav_account_filled", FILLED,
      (circle(12, 8, 5), FILL),
      ("M3,21V20C3,16.1 7.1,14 12,14C16.9,14 21,16.1 21,20V21Z", FILL))
dots = "".join(circle(x, y, 1.25) for y in (13, 16.5) for x in (8.5, 12, 15.5))
write("ic_nav_check_ride_filled", FILLED,
      (rrect(4, 2, 20, 22, 3) + rrect(8.5, 6.5, 15.5, 9.5, 0.75) + dots, FILL))
write("ic_nav_history_filled", FILLED,
      ("M3.5,12A8.5,8.5 0,1 0,6 6L3.5,8.5", STROKE),
      ("M3.5,4.5V8.5H7.5", STROKE),
      # The face, solid, with the hands cut out.
      (circle(12, 12, 7.6) + "M11.1,7.6H12.9V11.6L15.3,14L14,15.3L11.1,12.4Z", FILL))
write("ic_nav_reports_filled", FILLED,
      ("M4,4V20H20", STROKE),
      (rrect(7, 11, 10, 17, 1) + rrect(11.25, 7, 14.25, 17, 1) + rrect(15.5, 10, 18.5, 17, 1), FILL))
print("done")
