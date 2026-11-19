// AR view: camera feed with a pixie-dust trail and item beacon drawn on top.
// Each point is placed using its compass bearing relative to where the phone
// is looking, and its distance (which sets how far below the horizon it sits).
import { S } from "./sensors.js";
import { rad, deg, angleDiff, distance, toLocal, fmtDist } from "./geo.js";
import { getTrail, sampleTrail } from "./trail.js";

const CAM_HEIGHT = 1.4; // meters, phone held at chest height
const VFOV = 62; // typical phone main camera, portrait
const SPACING = 2; // meters between dust puffs
const FLOW = 1.3; // m/s the dust drifts toward the item

let video, canvas, ctx, running = false, stream = null;
let getTarget = () => null;

export function initAR(videoEl, canvasEl, targetGetter) {
  video = videoEl;
  canvas = canvasEl;
  ctx = canvas.getContext("2d");
  getTarget = targetGetter;
}

export async function startAR() {
  if (running) return;
  running = true;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" } },
      audio: false,
    });
    video.srcObject = stream;
    await video.play();
    canvas.parentElement.classList.remove("no-camera");
  } catch {
    canvas.parentElement.classList.add("no-camera"); // starry backdrop instead
  }
  requestAnimationFrame(frame);
}

export function stopAR() {
  running = false;
  stream?.getTracks().forEach((t) => t.stop());
  stream = null;
}

// Deterministic pseudo-random so sparkles twinkle instead of flickering.
const rnd = (n) => {
  const x = Math.sin(n * 127.1) * 43758.5453;
  return x - Math.floor(x);
};

function project(e, n, height, w, h, f) {
  const d = Math.hypot(e, n);
  const rel = angleDiff(deg(Math.atan2(e, n)), S.heading ?? 0);
  if (Math.abs(rel) > 80) return null;
  const elev = deg(Math.atan2(height - CAM_HEIGHT, d));
  const dv = elev - S.pitch;
  if (Math.abs(dv) > 80) return null;
  return {
    x: w / 2 + Math.tan(rad(rel)) * f,
    y: h / 2 - Math.tan(rad(dv)) * f,
    d,
    rel,
  };
}

function glow(x, y, r, alpha) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, `rgba(255,250,220,${alpha})`);
  g.addColorStop(0.3, `rgba(255,214,102,${alpha * 0.7})`);
  g.addColorStop(1, "rgba(255,180,60,0)");
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
}

function star(x, y, r, alpha) {
  ctx.strokeStyle = `rgba(255,255,255,${alpha})`;
  ctx.lineWidth = Math.max(1, r / 4);
  ctx.beginPath();
  ctx.moveTo(x - r, y); ctx.lineTo(x + r, y);
  ctx.moveTo(x, y - r); ctx.lineTo(x, y + r);
  ctx.stroke();
}

function frame(ms) {
  if (!running) return;
  requestAnimationFrame(frame);
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
    canvas.width = w * dpr;
    canvas.height = h * dpr;
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  const target = getTarget();
  if (!target || !S.pos || S.heading == null) return;

  const t = ms / 1000;
  const f = h / 2 / Math.tan(rad(VFOV / 2));
  const loc = target.loc;
  const dist = distance(S.pos, loc);

  // --- pixie-dust trail, far to near so close puffs draw on top ---
  const trail = getTrail(S.pos, loc);
  const pts = sampleTrail(S.pos, trail, SPACING, Math.min(dist, 45), (t * FLOW) % SPACING);
  ctx.globalCompositeOperation = "lighter";
  for (let i = pts.length - 1; i >= 0; i--) {
    const p = pts[i];
    if (p.along < 1.2) continue;
    const s = project(p.e, p.n, 0.15, w, h, f);
    if (!s) continue;
    const size = Math.max(3, Math.min(34, (f * 0.16) / s.d));
    const fade = Math.min(1, (p.along - 1.2) / 2) * Math.min(1, (46 - p.along) / 8);
    glow(s.x, s.y, size, 0.85 * fade);
    // a few loose sparkles drifting around each puff
    const id = Math.round((p.along - ((t * FLOW) % SPACING)) / SPACING) + Math.floor((t * FLOW) / SPACING);
    for (let k = 0; k < 4; k++) {
      const seed = id * 7 + k;
      const tw = 0.5 + 0.5 * Math.sin(t * (3 + rnd(seed) * 4) + seed);
      const ox = (rnd(seed + 1) - 0.5) * size * 5;
      const oy = (rnd(seed + 2) - 0.8) * size * 3 - tw * size * 0.6;
      star(s.x + ox, s.y + oy, size * 0.22 * (0.5 + tw), 0.9 * tw * fade);
    }
  }
  ctx.globalCompositeOperation = "source-over";

  // --- item beacon ---
  const l = toLocal(S.pos, loc);
  const b = project(l.e, l.n, 1.0, w, h, f);
  const rel = angleDiff(deg(Math.atan2(l.e, l.n)), S.heading);
  if (b && b.x > 30 && b.x < w - 30) {
    const y = Math.max(90, Math.min(h - 120, b.y));
    const pulse = 1 + 0.15 * Math.sin(t * 4);
    const col = ctx.createLinearGradient(0, y - 220, 0, y);
    col.addColorStop(0, "rgba(255,214,102,0)");
    col.addColorStop(1, "rgba(255,214,102,0.55)");
    ctx.fillStyle = col;
    ctx.fillRect(b.x - 5, y - 220, 10, 220);
    ctx.globalCompositeOperation = "lighter";
    glow(b.x, y, 46 * pulse, 0.9);
    ctx.globalCompositeOperation = "source-over";
    ctx.font = "34px system-ui";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(target.item.emoji, b.x, y);
    ctx.font = "600 15px system-ui";
    ctx.fillStyle = "#fff";
    ctx.shadowColor = "#000";
    ctx.shadowBlur = 6;
    ctx.fillText(`${target.item.name} · ${fmtDist(dist)}`, b.x, y + 46);
    ctx.shadowBlur = 0;
  } else {
    // Off-screen: nudge the user to turn.
    const right = rel > 0;
    const x = right ? w - 44 : 44;
    const bob = Math.sin(t * 5) * 6 * (right ? 1 : -1);
    ctx.fillStyle = "rgba(20,16,40,0.7)";
    ctx.beginPath();
    ctx.arc(x, h / 2, 34, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#ffd666";
    ctx.font = "700 34px system-ui";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(right ? "›" : "‹", x + bob, h / 2 - 3);
    ctx.font = "600 13px system-ui";
    ctx.fillStyle = "#fff";
    ctx.fillText(`turn ${right ? "right" : "left"}`, x, h / 2 + 50);
  }
}
