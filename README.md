# ✨ St. Anthony

Find what's lost. St. Anthony remembers where you left your things and guides
you back to them with a compass arrow, a pixie-dust trail in your camera, and a map.

This is the phase 1 prototype: a web app you can add to your phone's home
screen. No account, no server — your items stay on your device.

## What it does

| Tab | What you get |
| --- | --- |
| **Items** | Your belongings and when/where each was last seen. Tap 📍 → "It's right here" whenever you set something down, or pick the spot on the map. |
| **Compass** | A big arrow pointing at the item, distance, and "turn left 40°" prompts. Turn on **Buzz & beep guide** and you can use it face-down: sweep the phone around and it pulses when it points at the item. |
| **AR Trail** | Camera view with a trail of pixie dust on the ground leading to a glowing beacon over the item. |
| **Map** | You, all your items, the trail to the selected one, and the history of where it has been. |

**Follow walking paths** (off by default) bends the trail along real footpaths
using OpenStreetMap routing instead of a straight line. It sends your position
and the item's position to that routing service, so it asks first.

## Run it

It's plain HTML/JS with no build step:

```sh
python3 -m http.server 8000
# open http://localhost:8000/?demo
```

`?demo` adds sample items and a simulated compass, so you can try everything on
a laptop (drag the slider to turn; tap the map to walk).

**On a phone** the compass, GPS and camera only work over HTTPS. The easiest
route is GitHub Pages: repo Settings → Pages → deploy from the branch root,
then open the page on your phone and "Add to Home Screen".

## What to expect

- Phone GPS is good to about 5–10 m outdoors and much worse indoors. St. Anthony
  gets you to the right spot — the bench, the parking space, the right house —
  and then tells you to look around. Finding keys *under which cushion* needs the
  beacon or smart-glasses phases in [docs/DESIGN.md](docs/DESIGN.md).
- It only knows what you tell it. The habit is tapping "It's right here".
- Tested in a simulated phone browser only, not yet on real hardware. Compass
  behaviour varies by device; if the arrow is off, wave the phone in a figure-8
  to calibrate. Vibration works on Android only (iPhones get the beeps).
- Hold the phone upright in portrait for the AR trail.

## Code map

```
index.html, styles.css   UI shell
js/app.js                screens, item dialog, wiring
js/sensors.js            GPS + compass heading + tilt, simulator fallback
js/trail.js              straight-line / walking-route trail
js/ar.js                 camera overlay: pixie dust + beacon
js/guide.js              eyes-free buzz & beep guidance
js/map.js                Leaflet map
js/store.js              items + sighting history (localStorage)
js/geo.js                distance / bearing maths
vendor/leaflet           Leaflet 1.9.4 (BSD-2)
```
