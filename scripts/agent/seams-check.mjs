// Completeness checks: couplings (docs/agent/seams.md) and registry (docs/agent/registry.md).
// Prints failures only; exit 1 on any.
// Usage: node scripts/agent/seams-check.mjs [--init] [--write-allow] [--write]
//   --init         write scripts/seams-allow.txt and scripts/registry-allow.txt from what is uncovered today
//   --write-allow  drop allowlist entries that are now covered or no longer exist (the lists only shrink)
//   --write        fill the `[rg: pattern] -> files` lists in seams.md (otherwise a stale list fails the check)
// Missing seams.md / registry.md are created as stubs, and a missing allowlist is created from the current gaps.
import { existsSync, writeFileSync } from 'node:fs';
import { basename, join } from 'node:path';
import { globToRegExp, isMain, readList, readText, rel, walk } from './lib.mjs';
import { APP_ROOT, KOTLIN_ROOT, PLUGINS_ROOT } from './paths.mjs';

const SEAMS = join(APP_ROOT, 'docs/agent/seams.md');
const REGISTRY = join(APP_ROOT, 'docs/agent/registry.md');
const SEAMS_ALLOW = join(APP_ROOT, 'scripts/seams-allow.txt');
const REGISTRY_ALLOW = join(APP_ROOT, 'scripts/registry-allow.txt');
const MAIN_SRC = join(APP_ROOT, 'app/src/main');

const SEAMS_STUB = `# Couplings

Lists the places where one change has to be matched by another: shared flows and keys, call orders, locks,
file layouts, tuned constants. Phase 2 fills this; until then \`scripts/seams-allow.txt\` holds every item
that \`scripts/agent/seams-check.mjs\` finds in the code and that isn't written here yet.
`;

const REGISTRY_STUB = `# Registry

One entry per feature: vocabulary, owner file, files that change together, tests, rules, pitfalls.
Phase 2 fills this; until then \`scripts/registry-allow.txt\` holds every file and plugin folder that no
\`Owner:\` or \`Changes with:\` line covers yet.
`;

// ---------- code extraction ----------

const stem = (file) => basename(file).replace(/\.[^.]+$/, '');

function loadKotlin() {
  return walk(KOTLIN_ROOT, { filter: (f) => f.endsWith('.kt') }).map((abs) => ({ abs, rel: rel(KOTLIN_ROOT, abs), text: readText(abs) }));
}

function constMap(files) {
  const global = new Map();
  const local = new Map();
  for (const f of files) {
    const m = new Map();
    for (const c of f.text.matchAll(/\bconst val (\w+)\s*(?::\s*String)?\s*=\s*"([^"]*)"/g)) {
      m.set(c[1], c[2]);
      if (!global.has(c[1])) global.set(c[1], c[2]);
    }
    local.set(f.rel, m);
  }
  return (file, name) => local.get(file)?.get(name) ?? global.get(name);
}

function extract(files) {
  const lookup = constMap(files);
  const items = [];
  const add = (item) => items.push(item);

  const resolveArg = (file, quoted, ident) => (quoted !== undefined ? quoted : lookup(file, ident));

  for (const f of files) {
    const lines = f.text.split('\n');

    if (basename(f.rel) === 'FlowApp.kt') {
      for (const line of lines) {
        const m = /^    (?:(?:private|internal|protected|override|lateinit|@\w+(?:\([^)]*\))?)\s+)*(val|var)\s+(\w+)/.exec(line);
        if (m && !/^\s+private\b/.test(line) && !/\bprivate\s+(lateinit\s+)?(val|var)\b/.test(line)) {
          add({ kind: 'flowapp', name: m[2], aliases: [m[2]] });
        }
      }
      for (const m of f.text.matchAll(/^\s+(\w+)\.(\w+)\s*=\s*/gm)) {
        if (['pluginBooks'].includes(m[1])) add({ kind: 'flowapp', name: `${m[1]}.${m[2]}`, aliases: [`${m[1]}.${m[2]}`, m[2]] });
      }
    }

    lines.forEach((line) => {
      const ind = /^( *)/.exec(line)[1].length;
      if (ind > 4 || /^\s*(private|protected|internal)\b/.test(line)) return;
      const m = /^\s*(?:override\s+|lateinit\s+)*(?:val|var)\s+(\w+)\s*(?::\s*([^=]*))?(?:=\s*(.*))?$/.exec(line);
      if (!m) return;
      const typed = m[2] && /\b(Flow|StateFlow|SharedFlow|MutableStateFlow|MutableSharedFlow)</.test(m[2]);
      const built = m[3] && /(\.asStateFlow\(|\.asSharedFlow\(|\.stateIn\(|\.shareIn\(|\bMutable(State|Shared)Flow\()/.test(m[3]);
      if (typed || built) add({ kind: 'flow', name: `${stem(f.rel)}.${m[1]}`, aliases: [m[1]], qualifier: stem(f.rel) });
    });

    for (const m of f.text.matchAll(/\b\w*PreferencesKey\(\s*"([^"]+)"/g)) add({ kind: 'key', name: m[1], aliases: [m[1]] });

    for (const m of f.text.matchAll(/\bconst val (\w*CHANNEL_ID)\s*=\s*"([^"]+)"/g)) add({ kind: 'intent', name: m[2], aliases: [m[2], m[1]] });
    for (const m of f.text.matchAll(/\bNotificationChannel\(\s*"([^"]+)"/g)) add({ kind: 'intent', name: m[1], aliases: [m[1]] });

    for (const m of f.text.matchAll(/\benqueueUnique\w*Work\(\s*(?:"([^"]+)"|(\w+))/g)) {
      const v = resolveArg(f.rel, m[1], m[2]);
      if (v) add({ kind: 'intent', name: v, aliases: [v] });
    }

    for (const m of f.text.matchAll(/\bcomposable\(\s*(?:route\s*=\s*)?(?:"([^"]+)"|(\w+))/g)) {
      const v = resolveArg(f.rel, m[1], m[2]);
      if (v) add({ kind: 'route', name: v, aliases: [v, v.split('?')[0]] });
    }

    for (const m of f.text.matchAll(/\bconst val (\w*ACTION\w*)\s*=\s*"([^"]+)"/g)) {
      const tail = m[2].split('.').slice(-2).join('.');
      add({ kind: 'intent', name: m[2], aliases: [m[2], m[1], m[2].split('.').pop(), tail] });
    }
    for (const m of f.text.matchAll(/(?:\.action\s*=|setAction\(|addAction\()\s*"([^"]+)"/g)) add({ kind: 'intent', name: m[1], aliases: [m[1]] });
  }

  const seen = new Set();
  return items.filter((i) => {
    const key = `${i.kind}: ${i.name}`;
    if (seen.has(key)) return false;
    seen.add(key);
    i.id = key;
    return true;
  });
}

// ---------- doc matching ----------

function wordRe(s) {
  return new RegExp(`(^|[^\\w])${s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}($|[^\\w])`);
}

function documented(item, seamsLines) {
  const hasAlias = (line) => item.aliases.some((a) => (/^\w+$/.test(a) ? wordRe(a).test(line) : line.includes(a)));
  return seamsLines.some((line) => hasAlias(line) && (!item.qualifier || line.includes(item.qualifier)));
}

// ---------- registry ----------

function registryTokens(text) {
  const tokens = [];
  for (const line of text.split('\n')) {
    const m = /^\s*[-*]\s*(Owner|Changes with):\s*(.*)$/i.exec(line);
    if (!m) continue;
    for (const raw of m[2].replace(/`/g, ' ').split(/[,;\s]+/)) {
      const t = raw.replace(/^\(+|[).:]+$/g, '').replace(/\/$/, '');
      if (t && !t.startsWith('<')) tokens.push(t);
    }
  }
  return tokens;
}

const KOTLIN_PREFIXES = ['app/src/main/java/com/personal/flowreader/', 'com/personal/flowreader/'];

function covers(token, relPath) {
  let t = token.replace(/\\/g, '/');
  for (const p of KOTLIN_PREFIXES) if (t.startsWith(p)) t = t.slice(p.length);
  if (t.includes('*')) return globToRegExp(t).test(relPath);
  if (relPath === t || relPath.startsWith(`${t}/`)) return true;
  return !t.includes('/') && basename(relPath) === t;
}

function pluginFolders() {
  if (!existsSync(PLUGINS_ROOT)) return [];
  return walk(PLUGINS_ROOT, { filter: (f) => basename(f) === 'plugin.json' })
    .map((f) => rel(PLUGINS_ROOT, f))
    .filter((p) => p.split('/').length === 2)
    .map((p) => p.split('/')[0])
    .sort();
}

function pluginCovered(token, folder) {
  const t = token.replace(/\\/g, '/').replace(/^(\.\.\/)?flow-reader-plugins\//, '');
  return t === folder || t.startsWith(`${folder}/`);
}

// ---------- seam file lists ----------

function fillLists(seamsText, files, write) {
  const sources = files.map((f) => ({ name: basename(f.rel), text: f.text }));
  for (const abs of walk(MAIN_SRC, { filter: (f) => f.endsWith('.xml') })) sources.push({ name: basename(abs), text: readText(abs) });
  if (existsSync(PLUGINS_ROOT)) {
    for (const abs of walk(PLUGINS_ROOT, { filter: (f) => /index\.js$/.test(f) && rel(PLUGINS_ROOT, f).split('/').length === 2 })) {
      sources.push({ name: rel(PLUGINS_ROOT, abs), text: readText(abs) });
    }
  }
  const stale = [];
  const out = seamsText.split('\n').map((line) => {
    const m = /^(\s*-\s*\w+:\s*.*?\[rg:\s*(.+?)\s*\])(?:\s*->\s*([^;]*?))?\s*(;.*)?$/.exec(line);
    if (!m) return line;
    let re;
    try {
      re = new RegExp(m[2]);
    } catch {
      return line;
    }
    const hits = [...new Set(sources.filter((s) => re.test(s.text)).map((s) => s.name))].sort().join(', ');
    if ((m[3] || '') !== hits) stale.push(m[2]);
    return `${m[1]} -> ${hits}${m[4] ? m[4] : ''}`;
  });
  if (write && stale.length) writeFileSync(SEAMS, out.join('\n'));
  return stale;
}

// ---------- main ----------

function writeList(file, header, lines) {
  writeFileSync(file, `${header}\n${[...lines].sort().join('\n')}${lines.length ? '\n' : ''}`);
}

function main() {
  const args = new Set(process.argv.slice(2));
  const notes = [];
  if (!existsSync(SEAMS)) {
    writeFileSync(SEAMS, SEAMS_STUB);
    notes.push('created docs/agent/seams.md stub');
  }
  if (!existsSync(REGISTRY)) {
    writeFileSync(REGISTRY, REGISTRY_STUB);
    notes.push('created docs/agent/registry.md stub');
  }

  const files = loadKotlin();
  const items = extract(files);
  let seamsText = readText(SEAMS);
  const failures = [];

  const stale = fillLists(seamsText, files, args.has('--write'));
  if (args.has('--write')) seamsText = readText(SEAMS);
  else for (const p of stale) failures.push(`seams: file list for [rg: ${p}] is stale; run node scripts/agent/seams-check.mjs --write`);

  const seamsLines = seamsText.split('\n');
  const uncovered = items.filter((i) => !documented(i, seamsLines));

  const regText = readText(REGISTRY);
  const tokens = registryTokens(regText);
  const kt = files.map((f) => f.rel);
  const regGaps = kt.filter((p) => !tokens.some((t) => covers(t, p)));
  const plugGaps = pluginFolders().filter((d) => !tokens.some((t) => pluginCovered(t, d))).map((d) => `plugin:${d}`);
  const registryUncovered = [...regGaps, ...plugGaps];

  const init = args.has('--init');
  const prune = args.has('--write-allow');

  const seamsHeader = '# Couplings in the code that docs/agent/seams.md doesn\'t cover yet. Entries only leave this list.\n# Format: <kind>: <name>, as printed by scripts/agent/seams-check.mjs.';
  const regHeader = '# Files and plugin folders that no Owner:/Changes with: line in docs/agent/registry.md covers yet.\n# Entries only leave this list. A file is a path under app/src/main/java/com/personal/flowreader/.';

  const seamsAllowRaw = readList(SEAMS_ALLOW);
  if (init || seamsAllowRaw === null) {
    writeList(SEAMS_ALLOW, seamsHeader, uncovered.map((i) => i.id));
    notes.push(`wrote scripts/seams-allow.txt (${uncovered.length} entries); review`);
  } else {
    const allow = new Set(seamsAllowRaw);
    for (const i of uncovered) if (!allow.has(i.id)) failures.push(`seams: ${i.id} is in the code but not in docs/agent/seams.md`);
    if (prune) {
      const need = new Set(uncovered.map((i) => i.id));
      const keep = seamsAllowRaw.filter((e) => need.has(e));
      if (keep.length !== seamsAllowRaw.length) writeList(SEAMS_ALLOW, seamsHeader, keep);
    }
  }

  const regAllowRaw = readList(REGISTRY_ALLOW);
  if (init || regAllowRaw === null) {
    writeList(REGISTRY_ALLOW, regHeader, registryUncovered);
    notes.push(`wrote scripts/registry-allow.txt (${registryUncovered.length} entries); review`);
  } else {
    const allow = new Set(regAllowRaw);
    for (const p of registryUncovered) if (!allow.has(p)) failures.push(`registry: ${p} has no Owner:/Changes with: entry in docs/agent/registry.md`);
    if (prune) {
      const need = new Set(registryUncovered);
      const keep = regAllowRaw.filter((e) => need.has(e));
      if (keep.length !== regAllowRaw.length) writeList(REGISTRY_ALLOW, regHeader, keep);
    }
  }

  for (const n of notes) console.log(n);
  for (const f of failures) console.log(f);
  process.exit(failures.length ? 1 : 0);
}

if (isMain(import.meta.url)) main();
