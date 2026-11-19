// Items and their sighting history ("paper trail"), kept on-device in localStorage.
// item: { id, name, emoji, sightings: [{ lat, lng, acc, t, note }] }  (newest sighting last)
const KEY = "st-anthony.items.v1";
const subs = new Set();

let items = [];
try {
  items = JSON.parse(localStorage.getItem(KEY)) || [];
} catch {
  items = [];
}

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(items));
  } catch {
    /* storage unavailable: keep working in memory */
  }
  subs.forEach((f) => f(items));
}

export const onItems = (f) => subs.add(f);
export const all = () => items;
export const get = (id) => items.find((i) => i.id === id);
export const last = (item) => item?.sightings[item.sightings.length - 1];

export function upsert({ id, name, emoji }, sighting) {
  let item = id && get(id);
  if (!item) {
    item = { id: crypto.randomUUID(), sightings: [] };
    items.push(item);
  }
  item.name = name;
  item.emoji = emoji;
  if (sighting) {
    item.sightings.push({ ...sighting, t: Date.now() });
    item.sightings = item.sightings.slice(-50);
  }
  save();
  return item;
}

export function remove(id) {
  items = items.filter((i) => i.id !== id);
  save();
}
