// Copied-code check: runs the pinned jscpd over the app's Kotlin and the plugins' index.js files and fails on
// clones that are new or occur more often than in scripts/jscpd-baseline.json. Prints failures only; exit 1 on any.
// Usage: node scripts/agent/dup-check.mjs [--init] [--write-allow]
//   --init         write the baseline from the current clones (only the owner approves baseline growth)
//   --write-allow  lower baseline counts that dropped, and drop clones that are gone
// A clone is keyed by a hash of its whitespace-collapsed fragment, not by file pair, so moving code between
// files doesn't change the key. Pairs across two plugin folders are ignored: plugins can't share code.
import { createHash } from 'node:crypto';
import { existsSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { isAbsolute, join, relative } from 'node:path';
import { isMain, readJson, writeJson } from './lib.mjs';
import { APP_ROOT, PLUGINS_ROOT, stateDir } from './paths.mjs';

const TOOLS = join(APP_ROOT, 'scripts/tools');
const JSCPD = join(TOOLS, 'node_modules/jscpd/run-jscpd.js');
const BASELINE = join(APP_ROOT, 'scripts/jscpd-baseline.json');
const COMMON = ['--min-tokens', '70', '--min-lines', '8', '--reporters', 'json', '--silent', '--no-tips', '--no-colors'];

function ensureTools() {
  if (existsSync(JSCPD)) return null;
  const r = spawnSync('npm ci', { cwd: TOOLS, shell: true, encoding: 'utf8', windowsHide: true });
  if (r.status !== 0 || !existsSync(JSCPD)) return `npm ci in scripts/tools failed: ${String(r.stderr || r.stdout).trim().split('\n').slice(-3).join(' | ')}`;
  return null;
}

function runJscpd(cwd, out, extra) {
  rmSync(out, { recursive: true, force: true });
  const r = spawnSync(process.execPath, [JSCPD, ...COMMON, '--output', out, ...extra], { cwd, encoding: 'utf8', windowsHide: true, maxBuffer: 64 * 1024 * 1024 });
  const report = readJson(join(out, 'jscpd-report.json'), null);
  if (!report) throw new Error(`jscpd produced no report in ${cwd}: ${String(r.stderr).trim()}`);
  return report.duplicates || [];
}

const normFile = (cwd, name) => {
  let n = String(name).replace(/^\\\\\?\\/, '').replace(/\\/g, '/');
  if (isAbsolute(n)) n = relative(cwd, n).replace(/\\/g, '/');
  return n.replace(/^\.\//, '');
};

const normFragment = (s) => String(s).replace(/\s+/g, ' ').trim();
const keyOf = (scope, fragment) => `${scope}:${createHash('sha1').update(normFragment(fragment)).digest('hex').slice(0, 16)}`;

function collect() {
  const dir = join(stateDir(APP_ROOT), 'jscpd');
  const clones = [];

  for (const d of runJscpd(APP_ROOT, join(dir, 'app'), ['app/src/main', '--format', 'kotlin', '--ignore', '**/build/**,**/generated/**'])) {
    clones.push({ scope: 'app', a: normFile(APP_ROOT, d.firstFile.name), b: normFile(APP_ROOT, d.secondFile.name), d });
  }

  if (existsSync(PLUGINS_ROOT)) {
    const pluginFile = /^[^/]+\/index\.js$/;
    const found = runJscpd(PLUGINS_ROOT, join(dir, 'plugins'), ['.', '--format', 'javascript', '--ignore', '**/node_modules/**,**/test/**,**/scripts/**']);
    for (const d of found) {
      const a = normFile(PLUGINS_ROOT, d.firstFile.name);
      const b = normFile(PLUGINS_ROOT, d.secondFile.name);
      if (!pluginFile.test(a) || !pluginFile.test(b)) continue;
      if (a.split('/')[0] !== b.split('/')[0]) continue;
      clones.push({ scope: 'plugins', a, b, d });
    }
  }

  const byKey = new Map();
  for (const c of clones) {
    const key = keyOf(c.scope, c.d.fragment);
    const at = (f, file) => `${file}:${f.start}-${f.end}`;
    const sample = `${at(c.d.firstFile, c.a)} ~ ${at(c.d.secondFile, c.b)} (${c.d.lines} lines)`;
    const e = byKey.get(key) || { count: 0, sample };
    e.count++;
    byKey.set(key, e);
  }
  return byKey;
}

function main() {
  const args = new Set(process.argv.slice(2));
  const failure = ensureTools();
  if (failure) {
    console.log(`dup: ${failure}`);
    process.exit(1);
  }
  const current = collect();

  if (args.has('--init') || !existsSync(BASELINE)) {
    writeJson(BASELINE, Object.fromEntries([...current].sort(([a], [b]) => a.localeCompare(b))));
    console.log(`dup: wrote baseline (${current.size} clones); review`);
    return;
  }

  const baseline = readJson(BASELINE, {});
  const failures = [];
  for (const [key, e] of current) {
    const allowed = baseline[key]?.count ?? 0;
    if (e.count > allowed) {
      failures.push(allowed === 0 ? `dup: new clone ${e.sample}` : `dup: clone grew from ${allowed} to ${e.count} occurrences, e.g. ${e.sample}`);
    }
  }
  if (args.has('--write-allow')) {
    const next = {};
    for (const [key, e] of current) {
      if (baseline[key]) next[key] = { count: Math.min(e.count, baseline[key].count), sample: baseline[key].sample };
    }
    const sorted = Object.fromEntries(Object.entries(next).sort(([a], [b]) => a.localeCompare(b)));
    if (JSON.stringify(sorted) !== JSON.stringify(baseline)) writeJson(BASELINE, sorted);
  }
  for (const f of failures) console.log(f);
  process.exit(failures.length ? 1 : 0);
}

if (isMain(import.meta.url)) main();
