import { S, onSensors, start, enableSim, setSimHeading, setSimPos } from "./sensors.js";
import * as store from "./store.js";
import { distance, bearing, angleDiff, fmtDist, cardinal, timeAgo, fromLocal } from "./geo.js";
import { initAR, startAR, stopAR } from "./ar.js";
import { initMap, refreshMap, setMapClick, recenter } from "./map.js";
import { initGuide, setGuide, guideOn } from "./guide.js";
import { setWalking, walkingEnabled } from "./trail.js";

const $ = (id) => document.getElementById(id);
const esc = (s) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
const DEMO = new URLSearchParams(location.search).has("demo");
const EMOJIS = ["🔑", "👛", "📱", "🎧", "👓", "🎒", "🚲", "🚗", "☂️", "💻", "🧸", "📦"];

let view = "items";
let targetId = localStorage.getItem("st-anthony.target");
let editing = null; // { id?, emoji }
let picking = null; // item draft waiting for a map tap
let wakeLock = null;

// The item being searched for plus its last known location, or null.
function getTarget() {
  const item = store.get(targetId);
  const loc = store.last(item);
  return item && loc ? { item, loc } : null;
}

function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 2600);
}

// ---------- navigation ----------
function show(next) {
  view = next;
  document.querySelectorAll(".view").forEach((v) => v.classList.toggle("active", v.id === `view-${next}`));
  document.querySelectorAll("nav button").forEach((b) => b.classList.toggle("active", b.dataset.view === next));
  if (next === "ar" && getTarget()) startAR();
  else stopAR();
  if (next === "map") recenter();
  if (next !== "items" && getTarget()) navigator.wakeLock?.request("screen").then((l) => (wakeLock = l)).catch(() => {});
  else { wakeLock?.release().catch(() => {}); wakeLock = null; }
  render();
}

function setTarget(id, goto) {
  targetId = id;
  localStorage.setItem("st-anthony.target", id ?? "");
  recenter();
  if (goto) show(goto);
  else render();
}

// ---------- rendering ----------
function renderItems() {
  const items = store.all();
  $("empty").hidden = items.length > 0;
  $("items").innerHTML = items
    .map((item) => {
      const loc = store.last(item);
      const d = loc && S.pos ? fmtDist(distance(S.pos, loc)) + " away · " : "";
      const meta = loc ? `${d}seen ${timeAgo(loc.t)}${loc.note ? ` · ${esc(loc.note)}` : ""}` : "No location yet";
      return `<article class="item${item.id === targetId ? " target" : ""}" data-id="${item.id}">
        <span class="emoji">${item.emoji}</span>
        <div class="info"><strong>${esc(item.name)}</strong><small>${meta}</small></div>
        <button data-act="edit" title="Update location or edit">📍</button>
        <button data-act="find" class="primary" ${loc ? "" : "disabled"}>Find</button>
      </article>`;
    })
    .join("");
}

function render() {
  const target = getTarget();
  const gps = S.pos ? `GPS ±${Math.round(S.pos.acc)} m` : S.gpsError ? "No GPS" : "Finding GPS…";
  $("status").textContent = S.sim ? `Sim · ${gps}` : gps;
  $("status").classList.toggle("warn", !S.pos);
  $("sim").hidden = !S.sim || view === "items";

  const dist = target && S.pos ? distance(S.pos, target.loc) : null;
  const brg = target && S.pos ? bearing(S.pos, target.loc) : null;

  $("target-bar").hidden = !target || view === "items";
  if (target) {
    $("target-emoji").textContent = target.item.emoji;
    $("target-name").textContent = target.item.name;
    $("target-meta").textContent = `Last seen ${timeAgo(target.loc.t)}${target.loc.note ? ` · ${target.loc.note}` : ""}`;
    $("target-dist").textContent = fmtDist(dist);
  }

  if (view === "items") renderItems();

  if (view === "find") {
    $("no-target").hidden = !!target;
    $("finder").hidden = !target;
    if (target) {
      const h = S.heading ?? 0;
      $("rose").style.transform = `rotate(${-h}deg)`;
      const near = dist != null && dist < Math.max(8, S.pos.acc);
      $("dial").classList.toggle("near", near);
      if (dist == null) {
        $("instruction").textContent = S.gpsError ? `Can't get your location: ${S.gpsError}` : "Waiting for GPS…";
      } else {
        const rel = angleDiff(brg, h);
        $("arrow").style.transform = `rotate(${rel}deg)`;
        $("arrow").classList.toggle("aligned", Math.abs(rel) < 15);
        $("dial-dist").textContent = fmtDist(dist);
        $("dial-dir").textContent = `${cardinal(brg)} · ${Math.round(brg)}°`;
        $("instruction").textContent = near
          ? "You're right on top of it — look around here ✨"
          : Math.abs(rel) < 15
          ? "Straight ahead — follow the arrow"
          : Math.abs(rel) > 150
          ? "It's behind you — turn around"
          : `Turn ${rel > 0 ? "right" : "left"} ${Math.round(Math.abs(rel))}°`;
      }
    }
  }

  if (view === "ar") {
    const msg = !target
      ? "Pick an item from <b>Items</b> first."
      : !S.pos
      ? "Waiting for GPS…"
      : dist < Math.max(8, S.pos.acc)
      ? "You're within GPS accuracy of it — look around here ✨"
      : S.pitch > 50
      ? "Hold the phone upright to see the trail."
      : "";
    $("ar-msg").innerHTML = msg;
    $("ar-msg").hidden = !msg;
  }

  refreshMap(targetId, view === "map");
}

// ---------- add / update item ----------
function openDialog(item) {
  editing = { id: item?.id, emoji: item?.emoji ?? EMOJIS[0] };
  $("dlg-title").textContent = item ? `Update ${item.name}` : "Add an item";
  $("dlg-name").value = item?.name ?? "";
  $("dlg-note").value = "";
  $("dlg-delete").hidden = !item;
  $("emoji-row").innerHTML = EMOJIS.map(
    (e) => `<button type="button" class="${e === editing.emoji ? "sel" : ""}">${e}</button>`
  ).join("");
  $("item-dialog").showModal();
}

function draft() {
  const name = $("dlg-name").value.trim();
  if (!name) {
    $("dlg-name").focus();
    toast("Give it a name first");
    return null;
  }
  return { id: editing.id, name, emoji: editing.emoji, note: $("dlg-note").value.trim() };
}

function saveAt(d, loc, acc) {
  const item = store.upsert(d, { lat: loc.lat, lng: loc.lng, acc, note: d.note });
  toast(`${item.emoji} ${item.name} saved`);
  if (!getTarget()) setTarget(item.id);
  return item;
}

$("emoji-row").onclick = (e) => {
  if (e.target.tagName !== "BUTTON") return;
  editing.emoji = e.target.textContent;
  [...$("emoji-row").children].forEach((b) => b.classList.toggle("sel", b === e.target));
};
$("dlg-here").onclick = () => {
  const d = draft();
  if (!d) return;
  if (!S.pos) return toast("No GPS fix yet — try “Pick the spot on the map”");
  saveAt(d, S.pos, S.pos.acc);
  $("item-dialog").close();
};
$("dlg-pick").onclick = () => {
  const d = draft();
  if (!d) return;
  picking = d;
  $("item-dialog").close();
  $("pick-banner").hidden = false;
  show("map");
};
$("dlg-delete").onclick = () => {
  if (!confirm("Delete this item and its history?")) return;
  store.remove(editing.id);
  if (targetId === editing.id) setTarget(null);
  $("item-dialog").close();
};
$("dlg-cancel").onclick = () => $("item-dialog").close();
$("pick-cancel").onclick = () => {
  picking = null;
  $("pick-banner").hidden = true;
};

// ---------- wiring ----------
$("add").onclick = () => openDialog(null);
$("items").onclick = (e) => {
  const btn = e.target.closest("button");
  const id = e.target.closest(".item")?.dataset.id;
  if (!id) return;
  if (btn?.dataset.act === "edit") openDialog(store.get(id));
  else if (btn?.dataset.act === "find") setTarget(id, "find");
  else setTarget(id);
};
document.querySelector("nav").onclick = (e) => {
  const b = e.target.closest("button");
  if (b) show(b.dataset.view);
};
$("guide").onclick = () => {
  const on = setGuide(!guideOn());
  $("guide").setAttribute("aria-pressed", on);
  toast(on ? "Guide on — pulses when you point at it" : "Guide off");
};
$("walk").onclick = () => {
  const on = !walkingEnabled;
  if (on && !confirm("Walking paths come from OpenStreetMap's routing service. Your position and the item's position are sent to it to plan the route. Turn on?")) return;
  setWalking(on);
  $("walk").setAttribute("aria-pressed", on);
  render();
};
$("found").onclick = () => {
  const t = getTarget();
  if (!t) return;
  if (S.pos) store.upsert(t.item, { lat: S.pos.lat, lng: S.pos.lng, acc: S.pos.acc, note: "Found here" });
  setGuide(false);
  $("guide").setAttribute("aria-pressed", false);
  toast(`🎉 Found ${t.item.name}! Location updated.`);
  show("items");
};
$("recenter").onclick = () => { recenter(); render(); };
$("sim-heading").oninput = (e) => {
  $("sim-out").textContent = `${e.target.value}°`;
  setSimHeading(+e.target.value);
};

$("start").onclick = async () => {
  $("welcome").remove();
  await start({ forceSim: DEMO });
  if (DEMO) setTimeout(seedDemo, 1200);
};

function seedDemo() {
  if (!S.pos) setSimPos({ lat: 37.7694, lng: -122.4862 });
  if (store.all().length) return;
  const keys = store.upsert({ name: "Keys", emoji: "🔑" }, { ...fromLocal(S.pos, 45, 60), acc: 5, note: "on the park bench" });
  store.upsert({ name: "Bike", emoji: "🚲" }, { ...fromLocal(S.pos, -180, 90), acc: 5, note: "" });
  setTarget(keys.id);
}

initAR($("cam"), $("overlay"), getTarget);
initGuide(getTarget);
initMap($("map"), (id) => setTarget(id));
setMapClick((p) => {
  if (picking) {
    saveAt(picking, p, 10);
    picking = null;
    $("pick-banner").hidden = true;
    render();
  } else if (S.sim) {
    setSimPos(p);
  }
});

let queued = false;
const schedule = () => {
  if (queued) return;
  queued = true;
  requestAnimationFrame(() => { queued = false; render(); });
};
onSensors(schedule);
store.onItems(schedule);
setInterval(schedule, 15000); // keeps "5 min ago" labels fresh
render();
