// Selftest cases: git guard parsing, hook CLIs, Shell writes, adb guard.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fwd, hook, stateFile } from './selftest-lib.mjs';
import { clearLock, setLock, shell } from './selftest-payloads.mjs';

const HOLDER = 'conv-holder';
const B = 'conv-b';

export async function shellCases(world, t) {
  const { app, plugins, chats } = world.roots;
  const run = (conv, command, cwd, env) => hook(world, 'hook-preToolUse', shell(world, conv, command, cwd), { env }).json;
  const verdict = (name, conv, command, expected, cwd, env) => {
    const r = run(conv, command, cwd, env);
    t.eq(`${expected ? 'allow' : 'deny '}: ${name}`, r?.permission, expected ? 'allow' : 'deny');
    return r;
  };
  setLock(app, HOLDER);
  setLock(plugins, HOLDER);
  const a = fwd(app);

  t.section('Git guard');
  for (const [name, cmd] of [
    ['status', 'git status'],
    ['two read-only commands chained with ;', 'git status; git log -3'],
    ['read-only chained with &&', 'git log --oneline -5 && git diff HEAD~1'],
    ['-C with status', `git -C ${a} status`],
    ['-c k=v with log', 'git -c core.pager=cat log'],
    ['--no-pager show', 'git --no-pager show HEAD'],
    ['redirect 2>&1 is not a write', 'git status 2>&1'],
    ['piped to Select-Object', 'git diff | Select-Object -First 5'],
    ['branch listing', 'git branch'],
    ['branch -a', 'git branch -a'],
    ['tag listing', 'git tag'],
    ['remote -v', 'git remote -v'],
    ['config --get', 'git config --get user.name'],
    ['stash list', 'git stash list'],
    ['worktree list', 'git worktree list'],
    ['fetch', 'git fetch origin'],
    ['git inside quotes is text', 'echo "a; git add ."'],
    ['cd to a non-repo folder then git add', `cd ${fwd(world.outside)}; git add .`],
  ]) verdict(name, B, cmd, true);

  for (const [name, cmd, cwd] of [
    ['add -A', 'git add -A'],
    ['commit -a', 'git commit -a -m "x"'],
    ['commit with a ; inside the message', 'git commit -m "a; b"'],
    ['chained after echo', 'echo hi; git add .'],
    ['chained with &&', 'git status && git add .'],
    ['chained after a pipe', 'echo x | git add .'],
    ['cd into the repo from outside, then add', `cd ${a} ; git add .`, world.outside],
    ['Set-Location from outside, then add', `Set-Location -Path '${a}'; git add .`, world.outside],
    ['-C with commit from outside', `git -C ${a} commit -m x`, world.outside],
    ['-c k=v with commit', 'git -c user.name=x -c user.email=y commit -m x'],
    ['call operator &', '& git add .'],
    ['inside powershell -Command', 'powershell -Command "git add ."'],
    ['inside bash -c', "bash -c 'git add .'"],
    ['inside Invoke-Expression', 'iex "git add ."'],
    ['inside a script block', 'foreach ($i in 1..2) { git add . }'],
    ['reset --hard', 'git reset --hard'],
    ['checkout', 'git checkout .'],
    ['branch -D', 'git branch -D x'],
    ['tag creation', 'git tag v1'],
    ['remote add', 'git remote add x y'],
    ['config write', 'git config user.name x'],
    ['stash push', 'git stash'],
    ['log --output writes a file', 'git log --output=out.txt'],
    ['here-string commit message', "git commit -m @'\nmsg; foo\n'@"],
  ]) {
    const r = verdict(name, B, cmd, false, cwd);
    if (name === 'add -A') t.check('deny text points to commit-mine', /commit-mine\.mjs/.test(r?.agent_message || ''), JSON.stringify(r));
  }
  verdict('git add from a cwd outside the repos is not guarded', B, 'git add .', true, world.outside);

  t.section('git push');
  verdict('push by a non-holder', B, 'git push', false);
  verdict('push by the holder', HOLDER, 'git push', true);
  verdict('push --force by the holder', HOLDER, 'git push --force', false);
  verdict('push with a +refspec by the holder', HOLDER, 'git push origin +master', false);
  verdict('push in the chats repo by anyone', B, 'git push', true, chats);

  t.section('Hook CLIs');
  const hooksDir = fwd(join(world.home, '.cursor/hooks/flow-agent'));
  let r = run(B, `node ${hooksDir}/commit-mine.mjs -m "x: y" --device none --conversation evil`);
  t.check('commit-mine gets --conversation of the caller, not the typed one', r?.permission === 'allow' && /--conversation conv-b$/.test(r.updated_input?.command || '') && !/evil/.test(r.updated_input.command), JSON.stringify(r));
  r = run(B, `node ${hooksDir}/commit-mine.mjs -m "a; b" --device x ; git status`);
  t.check('injection lands at the end of the right segment', r?.updated_input?.command === `node ${hooksDir}/commit-mine.mjs -m "a; b" --device x --conversation conv-b ; git status`, JSON.stringify(r));
  r = run(B, `node ${hooksDir}/lock.mjs status`);
  t.check('lock.mjs gets --conversation', /--conversation conv-b$/.test(r?.updated_input?.command || ''), JSON.stringify(r));
  r = run(B, 'node scripts/agent/release-check.mjs');
  t.check('release-check.mjs gets --conversation', /--conversation conv-b$/.test(r?.updated_input?.command || ''), JSON.stringify(r));
  t.eq('updated_input keeps the other tool_input fields', Object.keys(hook(world, 'hook-preToolUse', { ...shell(world, B, `node ${hooksDir}/lock.mjs status`), tool_input: { command: `node ${hooksDir}/lock.mjs status`, working_directory: '.', timeout: 5000 } }).json?.updated_input || {}).sort(), ['command', 'timeout', 'working_directory']);
  verdict('lock.mjs release --force', B, `node ${hooksDir}/lock.mjs release --force`, false);
  verdict('install-hooks.mjs', B, 'node scripts/agent/install-hooks.mjs', false);
  verdict('reading install-hooks.mjs is fine', B, 'Get-Content scripts/agent/install-hooks.mjs', true);
  r = run(B, 'node scripts/agent/verify-state.mjs plan .');
  t.check('other node scripts are left alone', r?.permission === 'allow' && !r.updated_input, JSON.stringify(r));

  t.section('Shell writes');
  for (const [name, cmd, cwd] of [
    ['Set-Content with a path in the app repo', `Set-Content ${a}/x.txt hi`],
    ['Set-Content -Path -Value', `Set-Content -Path ${a}/x.txt -Value hi`],
    ['Add-Content', `Add-Content ${a}/x.txt hi`],
    ['Out-File through a pipe', `"hi" | Out-File ${a}/x.txt`],
    ['Remove-Item after cd', `cd ${a}; Remove-Item foo.txt`],
    ['rm -rf', `rm -rf ${a}/build`],
    ['New-Item', `New-Item -ItemType File -Path ${a}/n.txt`],
    ['Move-Item', `Move-Item ${a}/a.txt ${a}/b.txt`],
    ['Copy-Item with the repo as cwd', 'Copy-Item a b', app],
    ['redirect > with the repo as cwd', 'echo hi > out.txt', app],
    ['redirect >> to a repo path', `echo hi >> ${a}/out.txt`],
    ['write in the plugins repo', `Out-File ${fwd(plugins)}/x.txt`],
    ['write inside powershell -Command', `powershell -Command "Set-Content ${a}/x.txt hi"`],
  ]) verdict(`non-holder: ${name}`, B, cmd, false, cwd);
  const w = run(B, `Set-Content ${a}/x.txt hi`);
  t.check('write deny uses the lock messages', /being edited by another chat/.test(w?.user_message || '') && /Edit refused/.test(w?.agent_message || ''), JSON.stringify(w));
  for (const [name, cmd, cwd] of [
    ['redirect to $null', 'echo hi > $null', app],
    ['2>&1', 'node -v 2>&1', app],
    ['write outside the repos', `Set-Content ${fwd(world.outside)}/x.txt hi`, app],
    ['move outside the repos', `mv ${fwd(world.outside)}/a ${fwd(world.outside)}/b`, app],
    ['write in the chats repo (no lock)', `Set-Content ${fwd(chats)}/note.md hi`],
    ['build command', '.\\gradlew.bat :app:compileDebugKotlin'],
    ['read a file', `Get-Content ${a}/README.md`],
    ['test command', 'npm test', plugins],
  ]) verdict(`non-holder: ${name}`, B, cmd, true, cwd);
  verdict('holder: Set-Content in the app repo', HOLDER, `Set-Content ${a}/x.txt hi`, true);
  t.check('holder write is logged for commit-mine', readFileSync(stateFile(app, 'edits', `${HOLDER}.txt`), 'utf8').includes('x.txt'));
  verdict('holder: redirect with the repo as cwd', HOLDER, 'echo hi > y.txt', true, app);
  clearLock(app);
  verdict('free lock: first write takes it', 'conv-free', `Set-Content ${a}/z.txt hi`, true);
  t.eq('the writer now holds the app lock', JSON.parse(readFileSync(stateFile(app, 'lock.json'), 'utf8')).conversation_id, 'conv-free');
  verdict('hooks folder is protected even for the holder', 'conv-free', `Set-Content ${fwd(world.home)}/.cursor/hooks/flow-agent/OFF x`, false);
  verdict('hooks.json is protected', 'conv-free', `Set-Content ${fwd(world.home)}/.cursor/hooks.json x`, false);
  verdict('hook state is protected', 'conv-free', `Remove-Item ${a}/.git/flow-agent/lock.json`, false);

  t.section('adb guard');
  const EMU = 'List of devices attached\nemulator-5554\tdevice\n';
  const BOTH = `${EMU}R58M123ABC\tdevice\n`;
  const PHONE = 'List of devices attached\nR58M123ABC\tdevice\n';
  const adb = (name, cmd, mock, expected) => verdict(name, B, cmd, expected, world.outside, { FLOW_AGENT_ADB_MOCK: mock });
  adb('run-as with only an emulator attached', 'adb shell run-as com.personal.flowreader ls files', EMU, true);
  adb('run-as with an emulator and a phone attached', 'adb shell run-as com.personal.flowreader ls files', BOTH, false);
  adb('run-as with only a phone attached', 'adb shell run-as com.personal.flowreader ls', PHONE, false);
  adb('-s emulator-5554 with a phone attached', 'adb -s emulator-5554 shell pm clear com.x', BOTH, true);
  adb('-s <phone serial>', 'adb -s R58M123ABC shell rm /sdcard/x', BOTH, false);
  adb('-e with a phone attached', 'adb -e shell sqlite3 db', BOTH, true);
  adb('-d', 'adb -d shell run-as com.x ls', EMU, false);
  adb('push to a phone', 'adb push a.txt /sdcard/', PHONE, false);
  adb('quoted rm in a shell string', 'adb shell "rm -rf /sdcard/x"', PHONE, false);
  adb('content update', 'adb shell content update --uri content://x', PHONE, false);
  adb('devices', 'adb devices', PHONE, true);
  adb('install', 'adb install app.apk', PHONE, true);
  adb('logcat', 'adb logcat -d', PHONE, true);
  adb('input tap', 'adb shell input tap 1 1', PHONE, true);
  adb('no devices attached', 'adb shell run-as com.x ls', 'List of devices attached\n', true);
  adb('adb in a chain after cd', 'cd C:/; adb shell run-as com.x ls', PHONE, false);
}
