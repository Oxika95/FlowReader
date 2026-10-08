// Traces brand/concept/flow-reader-concept.jpg into brand/flow-reader-mark.svg (overwrites it).
// Run from brand/trace: npm run trace, then npm run compare, then node ../build.mjs.
import { mkdirSync, writeFileSync } from 'node:fs';
import potrace from 'potrace';
import svgpath from 'svgpath';
import { buildMasks, dilate, erode, fromPixels, lum, maskImage, saveMask, smooth } from './masks.mjs';

const OUT = new URL('./out/', import.meta.url);
const MASTER = new URL('../flow-reader-mark.svg', import.meta.url);
const file = (u) => u.pathname.replace(/^\/([A-Za-z]:)/, '$1');

export const LAYOUT = {
  /** Farthest silhouette point from the centre, in grid units (safe circle radius is 33). */
  radius: 33,
  /** Concept rim width (px); the rim is a stroke centred on the traced edges. */
  rimPx: 6,
  /** Text bars are at least this thick (grid units) so they survive at 48dp. */
  barMin: 2,
  /** Play triangle corner rounding, as a fraction of its height. */
  playRound: 0.11,
  /** Majority-filter radius (px) applied to masks before tracing. */
  smoothPx: 3,
};

const POTRACE = { turdSize: 20, optTolerance: 1, alphaMax: 1.1, threshold: 128, blackOnWhite: true };

async function trace(mask, w, h) {
  // potrace bundles its own Jimp, so hand it encoded PNG bytes rather than our Jimp instance.
  const png = await maskImage(mask, w, h).getBufferAsync('image/png');
  return new Promise((resolve, reject) => {
    const p = new potrace.Potrace(POTRACE);
    p.loadImage(png, (err) => {
      if (err) return reject(err);
      resolve(/ d="([^"]+)"/.exec(p.getPathTag())[1]);
    });
  });
}

const r2 = (v) => Math.round(v * 100) / 100;

/** Absolute path data with `x,y` pairs (the form build.mjs reads); every subpath closed for strokes. */
function serialize(d) {
  const segs = svgpath(d).abs().round(2).segments;
  let out = '';
  segs.forEach(([cmd, ...n], i) => {
    if (cmd === 'M' && i > 0 && !out.endsWith('Z')) out += 'Z';
    const pairs = [];
    for (let k = 0; k < n.length; k += 2) pairs.push(`${n[k]},${n[k + 1]}`);
    out += cmd + pairs.join(' ');
  });
  return out.endsWith('Z') ? out : `${out}Z`;
}

const hex = (c) => '#' + c.map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('').toUpperCase();
const mean = (cols) => [0, 1, 2].map((k) => cols.reduce((s, c) => s + c[k], 0) / cols.length);

function bar(x1, x2, cy, th) {
  const r = r2(th / 2);
  const a = r2(x1 + r), b = r2(x2 - r), t = r2(cy - r), u = r2(cy + r);
  return `M${a},${t}H${b}A${r},${r} 0 0 1 ${b},${u}H${a}A${r},${r} 0 0 1 ${a},${t}Z`;
}

/** Right-pointing triangle in box (x1,y1)-(x2,y2), corners rounded by `k` along each edge. */
function playTriangle(x1, y1, x2, y2, k) {
  const A = [x1, y1], B = [x1, y2], C = [x2, (y1 + y2) / 2];
  const toward = (p, q) => {
    const dx = q[0] - p[0], dy = q[1] - p[1], len = Math.hypot(dx, dy);
    return [r2(p[0] + (dx / len) * k), r2(p[1] + (dy / len) * k)];
  };
  const f = (p) => `${r2(p[0])},${r2(p[1])}`;
  const [ab, ac] = [toward(A, B), toward(A, C)];
  const [ba, bc] = [toward(B, A), toward(B, C)];
  const [cb, ca] = [toward(C, B), toward(C, A)];
  return `M${f(ab)}L${f(ba)}Q${f(B)} ${f(bc)}L${f(cb)}Q${f(C)} ${f(ca)}L${f(ac)}Q${f(A)} ${f(ab)}Z`;
}

export async function run() {
  const m = await buildMasks();
  const { w, h, rgb, silhouette, details, backFaces, mark } = m;
  mkdirSync(OUT, { recursive: true });

  const half = Math.round(LAYOUT.rimPx / 2);
  const sheetMask = smooth(erode(silhouette, w, h, half), w, h, LAYOUT.smoothPx);
  const faceMasks = backFaces.map((b) => smooth(dilate(b.mask, w, h, half), w, h, LAYOUT.smoothPx));

  await saveMask(silhouette, w, h, file(new URL('silhouette.png', OUT)));
  await saveMask(fromPixels([...details.bars.flatMap((b) => b.pixels), ...details.play.pixels], w, h), w, h, file(new URL('details.png', OUT)));
  const faces = new Uint8Array(w * h);
  faceMasks.forEach((fm) => fm.forEach((v, i) => { if (v) faces[i] = 1; }));
  await saveMask(faces, w, h, file(new URL('backfaces.png', OUT)));

  // Grid mapping: silhouette bbox centre -> (54,54), farthest pixel -> LAYOUT.radius.
  const cx = (mark.x1 + mark.x2 + 1) / 2;
  const cy = (mark.y1 + mark.y2 + 1) / 2;
  let far = 0;
  for (let i = 0; i < w * h; i++) {
    if (!silhouette[i]) continue;
    far = Math.max(far, Math.hypot((i % w) + 0.5 - cx, ((i / w) | 0) + 0.5 - cy));
  }
  const s = LAYOUT.radius / far;
  const gx = (x) => r2(54 + (x - cx) * s);
  const gy = (y) => r2(54 + (y - cy) * s);
  const toGrid = (d) => serialize(svgpath(d).translate(-cx, -cy).scale(s).translate(54, 54).toString());

  const sheetD = toGrid(await trace(sheetMask, w, h));
  const faceDs = [];
  for (const fm of faceMasks) faceDs.push(toGrid(await trace(fm, w, h)));

  const th = Math.max(LAYOUT.barMin, ...details.bars.map((b) => (b.y2 - b.y1 + 1) * s));
  const barsD = details.bars.map((b) => bar(gx(b.x1), gx(b.x2 + 1), gy((b.y1 + b.y2 + 1) / 2), th)).join(' ');
  const p = details.play;
  const ph = (p.y2 - p.y1 + 1) * s;
  const playD = playTriangle(gx(p.x1), gy(p.y1), gx(p.x2 + 1), gy(p.y2 + 1), ph * LAYOUT.playRound * 2);

  // Colours sampled from the concept.
  const pix = (mask) => { const out = []; mask.forEach((v, i) => { if (v) out.push(rgb(i)); }); return out; };
  const rimInner = erode(silhouette, w, h, LAYOUT.rimPx);
  const rim = silhouette.map((v, i) => (v && !rimInner[i] ? 1 : 0));
  // Brightest third of the rim band: the band's edge pixels are anti-aliased into the background.
  const rimPix = (top) => {
    const out = [];
    rim.forEach((v, i) => { const y = (i / w) | 0; if (v && (top ? y < mark.y1 + 40 : y > mark.y2 - 40)) out.push(rgb(i)); });
    return out.sort((a, b) => lum(b) - lum(a)).slice(0, Math.ceil(out.length / 3));
  };
  const bgPix = []; for (let y = 30; y < 80; y++) for (let x = 30; x < 80; x++) bgPix.push(rgb(y * w + x));
  const bgCentre = []; for (let y = 440; y < 480; y++) for (let x = 200; x < 314; x++) bgCentre.push(rgb(y * w + x));
  const half3 = details.bars.length >> 1;
  const colors = {
    'bg-center': hex(mean(bgCentre)),
    'bg-edge': hex(mean(bgPix)),
    'sheet-start': hex(m.rowRef[mark.y1 + 10]),
    'sheet-end': hex(m.rowRef[mark.y2 - 10]),
    'back-start': hex(mean(pix(backFaces[0].mask))),
    'back-end': hex(mean(pix(backFaces[1].mask))),
    'rim-start': hex(mean(rimPix(true))),
    'rim-end': hex(mean(rimPix(false))),
    'ink-start': hex(mean(details.bars.slice(0, half3).flatMap((b) => b.pixels.map(rgb)))),
    'ink-end': hex(mean(details.bars.slice(half3).flatMap((b) => b.pixels.map(rgb)))),
  };
  const yTop = gy(mark.y1), yBot = gy(mark.y2 + 1);
  const inkTop = gy(details.bars[0].y1), inkBot = gy(details.bars.at(-1).y2 + 1);
  const backTop = gy(backFaces[0].cy), backBot = gy(backFaces[1].cy);
  const rimW = r2(LAYOUT.rimPx * s);
  const stop = (name, off) => `      <stop offset="${off}" style="stop-color: var(--fr-${name}, ${colors[name]})" />`;
  const lin = (id, a, b, y1, y2) => `    <linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="0" y1="${y1}" x2="0" y2="${y2}">
${stop(a, 0)}
${stop(b, 1)}
    </linearGradient>`;
  const wrap = (d) => d.replace(/(?<=Z)(?=M)/g, '\n         ');

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1080" height="1080">
  <title>Flow Reader mark</title>
  <!--
    Master source for every Flow Reader icon, traced from brand/concept/flow-reader-concept.jpg by
    brand/trace/trace.mjs (re-running it overwrites this file; hand edits are fine otherwise).
    108-unit grid = Android adaptive icon canvas: the launcher shows the centre 72 units; keep foreground
    art inside the 66-unit circle around (54,54).
    Colours: override the fr-* CSS custom properties (inline SVG / CSS); var() fallbacks are the brand defaults.
    data-mono: body = silhouette that holes cut through, solid = extra silhouette shape, hole = cut-out,
    skip = colour-only detail. data-stat="skip" drops a shape from the 24dp status-bar icon.
    Regenerate Android resources: node brand/build.mjs
  -->
  <defs>
    <radialGradient id="g-bg" gradientUnits="userSpaceOnUse" cx="54" cy="54" r="76">
${stop('bg-center', 0)}
${stop('bg-edge', 1)}
    </radialGradient>
${lin('g-sheet', 'sheet-start', 'sheet-end', yTop, yBot)}
${lin('g-back', 'back-start', 'back-end', backTop, backBot)}
${lin('g-rim', 'rim-start', 'rim-end', yTop, yBot)}
${lin('g-ink', 'ink-start', 'ink-end', inkTop, inkBot)}
  </defs>

  <g id="background">
    <path d="M0,0H108V108H0Z" fill="url(#g-bg)" />
  </g>

  <g id="foreground">
    <path id="sheet" data-mono="body" fill="url(#g-sheet)"
      stroke="url(#g-rim)" stroke-width="${rimW}" stroke-linejoin="round"
      d="${wrap(sheetD)}" />
${faceDs.map((d, i) => `    <path id="roll-${i === 0 ? 'top' : 'bottom'}" data-mono="skip" data-stat="skip" fill="url(#g-back)"
      stroke="url(#g-rim)" stroke-width="${rimW}" stroke-linejoin="round"
      d="${wrap(d)}" />`).join('\n')}
    <path id="lines" data-mono="hole" data-stat="skip" fill="url(#g-ink)"
      d="${wrap(barsD)}" />
    <path id="play" data-mono="hole" fill="url(#g-ink)"
      d="${playD}" />
  </g>
</svg>
`;
  writeFileSync(MASTER, svg);
  console.log(`wrote brand/flow-reader-mark.svg (scale ${s.toFixed(4)} grid units/px, rim ${rimW})`);
  return { cx, cy, s };
}

const isMain = process.argv[1]
  && file(new URL(import.meta.url)).toLowerCase() === process.argv[1].replace(/\\/g, '/').toLowerCase();
if (isMain) await run();
