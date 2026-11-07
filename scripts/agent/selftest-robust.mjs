// Selftest cases: crashes, BOM, bad input, stdin deadline, kill switch, PowerShell pipe, installer, capture.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { cli, fwd, hook, hookNoClose, makeWorld, readJsonFile, SRC, stateFile } from './selftest-lib.mjs';
import { base, clearLock, edit, prompt, setLock, shell, stopEvent } from './selftest-payloads.mjs';

const ALLOW = { permission: 'allow' };

export async function robustCases(world, t) {
  const { app } = world.roots;
  const A = 'conv-a';
  const B = 'conv-b';
  clearLock(app);
  setLock(app, A);

  t.section('Robustness');
  const crash = { FLOW_AGENT_TEST_CRASH: '1' };
  let r = hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(app, 'x.txt')), { env: crash });
  t.check('crash in preToolUse: neutral allow, exit 0', r.status === 0 && r.stdout === '{"permission":"allow"}\n', r.stdout + r.stderr);
  t.eq('crash in beforeSubmitPrompt: continue', hook(world, 'hook-beforeSubmitPrompt', prompt(world, B, 'x'), { env: crash }).json, { continue: true });
  t.eq('crash in stop: {}', hook(world, 'hook-stop', stopEvent(world, B), { env: crash }).json, {});
  r = hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(app, 'x.txt')), { env: { FLOW_AGENT_TEST_CRASH: 'async' } });
  t.check('uncaught exception after start: neutral allow, exit 0', r.status === 0 && r.stdout === '{"permission":"allow"}\n', r.stdout + r.stderr);
  r = hook(world, 'hook-preToolUse', null, { raw: '\uFEFF' + JSON.stringify(edit(world, B, 'Write', join(app, 'x.txt'))) });
  t.eq('BOM before the JSON is stripped (hook still denies)', r.json?.permission, 'deny');
  r = hook(world, 'hook-preToolUse', null, { raw: '{not json' });
  t.check('invalid JSON on stdin: neutral allow', r.stdout === '{"permission":"allow"}\n', r.stdout);
  r = hook(world, 'hook-preToolUse', null, { raw: '' });
  t.check('empty stdin: neutral allow', r.stdout === '{"permission":"allow"}\n', r.stdout);
  r = hook(world, 'hook-preToolUse', null, { raw: '[1,2]' });
  t.check('JSON that is not an object: neutral allow', r.json?.permission === 'allow', r.stdout);
  r = hook(world, 'hook-preToolUse', { tool_name: 'Write', tool_input: { path: fwd(join(app, 'x.txt')) } });
  t.eq('no conversation id: neutral allow', r.json, ALLOW);
  r = await hookNoClose(world, 'hook-preToolUse');
  t.check('stdin that never closes: neutral allow after about 2 s', r.stdout === '{"permission":"allow"}\n' && r.ms >= 1800 && r.ms < 6000, `${r.ms} ms, ${r.stdout}`);

  t.section('Kill switch');
  const off = join(world.home, '.cursor', 'hooks', 'flow-agent', 'OFF');
  writeFileSync(off, '');
  t.eq('OFF: preToolUse allows an edit another chat would be denied', hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(app, 'x.txt'))).json, ALLOW);
  t.eq('OFF: git add allowed', hook(world, 'hook-preToolUse', shell(world, B, 'git add -A')).json, ALLOW);
  t.eq('OFF: beforeSubmitPrompt continues', hook(world, 'hook-beforeSubmitPrompt', prompt(world, B, '/takeover')).json, { continue: true });
  t.eq('OFF: /takeover did nothing', readJsonFile(stateFile(app, 'lock.json'))?.conversation_id, A);
  t.eq('OFF: stop is {}', hook(world, 'hook-stop', stopEvent(world, B)).json, {});
  rmSync(off);
  t.eq('OFF removed: guard is back', hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(app, 'x.txt'))).json?.permission, 'deny');

  t.section('PowerShell pipe (the way Cursor launches hooks)');
  if (process.platform === 'win32') {
    clearLock(app);
    rmSync(stateFile(app, 'edits', `${A}.txt`), { force: true });
    const payload = JSON.stringify(edit(world, A, 'Write', join(app, 'caf\u00e9.md')));
    const ps = spawnSync('powershell.exe', ['-NoProfile', '-Command', `node "${join(SRC, 'hook-preToolUse.mjs')}"`], { input: Buffer.from(payload, 'utf8'), encoding: 'utf8', env: world.env, windowsHide: true });
    t.check('powershell.exe -> node: allowed with one JSON line', ps.status === 0 && ps.stdout.trim() === '{"permission":"allow"}', `${ps.status} ${ps.stdout} ${ps.stderr}`);
    const logged = readFileSync(stateFile(app, 'edits', `${A}.txt`), 'utf8');
    t.eq('non-ASCII path survives the pipe (checked in the edit log)', logged, 'caf\u00e9.md\n');
  } else console.log('  skip  not Windows');
  clearLock(app);
  rmSync(stateFile(app, 'edits', `${A}.txt`), { force: true });
}

export async function installCases(t) {
  t.section('Installer and capture');
  const w = makeWorld();
  try {
    const home = w.home;
    const hooksJson = join(home, '.cursor', 'hooks.json');
    const dir = join(home, '.cursor', 'hooks', 'flow-agent');
    writeFileSync(hooksJson, '\uFEFF' + JSON.stringify({ version: 1, hooks: { stop: [{ command: 'echo other-tool' }], preToolUse: [{ command: 'echo other-pre', matcher: 'Read' }] } }));
    const install = (...a) => spawnSync(process.execPath, [join(SRC, 'install-hooks.mjs'), ...a], { encoding: 'utf8', env: w.env, windowsHide: true });
    let r = install('--no-selftest');
    t.check('install runs', r.status === 0, r.stdout + r.stderr);
    let j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    const ours = (e) => /flow-agent/.test(e.command);
    t.check('existing hooks kept', j.hooks.stop.some((e) => e.command === 'echo other-tool') && j.hooks.preToolUse.some((e) => e.command === 'echo other-pre'));
    t.check('events installed', ['preToolUse', 'beforeSubmitPrompt', 'stop', 'preCompact', 'sessionEnd', 'subagentStop', 'postToolUse'].every((ev) => j.hooks[ev]?.some(ours)));
    const pre = j.hooks.preToolUse.find(ours);
    t.check('preToolUse has a narrow matcher including Shell and CallDynamicTool, never *', /Shell/.test(pre.matcher) && /CallDynamicTool/.test(pre.matcher) && !/(^|\|)\*($|\|)/.test(pre.matcher), pre.matcher);
    t.check('timeouts: 5 s, stop 10 s, failClosed false, stop loop_limit', pre.timeout === 5 && pre.failClosed === false && j.hooks.stop.find(ours).timeout === 10 && j.hooks.stop.find(ours).loop_limit > 0);
    t.check('backup of the original hooks.json', existsSync(`${hooksJson}.flow-bak`));
    t.check('copies and config.json written', ['hook-lib.mjs', 'hook-preToolUse.mjs', 'commit-mine.mjs', 'lock.mjs', 'state-hash.mjs', 'lib.mjs'].every((f) => existsSync(join(dir, f))) && !!readJsonFile(join(dir, 'config.json'))?.identity?.email);
    t.check('install-hooks.mjs and the selftest are not copied', !existsSync(join(dir, 'install-hooks.mjs')) && !existsSync(join(dir, 'selftest.mjs')));
    const count = j.hooks.preToolUse.length + j.hooks.stop.length;
    install('--no-selftest');
    j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    t.eq('second install is idempotent', j.hooks.preToolUse.length + j.hooks.stop.length, count);

    const cfgPath = join(dir, 'config.json');
    const cfg = readJsonFile(cfgPath);
    writeFileSync(cfgPath, JSON.stringify({ ...cfg, roots: w.roots }));
    const env = { ...w.env };
    delete env.FLOW_AGENT_CONFIG;
    const installed = spawnSync(process.execPath, [join(dir, 'hook-preToolUse.mjs')], { input: JSON.stringify(edit(w, 'conv-i', 'Write', join(w.roots.app, 'src/a.txt'))), encoding: 'utf8', env, windowsHide: true });
    t.check('installed copy runs from the install folder using config.json', installed.stdout.trim() === '{"permission":"allow"}' && readFileSync(stateFile(w.roots.app, 'edits', 'conv-i.txt'), 'utf8') === 'src/a.txt\n', installed.stdout + installed.stderr);

    r = install('--shell-hook', 'beforeShellExecution', '--no-selftest');
    j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    t.check('--shell-hook beforeShellExecution moves the Shell guard', !/Shell/.test(j.hooks.preToolUse.find(ours).matcher.replace('CallDynamicTool', '')) && j.hooks.beforeShellExecution?.some(ours), r.stdout + r.stderr);
    t.check('--no-selftest is refused outside the selftest', spawnSync(process.execPath, [join(SRC, 'install-hooks.mjs'), '--no-selftest'], { encoding: 'utf8', env: { ...process.env, FLOW_AGENT_USERHOME: '' }, windowsHide: true }).status === 2);

    r = install('--capture');
    j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    t.check('--capture adds capture entries for every event used', ['preToolUse', 'postToolUse', 'beforeShellExecution', 'beforeSubmitPrompt', 'stop', 'sessionEnd', 'preCompact', 'subagentStop'].every((ev) => j.hooks[ev]?.some((e) => /hook-capture\.mjs" /.test(e.command))), r.stdout + r.stderr);
    const cap = spawnSync(process.execPath, [join(dir, 'hook-capture.mjs'), 'preToolUse'], { input: JSON.stringify({ hook_event_name: 'preToolUse', tool_name: 'Write', tool_input: { path: 'caf\u00e9.md' } }), encoding: 'utf8', env: w.env, windowsHide: true });
    const line = readFileSync(join(home, '.cursor', 'hooks', 'flow-capture', 'preToolUse.jsonl'), 'utf8').trim();
    t.check('capture prints the neutral response and records the payload', cap.stdout.trim() === '{"permission":"allow"}' && JSON.parse(line).input.tool_input.path === 'caf\u00e9.md' && JSON.parse(line).replacement_chars === false, cap.stdout + line);
    const cap2 = spawnSync(process.execPath, [join(dir, 'hook-capture.mjs'), 'beforeSubmitPrompt'], { input: '{"prompt":"/takeover"}', encoding: 'utf8', env: w.env, windowsHide: true });
    t.eq('capture neutral for beforeSubmitPrompt', cap2.stdout.trim(), '{"continue":true}');
    install('--uninstall-capture');
    j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    t.check('--uninstall-capture removes only capture entries', !JSON.stringify(j).includes('hook-capture') && JSON.stringify(j).includes('hook-preToolUse'));

    r = install('--uninstall');
    j = JSON.parse(readFileSync(hooksJson, 'utf8'));
    t.check('--uninstall removes our entries, keeps the others, deletes the folder', !JSON.stringify(j).includes('flow-agent') && j.hooks.stop.some((e) => e.command === 'echo other-tool') && !existsSync(dir), r.stdout + r.stderr);
    mkdirSync(join(home, '.cursor'), { recursive: true });
    writeFileSync(hooksJson, '{ broken');
    r = install('--no-selftest');
    t.check('unparseable hooks.json: refuses and leaves it alone', r.status === 1 && readFileSync(hooksJson, 'utf8') === '{ broken', r.stdout + r.stderr);
    t.check('cli(): commit-mine refuses without --conversation', cli(w, 'commit-mine', ['-m', 'x: y', '--device', 'none']).status === 1);
  } finally {
    w.cleanup();
  }
}
