// Content map of a repo: { repo-relative path: git blob id } for the whole working tree.
// The hash covers file contents, not commits, so committing the same content doesn't change it.
// Usage: node state-hash.mjs [repo] [--out <file>]   prints {hash, paths}; --out also writes {hash, map}.
import { createHash } from 'node:crypto';
import { existsSync, lstatSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { git, isMain } from './lib.mjs';
import { APP_ROOT } from './paths.mjs';

/** Blob ids of every file in [ref]'s tree. Empty when the ref doesn't exist. */
export function treeMap(repoRoot, ref = 'HEAD') {
  const map = {};
  const out = git(repoRoot, ['ls-tree', '-r', '-z', ref], { allowFail: true });
  if (!out) return map;
  for (const entry of out.split('\0')) {
    if (!entry) continue;
    const tab = entry.indexOf('\t');
    const [, type, hash] = entry.slice(0, tab).split(' ');
    if (type === 'blob' || type === 'commit') map[entry.slice(tab + 1)] = hash;
  }
  return map;
}

function dirtyPaths(repoRoot) {
  const out = git(repoRoot, ['status', '--porcelain=v1', '-z', '--untracked-files=all']) || '';
  const entries = out.split('\0');
  const present = [];
  const deleted = [];
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i];
    if (!e) continue;
    const x = e[0];
    const y = e[1];
    const path = e.slice(3);
    if (x === 'R' || x === 'C' || y === 'R' || y === 'C') {
      const original = entries[++i];
      if (x === 'R' || y === 'R') deleted.push(original);
    }
    if (x === 'D' || y === 'D') deleted.push(path);
    else present.push(path);
  }
  return { present, deleted };
}

export async function contentMap(repoRoot) {
  const map = treeMap(repoRoot, 'HEAD');
  const { present, deleted } = dirtyPaths(repoRoot);
  for (const p of deleted) delete map[p];
  const toHash = [];
  for (const p of present) {
    const full = join(repoRoot, p);
    if (!existsSync(full)) {
      delete map[p];
      continue;
    }
    if (lstatSync(full).isDirectory()) continue;
    toHash.push(p);
  }
  if (toHash.length > 0) {
    const out = git(repoRoot, ['hash-object', '--stdin-paths'], { input: toHash.join('\n') + '\n' });
    const ids = out.split('\n').filter(Boolean);
    toHash.forEach((p, i) => {
      map[p] = ids[i];
    });
  }
  return map;
}

export function hashOfMap(map) {
  const lines = Object.keys(map)
    .sort()
    .map((p) => `${p}\0${map[p]}\n`);
  return createHash('sha256').update(lines.join('')).digest('hex');
}

if (isMain(import.meta.url)) {
  const args = process.argv.slice(2);
  const outIdx = args.indexOf('--out');
  const outFile = outIdx >= 0 ? args.splice(outIdx, 2)[1] : null;
  const repo = resolve(args[0] || APP_ROOT);
  const map = await contentMap(repo);
  const hash = hashOfMap(map);
  if (outFile) writeFileSync(outFile, JSON.stringify({ hash, map }));
  console.log(JSON.stringify({ hash, paths: Object.keys(map).length }));
}
