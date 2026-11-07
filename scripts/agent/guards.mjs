// Policy for individual Shell segments: git (read-only plus push for the lock holder), the hook CLIs, and adb on a phone.
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { repoOf, testMode } from './hook-lib.mjs';
import { isHolder, repoName } from './hook-state.mjs';
import { resolveTarget } from './shell-parse.mjs';

const GIT_READ = new Set([
  'status', 'log', 'diff', 'show', 'blame', 'grep', 'rev-parse', 'rev-list', 'ls-files', 'ls-tree', 'cat-file',
  'describe', 'merge-base', 'shortlog', 'fetch', 'show-ref', 'for-each-ref', 'name-rev', 'diff-tree',
  'diff-index', 'check-ignore', 'ls-remote', 'version', 'help',
]);
const BRANCH_FLAGS = new Set(['-a', '-r', '-v', '-vv', '--list', '-l', '--show-current', '--all', '--remotes', '--verbose', '--no-color', '--color', '--column', '--no-column']);
const LIST_VALUE_FLAGS = new Set(['--contains', '--no-contains', '--merged', '--no-merged', '--points-at', '--format', '--sort']);
const TAG_FLAGS = new Set(['-l', '--list', '-n', '-v', '--verify', '--no-color', '--color', '--column', '--no-column']);
const CONFIG_READ = new Set(['--get', '--get-all', '--get-regexp', '--get-urlmatch', '--list', '-l', '--show-origin', '--show-scope']);
const GLOBAL_SWITCHES = new Set(['--no-pager', '--no-optional-locks', '-P', '-p', '--paginate', '--literal-pathspecs', '--no-replace-objects']);

export const denyBoth = (message) => ({ user_message: message, agent_message: message });

/** Skip git's global options. Returns {dir, sub, rest, error}; [dir] follows -C / --work-tree. */
export function parseGit(args, cwd) {
  let dir = cwd;
  let i = 0;
  for (; i < args.length; i++) {
    const a = args[i];
    if (a === '-C') {
      dir = resolveTarget(dir, args[++i]) || dir;
    } else if (a === '-c') i++;
    else if (/^-c.+=/.test(a) || GLOBAL_SWITCHES.has(a)) continue;
    else if (a.startsWith('--work-tree=')) dir = resolveTarget(dir, a.slice(12)) || dir;
    else if (a.startsWith('--git-dir=')) {
      const g = resolveTarget(dir, a.slice(10));
      if (g) dir = basename(g).toLowerCase() === '.git' ? dirname(g) : dir;
    } else if (a === '--version' || a === '--help' || a === '-h') return { dir, sub: 'version', rest: [] };
    else if (a.startsWith('-')) return { dir, error: `unrecognized git option ${a}` };
    else break;
  }
  return { dir, sub: args[i], rest: args.slice(i + 1) };
}

function listingOnly(rest, allowed, valueFlags = LIST_VALUE_FLAGS) {
  let listMode = false;
  const positional = [];
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i];
    if (a === '--list' || a === '-l') listMode = true;
    if (allowed.has(a)) continue;
    if (valueFlags.has(a)) {
      i++;
      continue;
    }
    if (/^--(contains|no-contains|merged|no-merged|points-at|format|sort)=/.test(a)) continue;
    if (a.startsWith('-')) return false;
    positional.push(a);
  }
  return positional.length === 0 || listMode;
}

function isReadOnlyGit(sub, rest) {
  if (GIT_READ.has(sub)) return !rest.some((a) => a === '--output' || a.startsWith('--output='));
  switch (sub) {
    case 'branch': return listingOnly(rest, BRANCH_FLAGS);
    case 'tag': return listingOnly(rest, TAG_FLAGS);
    case 'remote': return rest.length === 0 || rest[0] === '-v' || (['show', 'get-url'].includes(rest[0]));
    case 'config': return rest.some((a) => CONFIG_READ.has(a));
    case 'worktree': return rest[0] === 'list';
    case 'stash': return rest[0] === 'list' || rest[0] === 'show';
    case 'reflog': return !rest.some((a) => a === 'expire' || a === 'delete');
    default: return false;
  }
}

const FORCE_PUSH = /^(-f|--force.*|--delete|-d|--mirror|--prune)$/;

/** Verdict for one git segment: null to allow, or {user_message, agent_message}. */
export function checkGit(args, cwd, conv) {
  const g = parseGit(args, cwd);
  const repo = repoOf(g.dir);
  if (!repo) return null;
  if (g.error) return denyBoth(`git is blocked for agents here: ${g.error}. Ask the user.`);
  const sub = g.sub || '';
  if (isReadOnlyGit(sub, g.rest)) return null;
  if (sub === 'push') {
    if (g.rest.some((a) => FORCE_PUSH.test(a) || a.startsWith('+') || a.startsWith(':'))) {
      return denyBoth('Forced, deleting or mirroring pushes are blocked for agents. Ask the user to run them.');
    }
    if (repo.key === 'chats' || isHolder(repo.root, conv)) return null;
    return denyBoth(`git push is only allowed from the chat that holds the edit lock for ${repoName(repo.root)}.`);
  }
  return denyBoth(`git ${sub} is blocked for agents. Commit with commit-mine.mjs (see AGENTS.md); ask the user for anything else.`);
}

// --- hook CLIs ---------------------------------------------------------------------------------

export const INJECT_CLIS = new Set(['commit-mine.mjs', 'lock.mjs', 'release-check.mjs']);

/** {deny} for install-hooks.mjs and `lock.mjs release --force`; {inject:true} when the segment runs a CLI that needs --conversation. */
export function checkCli(seg) {
  const names = seg.tokens.map((t) => basename(t.v.replace(/\\/g, '/')).toLowerCase());
  if (names.includes('install-hooks.mjs')) {
    const asCommand = seg.cmd === 'install-hooks.mjs' || seg.cmd === 'install-hooks';
    if (asCommand || ['node', 'nodejs', 'npx', 'powershell', 'pwsh', 'cmd', 'bash', 'sh'].includes(seg.cmd)) return { deny: denyBoth('install-hooks.mjs is for the owner. Ask the user to run it from a terminal.') };
  }
  if (names.includes('lock.mjs') && names.includes('release') && seg.tokens.some((t) => t.v === '--force' || t.v === '-f')) {
    return { deny: denyBoth('lock.mjs release --force is for the owner, from a terminal. Ask the user.') };
  }
  if (!seg.nested && names.some((n) => INJECT_CLIS.has(n)) && ['node', 'nodejs'].includes(seg.cmd)) return { inject: true };
  return null;
}

// --- adb ---------------------------------------------------------------------------------------

const ADB_RISKY = /\b(run-as|sqlite3)\b|(^|\s)push(\s|$)|\bpm\s+clear\b|(^|[\s"';&|])rm\s|\bcontent\s+(delete|insert|update)\b/i;

function adbPath(seg) {
  const first = seg.tokens[seg.index]?.v || 'adb';
  if (/[\\/]/.test(first)) return first;
  for (const base of [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT, join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk')]) {
    const p = base && join(base, 'platform-tools', 'adb.exe');
    if (p && existsSync(p)) return p;
  }
  return 'adb';
}

/** Serials from `adb devices`. null when adb can't be asked; [] when not installed. */
function attachedDevices(seg) {
  let out;
  if (testMode() && process.env.FLOW_AGENT_ADB_MOCK !== undefined) out = process.env.FLOW_AGENT_ADB_MOCK;
  else {
    const r = spawnSync(adbPath(seg), ['devices'], { encoding: 'utf8', timeout: 2000, windowsHide: true });
    if (r.error && r.error.code === 'ENOENT') return [];
    if (r.error || r.status !== 0) return null;
    out = r.stdout;
  }
  return out.split(/\r?\n/).map((l) => l.trim().split(/\s+/)).filter((p) => p.length >= 2 && p[1] !== 'daemon' && !/^List|^\*/.test(p[0])).map((p) => p[0]);
}

export function checkAdb(seg) {
  if (seg.cmd !== 'adb' || !ADB_RISKY.test(seg.text)) return null;
  const args = seg.tokens.slice(seg.index + 1).map((t) => t.v);
  let serial = null;
  let emulatorFlag = false;
  let usbFlag = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '-s') serial = args[++i];
    else if (args[i] === '-e') emulatorFlag = true;
    else if (args[i] === '-d') usbFlag = true;
    else if (['-t', '-H', '-P', '-L'].includes(args[i])) i++;
    else if (!args[i].startsWith('-')) break;
  }
  const deny = denyBoth('This command would change app data on a physical device. Ask the user first; they can run it themselves.');
  if (serial) return /^emulator-/i.test(serial) ? null : deny;
  if (usbFlag) return deny;
  if (emulatorFlag) return null;
  const devices = attachedDevices(seg);
  if (devices === null) return denyBoth('Could not check which devices are attached (adb devices failed). Ask the user before changing app data with adb.');
  return devices.every((d) => /^emulator-/i.test(d)) ? null : deny;
}