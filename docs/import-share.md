# Import and share

Shared text and URLs enter through **ShareIngress**, then **Import → Router** (and Parser for crawls). Book files can also arrive via Open-with / SEND EPUB on MainActivity.

## Share targets

| Target | MIME | Activity |
|--------|------|----------|
| Flow Reader (alias) | `text/plain` | ShareIngress |
| Open with / VIEW | EPUB, TXT | MainActivity |
| SEND EPUB | `application/epub+zip` | MainActivity |

See [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml).

## Pipeline

1. ShareIngress reads `EXTRA_TEXT`
2. [`ShareRouter.decide`](../app/src/main/java/com/personal/flowreader/share/ShareRouter.kt) with prefs + router + parse rules
3. Outcomes: Files, Queue, Crawl (fetch page), RoyalRoadPlugin, or ShowChooser
4. Optional floating overlay ([`ShareOverlay.kt`](../app/src/main/java/com/personal/flowreader/share/ShareOverlay.kt)): Plugin vs Parse/Queue, Cancel — needs `SYSTEM_ALERT_WINDOW` when Manual Override is Ask
5. `ShareDispatch` → MainActivity `ACTION_EXECUTE` → library ingest

![Import Router](images/settings-import.png)

## Intended defaults

1. EPUB / book files → **Files**
2. Clipboard / raw text / most URLs → **Queue**
3. Plugin-owned URLs (e.g. royalroad.com) → **Plugin**
4. Remaining URLs → parse rules → crawl to Queue or Files

Treat as a URL only when the **entire** shared string is a URL.

## Web ingest

[`WebPageIngest.kt`](../app/src/main/java/com/personal/flowreader/share/WebPageIngest.kt) + Parser CSS selectors (content / title / remove).

## Related UI

Configure rules under Settings → **Import** ([settings.md](settings.md)). Custom shelves from Library → Add a tab appear as destinations.

## Source

- [`ShareIngressActivity.kt`](../app/src/main/java/com/personal/flowreader/share/ShareIngressActivity.kt)
- [`ShareRouter.kt`](../app/src/main/java/com/personal/flowreader/share/ShareRouter.kt)
- [`ShareModels.kt`](../app/src/main/java/com/personal/flowreader/share/ShareModels.kt)
- [`ShareDomainRules.kt`](../app/src/main/java/com/personal/flowreader/share/ShareDomainRules.kt)
- [`SharingSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SharingSettingsTab.kt)

[Back to hub](README.md)
