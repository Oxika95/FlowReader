// Per-repo hook state in <repo>/.git/flow-agent/: edit lock, edit logs, subagent claim, turn-start maps, gate, log.
import {
  appendFileSync, closeSync, existsSync, mkdirSync, openSync, readFileSync, readdirSync,
  renameSync, rmSync, statSync, unlinkSync, writeFileSync,
} from 'node:fs';
import { basename, join } from 'node:path';
import { git, readJson } from './lib.mjs';
import { loadConfig } from './hook-lib.mjs';
import { contentMap, hashOfMap } from './state-hash.mjs';

export const LOCKED_KEYS = ['app', 'plugins'];
const MUTEX_STALE_MS = 5000;
const LOG_MAX_BYTES = 1024 * 1024;

export const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
export const safeId = (id) => String(id).replace(/[^A-Za-z0-9._-]/g, '_');
export const repoName = (root) => basename(root);

export function stateDir(root, sub = '') {
  const dir = join(root, '.git', 'flow-agent', sub);
  mkdirSync(dir, { recursive: true });
  return dir;
}

export const hasGitDir = (root) => !!root && existsSync(join(root, '.git')) && statSync(join(root, '.git')).isDirectory();

/** Run [fn] holding <state>/lock.mutex (created with flag wx; one older than 5 s counts as stale). */
export function withMutex(root, fn) {
  const mutex = join(stateDir(root), 'lock.mutex');
  let have = false;
  for (let i = 0; i < 120 && !have; i++) {
    try {
      closeSync(openSync(mutex, 'wx'));
      have = true;
    } catch (e) {
      if (e.code !== 'EEXIST' && e.code !== 'EPERM' && e.code !== 'EBUSY') throw e;
      try {
        if (Date.now() - statSync(mutex).mtimeMs > MUTEX_STALE_MS) unlinkSync(mutex);
      } catch {
        /* another process removed it */
      }
      sleep(25);
    }
  }
  if (!have) throw new Error(`could not get ${mutex}`);
  try {
    return fn();
  } finally {
    try {
      unlinkSync(mutex);
    } catch {
      /* already gone */
    }
  }
}

/** Write a temp file and rename it into place, retrying on the Windows sharing errors. */
export function writeJsonAtomic(file, value) {
  const tmp = `${file}.${process.pid}.tmp`;
  writeFileSync(tmp, JSON.stringify(value));
  for (let i = 0; ; i++) {
    try {
      renameSync(tmp, file);
      return;
    } catch (e) {
      if (i >= 5 || !['EPERM', 'EBUSY', 'EACCES'].includes(e.code)) throw e;
      sleep(50);
    }
  }
}

export function logEvent(root, entry) {
  try {
    const file = join(stateDir(root), 'log.jsonl');
    appendFileSync(file, JSON.stringify({ at: new Date().toISOString(), ...entry }) + '\n');
    if (statSync(file).size > LOG_MAX_BYTES) {
      const tail = readFileSync(file, 'utf8').slice(-LOG_MAX_BYTES / 2);
      writeFileSync(file, tail.slice(tail.indexOf('\n') + 1));
    }
  } catch {
    /* logging is best effort */
  }
}

const iso = (ms = Date.now()) => new Date(ms).toISOString();
export const hhmm = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
const lockFile = (root) => join(stateDir(root), 'lock.json');

export function readLock(root) {
  const lock = readJson(lockFile(root), null);
  return lock && lock.conversation_id ? lock : null;
}

export function lockExpiresAt(lock) {
  return (Date.parse(lock.last_active_at) || 0) + loadConfig().lockTimeoutMinutes * 60000;
}

export const isHolder = (root, conv) => readLock(root)?.conversation_id === conv;

// --- edit log ----------------------------------------------------------------------------------

const editsFile = (root, conv) => join(stateDir(root, 'edits'), `${safeId(conv)}.txt`);

export function readEdits(root, conv) {
  const f = editsFile(root, conv);
  return existsSync(f) ? readFileSync(f, 'utf8').split('\n').filter(Boolean) : [];
}

function appendEditUnlocked(root, conv, relPaths) {
  const have = new Set(readEdits(root, conv));
  const add = relPaths.filter((p) => p && !have.has(p));
  if (add.length) appendFileSync(editsFile(root, conv), add.join('\n') + '\n');
}

export function appendEdits(root, conv, relPaths) {
  withMutex(root, () => appendEditUnlocked(root, conv, relPaths));
}

export function removeEdits(root, conv, relPaths) {
  const drop = new Set(relPaths);
  withMutex(root, () => {
    const keep = readEdits(root, conv).filter((p) => !drop.has(p));
    writeFileSync(editsFile(root, conv), keep.length ? keep.join('\n') + '\n' : '');
  });
}

/** Paths git reports as changed, limited to [paths] (each may be a file or a folder). Includes rename sources. */
export function dirtyAmong(root, paths) {
  if (!paths.length) return [];
  const out = git(root, ['--literal-pathspecs', 'status', '--porcelain=v1', '-z', '--untracked-files=all', '--', ...paths], { allowFail: true }) || '';
  const entries = out.split('\0');
  const found = [];
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i];
    if (!e) continue;
    found.push(e.slice(3));
    if (e[0] === 'R' || e[0] === 'C' || e[1] === 'R' || e[1] === 'C') found.push(entries[++i]);
  }
  return found;
}

export const dirtyEditPaths = (root, conv) => dirtyAmong(root, readEdits(root, conv));

// --- lock and subagent claim -------------------------------------------------------------------

/**
 * Take or refresh the edit lock for [conv]. Returns {ok:true}, or {ok:false, reason, ...} where reason is
 * 'held' (another chat, not expired), 'expired-dirty' (expired but its edits are uncommitted) or 'subagent'.
 * With [editPath] the repo-relative path is added to this chat's edit log in the same step.
 */
export function acquire(root, conv, { subagent = null, editPath = null } = {}) {
  const cfg = loadConfig();
  return withMutex(root, () => {
    const now = Date.now();
    const lock = readLock(root);
    if (!lock || lock.conversation_id === conv) {
      writeJsonAtomic(lockFile(root), { conversation_id: conv, acquired_at: lock ? lock.acquired_at : iso(now), last_active_at: iso(now) });
    } else if (now < lockExpiresAt(lock)) {
      return { ok: false, reason: 'held', holder: lock.conversation_id, lastActive: Date.parse(lock.last_active_at) || 0, until: lockExpiresAt(lock) };
    } else {
      const dirty = dirtyEditPaths(root, lock.conversation_id);
      if (dirty.length) return { ok: false, reason: 'expired-dirty', holder: lock.conversation_id, files: dirty };
      writeJsonAtomic(lockFile(root), { conversation_id: conv, acquired_at: iso(now), last_active_at: iso(now) });
      logEvent(root, { event: 'lock-taken-after-expiry', from: lock.conversation_id, to: conv });
    }
    if (subagent) {
      const claimFile = join(stateDir(root), 'subagent.json');
      const claim = readJson(claimFile, null);
      const idle = cfg.subagentIdleMinutes * 60000;
      const live = claim && claim.conversation_id === conv && now - (Date.parse(claim.last_active_at) || 0) < idle;
      if (live && claim.subagent_id !== subagent) return { ok: false, reason: 'subagent' };
      writeJsonAtomic(claimFile, { subagent_id: subagent, conversation_id: conv, last_active_at: iso(now) });
    }
    if (editPath) appendEditUnlocked(root, conv, [editPath]);
    return { ok: true };
  });
}

/** Refresh last_active_at when [conv] holds the lock. Never takes it. */
export function touch(root, conv) {
  if (!hasGitDir(root)) return;
  withMutex(root, () => {
    const lock = readLock(root);
    if (lock && lock.conversation_id === conv) writeJsonAtomic(lockFile(root), { ...lock, last_active_at: iso() });
  });
}

export function touchAll(conv) {
  const { roots } = loadConfig();
  for (const key of LOCKED_KEYS) {
    try {
      touch(roots[key], conv);
    } catch {
      /* best effort */
    }
  }
}

/** Release the lock (and subagent claim) when [conv] holds it, or whoever holds it with [force]. */
export function release(root, conv, { force = false } = {}) {
  return withMutex(root, () => {
    const lock = readLock(root);
    if (!lock) return { released: false, reason: 'free' };
    if (lock.conversation_id !== conv && !force) return { released: false, reason: 'other', holder: lock.conversation_id };
    unlinkSync(lockFile(root));
    rmSync(join(stateDir(root), 'subagent.json'), { force: true });
    logEvent(root, { event: 'release', holder: lock.conversation_id, by: force ? 'force' : conv });
    return { released: true, holder: lock.conversation_id };
  });
}

/** Move the lock to [conv]. Returns the previous holder and the files it left uncommitted. */
export function takeover(root, conv) {
  return withMutex(root, () => {
    const prev = readLock(root);
    const other = prev && prev.conversation_id !== conv ? prev.conversation_id : null;
    const dirty = other ? dirtyEditPaths(root, other) : [];
    writeJsonAtomic(lockFile(root), { conversation_id: conv, acquired_at: iso(), last_active_at: iso() });
    rmSync(join(stateDir(root), 'subagent.json'), { force: true });
    logEvent(root, { event: 'takeover', to: conv, from: other, dirty });
    return { previous: other, dirty };
  });
}

/** Clear the subagent claim: [subagent] is the id that stopped; without it, any claim of [conv]. */
export function releaseSubagent(root, conv, subagent) {
  const file = join(stateDir(root), 'subagent.json');
  withMutex(root, () => {
    const claim = readJson(file, null);
    if (claim && claim.conversation_id === conv && (!subagent || claim.subagent_id === subagent)) rmSync(file, { force: true });
  });
}

export function describeFiles(files, max = 5) {
  const shown = files.slice(0, max).join(', ');
  return files.length > max ? `${shown} and ${files.length - max} more` : shown;
}

/** user_message and agent_message for a failed acquire(). */
export function denyMessages(root, res) {
  const name = repoName(root);
  if (res.reason === 'subagent') {
    const m = `Another subagent of this chat is editing ${name}. Wait for it to finish.`;
    return { user_message: m, agent_message: m };
  }
  if (res.reason === 'expired-dirty') {
    const files = describeFiles(res.files);
    return {
      user_message: `${name}: the previous chat's lock expired, but it left uncommitted changes in ${files}. Commit them from that chat, or type /takeover here to take the lock anyway.`,
      agent_message: `Edit refused: the previous chat's lock on ${name} expired but it left uncommitted changes in ${files}. Do not edit files another way (Shell, git, scripts). Tell the user they can commit those files from the other chat or type /takeover here, and continue with read-only work.`,
    };
  }
  return {
    user_message: `${name} is being edited by another chat (last active ${hhmm(res.lastActive)}). This chat can still read, plan and review. Finish or /handoff the other chat, wait until ${hhmm(res.until)}, or type /takeover here.`,
    agent_message: `Edit refused: another conversation holds the edit lock for ${name} until ${hhmm(res.until)}. Do not edit files another way (Shell, git, scripts). Tell the user and continue with read-only work.`,
  };
}

// --- content maps, turn start, gate ------------------------------------------------------------

export async function snapshot(root) {
  const map = await contentMap(root);
  return { hash: hashOfMap(map), map };
}

export function changedPaths(before, after) {
  const out = [];
  for (const p of new Set([...Object.keys(before), ...Object.keys(after)])) if (before[p] !== after[p]) out.push(p);
  return out.sort();
}

export const turnstartFile = (root, conv) => join(stateDir(root, 'turnstart'), `${safeId(conv)}.json`);
export const gateFile = (root, conv) => join(stateDir(root, 'gate'), `${safeId(conv)}.json`);

/** Delete state files older than [days]. */
export function cleanOldState(root, days = 14) {
  const cutoff = Date.now() - days * 86400000;
  for (const sub of ['edits', 'turnstart', 'gate']) {
    const dir = join(stateDir(root), sub);
    for (const name of readdirSync(dir)) {
      try {
        if (statSync(join(dir, name)).mtimeMs < cutoff) unlinkSync(join(dir, name));
      } catch {
        /* skip */
      }
    }
  }
}
