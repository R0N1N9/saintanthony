// Small-distance geo helpers. Points are {lat, lng} in degrees.
const R = 6371000;
export const rad = (d) => (d * Math.PI) / 180;
export const deg = (r) => (r * 180) / Math.PI;

export function distance(a, b) {
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// Compass bearing from a to b, 0 = north, clockwise, 0..360.
export function bearing(a, b) {
  const y = Math.sin(rad(b.lng - a.lng)) * Math.cos(rad(b.lat));
  const x =
    Math.cos(rad(a.lat)) * Math.sin(rad(b.lat)) -
    Math.sin(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.cos(rad(b.lng - a.lng));
  return (deg(Math.atan2(y, x)) + 360) % 360;
}

// Flat-earth offset of p from origin in meters: e = east, n = north.
export function toLocal(origin, p) {
  return {
    e: rad(p.lng - origin.lng) * R * Math.cos(rad(origin.lat)),
    n: rad(p.lat - origin.lat) * R,
  };
}

export function fromLocal(origin, e, n) {
  return {
    lat: origin.lat + deg(n / R),
    lng: origin.lng + deg(e / (R * Math.cos(rad(origin.lat)))),
  };
}

// Signed smallest difference a - b, in -180..180.
export const angleDiff = (a, b) => ((a - b + 540) % 360) - 180;

export function fmtDist(m) {
  if (m == null || !isFinite(m)) return "—";
  if (m < 1000) return `${Math.round(m)} m`;
  return `${(m / 1000).toFixed(m < 10000 ? 1 : 0)} km`;
}

export function cardinal(b) {
  return ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][Math.round(b / 45) % 8];
}

export function timeAgo(t) {
  const s = (Date.now() - t) / 1000;
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return `${Math.floor(s / 86400)} d ago`;
}
