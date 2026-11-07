// Shared locations and path helpers for the scripts in scripts/agent.
import { mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const APP_ROOT = resolve(join(dirname(fileURLToPath(import.meta.url)), '..', '..'));
export const PLUGINS_ROOT = resolve(join(APP_ROOT, '../flow-reader-plugins'));
export const CHATS_ROOT = resolve(join(APP_ROOT, '../flow-reader-chats'));
export const KOTLIN_ROOT = join(APP_ROOT, 'app/src/main/java/com/personal/flowreader');

/** Per-repo scratch state (stamps, jscpd reports). Lives inside .git so it is never committed. */
export function stateDir(repo) {
  const dir = join(repo, '.git/flow-agent');
  mkdirSync(dir, { recursive: true });
  return dir;
}

export function normPath(p) {
  return String(p).replace(/\\/g, '/').toLowerCase();
}

/** Repo root that contains [path]. Plugins and chats are checked first: their names start with the app's. */
export function whichRepo(path) {
  const p = `${normPath(path)}/`;
  for (const root of [PLUGINS_ROOT, CHATS_ROOT, APP_ROOT]) {
    const r = `${normPath(root)}/`;
    if (p.startsWith(r)) return root;
  }
  return null;
}
