// Commit only the files this chat edited. Agents can't run git add/commit; this is the way.
// Usage: node commit-mine.mjs -m "<area>: <msg>" --device "ran: ... | left for owner: ... | none: <reason>"
//          [--also <path>...] [--no-verify-reason "<why>"] [--repo <path>]
// The Shell hook appends --conversation <id>; the script refuses to run without it.
import { join, resolve } from 'node:path';
import { git, readJson } from './lib.mjs';
import { loadConfig, relIn, repoOf } from './hook-lib.mjs';
import { acquire, denyMessages, readEdits, removeEdits, stateDir } from './hook-state.mjs';
import { contentMap } from './state-hash.mjs';

function fail(message) {
  console.error(`commit-mine: ${message}`);
  process.exit(1);
}

function parseArgs(argv) {
  const o = { also: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '-m' || a === '--message') o.message = argv[++i];
    else if (a === '--device') o.device = argv[++i];
    else if (a === '--conversation') o.conversation = argv[++i];
    else if (a === '--no-verify-reason') o.skipReason = argv[++i];
    else if (a === '--repo') o.repo = argv[++i];
    else if (a === '--also') {
      while (argv[i + 1] && !argv[i + 1].startsWith('-')) o.also.push(argv[++i]);
    } else fail(`unknown argument ${a}`);
  }
  return o;
}

/** Everything git shows as changed, as {path, status}; a rename lists both paths. */
function dirtyEntries(root) {
  const entries = (git(root, ['status', '--porcelain=v1', '-z', '--untracked-files=all']) || '').split('\0');
  const out = [];
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i];
    if (!e) continue;
    out.push({ path: e.slice(3), status: e.slice(0, 2) });
    if (e[0] === 'R' || e[0] === 'C' || e[1] === 'R' || e[1] === 'C') out.push({ path: entries[++i], status: 'D ' });
  }
  return out;
}

const o = parseArgs(process.argv.slice(2));
if (!o.conversation) {
  fail('missing --conversation. The Shell hook adds it; if you see this, the hook did not run. Stop and tell the user (is the flow-agent hook installed?).');
}
if (!o.device) fail('missing --device "ran: ... | left for owner: ... | none: <reason>".');
if (!o.message) fail('missing -m "<area>: <msg>".');

const here = resolve(o.repo || process.cwd());
const repo = repoOf(here);
if (!repo) fail(`${here} is not inside the app, plugins or chats repo.`);
const { root } = repo;
const cfg = loadConfig();
if (!cfg.identity.name || !cfg.identity.email) fail('no git identity in config.json (identity.name / identity.email).');

if (repo.key !== 'chats') {
  const res = acquire(root, o.conversation);
  if (!res.ok) fail(denyMessages(root, res).user_message);
}

const dirty = dirtyEntries(root);
const dirtySet = new Map(dirty.map((d) => [d.path, d.status]));
const edits = readEdits(root, o.conversation);
const matchedEdits = new Set();
const paths = new Set();
for (const d of dirty) {
  for (const e of edits) {
    if (d.path === e || d.path.startsWith(`${e}/`)) {
      paths.add(d.path);
      matchedEdits.add(e);
    }
  }
}
for (const p of o.also) {
  const rel = relIn(root, resolve(here, p));
  if (rel.startsWith('..')) fail(`--also ${p} is outside ${root}.`);
  if (dirtySet.has(rel)) paths.add(rel);
  else console.log(`commit-mine: --also ${rel} has no changes; skipped.`);
}
if (paths.size === 0) fail('nothing to commit: none of the files this chat edited have changes.');

const trailers = [`Device-check: ${o.device}`];
if (repo.key !== 'chats') {
  const stamp = readJson(join(stateDir(root), 'stamp-pass.json'), null);
  if (o.skipReason) trailers.push(`Verify-skipped: ${o.skipReason}`);
  else {
    if (!stamp) fail('no passing verify stamp. Run scripts/verify.ps1 first, or pass --no-verify-reason "<why>".');
    const map = await contentMap(root);
    const stale = [...paths].filter((p) => stamp.map[p] !== map[p]);
    if (stale.length) {
      fail(`these files changed since the last passing verify: ${stale.slice(0, 5).join(', ')}${stale.length > 5 ? ` and ${stale.length - 5} more` : ''}. Run scripts/verify.ps1 again, or pass --no-verify-reason "<why>".`);
    }
  }
}

const list = [...paths];
const untracked = list.filter((p) => dirtySet.get(p) === '??');
if (untracked.length) git(root, ['--literal-pathspecs', 'add', '--', ...untracked]);
try {
  git(root, ['--literal-pathspecs', '-c', `user.name=${cfg.identity.name}`, '-c', `user.email=${cfg.identity.email}`, 'commit', '--only', '-m', o.message, '-m', trailers.join('\n'), '--', ...list]);
} catch (e) {
  fail(String(e.message).split('\n').slice(0, 6).join('\n'));
}
removeEdits(root, o.conversation, [...matchedEdits]);
const head = git(root, ['rev-parse', '--short', 'HEAD']).trim();
console.log(`commit-mine: committed ${list.length} file(s) as ${head}:`);
for (const p of list) console.log(`  ${p}`);
const left = dirtyEntries(root).map((d) => d.path).filter((p) => !paths.has(p));
if (left.length) console.log(`not committed: not edited by this chat: ${left.slice(0, 20).join(', ')}${left.length > 20 ? ` and ${left.length - 20} more` : ''}`);
