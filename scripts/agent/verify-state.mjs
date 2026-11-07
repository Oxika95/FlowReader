// State helper for scripts/verify.ps1: which paths changed, which steps they need, stamps, and rule changes.
// Usage:
//   node verify-state.mjs plan <repo>                      prints JSON; writes current-map.json and base-map.json
//   node verify-state.mjs stamp <repo> --ok 0|1 --mode fast|full --ran a,b --failures-file <file>
//   node verify-state.mjs rules-changed <repo>             prints changed rule lines (at most 30)
// Files live in <repo>/.git/flow-agent/:
//   current-map.json  content map at the start of the run
//   base-map.json     map of the last passing stamp, or of the merge-base with the remote branch
//   stamp.json        result of the last run {hash, map, ok, mode, ran, failures, at}
//   stamp-pass.json   copy of the last run that passed; the base for the next run's changes
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { git, globToRegExp, isMain, readJson } from './lib.mjs';
import { stateDir } from './paths.mjs';
import { contentMap, hashOfMap, treeMap } from './state-hash.mjs';

const NO_BUILD = ['**/*.md', 'docs/**', '.cursor/**', 'brand/**', 'scripts/agent/**', '.github/**'].map(globToRegExp);
const GENERATED = /^(scripts\/[\w-]+-allow\.(txt|json)|scripts\/size-baseline\.json|scripts\/jscpd-baseline\.json|docs\/agent\/seams\.md)$/;
const RULE_PATHS = [/^\.cursor\/rules\//, /^\.cursor\/commands\//, /^AGENTS\.md$/, /^scripts\/agent\//];

function baseMapFor(repo, state) {
  const stamp = readJson(join(state, 'stamp-pass.json'), null);
  if (stamp && stamp.map) return { kind: 'stamp', ref: stamp.at, map: stamp.map };
  for (const remote of ['origin/master', 'origin/main']) {
    const base = git(repo, ['merge-base', 'HEAD', remote], { allowFail: true });
    if (base) return { kind: 'merge-base', ref: `${remote} ${base.trim().slice(0, 8)}`, map: treeMap(repo, base.trim()) };
  }
  return { kind: 'none', ref: '', map: {} };
}

function changedPaths(base, map) {
  const out = [];
  for (const p of new Set([...Object.keys(base), ...Object.keys(map)])) if (base[p] !== map[p]) out.push(p);
  return out.sort();
}

async function plan(repo) {
  const state = stateDir(repo);
  const map = await contentMap(repo);
  const hash = hashOfMap(map);
  const base = baseMapFor(repo, state);
  const changed = changedPaths(base.map, map);
  writeFileSync(join(state, 'current-map.json'), JSON.stringify({ hash, map }));
  writeFileSync(join(state, 'base-map.json'), JSON.stringify({ kind: base.kind, map: base.map }));
  writeFileSync(join(state, 'changed.txt'), changed.join('\n') + (changed.length ? '\n' : ''));
  const buildPaths = changed.filter((p) => !NO_BUILD.some((re) => re.test(p)));
  const testPaths = changed.filter((p) => !/\.md$|^\.cursor\/|^LICENSE$|^\.gitignore$/.test(p));
  return {
    repo,
    hash,
    baseKind: base.kind,
    baseRef: base.ref,
    changed: changed.length,
    needsBuild: buildPaths.length > 0,
    needsTests: testPaths.length > 0,
    buildPaths: buildPaths.slice(0, 5),
  };
}

async function stamp(repo, opts) {
  const state = stateDir(repo);
  const start = readJson(join(state, 'current-map.json'), null);
  if (!start) throw new Error('no current-map.json; run plan first');
  // Allowlists and baselines trim themselves during a run; count that as checked state.
  const end = await contentMap(repo);
  const map = { ...start.map };
  for (const p of new Set([...Object.keys(map), ...Object.keys(end)])) {
    if (GENERATED.test(p)) {
      if (end[p]) map[p] = end[p];
      else delete map[p];
    }
  }
  const failures = opts.failuresFile && existsSync(opts.failuresFile) ? readFileSync(opts.failuresFile, 'utf8').split('\n').filter(Boolean) : [];
  const record = {
    hash: hashOfMap(map),
    map,
    ok: opts.ok === '1',
    mode: opts.mode || 'fast',
    ran: (opts.ran || '').split(',').filter(Boolean),
    failures,
    at: new Date().toISOString(),
  };
  writeFileSync(join(state, 'stamp.json'), JSON.stringify(record));
  if (record.ok) writeFileSync(join(state, 'stamp-pass.json'), JSON.stringify(record));
  return { ok: record.ok, hash: record.hash };
}

function blobText(repo, id) {
  if (!id) return '';
  const t = git(repo, ['cat-file', 'blob', id], { allowFail: true });
  return t == null ? '' : t.replace(/\r\n/g, '\n');
}

/** Lines of [text] that count as rules for [path]. */
function ruleLines(path, text) {
  if (RULE_PATHS.some((re) => re.test(path))) return text.split('\n');
  if (path === 'docs/agent/seams.md') return text.split('\n').filter((l) => /^\s*-\s*(order|tuned):/.test(l));
  if (/^docs\/.*\.md$/.test(path)) {
    const out = [];
    let inRules = false;
    for (const l of text.split('\n')) {
      if (/^##\s+Rules\b/.test(l)) inRules = true;
      else if (/^##\s/.test(l)) inRules = false;
      else if (inRules) out.push(l);
    }
    return out;
  }
  return [];
}

const interesting = (p) => RULE_PATHS.some((re) => re.test(p)) || /^docs\/.*\.md$/.test(p);

function rulesChanged(repo) {
  const state = stateDir(repo);
  const current = readJson(join(state, 'current-map.json'), { map: {} }).map;
  const base = readJson(join(state, 'base-map.json'), { map: {} }).map;
  const out = [];
  for (const p of changedPaths(base, current).filter(interesting)) {
    const oldLines = ruleLines(p, blobText(repo, base[p]));
    const full = join(repo, p);
    const newLines = ruleLines(p, existsSync(full) ? readFileSync(full, 'utf8').replace(/\r\n/g, '\n') : '');
    if (!base[p] && newLines.some((l) => l.trim())) {
      out.push(`+ ${p}: new file (${newLines.length} lines)`);
      continue;
    }
    if (!current[p] && oldLines.some((l) => l.trim())) {
      out.push(`- ${p}: deleted (${oldLines.length} lines)`);
      continue;
    }
    const count = (arr) => arr.reduce((m, l) => m.set(l, (m.get(l) || 0) + 1), new Map());
    const oldCount = count(oldLines);
    const newCount = count(newLines);
    const show = (l) => (l.length > 160 ? `${l.slice(0, 157)}...` : l);
    for (const l of oldLines) {
      if (!l.trim()) continue;
      if ((newCount.get(l) || 0) < oldCount.get(l)) {
        out.push(`- ${p}: ${show(l.trim())}`);
        oldCount.set(l, oldCount.get(l) - 1);
      }
    }
    for (const l of newLines) {
      if (!l.trim()) continue;
      if ((oldCount.get(l) || 0) < newCount.get(l)) {
        out.push(`+ ${p}: ${show(l.trim())}`);
        newCount.set(l, newCount.get(l) - 1);
      }
    }
  }
  const lines = out.slice(0, 30);
  if (out.length > 30) lines.push(`... ${out.length - 30} more changed rule lines`);
  return lines;
}

if (isMain(import.meta.url)) {
  const [cmd, repoArg, ...rest] = process.argv.slice(2);
  const repo = resolve(repoArg);
  const opts = {};
  for (let i = 0; i < rest.length; i += 2) opts[rest[i].replace(/^--/, '').replace(/-(\w)/g, (_, c) => c.toUpperCase())] = rest[i + 1];
  if (cmd === 'plan') console.log(JSON.stringify(await plan(repo)));
  else if (cmd === 'stamp') console.log(JSON.stringify(await stamp(repo, opts)));
  else if (cmd === 'rules-changed') for (const l of rulesChanged(repo)) console.log(l);
  else {
    console.error(`unknown command ${cmd}`);
    process.exit(2);
  }
}
