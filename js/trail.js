// The trail from you to the item: a straight line by default, or a walking
// route along real paths (OSRM / OpenStreetMap) when the user opts in.
import { distance, toLocal } from "./geo.js";

const OSRM = "https://routing.openstreetmap.de/routed-foot/route/v1/foot";

let route = null; // [{lat,lng}] full walking route, or null
let routeFor = null; // target the route was fetched for
let lastFetch = 0;
let fetching = false;

export let walkingEnabled = false;
export function setWalking(on) {
  walkingEnabled = on;
  if (!on) route = null;
}

async function fetchRoute(pos, target) {
  fetching = true;
  lastFetch = Date.now();
  try {
    const url = `${OSRM}/${pos.lng},${pos.lat};${target.lng},${target.lat}?overview=full&geometries=geojson`;
    const res = await fetch(url);
    const data = await res.json();
    const coords = data.routes?.[0]?.geometry?.coordinates;
    if (coords?.length > 1) {
      route = coords.map(([lng, lat]) => ({ lat, lng }));
      route.push({ lat: target.lat, lng: target.lng }); // routes end at the nearest path, not the item
      routeFor = target;
    }
  } catch {
    route = null; // offline or service down: straight line still works
  }
  fetching = false;
}

function distToSegment(p, a, b) {
  const dx = b.e - a.e, dy = b.n - a.n;
  const len2 = dx * dx + dy * dy || 1e-9;
  const t = Math.max(0, Math.min(1, ((p.e - a.e) * dx + (p.n - a.n) * dy) / len2));
  return Math.hypot(p.e - (a.e + t * dx), p.n - (a.n + t * dy));
}

// Remaining path as [{lat,lng}], starting at the user's position.
export function getTrail(pos, target) {
  if (!pos || !target) return [];
  const straight = [pos, target];
  if (!walkingEnabled) return straight;

  const stale = !route || routeFor !== target;
  if (stale && !fetching && Date.now() - lastFetch > 5000) fetchRoute(pos, target);
  if (stale) return straight;

  // Find the route segment we're on and keep only what's ahead of it.
  const local = route.map((p) => toLocal(pos, p));
  let best = 0, bestD = Infinity;
  for (let i = 0; i < local.length - 1; i++) {
    const d = distToSegment({ e: 0, n: 0 }, local[i], local[i + 1]);
    if (d < bestD) { bestD = d; best = i; }
  }
  if (bestD > 50 && distance(pos, target) > 50 && !fetching && Date.now() - lastFetch > 15000) {
    fetchRoute(pos, target); // wandered off the route
  }
  return [pos, ...route.slice(best + 1)];
}

// Points every `spacing` meters along the trail, in local meters {e, n, along}.
export function sampleTrail(pos, trail, spacing, maxDist, offset = 0) {
  const pts = [];
  let walked = 0;
  let next = offset;
  for (let i = 0; i < trail.length - 1 && walked < maxDist; i++) {
    const a = toLocal(pos, trail[i]);
    const b = toLocal(pos, trail[i + 1]);
    const len = Math.hypot(b.e - a.e, b.n - a.n);
    while (next <= walked + len && next <= maxDist) {
      const t = len ? (next - walked) / len : 0;
      pts.push({ e: a.e + (b.e - a.e) * t, n: a.n + (b.n - a.n) * t, along: next });
      next += spacing;
    }
    walked += len;
  }
  return pts;
}
