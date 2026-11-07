// Test harness for selftest.mjs: throwaway repos, hook runner, small assertion helpers.
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SRC = dirname(fileURLToPath(import.meta.url));
export const fwd = (p) => p.replace(/\\/g, '/');

export function gitIn(root, ...args) {
  const r = spawnSync('git', ['-c', 'user.name=selftest', '-c', 'user.email=selftest@example.test', ...args], { cwd: root, encoding: 'utf8', windowsHide: true });
  if (r.status !== 0) throw new Error(`git ${args.join(' ')} failed in ${root}: ${r.stderr}`);
  return r.stdout;
}

export const commitAll = (root, msg = 'selftest') => {
  gitIn(root, 'add', '-A');
  gitIn(root, 'commit', '-q', '--allow-empty', '-m', msg);
};

export function makeWorld() {
  const base = mkdtempSync(join(tmpdir(), 'flow-agent-selftest-'));
  const roots = {};
  for (const [key, name] of [['app', 'flow-reader'], ['plugins', 'flow-reader-plugins'], ['chats', 'flow-reader-chats']]) {
    const dir = join(base, name);
    mkdirSync(join(dir, 'src'), { recursive: true });
    mkdirSync(join(dir, 'docs'), { recursive: true });
    writeFileSync(join(dir, 'README.md'), 'readme\n');
    writeFileSync(join(dir, 'src', 'a.txt'), 'a\n');
    writeFileSync(join(dir, 'docs', 'b.md'), 'b\n');
    gitIn(dir, 'init', '-q');
    gitIn(dir, 'config', 'core.autocrlf', 'false');
    commitAll(dir, 'initial');
    roots[key] = dir;
  }
  const outside = join(base, 'outside');
  mkdirSync(outside);
  const home = join(base, 'home');
  mkdirSync(join(home, '.cursor', 'hooks', 'flow-agent'), { recursive: true });
  const cfgFile = join(base, 'config.json');
  writeFileSync(cfgFile, JSON.stringify({ roots, identity: { name: 'Self Test', email: 'self@example.test' }, lockTimeoutMinutes: 30, subagentIdleMinutes: 10 }));
  const env = { ...process.env, FLOW_AGENT_USERHOME: home, FLOW_AGENT_CONFIG: cfgFile };
  delete env.FLOW_AGENT_TEST_CRASH;
  delete env.FLOW_AGENT_ADB_MOCK;
  return { base, roots, outside, home, cfgFile, env, cleanup: () => rmSync(base, { recursive: true, force: true, maxRetries: 3 }) };
}

/** Run a hook script with [payload] on stdin. Returns {stdout, json, status, stderr}. */
export function hook(world, name, payload, { env = {}, raw = null } = {}) {
  const input = raw ?? JSON.stringify(payload);
  const r = spawnSync(process.execPath, [join(SRC, `${name}.mjs`)], { input, encoding: 'utf8', env: { ...world.env, ...env }, windowsHide: true });
  let json = null;
  try {
    json = JSON.parse(r.stdout);
  } catch {
    /* leave null */
  }
  return { stdout: r.stdout, json, status: r.status, stderr: r.stderr };
}

/** Run a hook whose stdin never closes; resolves with its stdout and how long it took. */
export function hookNoClose(world, name, env = {}) {
  return new Promise((done) => {
    const t0 = Date.now();
    const child = spawn(process.execPath, [join(SRC, `${name}.mjs`)], { env: { ...world.env, ...env }, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    let out = '';
    child.stdout.on('data', (c) => (out += c));
    child.on('close', (status) => done({ stdout: out, status, ms: Date.now() - t0 }));
    setTimeout(() => child.kill(), 8000).unref();
  });
}

export function cli(world, script, args, { cwd, env = {} } = {}) {
  const r = spawnSync(process.execPath, [join(SRC, `${script}.mjs`), ...args], { cwd: cwd || world.roots.app, encoding: 'utf8', env: { ...world.env, ...env }, windowsHide: true });
  return { status: r.status, out: `${r.stdout}${r.stderr}`, stdout: r.stdout, stderr: r.stderr };
}

export const stateFile = (root, ...p) => join(root, '.git', 'flow-agent', ...p);
export const readJsonFile = (f) => (existsSync(f) ? JSON.parse(readFileSync(f, 'utf8')) : null);

export function makeTester() {
  const failures = [];
  let passed = 0;
  const t = {
    check(name, ok, detail = '') {
      if (ok) {
        passed++;
        console.log(`  ok    ${name}`);
      } else {
        failures.push(name);
        console.log(`  FAIL  ${name}${detail ? `\n          ${String(detail).replace(/\n/g, '\n          ')}` : ''}`);
      }
    },
    eq(name, actual, expected) {
      t.check(name, JSON.stringify(actual) === JSON.stringify(expected), `expected ${JSON.stringify(expected)}\n          got      ${JSON.stringify(actual)}`);
    },
    section: (title) => console.log(`\n${title}`),
    summary() {
      console.log(`\n${passed} passed, ${failures.length} failed`);
      for (const f of failures) console.log(`  failed: ${f}`);
      return failures.length === 0;
    },
  };
  return t;
}
