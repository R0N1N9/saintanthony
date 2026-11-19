// Signal finder: listens for one Bluetooth LE device's broadcasts and turns
// their strength (RSSI) into a hot/cold meter and a rough direction.
//
// A phone has one Bluetooth antenna, so a single reading has no direction.
// The direction comes from a "spin sweep": with the phone held close to your
// body, your body blocks part of the signal from behind. Turning a full circle
// and noting where the signal was strongest gives a rough bearing to the source.
import { S } from "./sensors.js";
import { rad, deg, angleDiff, distance, bearing } from "./geo.js";

const BINS = 12; // 30 degree slices of the compass
const NEED = 10; // slices that must be covered before we estimate

export const Sig = {
  supported: !!navigator.bluetooth?.requestDevice,
  name: null, // device being tracked
  rssi: null, // smoothed dBm
  lastHeard: 0,
  trend: 0, // dB change over the last few seconds (+ = warmer)
  sweep: null, // { sums:[], counts:[] } while spinning
  direction: null, // { bearing, spread, clear }
  error: null,
};

const subs = new Set();
export const onSignal = (f) => subs.add(f);
const emit = () => subs.forEach((f) => f(Sig));

let device = null, simTimer = null, history = [];

function onRssi(r) {
  if (r == null || !isFinite(r)) return;
  const now = Date.now();
  Sig.rssi = Sig.rssi == null ? r : Sig.rssi * 0.7 + r * 0.3;
  Sig.lastHeard = now;
  history.push({ t: now, v: Sig.rssi });
  history = history.filter((h) => now - h.t < 4000);
  Sig.trend = Sig.rssi - history[0].v;

  if (Sig.sweep && S.heading != null) {
    const b = Math.floor(S.heading / (360 / BINS)) % BINS;
    Sig.sweep.sums[b] += r;
    Sig.sweep.counts[b]++;
    if (Sig.sweep.counts.filter((c) => c >= 2).length >= NEED) finishSweep();
  }
  emit();
}

function finishSweep() {
  const { sums, counts } = Sig.sweep;
  const known = counts.map((c, i) => (c ? sums[i] / c : null));
  // Fill the odd missed slice from its neighbours.
  const mean = known.map((v, i) => {
    if (v != null) return v;
    const near = [known[(i + BINS - 1) % BINS], known[(i + 1) % BINS]].filter((x) => x != null);
    return near.length ? near.reduce((a, b) => a + b) / near.length : Math.min(...known.filter((x) => x != null));
  });
  const min = Math.min(...mean), max = Math.max(...mean);
  let x = 0, y = 0, total = 0;
  mean.forEach((v, i) => {
    const w = v - min;
    const a = rad((i + 0.5) * (360 / BINS));
    x += w * Math.sin(a);
    y += w * Math.cos(a);
    total += w;
  });
  const sharp = total ? Math.hypot(x, y) / total : 0; // 0 = same everywhere, 1 = one clear peak
  Sig.direction = {
    bearing: (deg(Math.atan2(x, y)) + 360) % 360,
    spread: max - min,
    clear: max - min >= 4 && sharp >= 0.15,
  };
  Sig.sweep = null;
  navigator.vibrate?.(120);
}

export function startSweep() {
  Sig.direction = null;
  Sig.sweep = { sums: Array(BINS).fill(0), counts: Array(BINS).fill(0) };
  emit();
}

export function cancelSweep() {
  Sig.sweep = null;
  emit();
}

export const sweepProgress = () => (Sig.sweep ? Sig.sweep.counts.map((c) => c >= 2) : []);

// Opens the browser's device chooser. Must be called from a tap.
export async function pickDevice() {
  Sig.error = null;
  try {
    const d = await navigator.bluetooth.requestDevice({ acceptAllDevices: true });
    if (!d.watchAdvertisements) {
      Sig.error = "needs-flag";
      return emit();
    }
    stop();
    device = d;
    Sig.name = d.name || "Unnamed device";
    d.addEventListener("advertisementreceived", (e) => device === d && onRssi(e.rssi));
    await d.watchAdvertisements();
  } catch (e) {
    if (e.name !== "NotFoundError") Sig.error = e.message || "Bluetooth failed"; // NotFound = user cancelled
  }
  emit();
}

// Demo: a pretend beacon at `getSource()`, with distance fall-off and body shadow.
export function pickSim(name, getSource) {
  stop();
  Sig.name = `${name} (demo signal)`;
  simTimer = setInterval(() => {
    const src = getSource();
    if (!src || !S.pos) return;
    const d = Math.max(1, distance(S.pos, src));
    const rel = rad(angleDiff(bearing(S.pos, src), S.heading ?? 0));
    onRssi(-45 - 22 * Math.log10(d) - 4 * (1 - Math.cos(rel)) + (Math.random() - 0.5) * 4);
  }, 250);
  emit();
}

export function stop() {
  try {
    device?.unwatchAdvertisements?.();
  } catch { /* already stopped */ }
  device = null;
  clearInterval(simTimer);
  history = [];
  Object.assign(Sig, { name: null, rssi: null, lastHeard: 0, trend: 0, sweep: null, direction: null });
  emit();
}

// Signal strength is a loose guide to distance: walls, pockets and bodies all
// eat signal. Bands are honest; metres would not be.
export function proximity(rssi) {
  if (rssi > -55) return "Right next to you";
  if (rssi > -68) return "Very close — within a few steps";
  if (rssi > -80) return "Nearby — probably this room";
  if (rssi > -92) return "Further off, or behind a wall";
  return "Faint — at the edge of range";
}
