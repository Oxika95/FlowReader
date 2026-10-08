// Scores brand/flow-reader-mark.svg against the concept: IoU of the silhouette and of the details,
// plus out/overlay.png (concept dimmed; red = in concept only, green = in the master only).
import { mkdirSync, readFileSync } from 'node:fs';
import Jimp from 'jimp';
import { Resvg } from '@resvg/resvg-js';
import { buildMasks, fromPixels, loadConcept } from './masks.mjs';
import { LAYOUT } from './trace.mjs';

const OUT = new URL('./out/', import.meta.url);
const file = (u) => u.pathname.replace(/^\/([A-Za-z]:)/, '$1');
const SILHOUETTE_TARGET = 0.95;

const master = readFileSync(new URL('../flow-reader-mark.svg', import.meta.url), 'utf8');
const m = await buildMasks();
const { w, h, img } = { ...m, ...(await loadConcept()) };

// Same mapping trace.mjs used: bbox centre -> (54,54), farthest silhouette pixel -> LAYOUT.radius.
const cx = (m.mark.x1 + m.mark.x2 + 1) / 2;
const cy = (m.mark.y1 + m.mark.y2 + 1) / 2;
let far = 0;
for (let i = 0; i < w * h; i++) {
  if (m.silhouette[i]) far = Math.max(far, Math.hypot((i % w) + 0.5 - cx, ((i / w) | 0) + 0.5 - cy));
}
const s = LAYOUT.radius / far;

/** Renders the master (optionally only some foreground paths) into concept pixel space as a mask. */
function render(keepIds) {
  let svg = master.replace(/<g id="background">[\s\S]*?<\/g>/, '');
  if (keepIds) {
    svg = svg.replace(/<path id="([\w-]+)"[\s\S]*?\/>/g, (tag, id) => (keepIds.includes(id) ? tag : ''));
  }
  // Grid unit g maps to concept px (g - 54) / s + c; render the 108 grid at 1/s px per unit, then shift.
  const size = Math.round(108 / s);
  const out = new Resvg(svg, { fitTo: { mode: 'width', value: size } }).render();
  const ox = Math.round(54 * (size / 108) - cx);
  const oy = Math.round(54 * (size / 108) - cy);
  // `pixels` is a getter that copies the whole buffer; read it once.
  const { pixels, width, height } = { pixels: out.pixels, width: out.width, height: out.height };
  const mask = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const rx = x + ox, ry = y + oy;
      if (rx < 0 || ry < 0 || rx >= width || ry >= height) continue;
      if (pixels[(ry * width + rx) * 4 + 3] > 127) mask[y * w + x] = 1;
    }
  }
  return mask;
}

function iou(a, b) {
  let inter = 0, union = 0;
  for (let i = 0; i < a.length; i++) {
    if (a[i] && b[i]) inter++;
    if (a[i] || b[i]) union++;
  }
  return union ? inter / union : 1;
}

const silMaster = render(null);
const detailsConcept = fromPixels([...m.details.bars.flatMap((b) => b.pixels), ...m.details.play.pixels], w, h);
const detailsMaster = render(['lines', 'play']);

const silIou = iou(m.silhouette, silMaster);
const detIou = iou(detailsConcept, detailsMaster);

mkdirSync(OUT, { recursive: true });
const overlay = img.clone().brightness(-0.6);
for (let i = 0; i < w * h; i++) {
  const a = m.silhouette[i], b = silMaster[i];
  if (a && !b) overlay.bitmap.data.writeUInt32BE(0xff3030ff, i * 4);
  else if (!a && b) overlay.bitmap.data.writeUInt32BE(0x30ff30ff, i * 4);
}
await overlay.writeAsync(file(new URL('overlay.png', OUT)));

console.log(`silhouette IoU ${silIou.toFixed(4)} (target >= ${SILHOUETTE_TARGET})`);
console.log(`details IoU    ${detIou.toFixed(4)} (lower by design: bars are thickened for 48dp)`);
console.log(`overlay: brand/trace/out/overlay.png`);
if (silIou < SILHOUETTE_TARGET) process.exitCode = 1;
