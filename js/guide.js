// Eyes-free guidance: buzzes and beeps when the phone points at the item, so it
// works face-down or in your hand without looking. Vibration is Android-only
// on the web; the beeps cover iPhones.
import { S } from "./sensors.js";
import { angleDiff, bearing, distance } from "./geo.js";

let on = false, timer = null, audio = null, lastPulse = 0, arrived = false;
let getTarget = () => null;

function beep(freq, ms) {
  if (!audio) return;
  const o = audio.createOscillator();
  const g = audio.createGain();
  o.frequency.value = freq;
  g.gain.setValueAtTime(0.15, audio.currentTime);
  g.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + ms / 1000);
  o.connect(g).connect(audio.destination);
  o.start();
  o.stop(audio.currentTime + ms / 1000);
}

function tick() {
  const target = getTarget();
  if (!target || !S.pos || S.heading == null) return;
  const d = distance(S.pos, target.loc);
  const near = d < Math.max(8, S.pos.acc || 0);
  if (near) {
    if (!arrived) {
      arrived = true;
      navigator.vibrate?.([200, 100, 200, 100, 400]);
      beep(880, 150);
      setTimeout(() => beep(1320, 300), 180);
    }
    return;
  }
  arrived = false;
  const err = Math.abs(angleDiff(bearing(S.pos, target.loc), S.heading));
  const now = Date.now();
  if (err < 15 && now - lastPulse > 700) {
    lastPulse = now;
    navigator.vibrate?.([50, 60, 50]); // "this way!"
    beep(1040, 90);
  } else if (err < 45 && now - lastPulse > 1500) {
    lastPulse = now;
    navigator.vibrate?.(25); // "getting warmer"
    beep(520, 60);
  }
}

export function initGuide(targetGetter) {
  getTarget = targetGetter;
}

// Call from a tap so the browser lets us play audio.
export function setGuide(enable) {
  on = enable;
  clearInterval(timer);
  if (on) {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    audio.resume?.();
    arrived = false;
    timer = setInterval(tick, 150);
  }
  return on;
}

export const guideOn = () => on;
