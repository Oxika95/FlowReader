// Mechanical project rules for app/src/main (Kotlin and res/xml). Prints failures only; exit 1 on any.
// Usage: node scripts/agent/rules-check.mjs [--init] [--write-allow]
//   --init         write scripts/rules-allow.json from the current counts (owner reviews it)
//   --write-allow  lower allowlist counts that dropped, and drop entries that reached zero
// Env: FLOW_STAMP_BASE = JSON file with a content map {path: blobId} (or {map}) to measure file growth against.
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { git, isMain, lineCount, readJson, readText, rel, walk, writeJson } from './lib.mjs';
import { APP_ROOT } from './paths.mjs';

const ALLOW_FILE = join(APP_ROOT, 'scripts/rules-allow.json');
const SIZE_FILE = join(APP_ROOT, 'scripts/size-baseline.json');
const MAIN = join(APP_ROOT, 'app/src/main');
const SIZE_LIMIT = 500;
const SIZE_GROWTH = 20;

const BANNED = /\b(AlertDialog|Dialog|ModalBottomSheet|Card|Scaffold)\s*[({]/;
const LITERALS = [/\b\d+(\.\d+)?\.dp\b/, /alpha\s*=\s*0?\.\d+f/, /zIndex\(\s*-?\d/];
const PLUGIN_NAME = /royalroad|royal road|patreon|edgetts/i;
const STRING_LITERAL = /"((?:[^"\\]|\\.)*)"/g;

// Position writes: only AppPositionStore (and the plugin DAO call in PluginCatalog) may call a DAO's writePosition;
// only ProgressWriter.chapterSeek and the two session locators may construct a ProgressUpdate. Kotlin has no
// package-private, so a visibility modifier cannot stop a bypass inside the one app module.
const POSITION_WRITE_FILES = /\/(AppPositionStore|PluginCatalog)\.kt$/;
const PROGRESS_UPDATE_FILES = /\/(ProgressWriter|ReaderSessions|QueueBook)\.kt$/;

// HTTP clients: only net/HttpClients.kt builds an OkHttpClient (shared pool, always a callTimeout).
const HTTP_CLIENT_FILES = /\/net\/HttpClients\.kt$/;
const HTTP_CLIENT_BUILD = /\bOkHttpClient\s*(\.Builder\s*\(|\(\s*\))/;

// TTS clip cache: only TtsClipCache (with the names in ClipCacheNames) knows the on-disk file shapes, so
// parsing a clip name or touching the sidecar / temp suffixes elsewhere is a second owner.
const CLIP_CACHE_FILES = /\/tts\/(TtsClipCache|ClipCacheNames)\.kt$/;
const CLIP_CACHE_SHAPE = /ClipCacheNames\.(parse|isStale)\(|\.words\.json|\.part"/;

const inDesignSystem = (file) => /\/ui\/(design|theme)\//.test(file);

/** Code lines of a Kotlin file with `//` and block-comment lines removed: [{no, text}]. */
function codeLines(text) {
  const out = [];
  let inBlock = false;
  text.split('\n').forEach((raw, i) => {
    let line = raw;
    if (inBlock) {
      const end = line.indexOf('*/');
      if (end < 0) return;
      inBlock = false;
      line = line.slice(end + 2);
    }
    const t = line.trim();
    if (t.startsWith('//') || t.startsWith('*')) return;
    if (t.startsWith('/*')) {
      if (!t.includes('*/')) inBlock = true;
      return;
    }
    out.push({ no: i + 1, text: stripTrailingComment(line) });
  });
  return out;
}

function stripTrailingComment(line) {
  let quotes = 0;
  for (let i = 0; i < line.length - 1; i++) {
    const c = line[i];
    if (c === '\\') i++;
    else if (c === '"') quotes++;
    else if (c === '/' && line[i + 1] === '/' && quotes % 2 === 0 && line[i - 1] !== ':') return line.slice(0, i);
  }
  return line;
}

function xmlLines(text) {
  const out = [];
  let inComment = false;
  text.split('\n').forEach((line, i) => {
    let l = line;
    if (inComment) {
      const end = l.indexOf('-->');
      if (end < 0) return;
      inComment = false;
      l = l.slice(end + 3);
    }
    l = l.replace(/<!--.*?-->/g, '');
    const start = l.indexOf('<!--');
    if (start >= 0) {
      inComment = true;
      l = l.slice(0, start);
    }
    if (l.trim()) out.push({ no: i + 1, text: l });
  });
  return out;
}

/** hits: Map "rule\0file" -> [lineNo...] */
function addHit(hits, rule, file, no) {
  const key = `${rule}\0${file}`;
  if (!hits.has(key)) hits.set(key, []);
  hits.get(key).push(no);
}

function scan() {
  const files = walk(MAIN, { filter: (f) => f.endsWith('.kt') || (f.endsWith('.xml') && /[\\/]res[\\/]xml[\\/][^\\/]+$/.test(f)) });
  const hits = new Map();
  const sizes = {};
  for (const abs of files) {
    const file = rel(APP_ROOT, abs);
    const text = readText(abs);
    const isKt = file.endsWith('.kt');
    const lines = isKt ? codeLines(text) : xmlLines(text);
    if (isKt) {
      const n = lineCount(text);
      if (n > SIZE_LIMIT) sizes[file] = n;
    }
    const design = inDesignSystem(file);
    const inData = /\/flowreader\/data\//.test(file);
    for (const { no, text: line } of lines) {
      if (isKt && !design) {
        if (BANNED.test(line)) addHit(hits, 'banned', file, no);
        if (LITERALS.some((re) => re.test(line))) addHit(hits, 'literals', file, no);
      }
      if (isKt && !POSITION_WRITE_FILES.test(file) && /\.writePosition\(/.test(line)) addHit(hits, 'position-write', file, no);
      if (isKt && !PROGRESS_UPDATE_FILES.test(file) && /\bProgressUpdate\(/.test(line)) addHit(hits, 'position-update', file, no);
      if (isKt && !HTTP_CLIENT_FILES.test(file) && HTTP_CLIENT_BUILD.test(line)) addHit(hits, 'http-client', file, no);
      if (isKt && !CLIP_CACHE_FILES.test(file) && CLIP_CACHE_SHAPE.test(line)) addHit(hits, 'clip-cache', file, no);
      if (isKt && inData && /^\s*import\s+([\w.]*\.)?(ui\.|FlowApp\b)/.test(line)) addHit(hits, 'layering', file, no);
      STRING_LITERAL.lastIndex = 0;
      let m;
      while ((m = STRING_LITERAL.exec(line))) {
        if (PLUGIN_NAME.test(m[1])) addHit(hits, 'plugin-names', file, no);
      }
    }
  }
  return { hits, sizes };
}

function loadStampBase() {
  const f = process.env.FLOW_STAMP_BASE;
  if (!f || !existsSync(f)) return null;
  const j = readJson(f, null);
  if (!j) return null;
  return j.map || j;
}

function baseLineCount(baseMap, file) {
  const id = baseMap && baseMap[file];
  if (!id) return null;
  const text = git(APP_ROOT, ['cat-file', 'blob', id], { allowFail: true });
  return text == null ? null : lineCount(text.replace(/\r\n/g, '\n'));
}

function checkSizes(sizes, failures) {
  const existing = readJson(SIZE_FILE, null);
  const baseline = existing || {};
  const baseMap = loadStampBase();
  let changed = !existing;
  for (const [file, n] of Object.entries(sizes)) {
    const prev = baseline[file];
    // Stamp blob ids from hash-object without -w aren't in the object store, so
    // cat-file fails for dirty paths. Fall back to size-baseline.json, not 0.
    const fromStamp = baseMap ? baseLineCount(baseMap, file) : null;
    let base = fromStamp ?? prev;
    if (base == null) {
      baseline[file] = n;
      changed = true;
      continue;
    }
    if (n > base + SIZE_GROWTH) {
      failures.push(`size: ${file} has ${n} lines (base ${base}); files over ${SIZE_LIMIT} lines may grow by at most ${SIZE_GROWTH}`);
    }
    if (prev === undefined || n < prev) {
      baseline[file] = n;
      changed = true;
    }
  }
  for (const file of Object.keys(baseline)) {
    if (!(file in sizes)) {
      delete baseline[file];
      changed = true;
    }
  }
  if (changed) {
    const sorted = Object.fromEntries(Object.entries(baseline).sort(([a], [b]) => a.localeCompare(b)));
    writeJson(SIZE_FILE, sorted);
  }
}

function currentEntries(hits) {
  return [...hits.entries()]
    .map(([key, lines]) => {
      const [rule, file] = key.split('\0');
      return { rule, file, count: lines.length };
    })
    .sort((a, b) => a.rule.localeCompare(b.rule) || a.file.localeCompare(b.file));
}

function main() {
  const args = new Set(process.argv.slice(2));
  const { hits, sizes } = scan();
  const failures = [];
  const entries = currentEntries(hits);

  if (args.has('--init') || !existsSync(ALLOW_FILE)) {
    writeJson(ALLOW_FILE, { entries });
    console.log('wrote allowlist; review');
  } else {
    const allow = readJson(ALLOW_FILE, { entries: [] });
    const allowed = new Map(allow.entries.map((e) => [`${e.rule}\0${e.file}`, e.count]));
    for (const e of entries) {
      const a = allowed.get(`${e.rule}\0${e.file}`) ?? 0;
      if (e.count > a) {
        const lines = hits.get(`${e.rule}\0${e.file}`);
        const where = lines.length <= 8 ? lines.join(',') : `${lines.slice(0, 8).join(',')},...`;
        failures.push(
          a === 0
            ? `${e.rule}: ${e.file} lines ${where} (${e.count} new)`
            : `${e.rule}: ${e.file} lines ${where} (${e.count} found, ${a} allowed)`,
        );
      }
    }
    if (args.has('--write-allow')) {
      const next = entries.filter((e) => {
        const a = allowed.get(`${e.rule}\0${e.file}`);
        return a !== undefined && e.count > 0;
      }).map((e) => ({ ...e, count: Math.min(e.count, allowed.get(`${e.rule}\0${e.file}`)) }));
      if (JSON.stringify(next) !== JSON.stringify(allow.entries)) writeJson(ALLOW_FILE, { entries: next });
    }
  }

  checkSizes(sizes, failures);
  for (const f of failures) console.log(f);
  process.exit(failures.length ? 1 : 0);
}

if (isMain(import.meta.url)) main();
