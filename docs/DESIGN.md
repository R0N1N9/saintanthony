# St. Anthony — design

## The idea

Help people find missing things — not only tagged electronics, but almost
anything — by remembering each item's **last known location** and guiding the
person back to it: a compass arrow, a pixie-dust trail in AR, a pin on a map.

Everything reduces to two questions:

1. **Where was the item last seen?** (the hard part — a sensing problem)
2. **How do I lead the person there?** (compass / AR / map — the same in every phase)

The guidance side is built once. The phases below are really about better and
more automatic answers to question 1.

## Where each location source gets you

| Source | Precision | Effort for the user | Works for |
| --- | --- | --- | --- |
| Manual "it's right here" pin (phase 1) | 5–10 m outdoors, poor indoors | A tap each time | Anything |
| Bluetooth beacon / tag | Room-level; "warmer/colder" by signal strength | Attach a tag | Things big enough to tag |
| UWB tag (AirTag-class) | ~10 cm with direction | Attach a tag, recent phone | Things big enough to tag |
| Smart-glasses camera | Exactly where you last *saw* it | None | Anything visible |

## Phase 1 — web app (built)

Phone-only PWA. Manual pins, sighting history, compass, eyes-free haptic guide,
AR trail, map, optional walking routes. Local storage, no backend.

The **Signal** tab is an early taste of phase 2: pick a Bluetooth LE device in
Chrome on Android, follow a strength meter, and spin for a rough bearing.

Known limits: GPS precision; browser AR is compass-and-tilt based, so the trail
floats rather than sticking to the floor; no background operation.

## Phase 2 — native app + beacons

Move to a native app (React Native/Expo or Swift/Kotlin) when the web hits its
ceiling:

- **Real AR** — ARKit / ARCore anchor the dust trail to the actual ground and
  keep it stable as you walk.
- **Bluetooth beacons** — any cheap BLE tag. The app notes the phone's location
  whenever it loses contact with a tag: that is an *automatic* last-seen pin.
  Up close, signal strength drives a warmer/colder finder.
- **Trusted devices** — list the phone's already-paired Bluetooth devices and
  link each to an item, remembered permanently (the browser forgets between
  visits and cannot see the paired list).
- **Wi-Fi** — read the strength of your router or any access point, with the
  same meter and spin-to-find.
- **Signal mapping** — record strength at many spots as you walk (positions
  from ARCore) and solve for where the source is; far better than a spin.
- **Friends** — phones deliberately hide their Bluetooth identity, so a
  friend's phone can only be found if they run St. Anthony and choose to share.
  Never track a device whose owner hasn't agreed.
- **UWB** where the phone supports it, for an arrow that is right to the
  centimetre indoors.
- **Automatic pins without hardware** — car parked (Bluetooth disconnect),
  arrived/left home, etc.
- Background location, widgets, watch app for the wrist-buzz guide.
- Optional encrypted sync so a household can share items.

Apple's and Google's finding networks are closed to third-party apps, so
St. Anthony's own tags would not be found by strangers' phones. That is the
"not quite an AirTag" trade: it is about *your* memory of your things.

## Phase 3 — smart glasses

The original vision: glasses that passively notice your belongings.

- On-device object recognition for a short list of "your things" (enrol by
  looking at each item a few times).
- Each sighting logs item + position (GPS outdoors, visual positioning indoors)
  + a snapshot. "Where are my keys?" → "Kitchen counter, 8:14 this morning",
  with the picture.
- The pixie trail renders in the glasses display; the same compass and haptic
  guide run on the phone or watch.
- Learns which things you carry most and where they usually live, so it can
  flag "you left without your wallet".

Privacy is the make-or-break here: recognition and the sighting log must stay
on-device, only enrolled items are stored, and bystanders are never recorded.

## Open questions

- Which glasses platform to target first — depends on who opens camera access.
- Indoor positioning without glasses: is beacon signal strength good enough, or
  is UWB required?
- Business model: app alone, or app + own-brand tags.
