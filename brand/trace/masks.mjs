// Colour masks over the concept image. Shared by trace.mjs and compare.mjs.
import Jimp from 'jimp';

export const CONCEPT = new URL('../concept/flow-reader-concept.jpg', import.meta.url);

export const TUNING = {
  /** Sum of |rgb - background| above which a pixel belongs to the mark. */
  silhouette: 110,
  /** Erosion (px) before detail/back-face detection, so the light rim is excluded. */
  rimInset: 7,
  /** Luminance above the row's sheet median that counts as a line/play pixel. */
  detail: 28,
  /** A point inside each roll's back face (concept px): top-right, bottom-left. */
  rollSeeds: [[357, 112], [150, 392]],
  /** Region growing: max colour step between neighbours (follows gradients, stops at the rim). */
  growStep: 10,
  /** Region growing: max colour distance from the seed. */
  growSpan: 60,
  /** Text bars thinner than this (px) are rim slivers. */
  barMinHeight: 4,
  /** Rows whose median is further than this from the fit are dropped while fitting. */
  fitTrim: 25,
  /** Back faces must be at least this many px. */
  backFaceMinArea: 300,
  /** Detail components below this many px are noise. */
  detailMinArea: 60,
};

export async function loadConcept() {
  const img = await Jimp.read(CONCEPT.pathname.replace(/^\/([A-Za-z]:)/, '$1'));
  const { width: w, height: h, data } = img.bitmap;
  const rgb = (i) => [data[i * 4], data[i * 4 + 1], data[i * 4 + 2]];
  return { img, w, h, rgb };
}

function linearFit(xs, ys) {
  const n = xs.length;
  const mx = xs.reduce((s, v) => s + v, 0) / n;
  const my = ys.reduce((s, v) => s + v, 0) / n;
  let sxy = 0, sxx = 0;
  for (let i = 0; i < n; i++) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) ** 2; }
  const b = sxx ? sxy / sxx : 0;
  return { a: my - b * mx, b };
}

export const lum = ([r, g, b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;
const dist = (a, b) => Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);

/** Connected components (4-neighbour) of a 0/1 mask, largest first. */
export function components(mask, w, h) {
  const label = new Int32Array(w * h).fill(-1);
  const out = [];
  for (let start = 0; start < w * h; start++) {
    if (!mask[start] || label[start] !== -1) continue;
    const id = out.length;
    const pixels = [];
    const stack = [start];
    label[start] = id;
    let x1 = w, y1 = h, x2 = 0, y2 = 0, sx = 0, sy = 0;
    while (stack.length) {
      const p = stack.pop();
      pixels.push(p);
      const x = p % w, y = (p / w) | 0;
      x1 = Math.min(x1, x); x2 = Math.max(x2, x); y1 = Math.min(y1, y); y2 = Math.max(y2, y);
      sx += x; sy += y;
      for (const q of [p - 1, p + 1, p - w, p + w]) {
        if (q < 0 || q >= w * h || !mask[q] || label[q] !== -1) continue;
        if ((q === p - 1 && x === 0) || (q === p + 1 && x === w - 1)) continue;
        label[q] = id;
        stack.push(q);
      }
    }
    out.push({ pixels, area: pixels.length, x1, y1, x2, y2, cx: sx / pixels.length, cy: sy / pixels.length });
  }
  return out.sort((a, b) => b.area - a.area);
}

export function fromPixels(pixels, w, h) {
  const m = new Uint8Array(w * h);
  for (const p of pixels) m[p] = 1;
  return m;
}

export function erode(mask, w, h, r) {
  let cur = mask;
  for (let i = 0; i < r; i++) {
    const next = new Uint8Array(w * h);
    for (let y = 1; y < h - 1; y++) {
      for (let x = 1; x < w - 1; x++) {
        const p = y * w + x;
        next[p] = cur[p] && cur[p - 1] && cur[p + 1] && cur[p - w] && cur[p + w] ? 1 : 0;
      }
    }
    cur = next;
  }
  return cur;
}

/** Magic-wand flood fill from `seed` inside `within`. */
function grow(rgb, within, w, h, seed, step, span) {
  const out = new Uint8Array(w * h);
  const s = rgb(seed);
  const stack = [seed];
  out[seed] = 1;
  while (stack.length) {
    const p = stack.pop();
    const c = rgb(p);
    const x = p % w;
    for (const q of [x > 0 ? p - 1 : -1, x < w - 1 ? p + 1 : -1, p - w, p + w]) {
      if (q < 0 || q >= w * h || out[q] || !within[q]) continue;
      const n = rgb(q);
      if (dist(n, c) > step || dist(n, s) > span) continue;
      out[q] = 1;
      stack.push(q);
    }
  }
  return out;
}

/** Fills holes: everything not reachable from the border through background. */
export function fillHoles(mask, w, h) {
  const outside = new Uint8Array(w * h);
  const stack = [];
  for (let x = 0; x < w; x++) stack.push(x, (h - 1) * w + x);
  for (let y = 0; y < h; y++) stack.push(y * w, y * w + w - 1);
  while (stack.length) {
    const p = stack.pop();
    if (outside[p] || mask[p]) continue;
    outside[p] = 1;
    const x = p % w;
    if (x > 0) stack.push(p - 1);
    if (x < w - 1) stack.push(p + 1);
    if (p >= w) stack.push(p - w);
    if (p < w * (h - 1)) stack.push(p + w);
  }
  return outside.map((o) => (o ? 0 : 1));
}

export async function buildMasks(t = TUNING) {
  const { w, h, rgb } = await loadConcept();
  const bg = rgb(60 * w + 60);

  const raw = new Uint8Array(w * h);
  for (let i = 0; i < w * h; i++) raw[i] = dist(rgb(i), bg) > t.silhouette ? 1 : 0;
  const centre = (h >> 1) * w + (w >> 1);
  const mark = components(raw, w, h).find((c) => c.pixels.includes(centre)) ?? components(raw, w, h)[0];
  const silhouette = fillHoles(fromPixels(mark.pixels, w, h), w, h);
  const inner = erode(silhouette, w, h, t.rimInset);

  // Sheet colour is close to linear in y. Fit each channel to the row medians, dropping rows a long
  // text line or a roll dominates (iterative trimming), so lines never become their own reference.
  const medians = [];
  for (let y = 0; y < h; y++) {
    const row = [];
    for (let x = 0; x < w; x++) if (inner[y * w + x]) row.push(rgb(y * w + x));
    if (row.length < 10) continue;
    const med = (k) => row.map((c) => c[k]).sort((a, b) => a - b)[row.length >> 1];
    medians.push({ y, c: [med(0), med(1), med(2)] });
  }
  let kept = medians;
  let fit = null;
  for (let pass = 0; pass < 6; pass++) {
    fit = [0, 1, 2].map((k) => linearFit(kept.map((m) => m.y), kept.map((m) => m.c[k])));
    const at = (y) => fit.map((f) => f.a + f.b * y);
    kept = medians.filter((m) => dist(m.c, at(m.y)) < t.fitTrim);
  }
  const rowRef = new Array(h).fill(null).map((_, y) => fit.map((f) => f.a + f.b * y));

  const detailRaw = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) {
    const ref = rowRef[y];
    for (let x = 0; x < w; x++) {
      const p = y * w + x;
      if (inner[p] && lum(rgb(p)) - lum(ref) > t.detail) detailRaw[p] = 1;
    }
  }
  // Lines are wide bars; the play button is the largest compact blob. Roll rims (arcs) are neither.
  const blobs = components(detailRaw, w, h).filter((c) => c.area >= t.detailMinArea);
  const bars = blobs.filter((c) => c.y2 - c.y1 + 1 >= t.barMinHeight && (c.x2 - c.x1 + 1) / (c.y2 - c.y1 + 1) >= 4);
  const play = blobs.filter((c) => !bars.includes(c))
    .map((c) => ({ c, fill: c.area / ((c.x2 - c.x1 + 1) * (c.y2 - c.y1 + 1)) }))
    .filter((o) => o.fill > 0.35)
    .sort((a, b) => b.c.area - a.c.area)[0]?.c;
  const details = { bars: bars.sort((a, b) => a.cy - b.cy), play };
  const backFaces = t.rollSeeds.map(([sx, sy]) => {
    const region = grow(rgb, silhouette, w, h, sy * w + sx, t.growStep, t.growSpan);
    const mask = fillHoles(region, w, h);
    const c = components(mask, w, h)[0];
    return { ...c, mask };
  });

  return { w, h, rgb, bg, silhouette, inner, rowRef, details, backFaces, mark };
}

export function dilate(mask, w, h, r) {
  let cur = mask;
  for (let i = 0; i < r; i++) {
    const next = new Uint8Array(cur);
    for (let p = 0; p < w * h; p++) {
      if (!cur[p]) continue;
      const x = p % w;
      if (x > 0) next[p - 1] = 1;
      if (x < w - 1) next[p + 1] = 1;
      if (p >= w) next[p - w] = 1;
      if (p < w * (h - 1)) next[p + w] = 1;
    }
    cur = next;
  }
  return cur;
}

/** Majority vote over a (2r+1)^2 window: removes JPEG/region-growing jaggies before tracing. */
export function smooth(mask, w, h, r) {
  const sat = new Int32Array((w + 1) * (h + 1));
  for (let y = 0; y < h; y++) {
    let row = 0;
    for (let x = 0; x < w; x++) {
      row += mask[y * w + x];
      sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + row;
    }
  }
  const out = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const x1 = Math.max(0, x - r), y1 = Math.max(0, y - r), x2 = Math.min(w, x + r + 1), y2 = Math.min(h, y + r + 1);
      const sum = sat[y2 * (w + 1) + x2] - sat[y1 * (w + 1) + x2] - sat[y2 * (w + 1) + x1] + sat[y1 * (w + 1) + x1];
      out[y * w + x] = sum * 2 > (x2 - x1) * (y2 - y1) ? 1 : 0;
    }
  }
  return out;
}

/** Black shape on white, the input potrace expects. */
export function maskImage(mask, w, h) {
  const img = new Jimp(w, h, 0xffffffff);
  for (let i = 0; i < w * h; i++) if (mask[i]) img.bitmap.data.writeUInt32BE(0x000000ff, i * 4);
  return img;
}

export async function saveMask(mask, w, h, file) {
  await maskImage(mask, w, h).writeAsync(file);
}
