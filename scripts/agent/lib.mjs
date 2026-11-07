// Small helpers shared by the check scripts: git, file walking, JSON files, CLI detection.
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { pathToFileURL } from 'node:url';

/** Run git in [repo] and return stdout. Returns null on a non-zero exit when `allowFail` is set. */
export function git(repo, args, { input, allowFail = false, encoding = 'utf8' } = {}) {
  const r = spawnSync('git', ['--no-optional-locks', ...args], {
    cwd: repo,
    input,
    encoding,
    maxBuffer: 256 * 1024 * 1024,
    windowsHide: true,
  });
  if (r.status !== 0) {
    if (allowFail) return null;
    throw new Error(`git ${args.join(' ')} failed in ${repo}: ${String(r.stderr).trim()}`);
  }
  return r.stdout;
}

const SKIP_DIRS = new Set(['.git', '.gradle', '.kotlin', 'build', 'node_modules', '.cxx', '.idea', 'captures']);

/** All files under [dir] as absolute paths, skipping build output and tool folders. */
export function walk(dir, { skip = SKIP_DIRS, filter = null } = {}) {
  const out = [];
  const visit = (d) => {
    let entries;
    try {
      entries = readdirSync(d, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      const full = join(d, e.name);
      if (e.isDirectory()) {
        if (!skip.has(e.name)) visit(full);
      } else if (!filter || filter(full)) {
        out.push(full);
      }
    }
  };
  visit(dir);
  return out;
}

export const rel = (root, file) => relative(root, file).replace(/\\/g, '/');

export function readText(file) {
  return readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
}

export function readJson(file, fallback = null) {
  if (!existsSync(file)) return fallback;
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

export function writeJson(file, value) {
  writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}

export function lineCount(text) {
  if (text === '') return 0;
  return text.split('\n').length - (text.endsWith('\n') ? 1 : 0);
}

/** Read a `#`-commented list file into an array of trimmed non-empty lines. */
export function readList(file) {
  if (!existsSync(file)) return null;
  return readText(file)
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'));
}

export const isMain = (metaUrl) => !!process.argv[1] && metaUrl === pathToFileURL(process.argv[1]).href;

export const isFile = (p) => existsSync(p) && statSync(p).isFile();

/** Glob (`**`, `*`, `?`) to a RegExp over forward-slash paths. */
export function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*') {
      if (glob[i + 1] === '*') {
        i++;
        if (glob[i + 1] === '/') {
          i++;
          re += '(?:.*/)?';
        } else {
          re += '.*';
        }
      } else {
        re += '[^/]*';
      }
    } else if (c === '?') {
      re += '[^/]';
    } else {
      re += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${re}$`);
}
