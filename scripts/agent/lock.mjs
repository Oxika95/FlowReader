// Edit lock status and release for the app and plugins repos.
// Usage: node lock.mjs status [--conversation <id>]
//        node lock.mjs release [--conversation <id>]          holder only
//        node lock.mjs release --force                        owner, from a terminal only
// The Shell hook appends --conversation <id> for agents and denies --force.
import { join } from 'node:path';
import { loadConfig } from './hook-lib.mjs';
import {
  LOCKED_KEYS, describeFiles, dirtyEditPaths, hasGitDir, hhmm, lockExpiresAt, readLock, release, repoName, stateDir,
} from './hook-state.mjs';
import { readJson } from './lib.mjs';

const [cmd, ...rest] = process.argv.slice(2);
const flag = (name) => rest.includes(name);
const value = (name) => (rest.includes(name) ? rest[rest.indexOf(name) + 1] : undefined);
const conv = value('--conversation');
const roots = LOCKED_KEYS.map((k) => loadConfig().roots[k]).filter(hasGitDir);

function status() {
  for (const root of roots) {
    const lock = readLock(root);
    const name = repoName(root);
    if (!lock) {
      console.log(`${name}: free`);
      continue;
    }
    const expires = lockExpiresAt(lock);
    const expired = Date.now() >= expires;
    const mine = conv && lock.conversation_id === conv ? ' (this chat)' : '';
    console.log(`${name}: held by ${lock.conversation_id}${mine}, last active ${hhmm(Date.parse(lock.last_active_at))}, ${expired ? 'expired' : `expires ${hhmm(expires)}`}`);
    const dirty = dirtyEditPaths(root, lock.conversation_id);
    if (dirty.length) console.log(`  uncommitted files from that chat: ${describeFiles(dirty, 10)}`);
    const claim = readJson(join(stateDir(root), 'subagent.json'), null);
    if (claim) console.log(`  editing subagent: ${claim.subagent_id}`);
  }
}

if (cmd === 'status') status();
else if (cmd === 'release') {
  if (!conv && !flag('--force')) {
    console.error('lock release: missing --conversation. The Shell hook adds it; if you see this, the hook did not run.');
    process.exit(1);
  }
  let released = 0;
  let others = 0;
  for (const root of roots) {
    const r = release(root, conv, { force: flag('--force') });
    if (r.released) {
      released++;
      console.log(`${repoName(root)}: released (was held by ${r.holder})`);
    } else if (r.reason === 'other') {
      others++;
      console.log(`${repoName(root)}: not released, held by another chat (${r.holder})`);
    } else console.log(`${repoName(root)}: already free`);
  }
  process.exit(released === 0 && others > 0 ? 1 : 0);
} else {
  console.error('usage: lock.mjs status|release [--conversation <id>] [--force]');
  process.exit(2);
}
