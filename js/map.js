// Map view (Leaflet + OpenStreetMap): your position, every item's last known
// spot, the trail to the selected item, and its history of past sightings.
import { S } from "./sensors.js";
import * as store from "./store.js";
import { getTrail } from "./trail.js";

const L = window.L;
let map, me, meAcc, trailLine, historyLine, markers = new Map(), centered = false;
let clickHandler = null;

const emojiIcon = (emoji, active) =>
  L.divIcon({
    className: "",
    html: `<div class="pin${active ? " active" : ""}">${emoji}</div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
  });

export function initMap(el, onPick) {
  map = L.map(el, { zoomControl: false }).setView([20, 0], 2);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "© OpenStreetMap contributors",
  }).addTo(map);
  trailLine = L.polyline([], { color: "#ffd666", weight: 5, dashArray: "2 12", lineCap: "round" }).addTo(map);
  historyLine = L.polyline([], { color: "#b9a7ff", weight: 2, opacity: 0.8, dashArray: "6 6" }).addTo(map);
  map.on("click", (e) => clickHandler?.({ lat: e.latlng.lat, lng: e.latlng.lng }));
  map.on("dragstart", () => (centered = true));
  initMap.onPick = onPick;
}

export const setMapClick = (f) => (clickHandler = f);

export function refreshMap(targetId, visible) {
  if (!map) return;
  if (visible) map.invalidateSize();

  if (S.pos) {
    const ll = [S.pos.lat, S.pos.lng];
    if (!me) {
      meAcc = L.circle(ll, { radius: S.pos.acc, color: "#6ab7ff", weight: 1, fillOpacity: 0.12 }).addTo(map);
      me = L.marker(ll, {
        icon: L.divIcon({ className: "", html: '<div class="me"><i></i></div>', iconSize: [28, 28], iconAnchor: [14, 14] }),
        interactive: false,
      }).addTo(map);
    }
    me.setLatLng(ll);
    meAcc.setLatLng(ll).setRadius(S.pos.acc || 5);
    me.getElement()?.querySelector("i")?.style.setProperty("transform", `rotate(${S.heading ?? 0}deg)`);
  }

  const seen = new Set();
  for (const item of store.all()) {
    const loc = store.last(item);
    if (!loc) continue;
    seen.add(item.id);
    let m = markers.get(item.id);
    if (!m) {
      m = L.marker([loc.lat, loc.lng]).addTo(map);
      m.on("click", () => initMap.onPick?.(item.id));
      markers.set(item.id, m);
    }
    m.setLatLng([loc.lat, loc.lng]);
    m.setIcon(emojiIcon(item.emoji, item.id === targetId));
  }
  for (const [id, m] of markers) {
    if (!seen.has(id)) { m.remove(); markers.delete(id); }
  }

  const target = store.get(targetId);
  const loc = store.last(target);
  trailLine.setLatLngs(loc && S.pos ? getTrail(S.pos, loc).map((p) => [p.lat, p.lng]) : []);
  historyLine.setLatLngs(target ? target.sightings.map((s) => [s.lat, s.lng]) : []);

  if (visible && !centered && S.pos) {
    centered = true;
    if (loc) map.fitBounds([[S.pos.lat, S.pos.lng], [loc.lat, loc.lng]], { padding: [70, 70], maxZoom: 18 });
    else map.setView([S.pos.lat, S.pos.lng], 17);
  }
}

export function recenter() {
  centered = false;
}
