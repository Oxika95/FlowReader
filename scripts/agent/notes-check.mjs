// Checks that names and paths quoted in the notes still exist in the code. Prints failures only; exit 1 on any.
// Usage: node scripts/agent/notes-check.mjs [--repo <root>|all] [--app-only] [--full] [--list] [--warn]
//   --repo      which repo's notes to check (default: the app repo)
//   --app-only  check app notes only; index app + plugins (skip chats). Also FLOW_NOTES_APP_ONLY=1.
//               For CI where the private chats repo is absent.
//   --full      check every note in scope (also FLOW_NOTES_FULL=1); otherwise only notes whose content differs
//               from the last passing stamp (all notes when there is no stamp)
//   --list      print the failing tokens only, one per line (to build scripts/notes-allow.txt)
//   --warn      also print `file:line` citations whose line is past the end of the file
// Allowlist: scripts/notes-allow.txt in the app repo (one token per line, `#` comments), plus the same file
// in the repo being checked when that is not the app repo.
// Scope: see appendix J of the context v2 plan.
import { existsSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { isMain, lineCount, readJson, readList, readText, rel, walk } from './lib.mjs';
import { APP_ROOT, CHATS_ROOT, PLUGINS_ROOT, stateDir } from './paths.mjs';
import { contentMap } from './state-hash.mjs';

const NOT_CHECKED_DIRS = /(^|\/)(decisions|reviews|history|tasks|pilot|raw)\//;
/** Paths that live only in the private chats repo; skipped under --app-only. */
const CHATS_ONLY_PATH = /^(decisions|stages|areas|_config|raw|history|pilot|reviews|setup|tasks|backlog\.md)\b/;

const SCOPES = {
  [APP_ROOT]: (p) =>
    (p === 'AGENTS.md' ||
      (p.startsWith('docs/') && p.endsWith('.md')) ||
      p.startsWith('.cursor/rules/') ||
      p.startsWith('.cursor/commands/')) &&
    /\.(md|mdc)$/.test(p),
  [PLUGINS_ROOT]: (p) => (p === 'AGENTS.md' || p === 'CONTEXT.md' || p.startsWith('.cursor/rules/')) && /\.(md|mdc)$/.test(p),
  [CHATS_ROOT]: (p) =>
    (p === 'AGENTS.md' || p === 'CONTEXT.md' || p === 'backlog.md' || p.startsWith('_config/') || p.startsWith('areas/')) &&
    /\.md$/.test(p) &&
    !/^stages\/[^/]+\/[^/]+\/output\//.test(p),
};

const PATH_EXT = 'kt|kts|java|xml|md|mdc|mjs|js|json|ps1|txt|gradle|properties|toml|yml|yaml|png|pro|html|css|webp|svg';
const PATH_EXT_RE = new RegExp(`\\.(${PATH_EXT})$`, 'i');
const SOURCE_EXT = /\.(kt|java|xml|kts|json|txt|gradle|properties|toml|pro)$/;

// ---------- source index (one pass, in memory) ----------

function buildIndex(repos) {
  const words = new Set();
  const fileWords = new Map();
  const decl = new Map();
  const stems = new Set();
  const suffixes = new Set();
  const corpus = [];

  const addPath = (p, prefix = '') => {
    const parts = p.split('/');
    if (prefix) suffixes.add(`${prefix}/${p}`);
    for (let i = 0; i < parts.length; i++) suffixes.add(parts.slice(i).join('/'));
    const name = parts[parts.length - 1];
    const dot = name.lastIndexOf('.');
    stems.add(dot > 0 ? name.slice(0, dot) : name);
  };

  const isCode = (root, abs) => {
    const r = rel(root, abs);
    if (root === APP_ROOT) return r.startsWith('app/src/') && SOURCE_EXT.test(r);
    if (root === PLUGINS_ROOT) return /\.(js|mjs|json)$/.test(r) && !r.startsWith('test/fixtures-raw/');
    return false;
  };

  for (const root of repos) {
    const files = walk(root);
    const dirs = new Set();
    const prefix = basename(root);
    for (const abs of files) {
      const r = rel(root, abs);
      addPath(r, prefix);
      const parts = r.split('/');
      for (let i = 1; i < parts.length; i++) dirs.add(parts.slice(0, i).join('/'));
      if (!isCode(root, abs)) continue;
      const text = readText(abs);
      corpus.push(text);
      const local = new Set(text.match(/[A-Za-z_][A-Za-z0-9_]*/g));
      for (const w of local) words.add(w);
      fileWords.set(r, local);
      for (const m of text.matchAll(/\b(?:class|object|interface|typealias)\s+([A-Za-z_]\w*)/g)) {
        if (!decl.has(m[1])) decl.set(m[1], []);
        decl.get(m[1]).push(r);
      }
    }
    for (const d of dirs) addPath(d, prefix);
    suffixes.add(prefix);
  }
  return { words, fileWords, decl, stems, suffixes, corpus: corpus.join('\n') };
}

// ---------- token extraction ----------

function* noteTokens(text) {
  let inFence = false;
  let rejectedLevel = 0;
  const lines = text.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      if (rejectedLevel && level <= rejectedLevel) rejectedLevel = 0;
      if (/^rejected\b/i.test(heading[2])) rejectedLevel = level;
      continue;
    }
    if (rejectedLevel) continue;
    const plain = line.replace(/^[\s>*+-]+/, '').replace(/^\*\*|^__/, '');
    if (/^rejected:/i.test(plain)) continue;
    for (const m of line.matchAll(/`([^`\n]+)`/g)) yield { line: i + 1, token: m[1].trim(), link: false };
    for (const m of line.matchAll(/\]\(([^)\s]+)\)/g)) yield { line: i + 1, token: m[1], link: true };
  }
}

const SKIP_START = /^(\.\.\/|-|\$|~|\/|#|@|[A-Za-z]:[\\/])/;

/** Returns null when the token isn't checkable, else {kind, value, cite?}. */
function classify(raw, isLink) {
  let t = raw.trim();
  if (!t) return null;
  if (isLink) {
    if (/^(https?:|mailto:|#)/i.test(t)) return null;
    t = t.replace(/[#?].*$/, '');
    if (!t || t.startsWith('../')) return null;
    return { kind: 'link', value: t.replace(/^\.\//, '') };
  }
  if (/[*{<]|:\/\/|\.\.\./.test(t) || SKIP_START.test(t) || /^[0-9a-f]{7,40}$/.test(t)) return null;
  const paren = t.indexOf('(');
  if (paren > 0 && t.endsWith(')')) t = t.slice(0, paren);
  if (/\s/.test(t)) return null;
  t = t.replace(/[,.;:]+$/, '').replace(/^\.\//, '');
  if (!t || t.startsWith('../')) return null;
  const cite = /^(.+?):(\d+)(?:[-–,](\d+))?$/.exec(t);
  let target = t;
  let citeLine = 0;
  if (cite && (cite[1].includes('/') || PATH_EXT_RE.test(cite[1]))) {
    target = cite[1];
    citeLine = Math.max(Number(cite[2]), Number(cite[3] || 0));
  }
  if (!/^[\w@.+\-/]+$/.test(target.replace(/::/g, '.'))) return null;
  if (target.includes('/') || PATH_EXT_RE.test(target)) return { kind: 'path', value: target.replace(/\/$/, ''), cite: citeLine };
  const dotted = target.replace(/::/g, '.');
  if (/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/.test(dotted)) return { kind: 'member', value: t, parts: dotted.split('.') };
  if (/^[A-Za-z_]\w*$/.test(target)) return { kind: 'ident', value: target };
  return null;
}

function pathExists(index, value, noteAbsDir) {
  if (index.suffixes.has(value)) return true;
  return existsSync(join(noteAbsDir, value));
}

/** Returns a failure reason, or null when the token resolves. */
function verify(index, tok, noteAbsDir, { appOnly = false } = {}) {
  const { words, fileWords, decl, stems } = index;
  if (appOnly && (tok.kind === 'link' || tok.kind === 'path') && CHATS_ONLY_PATH.test(tok.value)) {
    return null;
  }
  switch (tok.kind) {
    case 'link':
      return pathExists(index, tok.value, noteAbsDir) ? null : 'link target not found';
    case 'path': {
      if (pathExists(index, tok.value, noteAbsDir)) return null;
      const segs = tok.value.split('/');
      const alternatives = segs.length > 1 && segs.every((s) => /^[A-Za-z_]\w*$/.test(s));
      if (alternatives && segs.every((s) => words.has(s) || stems.has(s))) return null;
      return 'path not found';
    }
    case 'ident':
      return words.has(tok.value) || stems.has(tok.value) ? null : 'name not found in source';
    case 'member': {
      const [first, ...rest] = tok.parts;
      if (index.corpus.includes(tok.parts.join('.'))) return null;
      if (!words.has(first) && !stems.has(first)) return `${first} not found in source`;
      const declaring = decl.get(first) || [];
      for (const seg of rest) {
        const inDeclaring = declaring.some((f) => fileWords.get(f).has(seg));
        if (!inDeclaring && !words.has(seg)) return `${seg} not found in source`;
      }
      return null;
    }
    default:
      return null;
  }
}

// ---------- main ----------

async function checkRepo(root, { full, index, allow }) {
  const scope = SCOPES[root];
  const map = await contentMap(root);
  let notes = Object.keys(map).filter((p) => scope(p) && !NOT_CHECKED_DIRS.test(p));
  if (!full) {
    const stamp = readJson(join(stateDir(root), 'stamp-pass.json'), null);
    if (stamp && stamp.map) notes = notes.filter((p) => stamp.map[p] !== map[p]);
  }
  return notes.sort();
}

async function main() {
  const argv = process.argv.slice(2);
  const flag = (n) => argv.includes(n);
  const repoIdx = argv.indexOf('--repo');
  const appOnly = flag('--app-only') || process.env.FLOW_NOTES_APP_ONLY === '1';
  const which = appOnly ? 'app' : repoIdx >= 0 ? argv[repoIdx + 1] : 'app';
  const full = flag('--full') || !!process.env.FLOW_NOTES_FULL;
  const repos = (which === 'all' ? [APP_ROOT, PLUGINS_ROOT, CHATS_ROOT] : [which === 'app' ? APP_ROOT : which]).filter((r) => existsSync(r));
  const indexRoots = (appOnly ? [APP_ROOT, PLUGINS_ROOT] : [APP_ROOT, PLUGINS_ROOT, CHATS_ROOT]).filter((r) => existsSync(r));

  const failures = [];
  const warnings = [];
  const tokens = new Set();
  let index = null;
  for (const root of repos) {
    if (!SCOPES[root]) throw new Error(`unknown repo ${root}`);
    const notes = await checkRepo(root, { full });
    if (notes.length === 0) continue;
    index ||= buildIndex(indexRoots);
    const allow = new Set([
      ...(readList(join(APP_ROOT, 'scripts/notes-allow.txt')) || []),
      ...(root === APP_ROOT ? [] : readList(join(root, 'scripts/notes-allow.txt')) || []),
    ]);
    for (const note of notes) {
      const abs = join(root, note);
      const text = readText(abs);
      const seen = new Set();
      for (const { line, token, link } of noteTokens(text)) {
        const tok = classify(token, link);
        if (!tok || allow.has(tok.value) || allow.has(token) || seen.has(tok.value)) continue;
        const why = verify(index, tok, dirname(abs), { appOnly });
        if (why) {
          seen.add(tok.value);
          tokens.add(tok.value);
          failures.push(`notes: ${rel(dirname(root), abs)}:${line} \`${token}\` (${why})`);
        } else if (tok.cite) {
          seen.add(tok.value);
          const hit = index.suffixes.has(tok.value) ? findFile(root, tok.value) : null;
          if (hit && lineCount(readText(hit)) < tok.cite) {
            warnings.push(`notes: ${rel(dirname(root), abs)}:${line} \`${token}\` line is past the end of the file; cite a symbol instead`);
          }
        }
      }
    }
  }

  if (flag('--list')) {
    for (const t of [...tokens].sort()) console.log(t);
  } else {
    for (const f of failures) console.log(f);
    if (flag('--warn')) for (const w of warnings) console.log(w);
  }
  process.exit(failures.length ? 1 : 0);
}

function findFile(root, value) {
  const direct = join(root, value);
  if (existsSync(direct)) return direct;
  const hit = walk(root).find((f) => rel(root, f).endsWith(`/${value}`));
  return hit || null;
}

if (isMain(import.meta.url)) await main();
