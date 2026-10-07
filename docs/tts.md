# TTS

Listen while reading. Engines and voices live under Settings → **Audio**; the reader media card and gestures control playback.

![Audio Voice](images/settings-audio.png)

![Reader media chrome](images/reader-chrome.png)

## Engines

- **Edge TTS** — Neural voices (default `en-US-AndrewNeural`), clip prefetch and size controls
- **System Default** / other installed engines — Android TTS

Priority goals: stability → latency → footprint.

## Playback behavior

| Feature | Notes |
|---------|--------|
| Sentence highlight | Always |
| Word highlight | When engine provides boundaries |
| Auto-scroll | Follows playhead when enabled |
| Scroll lock | Forces follow; double-tap to unlock |
| Pin card | When playhead leaves the viewport |
| Margin rail | Cache / loading indicators for Edge clips |
| Media session | Notification + background keep-alive via `TtsPlaybackService` |
| Tonal underlay | Quiet noise while playing; optional standby until a paired A2DP device connects |

## Queue auto-advance

When listening to a queue item, finishing the book marks it done and opens the next unfinished item (`autoPlay=1`).

## Unspeakable sentences

- Text with no letters or digits after TTS-only filters (scene breaks like `* * *`, lone
  punctuation, text a filter emptied) is skipped silently; with debug on, the log gets a
  `skip i=… nothing to say` line.
- Edge rejecting a sentence (`EdgeContentException`: nothing to synthesize, too long, no audio
  returned) skips it and keeps playing: debug turns on, the log records
  `ERROR skipped i=…: <reason> text="…" spoken="…"`, and the log panel opens.
- Network/other failures still stop playback (logged as `ERROR`).
- A failed lookahead clip isn't retried by prefetch; the playhead retries or skips it when it gets
  there. Characters XML forbids (control chars, lone surrogates) are stripped before Edge SSML.

## Debug

About → Debug mode shows a synth dump FAB for Edge logs.

## Source

- [`tts/`](../app/src/main/java/com/personal/flowreader/tts/)
- [`TtsPlaybackService.kt`](../app/src/main/java/com/personal/flowreader/tts/TtsPlaybackService.kt)
- [`EdgeTtsClient.kt`](../app/src/main/java/com/personal/flowreader/tts/EdgeTtsClient.kt)
- [`AudioSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AudioSettings.kt)

[Back to hub](README.md)
