# Tokens

All in [`ui/theme/`](../../app/src/main/java/com/personal/flowreader/ui/theme/). Feature code uses
tokens only; a missing value means a missing token (add it here and in code).

## `FlowTokens` (`FlowTokens.kt`)

| Group | Members | Use |
| --- | --- | --- |
| `Space` | `None 0`, `Hair 2`, `XS 4`, `S 8`, `M 12`, `L 16`, `XL 24`, `XXL 32` dp | All gaps |
| `Pad` | `Screen 16`, `CardBody 16`, `FloatingCard 8`, `RowV 12`, `HeaderStart/End/Top`, `HeaderTitleStart` | Semantic padding |
| `Icon` | `S 16`, `M 20`, `L 24`, `XL 28`, `Hero 36` | Icon sizes |
| `Comp` | `ChipHeight`, `ButtonSecondary 40`, `ButtonPrimary 48`, `FieldHeight`, `TabBar 48`, `TabIndicatorPrimary 3`, `TabIndicatorSecondary 2`, `TabInnerPad`, `TabGap`, `ListRow 104`, `Fab 56`, `CircleButton 36`, `ProgressBar 3`, `GridMinCell 140`, `SegmentStrip`, `FabClearance`, `DismissTarget`, `CardMaxWidth 640`, `CompactCardMaxWidth 420` | Component metrics |
| `Radius` / `Shape` | `Shape.Card` (16), `Shape.Field` (12), `Shape.Pill` | Shapes |
| `Stroke` | `Hairline 1` | Borders |
| `Elevation` | `None` | Surfaces are flat |
| `Feather` | `None`, `Panel 20` | Halo outside panel cards (not in layout) |
| `Scrim` | `Standard .42`, `Hero .66`, `Busy .42`, `ParentScale .97` | Overlay scrims |
| `Dock` | `EdgePad 16`, `Gap 16` | Dock spacing |
| `Alpha` | `Disabled`, `Track`, `TrackOnDark`, `Divider`, `OnCoverMuted`, `CoverBand`, `CircleOnCover`, `CircleRingOnCover`, `TextShadow`, `SegmentBehind`, `SegmentEmpty` | Alphas |
| Cover | `CoverAspect 2:3`, `CoverBandBlack`, `CoverMutedWhite`, `CoverBlur`, `CoverGradientHeight`, `ShelfGradientHeight`, `HighlightRadius`, `NeutralCacheGray`, `CoverEdge.{Row,Tile,Banner,Hero}` (decode px) | Cover art |
| State | `MatchGreen` (positive outline; negative uses `colorScheme.error`) | Valid/invalid field outlines |

## `FlowType` (`FlowType.kt`)

Composable getters over `MaterialTheme.typography`, named by role:
`cardTitle`, `sectionTitle`, `subsectionTitle`, `tabPrimary`, `tabSecondary`, `rowTitle`, `body`,
`hint`, `label`, `action`, `displayTitle`, `tileTitle`, `mediaTitle`, `placeholder`.

Use the role, not the M3 slot: `FlowType.hint`, not `MaterialTheme.typography.bodySmall`.

## `FlowMotion` (`FlowMotion.kt`)

`Short 150` / `Medium 250` ms. Transitions by role: `scrimEnter/Exit`, `fullscreenEnter/Exit`,
`dockEnter/Exit(fromTop)`, `popEnter/Exit`, `expandEnter/Exit`. Components already apply them.

## `FlowLayer` (`FlowLayer.kt`)

| Layer | z | Holds |
| --- | --- | --- |
| `Content` | 0 | Lists, reading column, tab bars |
| `ContentScrim` | 10 | Busy scrim, reader tap bands |
| `Dock` | 20 | Top/bottom floating docks |
| `Action` | 30 | FAB, snackbar |
| `Fullscreen` | 40 (+depth) | Fullscreen card stack |
| `System` | 90 | Debug bubble |

Apply with `Modifier.zIndex(FlowLayer.X.z)`; never use literal z values.

## Color

Colors come from `MaterialTheme.colorScheme` (page theme from Settings > Layout). On covers use
`CoverBandBlack` / `CoverMutedWhite` / `FlowCircleStyle.OnCover`. Accent = `primary`; destructive
= `error`.
