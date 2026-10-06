# Reader

Vertical reflow reading with immersive body text and floating chrome cards (no auto-dismiss timeout).

![Reader body](images/reader-body.png)

![Reader chrome](images/reader-chrome.png)

## Purpose

Show book text as a continuous vertical flow. Tap to reveal title + media cards; TTS follows with highlights and an optional scroll lock.

## UI map

### Body

- `LazyColumn` of paragraphs, headings, quotes
- Left **margin rail:** accent bar for current sentence; cache dots for Edge prefetched clips; loading pulse while synthesizing
- Word + sentence highlight while playing
- Edge fade at top/bottom

### Title banner (top card)

| Control | Action |
|---------|--------|
| Back | Pop to library |
| Title / chapter | Context |
| Progress bar | Reading progress |
| Settings | Same Settings overlay as library (includes Local filters) |

### Media card (bottom)

| Control | Action |
|---------|--------|
| Contents | TOC overlay |
| Prev / Next sentence | Seek TTS locus |
| Play / Pause | Start or pause TTS |
| Lock scroll to TTS | Hide chrome and force follow; unlock with **double-tap** on the lock-open control |

### Docks

Chrome lives in two floating docks (`FlowDock`), items stacked edge-inward with a fixed gap:

| Dock | Items (top to bottom) |
|------|------------------------|
| Top | Title banner (chrome open) · pin / jump chip (when the target is above) |
| Bottom | Pin / jump chip (when the target is below) · Media card (chrome open) · Scroll-lock unlock (locked) |

Settings, Contents and their child editors are fullscreen cards that stack; Back closes the top one.

### Current position and home

The **current position** is always a sentence, never a paragraph: the spoken sentence while TTS
plays, otherwise the saved locus sentence. The **home position** is a horizontal line in the
viewport (Settings → Layout → UI, 15–85% from the top, default 50%). Every jump settles the current
sentence's center on that line: TTS follow, pin tap, jump-back chip, double-tap, scroll lock, ToC,
and opening the book. A sentence taller than the screen starts at the top instead.

With **Show home marker** on, the reader draws a solid bar across the text column at home; its
thickness follows the text size. Drag the bar anywhere along its length to move home (a "Home N%"
label shows while dragging); on release, if the current sentence is on-screen it settles onto the
new line.

Math lives in [`ReaderHome.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderHome.kt)
(unit-tested); the rail reports the current sentence's line bounds (`SentenceSpan`).

### Pin / jump

- While playing and the spoken **sentence** is off-screen: **playback pin** with snippet — tap/double-tap resumes follow
- While paused and the saved **sentence** is off-screen: **Jump back to saved position**
- A paragraph that is still visible does not hide the chip if its current sentence has scrolled past
- Both dock at the edge nearest the off-screen sentence and return it to home

Free scrolling does **not** update the saved reading position; double-tap play / TTS seek does.
Leaving the reader does not pause TTS; the Library now-playing card controls it.

## Gestures

| Gesture | Result |
|---------|--------|
| Single tap body / gap / edge band | Toggle chrome (or clear selection / dismiss overlay) |
| Double tap body | Start TTS at sentence (if “Double-tap starts playback”); disabled while selecting |
| Swipe left from right edge (~24dp) | Back to library |
| Double-tap unlock | Exit scroll lock |

## TOC

![Contents](images/reader-toc.png)

Modal “Contents”; tap a chapter to seek and settle its first sentence at home.

## Text selection

System ActionMode: Copy, Share, Web search, Select all. Double-tap while selecting clears selection.

## Related settings

Layout (theme, font, spacing, screen awake), Audio (all Voice + Playback), Filters including **Local**.

## Source

- [`ReaderScreen.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderScreen.kt)
- [`ReaderChrome.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderChrome.kt)
- [`ReaderHome.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderHome.kt), [`HomeMarker.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/HomeMarker.kt)
- [`ReaderTouchPolicy.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderTouchPolicy.kt)
- [`TocOverlay.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/TocOverlay.kt)
- [`ReaderTextToolbar.kt`](../app/src/main/java/com/personal/flowreader/ui/reader/ReaderTextToolbar.kt)

[Back to hub](README.md)
