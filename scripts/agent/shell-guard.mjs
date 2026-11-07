// Shell guard: runs every segment of a command through the git, CLI, adb and write checks.
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
import { normPath, relIn, repoOf, userHome } from './hook-lib.mjs';
import { checkAdb, checkCli, checkGit, denyBoth } from './guards.mjs';
import { acquire, appendEdits, denyMessages } from './hook-state.mjs';
import { parseShellSegments, resolveTarget } from './shell-parse.mjs';

const WRITE_CMDS = new Set([
  'set-content', 'sc', 'add-content', 'ac', 'out-file', 'clear-content', 'new-item', 'ni', 'remove-item', 'rm', 'del', 'erase',
  'rd', 'rmdir', 'move-item', 'mv', 'move', 'rename-item', 'ren', 'copy-item', 'cp', 'copy', 'mkdir', 'md', 'touch', 'tee', 'tee-object',
]);
const FIRST_ARG_ONLY = new Set(['set-content', 'sc', 'add-content', 'ac', 'out-file', 'tee', 'tee-object']);
const PATH_OPTS = new Set(['path', 'literalpath', 'filepath', 'destination', 'lp']);
const VALUE_OPTS = new Set(['value', 'encoding', 'itemtype', 'type', 'filter', 'include', 'exclude', 'newname', 'name', 'stream', 'width', 'inputobject']);

/** Folders agents may never write to: the installed hooks, hooks.json and the per-repo hook state. */
export function isProtectedPath(p) {
  const n = normPath(p);
  const h = normPath(join(userHome(), '.cursor'));
  return n === `${h}/hooks.json` || n.startsWith(`${h}/hooks/`) || n.startsWith(`${h}/hooks.json.`) || /\/\.git\/flow-agent(\/|$)/.test(n);
}

/** Paths a write command in [seg] targets: {targets: [absolute], unresolved: bool}. null when it isn't a write. */
export function writeTargets(seg) {
  const isWrite = WRITE_CMDS.has(seg.cmd);
  if (!isWrite && seg.redirects.length === 0) return null;
  const raw = [...seg.redirects];
  if (isWrite) {
    const args = seg.tokens.slice(seg.index + 1).map((t) => t.v);
    const positional = [];
    for (let i = 0; i < args.length; i++) {
      const a = args[i];
      if (a.startsWith('-')) {
        const name = a.replace(/^-+/, '').split(':')[0].toLowerCase();
        if (PATH_OPTS.has(name)) raw.push(args[++i] ?? '');
        else if (VALUE_OPTS.has(name) && !a.includes(':')) i++;
      } else positional.push(a);
    }
    raw.push(...(FIRST_ARG_ONLY.has(seg.cmd) ? positional.slice(0, 1) : positional));
  }
  const home = homedir();
  let unresolved = raw.length === 0;
  const targets = [];
  for (const t of raw) {
    const r = resolveTarget(seg.cwd, t, home);
    if (r) targets.push(r);
    else unresolved = true;
  }
  return { targets, unresolved };
}

/** Insert ` --conversation <id>` at the end of the segments listed in [injections] (and drop any the agent typed). */
function injectConversation(command, injections, conv) {
  let out = command;
  for (const seg of [...injections].sort((a, b) => b.start - a.start)) {
    const text = seg.text.replace(/\s--conversation(?:=|\s+)(?:"[^"]*"|'[^']*'|\S+)/g, '');
    out = `${out.slice(0, seg.start)}${text} --conversation ${conv}${out.slice(seg.end)}`;
  }
  return out;
}

/**
 * Evaluate a Shell command for chat [conv]. Returns {deny: {user_message, agent_message}} or {command}
 * (the command with --conversation injected, or unchanged).
 */
export function evaluateShell({ command, cwd, conv, subagent = null }) {
  const segs = parseShellSegments(String(command || ''), cwd);
  const injections = [];
  const logged = new Map();
  for (const seg of segs) {
    const cli = checkCli(seg);
    if (cli?.deny) return { deny: cli.deny };
    if (cli?.inject) injections.push(seg);

    if (seg.cmd === 'git') {
      const verdict = checkGit(seg.tokens.slice(seg.index + 1).map((t) => t.v), seg.cwd, conv);
      if (verdict) return { deny: verdict };
    }
    const adb = checkAdb(seg);
    if (adb) return { deny: adb };

    const w = writeTargets(seg);
    if (!w) continue;
    const places = w.targets.map((t) => ({ path: t, repo: repoOf(t) }));
    if (w.unresolved) places.push({ path: null, repo: repoOf(seg.cwd) });
    for (const { path, repo } of places) {
      if (path && isProtectedPath(path)) {
        return { deny: denyBoth('Agents cannot change the hook installation or its state. Ask the user.') };
      }
      if (!repo) continue;
      if (repo.key === 'chats') {
        if (path) logged.set(repo.root, [...(logged.get(repo.root) || []), relIn(repo.root, path)]);
        continue;
      }
      const res = acquire(repo.root, conv, { subagent });
      if (!res.ok) return { deny: denyMessages(repo.root, res) };
      if (path) logged.set(repo.root, [...(logged.get(repo.root) || []), relIn(repo.root, resolve(path))]);
    }
  }
  for (const [root, paths] of logged) appendEdits(root, conv, [...new Set(paths)]);
  return { command: injections.length ? injectConversation(command, injections, conv) : command };
}
