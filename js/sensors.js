// Position (GPS) + heading (compass) + pitch, with a simulator fallback for
// desktops and devices without a compass.
import { rad, deg, angleDiff } from "./geo.js";

export const S = {
  pos: null, // { lat, lng, acc }
  heading: null, // degrees, direction the phone is "pointing"
  pitch: 0, // degrees the camera is tilted above the horizon
  faceDown: false,
  sim: false,
  gpsError: null,
};

const subs = new Set();
export const onSensors = (f) => subs.add(f);
const emit = () => subs.forEach((f) => f(S));

let gotCompass = false;

function onOrientation(e) {
  if (S.sim || e.alpha == null) return;
  const ios = e.webkitCompassHeading != null;
  const absolute = ios || e.absolute || e.type === "deviceorientationabsolute";
  if (!absolute) return;

  const a = rad(e.alpha), b = rad(e.beta), g = rad(e.gamma);
  const cX = Math.cos(b), sX = Math.sin(b);
  const cY = Math.cos(g), sY = Math.sin(g);
  const cZ = Math.cos(a), sZ = Math.sin(a);

  // World-frame (east, north, up) direction of the rear camera...
  const bx = -cZ * sY - sZ * sX * cY;
  const by = -sZ * sY + cZ * sX * cY;
  const bz = -cX * cY;
  // ...and of the top edge of the phone.
  const tx = -cX * sZ;
  const ty = cX * cZ;

  S.pitch = deg(Math.asin(Math.max(-1, Math.min(1, bz))));
  S.faceDown = bz > 0.8; // camera pointing at the sky = screen on the table

  let h;
  if (ios) {
    h = e.webkitCompassHeading;
  } else {
    // Held upright -> use where the camera looks; held flat -> use the top edge.
    const upright = Math.hypot(bx, by) > Math.hypot(tx, ty);
    h = deg(upright ? Math.atan2(bx, by) : Math.atan2(tx, ty));
  }
  h = (h + 360) % 360;
  S.heading = S.heading == null ? h : (S.heading + angleDiff(h, S.heading) * 0.25 + 360) % 360;
  gotCompass = true;
  emit();
}

export function enableSim() {
  if (S.sim) return;
  S.sim = true;
  S.heading = S.heading ?? 0;
  S.pitch = 0;
  S.faceDown = false;
  emit();
}

export function setSimHeading(h) {
  S.heading = ((h % 360) + 360) % 360;
  emit();
}

export function setSimPos(p) {
  S.pos = { lat: p.lat, lng: p.lng, acc: 5 };
  S.gpsError = null;
  emit();
}

// Must be called from a tap: iOS only grants compass access on a user gesture.
export async function start({ forceSim = false } = {}) {
  if ("geolocation" in navigator) {
    navigator.geolocation.watchPosition(
      (p) => {
        S.pos = { lat: p.coords.latitude, lng: p.coords.longitude, acc: p.coords.accuracy };
        S.gpsError = null;
        emit();
      },
      (err) => {
        S.gpsError = err.message || "Location unavailable";
        emit();
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 20000 }
    );
  } else {
    S.gpsError = "This browser has no location support";
  }

  if (forceSim) return enableSim();

  try {
    if (typeof DeviceOrientationEvent?.requestPermission === "function") {
      await DeviceOrientationEvent.requestPermission();
    }
  } catch {
    /* denied: falls through to the simulator below */
  }
  window.addEventListener("deviceorientationabsolute", onOrientation);
  window.addEventListener("deviceorientation", (e) => {
    // Android fires both events; only iOS needs this one.
    if (e.webkitCompassHeading != null || !("ondeviceorientationabsolute" in window)) onOrientation(e);
  });
  setTimeout(() => {
    if (!gotCompass) enableSim();
  }, 2500);
}
