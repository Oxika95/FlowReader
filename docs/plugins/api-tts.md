# Plugin API: TTS providers

Part of the [Plugin API](api.md) (apiVersion 5).

TTS plugins synthesize speech. The app owns clip cache, prefetch scheduling, playback
(`TtsAudioEngine`), media session, and highlights. The plugin owns the remote protocol and
voice catalog.

## Manifest

```json
{
  "id": "edgetts",
  "name": "Edge TTS",
  "kind": "tts",
  "version": "1.0.0",
  "apiVersion": 5,
  "description": "Microsoft Edge Read Aloud voices",
  "allowedHosts": ["api.msedgeservices.com"],
  "minRequestIntervalMs": 0,
  "capabilities": ["clipAudio", "wordBoundaries", "prefetch", "networkLanes"]
}
```

| Field | Rules |
| --- | --- |
| `kind` | `"tts"` (source plugins omit it or use `"source"`). |
| `capabilities` | TTS-only: `clipAudio`, `wordBoundaries`, `prefetch`, `networkLanes`. |
| `allowedHosts` | Hosts the plugin may hit with `flow.fetch` / `flow.ws`. |
| Source fields | `bookIdPrefix`, `lists`, `auth`, `shareHosts` are ignored for TTS plugins. |

Engine key in Settings: `tts:{id}` (e.g. `tts:edgetts`).

## Functions

| Export | Result |
| --- | --- |
| `listVoices()` | `{ id, label, language, langTag, gender? }[]` — `language` is the Language dropdown label; optional `gender` is `"male"` / `"female"`. |
| `synthesize({ text, voice, lang, ratePercent, pitchPercent })` | `{ mp3Base64, boundaries? }` where `boundaries` is `{ offset, duration, text }[]` in 100-ns ticks. |

## Capabilities (app gating)

| Capability | App behavior when present |
| --- | --- |
| `clipAudio` | Per-clip MP3 cache, `TtsAudioEngine` path, clip size / prefetch UI |
| `wordBoundaries` | Word highlight from returned cues |
| `prefetch` | Lookahead synth jobs |
| `networkLanes` | Mobile-data / Wi-Fi overlap UI and staggered race |

Without a capability, the matching Settings controls stay hidden.

## Host: `flow.ws`

```js
const session = await flow.ws.collect({
  url: 'wss://…',
  headers: { Origin: '…', Cookie: 'muid=…' },
  messages: ['…speech.config…', '…ssml…'],
  endPath: 'turn.end',
  timeoutMs: 15000,
});
// { texts: string[], binariesBase64: string[] }
```

Opens a WebSocket (HTTP/1.1 upgrade), sends each string message in order, collects text and
binary frames until a text message whose headers include `Path: {endPath}` (or the socket
closes). Used by Edge Read Aloud; other TTS plugins may use `flow.fetch` instead.

## Host: `flow.crypto`

| Call | Result |
| --- | --- |
| `flow.crypto.sha256(inputBase64)` | Base64 (no wrap) of SHA-256 over the decoded input bytes. |
