// Install the Flow Reader agent hooks for the current user. Owner only; agents are denied this script.
// Usage: node scripts/agent/install-hooks.mjs [--dry-run] [--shell-hook preToolUse|beforeShellExecution] [--loop-limit N]
//        node scripts/agent/install-hooks.mjs --uninstall           remove the hooks and the installed copies
//        node scripts/agent/install-hooks.mjs --capture             install only the capture hook (no selftest)
//        node scripts/agent/install-hooks.mjs --uninstall-capture   remove the capture entries (keeps captured data)
// Install = selftest, then copy the scripts to ~/.cursor/hooks/flow-agent/, write config.json, merge ~/.cursor/hooks.json.
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { APP_ROOT, CHATS_ROOT, PLUGINS_ROOT } from './paths.mjs';

const SRC = dirname(fileURLToPath(import.meta.url));
const HOME = process.env.FLOW_AGENT_USERHOME || homedir();
const CURSOR = join(HOME, '.cursor');
const INSTALL = join(CURSOR, 'hooks', 'flow-agent');
const HOOKS_JSON = join(CURSOR, 'hooks.json');
const fwd = (p) => p.replace(/\\/g, '/');

const MODULES = [
  'hook-lib', 'hook-state', 'shell-parse', 'guards', 'shell-guard', 'hook-tools', 'hook-turn',
  'hook-preToolUse', 'hook-beforeShellExecution', 'hook-beforeSubmitPrompt', 'hook-stop', 'hook-preCompact',
  'hook-sessionEnd', 'hook-subagentStop', 'hook-postToolUse-grep', 'hook-capture', 'commit-mine', 'lock',
  'lib', 'paths', 'state-hash',
];
const EDIT_MATCHER = 'Write|StrReplace|Delete|EditNotebook|Edit|MultiEdit|ApplyPatch|NotebookEdit|CallDynamicTool|MCP:CallDynamicTool';
const CAPTURE_EVENTS = [
  'preToolUse', 'postToolUse', 'beforeShellExecution', 'beforeSubmitPrompt', 'stop', 'sessionEnd', 'preCompact',
  'subagentStart', 'subagentStop', 'afterFileEdit',
];

const args = process.argv.slice(2);
const flag = (n) => args.includes(n);
const value = (n, fallback) => (args.includes(n) ? args[args.indexOf(n) + 1] : fallback);

const stripBom = (s) => s.replace(/^\uFEFF/, '');
const command = (name, ...extra) => `node "${fwd(join(INSTALL, `${name}.mjs`))}"${extra.length ? ` ${extra.join(' ')}` : ''}`;
const isCapture = (e) => /hook-capture\.mjs/.test(e.command || '');
const isOurs = (e) => /\/hooks\/flow-agent\//.test(fwd(e.command || ''));

function readHooksJson() {
  if (!existsSync(HOOKS_JSON)) return { version: 1, hooks: {} };
  const text = stripBom(readFileSync(HOOKS_JSON, 'utf8'));
  try {
    const j = JSON.parse(text);
    j.hooks ??= {};
    j.version ??= 1;
    return j;
  } catch (e) {
    console.error(`Cannot parse ${HOOKS_JSON} (${e.message}). Fix it or move it aside; nothing was changed.`);
    process.exit(1);
  }
}

function writeHooksJson(json) {
  if (flag('--dry-run')) {
    console.log(`--dry-run: would write ${HOOKS_JSON}:\n${JSON.stringify(json, null, 2)}`);
    return;
  }
  mkdirSync(CURSOR, { recursive: true });
  const backup = `${HOOKS_JSON}.flow-bak`;
  if (existsSync(HOOKS_JSON) && !existsSync(backup)) copyFileSync(HOOKS_JSON, backup);
  writeFileSync(HOOKS_JSON, JSON.stringify(json, null, 2) + '\n');
}

/** Drop entries matched by [pred]; remove events that end up empty. */
function strip(json, pred) {
  for (const ev of Object.keys(json.hooks)) {
    json.hooks[ev] = (json.hooks[ev] || []).filter((e) => !pred(e));
    if (json.hooks[ev].length === 0) delete json.hooks[ev];
  }
}

const add = (json, ev, entry) => (json.hooks[ev] ??= []).push(entry);

function guardEntries(cfg) {
  const t = { timeout: 5, failClosed: false };
  const shellInPre = cfg.shellHook !== 'beforeShellExecution';
  const entries = {
    preToolUse: [{ command: command('hook-preToolUse'), matcher: shellInPre ? `${EDIT_MATCHER}|Shell` : EDIT_MATCHER, ...t }],
    beforeSubmitPrompt: [{ command: command('hook-beforeSubmitPrompt'), ...t }],
    stop: [{ command: command('hook-stop'), timeout: 10, failClosed: false, loop_limit: cfg.loopLimit }],
    preCompact: [{ command: command('hook-preCompact'), ...t }],
    sessionEnd: [{ command: command('hook-sessionEnd'), ...t }],
    subagentStop: [{ command: command('hook-subagentStop'), ...t }],
    postToolUse: [{ command: command('hook-postToolUse-grep'), matcher: 'Grep', timeout: 10, failClosed: false }],
  };
  if (!shellInPre) entries.beforeShellExecution = [{ command: command('hook-beforeShellExecution'), ...t }];
  return entries;
}

function copyFiles(names) {
  mkdirSync(INSTALL, { recursive: true });
  for (const n of names) copyFileSync(join(SRC, `${n}.mjs`), join(INSTALL, `${n}.mjs`));
}

function writeConfig() {
  const template = JSON.parse(readFileSync(join(SRC, 'config.template.json'), 'utf8'));
  const file = join(INSTALL, 'config.json');
  const existing = existsSync(file) ? JSON.parse(stripBom(readFileSync(file, 'utf8'))) : {};
  const cfg = { ...template, ...existing, roots: { app: APP_ROOT, plugins: PLUGINS_ROOT, chats: CHATS_ROOT } };
  if (value('--loop-limit')) cfg.loopLimit = Number(value('--loop-limit'));
  if (value('--shell-hook')) cfg.shellHook = value('--shell-hook');
  writeFileSync(file, JSON.stringify(cfg, null, 2) + '\n');
  return cfg;
}

function selftest() {
  console.log('Running selftest before installing...');
  const r = spawnSync(process.execPath, [join(SRC, 'selftest.mjs')], { stdio: 'inherit' });
  if (r.status !== 0) {
    console.error('\nSelftest failed. Nothing was installed.');
    process.exit(1);
  }
}

if (flag('--uninstall') || flag('--uninstall-capture')) {
  const json = readHooksJson();
  strip(json, flag('--uninstall') ? (e) => isOurs(e) || isCapture(e) : isCapture);
  writeHooksJson(json);
  if (flag('--uninstall') && !flag('--dry-run')) rmSync(INSTALL, { recursive: true, force: true });
  console.log(flag('--uninstall') ? `Removed the flow-agent hooks from ${HOOKS_JSON} and deleted ${INSTALL}.` : 'Removed the capture hook entries. Captured data stays in ~/.cursor/hooks/flow-capture/.');
  process.exit(0);
}

if (flag('--capture')) {
  if (!flag('--dry-run')) copyFiles(['hook-capture']);
  const json = readHooksJson();
  strip(json, isCapture);
  for (const ev of CAPTURE_EVENTS) add(json, ev, { command: command('hook-capture', ev), timeout: 5, failClosed: false });
  writeHooksJson(json);
  console.log(`Capture hook installed for: ${CAPTURE_EVENTS.join(', ')}.\nRun one chat that covers the checklist in scripts/agent/README.md, then:\n  node scripts/agent/install-hooks.mjs --uninstall-capture\nRaw payloads: ${join(CURSOR, 'hooks', 'flow-capture')}`);
  process.exit(0);
}

if (flag('--no-selftest')) {
  if (!process.env.FLOW_AGENT_USERHOME) {
    console.error('--no-selftest is only allowed by the selftest itself (FLOW_AGENT_USERHOME set).');
    process.exit(2);
  }
} else selftest();

let cfg = { ...JSON.parse(readFileSync(join(SRC, 'config.template.json'), 'utf8')) };
if (!flag('--dry-run')) {
  copyFiles(MODULES);
  cfg = writeConfig();
} else {
  cfg.loopLimit = Number(value('--loop-limit', cfg.loopLimit));
  cfg.shellHook = value('--shell-hook', cfg.shellHook);
}
const json = readHooksJson();
strip(json, (e) => isOurs(e) && !isCapture(e));
for (const [ev, list] of Object.entries(guardEntries(cfg))) for (const e of list) add(json, ev, e);
writeHooksJson(json);
if (flag('--dry-run')) {
  console.log('\nDry run: nothing was copied or written.');
  process.exit(0);
}
console.log(`
Installed. Scripts: ${INSTALL}
Hooks:   ${HOOKS_JSON}  (backup of the original: hooks.json.flow-bak, if there was one)
Shell guard runs through: ${cfg.shellHook}

Kill switch:  New-Item "${join(INSTALL, 'OFF')}"   (delete the file to turn the hooks back on)
Uninstall:    node scripts/agent/install-hooks.mjs --uninstall
After changing anything in scripts/agent/, run this installer again.`);
