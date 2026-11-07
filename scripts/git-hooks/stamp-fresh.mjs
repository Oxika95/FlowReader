#!/usr/bin/env node
// Exit 0 when the working tree is clean and a passing -Full stamp matches the content map.
// Used by pre-push to skip verify.ps1 -Full. Exit 1 means verify must run.
import { join } from 'node:path';
import { git, readJson } from '../agent/lib.mjs';
import { APP_ROOT, stateDir } from '../agent/paths.mjs';
import { contentMap, hashOfMap } from '../agent/state-hash.mjs';

const status = git(APP_ROOT, ['status', '--porcelain=v1', '--untracked-files=all']) || '';
if (status.trim()) {
  console.error('pre-push: working tree is dirty; running verify');
  process.exit(1);
}

const map = await contentMap(APP_ROOT);
const hash = hashOfMap(map);
const state = stateDir(APP_ROOT);
const stamp =
  readJson(join(state, 'stamp-pass.json'), null) || readJson(join(state, 'stamp.json'), null);

if (!stamp) {
  console.error('pre-push: no stamp; running verify');
  process.exit(1);
}
if (!stamp.ok) {
  console.error('pre-push: last stamp failed; running verify');
  process.exit(1);
}
if (stamp.mode !== 'full') {
  console.error(`pre-push: stamp mode is ${stamp.mode || '?'}, need full; running verify`);
  process.exit(1);
}
if (stamp.hash !== hash) {
  console.error('pre-push: stamp hash does not match content map; running verify');
  process.exit(1);
}

console.log('pre-push: clean tree and matching full stamp; skipping verify');
process.exit(0);
