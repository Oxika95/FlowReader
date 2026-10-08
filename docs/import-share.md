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
3. Outcomes: Files, Queue, Crawl (fetch page), Plugin (by plugin id), or ShowChooser
4. Optional floating overlay ([`ShareOverlay.kt`](../app/src/main/java/com/personal/flowreader/share/ShareOverlay.kt)): Plugin vs Parse/Queue, Cancel — needs `SYSTEM_ALERT_WINDOW` when Manual Override is Ask
5. `ShareDispatch` → MainActivity `ACTION_EXECUTE` → library ingest

![Import Router](images/settings-import.png)

## Intended defaults

1. EPUB / book files → **Files**
2. Clipboard / raw text / most URLs → **Queue**
3. Plugin-owned URLs (each installed plugin's `shareHosts`, e.g. royalroad.com) → **Plugin**
4. Remaining URLs → parse rules → crawl to Queue or Files

The first `http(s)://` or `www.` URL in the shared text is used, even when surrounded by other text
(`UrlDetector.firstUrl`); text with no URL is plain text.

## Web ingest

[`WebPageIngest.kt`](../app/src/main/java/com/personal/flowreader/share/WebPageIngest.kt) fetches the
page HTML once (mobile Chrome user agent, no scripts run) and parses it with Jsoup.

- The first matching parse rule wins; the protected **Default** rule (`seed-parse-default`, any URL)
  is always last, can't be deleted or disabled, and only its parser mode / CSS are editable.
- **Default** parser: strips `script, style, nav, footer, aside, noscript, iframe`, then reads the
  first `article`, `[role=main]`, `main`, or `body`.
- **Custom** parser fields (CSS selectors, matched against the downloaded HTML):

  | Field | Behavior |
  | --- | --- |
  | Title | First match's text; else og:title / `<title>` |
  | Body | Every match, in page order; matches nested in another match are read once. No match or an invalid selector is an error (no fallback to the whole page). Blank = Default content heuristics |
  | Previous / Next Button | First match; link = its own `href`, else the enclosing `<a>`, else the first `<a href>` inside. Read before Remove runs |
  | Remove | Comma-separated; each entry is matched on the whole page and removes hits inside Body. Invalid entries are skipped (shown in Test), valid ones still apply |

  Custom strips only `script, style, noscript, iframe` first, so Body may target `nav`/`aside`.
- Selectors copied from a desktop browser can miss: the server may send different HTML to the
  mobile user agent, and content built by page scripts is not in the download. Use **Test** (shows
  `Body 3 · Title 1 · Next → … · Remove 2/3`) or **Pick**.

### Crawl (Next button)

When the matched rule is Custom with a Next selector, sharing asks **Crawl chapters?**:
**This page** (single import as before), **Crawl**, or Cancel.
[`WebCrawl`](../app/src/main/java/com/personal/flowreader/share/WebCrawl.kt) follows Next (about 400 ms
apart) until there is no Next link, a URL repeats, the rule's Crawl limit is reached, or **Stop** is
pressed (pages fetched so far are kept). A later page failing ends the crawl with the pages so far
and an error message; a failing first page imports nothing. Pages become one EPUB
([`EpubWriter`](../app/src/main/java/com/personal/flowreader/share/EpubWriter.kt): one chapter per page,
Title field as chapter title, nav ToC; book title = first page's `<title>`) landing per the router
rule (Queue or a Files tab). The same crawl produces the same bytes, so re-crawling updates one book.

### On-page picker

**Pick** in the Custom editor opens the Test URL in a WebView (live page, scripts on, same user agent)
with [`picker.js`](../app/src/main/assets/picker/picker.js) injected; links are disabled. The page
runs edge to edge under a field tab bar (Title · Body · Prev · Next · Remove). Tap an element,
adjust with the **Narrower** / **Wider** icons, then **Set** (Remove: **Add**); **Clear** empties the
field. The line under the page shows the picked selector (or the field's saved one) with its counts,
plus the picked text or a warning. Filled fields are outlined on the page. The close button cancels.
[`SelectorBuilder`](../app/src/main/java/com/personal/flowreader/share/SelectorBuilder.kt) builds the
selector against the separately downloaded HTML (what imports parse): id, class, class-qualified
ancestor, then positional path; single fields must match exactly one element, Remove may match many.
The panel shows `Page N · Import M`; **Not in fetched HTML, won't import** means the element only
exists after page scripts run (try Wider). **Done** writes the fields back to the editor.
- Text is saved paragraph by paragraph ([`HtmlParagraphs.kt`](../app/src/main/java/com/personal/flowreader/share/HtmlParagraphs.kt)):
  every block element, `<br>` and `<pre>` line ends a paragraph, so words never join across a
  break; text directly inside a `<div>` is kept and nested blocks are read once. Plugin chapter
  HTML uses the same rules.
- Shared/pasted raw text: blank lines separate paragraphs (single newlines fold to spaces, for
  hard-wrapped books); text with no blank line keeps one paragraph per line. Lone `\r` and Unicode
  line/paragraph separators count as newlines. Already-imported items keep their stored text.

## Related UI

Configure rules under Settings → **Import** ([settings.md](settings.md)). Custom shelves from Library → Add a tab appear as destinations.

## Source

- [`ShareIngressActivity.kt`](../app/src/main/java/com/personal/flowreader/share/ShareIngressActivity.kt)
- [`ShareRouter.kt`](../app/src/main/java/com/personal/flowreader/share/ShareRouter.kt)
- [`ShareModels.kt`](../app/src/main/java/com/personal/flowreader/share/ShareModels.kt)
- [`ShareDomainRules.kt`](../app/src/main/java/com/personal/flowreader/share/ShareDomainRules.kt)
- [`SharingSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SharingSettingsTab.kt)
- [`ImportParseRules.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/ImportParseRules.kt), [`PagePickerOverlay.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/PagePickerOverlay.kt)
- [`WebCrawlCards.kt`](../app/src/main/java/com/personal/flowreader/ui/library/WebCrawlCards.kt) (crawl prompt / progress)

[Back to hub](README.md)
