// Logic behind the turn-level hooks: beforeSubmitPrompt, stop, preCompact, sessionEnd, subagentStop.
import { join, resolve } from 'node:path';
import { readJson } from './lib.mjs';
import { identityOf, loadConfig, relIn, repoOf } from './hook-lib.mjs';
import {
  LOCKED_KEYS, changedPaths, cleanOldState, gateFile, hasGitDir, appendEdits, readLock, release,
  releaseSubagent, repoName, snapshot, stateDir, takeover, touchAll, turnstartFile, writeJsonAtomic,
} from './hook-state.mjs';

const GATE_PREFIX = '[verify-gate]';
const MAX_REMINDERS = 2;

const lockedRoots = () => LOCKED_KEYS.map((k) => loadConfig().roots[k]).filter(hasGitDir);

function readGate(conv) {
  const root = loadConfig().roots.app;
  const g = hasGitDir(root) ? readJson(gateFile(root, conv), null) : null;
  return { count: g?.count || 0, sent: g?.sent || {} };
}

export async function beforeSubmitPrompt(input) {
  const out = { continue: true };
  const { conv } = identityOf(input);
  if (!conv) return out;
  const prompt = String(input.prompt || '');
  touchAll(conv);

  if (/^\s*\/takeover\b/.test(prompt) || prompt.includes('[flow-takeover]')) {
    for (const root of lockedRoots()) {
      const lock = readLock(root);
      if (lock && lock.conversation_id !== conv) takeover(root, conv);
    }
  }
  if (prompt.trimStart().startsWith(GATE_PREFIX)) return out;

  const app = loadConfig().roots.app;
  if (hasGitDir(app)) writeJsonAtomic(gateFile(app, conv), { count: 0, sent: readGate(conv).sent });
  for (const root of lockedRoots()) {
    const snap = await snapshot(root);
    writeJsonAtomic(turnstartFile(root, conv), { ...snap, at: new Date().toISOString() });
  }
  return out;
}

const failureText = (stamp) => stamp.failures.slice(0, 3).map((l) => String(l).slice(0, 200)).join(' | ');

export async function stop(input) {
  if (input.status !== 'completed') return {};
  const { conv } = identityOf(input);
  if (!conv) return {};
  touchAll(conv);
  const gate = readGate(conv);
  if (gate.count >= MAX_REMINDERS) return {};

  const lines = [];
  const sent = {};
  const roots = lockedRoots();
  for (const root of roots) {
    const start = readJson(turnstartFile(root, conv), null);
    if (!start) continue;
    const cur = await snapshot(root);
    const changed = changedPaths(start.map, cur.map);
    if (!changed.length) continue;
    const pass = readJson(join(stateDir(root), 'stamp-pass.json'), null);
    const unverified = changed.filter((p) => !pass || pass.map?.[p] !== cur.map[p]);
    const key = repoName(root);
    if (!unverified.length || gate.sent[key] === cur.hash) continue;
    const stamp = readJson(join(stateDir(root), 'stamp.json'), null);
    const prefix = roots.length > 1 ? `${key}: ` : '';
    if (stamp && stamp.hash === cur.hash && stamp.ok === false) {
      lines.push(`${prefix}verify failed for the current files: ${failureText(stamp)}. Fix it, or tell the user why it can't pass yet.`);
    } else {
      const shown = unverified.slice(0, 5).join(', ') + (unverified.length > 5 ? ` and ${unverified.length - 5} more` : '');
      lines.push(`${prefix}Files changed since the last passing verify: ${shown}. Run scripts/verify.ps1 and report the result in one line.`);
    }
    sent[key] = cur.hash;
  }
  if (!lines.length) return {};
  try {
    writeJsonAtomic(gateFile(loadConfig().roots.app, conv), { count: gate.count + 1, sent: { ...gate.sent, ...sent } });
  } catch {
    return {};
  }
  return { followup_message: `${GATE_PREFIX} ${lines.join(' ')}` };
}

export function preCompact(input) {
  if (input.is_first_compaction !== false) {
    return {
      user_message: 'This chat is being compacted: older messages are summarized to make room, and details from early in the chat can get lost. If the task is finished or changing, run /handoff and start a new chat.',
    };
  }
  return { user_message: `Compacted again (${input.message_count ?? '?'} messages).` };
}

export function sessionEnd(input) {
  const conv = input.conversation_id || input.session_id;
  const cfg = loadConfig();
  if (conv && cfg.releaseOnReasons.includes(input.reason)) {
    for (const root of lockedRoots()) release(root, conv);
  }
  for (const key of ['app', 'plugins', 'chats']) if (hasGitDir(cfg.roots[key])) cleanOldState(cfg.roots[key]);
  return {};
}

export function subagentStop(input) {
  const { conv, subagent } = identityOf(input);
  if (!conv) return {};
  const id = subagent || input.subagent_id || null;
  for (const root of lockedRoots()) releaseSubagent(root, conv, id);
  const base = (input.workspace_roots || [])[0] || process.cwd();
  if (Array.isArray(input.modified_files)) {
    for (const f of input.modified_files) {
      const repo = typeof f === 'string' ? repoOf(resolve(base, f)) : null;
      if (repo) appendEdits(repo.root, conv, [relIn(repo.root, resolve(base, f))]);
    }
  }
  return {};
}
