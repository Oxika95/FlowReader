// Plumbing shared by every Cursor hook: stdin, the single JSON write, kill switch, config, repo detection.
// Node built-ins only, so a broken helper module can't stop the hook from printing its neutral response.
import { existsSync, readFileSync, writeSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const PERMISSION_EVENTS = new Set(['preToolUse', 'beforeShellExecution', 'subagentStart', 'beforeMCPExecution', 'beforeReadFile']);

export const ALLOW = { permission: 'allow' };

export function neutralFor(event) {
  if (PERMISSION_EVENTS.has(event)) return { permission: 'allow' };
  if (event === 'beforeSubmitPrompt') return { continue: true };
  return {};
}

/** Test seams (fake adb output, crashes) only work when the home directory is redirected. */
export const testMode = () => !!process.env.FLOW_AGENT_USERHOME;
export const userHome = () => process.env.FLOW_AGENT_USERHOME || homedir();
export const installDir = () => join(userHome(), '.cursor', 'hooks', 'flow-agent');
export const killSwitchOn = () => existsSync(join(installDir(), 'OFF'));

export const stripBom = (s) => s.replace(/^\uFEFF/, '');
export const normPath = (p) => String(p).replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();

const DEFAULTS = {
  lockTimeoutMinutes: 30,
  subagentIdleMinutes: 10,
  loopLimit: 20,
  releaseOnReasons: ['user_close', 'window_close'],
  identity: { name: '', email: '' },
};

/** Repo roots when running from the source tree (scripts/agent next to the app's root). */
function sourceRoots() {
  const app = resolve(HERE, '..', '..');
  if (!existsSync(join(app, 'scripts', 'agent', 'hook-lib.mjs'))) return {};
  return { app, plugins: resolve(app, '..', 'flow-reader-plugins'), chats: resolve(app, '..', 'flow-reader-chats') };
}

let cached = null;

/** config.json next to the installed scripts (or config.template.json when run from source). */
export function loadConfig() {
  if (cached) return cached;
  const candidates = [process.env.FLOW_AGENT_CONFIG, join(HERE, 'config.json'), join(HERE, 'config.template.json')].filter(Boolean);
  let file = {};
  for (const c of candidates) {
    if (!existsSync(c)) continue;
    try {
      file = JSON.parse(stripBom(readFileSync(c, 'utf8')));
      break;
    } catch {
      /* try the next candidate */
    }
  }
  const src = sourceRoots();
  const roots = {};
  for (const k of ['app', 'plugins', 'chats']) roots[k] = (file.roots && file.roots[k]) || src[k] || '';
  const identity = { ...DEFAULTS.identity, ...(file.identity || {}) };
  if (process.env.FLOW_AGENT_GIT_NAME) identity.name = process.env.FLOW_AGENT_GIT_NAME;
  if (process.env.FLOW_AGENT_GIT_EMAIL) identity.email = process.env.FLOW_AGENT_GIT_EMAIL;
  cached = { ...DEFAULTS, ...file, roots, identity };
  return cached;
}

/** Which of the three repos holds [p]: {key: 'app'|'plugins'|'chats', root}. Plugins and chats first: the app's name is their prefix. */
export function repoOf(p) {
  if (!p) return null;
  const n = `${normPath(p)}/`;
  const { roots } = loadConfig();
  for (const key of ['plugins', 'chats', 'app']) {
    if (roots[key] && n.startsWith(`${normPath(roots[key])}/`)) return { key, root: roots[key] };
  }
  return null;
}

/** Repo-relative path with forward slashes and the original case. */
export function relIn(root, p) {
  return relative(resolve(root), resolve(p)).replace(/\\/g, '/');
}

/** Lock identity: the parent chat for a subagent transcript, else the conversation itself. */
export function identityOf(input) {
  const t = String(input.transcript_path || '');
  const m = /agent-transcripts[\\/]([^\\/]+)[\\/]subagents[\\/]([^\\/]+?)(?:\.jsonl)?$/i.exec(t);
  if (m) return { conv: m[1], subagent: m[2] };
  return { conv: input.conversation_id || input.session_id || '', subagent: null };
}

/** Read all of stdin, giving up after [deadlineMs]. Strips a BOM; decodes UTF-16 if that is what arrives. */
export function readStdin(deadlineMs = 2000) {
  return new Promise((done) => {
    const chunks = [];
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      const buf = Buffer.concat(chunks);
      const text = buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe ? buf.toString('utf16le') : buf.toString('utf8');
      done(stripBom(text));
    };
    const timer = setTimeout(finish, deadlineMs);
    process.stdin.on('data', (c) => chunks.push(c));
    process.stdin.on('end', finish);
    process.stdin.on('error', finish);
    process.stdin.resume();
  });
}

let written = false;

/** The only stdout write: one JSON document, non-ASCII escaped. Later calls do nothing. */
export function writeResponse(obj) {
  if (written) return;
  written = true;
  const text = JSON.stringify(obj).replace(/[\u007f-\uffff]/g, (c) => `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`) + '\n';
  const buf = Buffer.from(text, 'ascii');
  let off = 0;
  for (let spins = 0; off < buf.length; ) {
    try {
      off += writeSync(1, buf, off);
    } catch (e) {
      if (e.code === 'EAGAIN' && spins++ < 2000) continue;
      return;
    }
  }
}

/**
 * Run a hook: kill switch, stdin, handler, one response. Any failure prints the neutral response and exits 0.
 * [handler] gets the parsed input and returns the response object (or nothing for neutral).
 */
export async function run(event, handler, { deadlineMs = 4000 } = {}) {
  const neutral = neutralFor(event);
  const finish = (resp) => {
    writeResponse(resp ?? neutral);
    process.exit(0);
  };
  process.on('uncaughtException', () => finish(neutral));
  process.on('unhandledRejection', () => finish(neutral));
  setTimeout(() => finish(neutral), deadlineMs).unref();
  try {
    if (killSwitchOn()) return finish(neutral);
    let input;
    try {
      input = JSON.parse(await readStdin());
    } catch {
      return finish(neutral);
    }
    if (!input || typeof input !== 'object') return finish(neutral);
    if (testMode() && process.env.FLOW_AGENT_TEST_CRASH === '1') throw new Error('test crash');
    if (testMode() && process.env.FLOW_AGENT_TEST_CRASH === 'async') {
      setTimeout(() => {
        throw new Error('async test crash');
      }, 0);
      await new Promise((r) => setTimeout(r, 200));
    }
    finish(await handler(input));
  } catch {
    finish(neutral);
  }
}
