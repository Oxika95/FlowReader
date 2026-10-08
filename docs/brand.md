# Brand

The Flow Reader mark: a scroll whose edges bend in an S, rolled at the top-right and bottom-left, with
text lines and a play button. One master SVG generates every Android icon.

## Source and generation

| File | Role |
| --- | --- |
| `brand/concept/flow-reader-concept.jpg` | The design concept (514px). Reference for tracing. |
| `brand/trace/` | Traces the concept into the master and scores it (see Tracing). Dev-only Node deps. |
| `brand/flow-reader-mark.svg` | Master. 108-unit grid = adaptive icon canvas. Groups `#background`, `#foreground`. |
| `brand/build.mjs` | `node brand/build.mjs` writes the generated resources below. No dependencies. |

Edit the SVG (or re-trace), run `build.mjs`, commit both. Never hand-edit generated files (they say so
in a header).

Generated (`app/src/main/res/`):

| File | Use |
| --- | --- |
| `drawable/ic_launcher_background.xml` | Adaptive icon background (radial navy). |
| `drawable/ic_launcher_foreground.xml` | Adaptive icon foreground; also the Android 12+ splash icon. |
| `drawable/ic_launcher_monochrome.xml` | Android 13+ themed icon (launcher tints it from the wallpaper). |
| `drawable/ic_stat_flow.xml` | Status-bar icon (TTS notification). 24dp, 20dp live area. |
| `values/brand_colors.xml` | `brand_*` colours from the SVG defaults (splash background uses `brand_bg_center`). |

Hand-written: `mipmap-anydpi/ic_launcher.xml` (adaptive icon: background, foreground, monochrome),
`values-v31/themes.xml` (`Theme.FlowReader.Main` splash attributes, set on `MainActivity` only so the
translucent share activity is unaffected).

## Rules for editing the master

- Foreground art stays inside the 66-unit circle around (54,54); the launcher shows only the centre
  72 units and masks the shape (circle, squircle, ...). No baked-in frame, padding, or rounded corners.
- Paths only (`<path d fill stroke stroke-width stroke-linejoin>`), paints are a colour or
  `url(#gradient)`; gradients use
  `gradientUnits="userSpaceOnUse"` and stops `style="stop-color: var(--fr-name, #hex)"`.
- `data-mono`: `body` (silhouette), `hole` (cut out of the body, must lie inside it), `solid` (extra
  silhouette shape), `skip` (colour-only detail, e.g. the roll back faces). `data-stat="skip"` drops
  detail too thin for 24dp.
- No `--` inside XML comments (breaks the SVG).

## Tracing

Hand-tuning curve coordinates to match a raster concept doesn't converge; the geometry comes from the
image instead.

```powershell
cd brand\trace
npm install         # once
npm run trace       # overwrites ..\flow-reader-mark.svg; mask previews in out\
npm run compare     # IoU vs the concept + out\overlay.png; exits 1 below target
node ..\build.mjs   # regenerate Android resources
```

| Mask (`masks.mjs`) | How it is found |
| --- | --- |
| Silhouette | Pixels far from the background colour; the component at the image centre (drops the frame and glow); holes filled. |
| Details | Pixels lighter than the sheet colour at that height (a robust linear fit of sheet colour against y). Wide bars = text lines; largest compact blob = play. |
| Roll back faces | Magic-wand growth from a seed point per roll (`TUNING.rollSeeds`); follows the face gradient, stops at the rim. |

`trace.mjs` erodes the silhouette and dilates the faces by half the rim width (so the rim stroke's outer
edge lands on the concept's edge), majority-filters the masks, runs potrace, and maps concept pixels to
the grid (silhouette bbox centre to (54,54), farthest pixel to radius 33). Simplified for 48dp: text bars
become clean rounded bars of at least 2 units, the play button a rounded triangle fitted to its box.
Colours are sampled from the concept.

`compare.mjs` renders the master back into concept pixels: silhouette IoU (intersection over union)
must be at least 0.95 (currently 0.99). Details IoU is lower by design (thickened bars). Tune
`TUNING` / `LAYOUT` rather than editing coordinates by hand. A new concept: replace the JPEG, update
`rollSeeds`, re-run.

## Recolouring and resizing

The SVG is resolution-independent. Colours are CSS custom properties with brand defaults
(`--fr-bg-center`, `--fr-bg-edge`, `--fr-sheet-start/end`, `--fr-back-start/end`, `--fr-rim-start/end`,
`--fr-ink-start/end`).
Inline the SVG in HTML and set them, e.g. `svg { --fr-sheet-start: #F2A65A; }`. Custom properties do
not reach an SVG loaded through `<img>`; use inline SVG or edit the defaults.

## Theme colours

Android cannot recolour a launcher icon at runtime. The icon is fixed to the brand palette; the
monochrome layer gives Android 13+ users wallpaper-tinted themed icons. The splash is drawn before the
app process reads settings, so it also uses the brand palette, not the in-app accent.

## Not shipped

Legacy PNG launcher icons (minSdk 26 makes them unused), Play Store icon and feature graphic
(releases are GitHub APKs).
