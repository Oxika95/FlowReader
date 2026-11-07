// Selftest cases: commit-mine, stop gate, prompt and compaction hooks, grep double-check.
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { cli, commitAll, fwd, gitIn, hook, readJsonFile, stateFile, SRC } from './selftest-lib.mjs';
import { base, clearLock, edit, pre, prompt, setLock, shell, stopEvent, write } from './selftest-payloads.mjs';

/** Run verify-state's plan + stamp, as scripts/verify.ps1 does. */
function stamp(root, ok, failures = []) {
  const run = (...args) => {
    const r = spawnSync(process.execPath, [join(SRC, 'verify-state.mjs'), ...args], { encoding: 'utf8', windowsHide: true });
    if (r.status !== 0) throw new Error(`verify-state ${args.join(' ')}: ${r.stderr}`);
  };
  run('plan', root);
  const failFile = join(root, '.git', 'flow-agent', 'failures.txt');
  writeFileSync(failFile, failures.join('\n'));
  run('stamp', root, '--ok', ok ? '1' : '0', '--mode', 'fast', '--ran', 'selftest', '--failures-file', failFile);
}

const clearStamps = (root) => {
  for (const f of ['stamp.json', 'stamp-pass.json']) rmSync(stateFile(root, f), { force: true });
};

export async function commitCases(world, t) {
  const { app, chats } = world.roots;
  const C = 'conv-c';
  const B = 'conv-b';
  const mine = (root, ...args) => cli(world, 'commit-mine', args, { cwd: root });
  const hooksDir = fwd(join(world.home, '.cursor/hooks/flow-agent'));

  t.section('commit-mine');
  clearLock(app);
  clearStamps(app);
  commitAll(app, 'baseline');
  hook(world, 'hook-preToolUse', edit(world, C, 'StrReplace', join(app, 'src/a.txt')));
  hook(world, 'hook-preToolUse', edit(world, C, 'Write', join(app, 'src/new.txt')));
  hook(world, 'hook-preToolUse', pre(world, C, 'CallDynamicTool', { namespace: 'cursor', toolName: 'Delete', arguments: { path: fwd(join(app, 'docs/b.md')) } }));
  write(app, 'src/a.txt', 'edited by C\n');
  write(app, 'src/new.txt', 'new file\n');
  rmSync(join(app, 'docs/b.md'));
  write(app, 'README.md', 'edited by someone else\n');

  let r = mine(app, '-m', 'app: x', '--device', 'none: test');
  t.check('refuses without --conversation', r.status !== 0 && /missing --conversation/.test(r.out), r.out);
  r = mine(app, '-m', 'app: x', '--conversation', C);
  t.check('refuses without --device', r.status !== 0 && /missing --device/.test(r.out), r.out);
  r = mine(app, '--device', 'none: test', '--conversation', C);
  t.check('refuses without a message', r.status !== 0 && /missing -m/.test(r.out), r.out);
  r = mine(world.outside, '-m', 'app: x', '--device', 'none: t', '--conversation', C);
  t.check('refuses outside the three repos', r.status !== 0 && /not inside/.test(r.out), r.out);

  setLock(app, 'conv-holder');
  const injected = hook(world, 'hook-preToolUse', shell(world, B, `node ${hooksDir}/commit-mine.mjs -m "app: x" --device "none: t"`)).json?.updated_input?.command || '';
  t.check('hook appends --conversation for commit-mine', injected.endsWith('--conversation conv-b'), injected);
  r = mine(app, '-m', 'app: x', '--device', 'none: t', '--conversation', B);
  t.check('commit-mine from a non-holder is refused', r.status !== 0 && /another chat/.test(r.out), r.out);
  clearLock(app);

  r = mine(app, '-m', 'app: x', '--device', 'none: t', '--conversation', C);
  t.check('refuses without a passing verify stamp', r.status !== 0 && /no passing verify stamp/.test(r.out), r.out);
  stamp(app, true);
  write(app, 'src/a.txt', 'edited by C again\n');
  r = mine(app, '-m', 'app: x', '--device', 'none: t', '--conversation', C);
  t.check('refuses files changed since the stamp', r.status !== 0 && /changed since the last passing verify: src\/a\.txt/.test(r.out), r.out);
  stamp(app, true);
  r = mine(app, '-m', 'app: change a, add new, drop b', '--device', 'ran: selftest | left for owner: nothing', '--conversation', C);
  t.check('commits when the stamp matches', r.status === 0, r.out);
  const files = gitIn(app, 'show', '--name-status', '--format=', 'HEAD').trim().split(/\r?\n/).map((l) => l.replace(/\s+/g, ' ')).sort();
  t.eq('commit holds this chat\'s edit, new file and deletion', files, ['A src/new.txt', 'D docs/b.md', 'M src/a.txt']);
  t.eq('author comes from config.json', gitIn(app, 'log', '-1', '--format=%an <%ae>').trim(), 'Self Test <self@example.test>');
  const body = gitIn(app, 'log', '-1', '--format=%B');
  t.check('message has the Device-check trailer', /\n\nDevice-check: ran: selftest \| left for owner: nothing\s*$/.test(body), body);
  t.check('other dirty files are listed as not committed', /not committed: not edited by this chat: README\.md/.test(r.out), r.out);
  t.check('README.md is still modified', /README\.md/.test(gitIn(app, 'status', '--porcelain')));
  t.eq('committed paths leave the edit log', readFileSync(stateFile(app, 'edits', `${C}.txt`), 'utf8').trim(), '');

  hook(world, 'hook-preToolUse', edit(world, C, 'StrReplace', join(app, 'src/a.txt')));
  write(app, 'src/a.txt', 'docs-only style change\n');
  r = mine(app, '-m', 'app: skip verify', '--device', 'none: test', '--no-verify-reason', 'selftest skip', '--conversation', C);
  t.check('--no-verify-reason commits with a trailer', r.status === 0 && /Verify-skipped: selftest skip/.test(gitIn(app, 'log', '-1', '--format=%B')), r.out);

  hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(chats, 'note.md')));
  write(chats, 'note.md', 'a note\n');
  r = mine(chats, '-m', 'chats: note', '--device', 'none: notes only', '--conversation', B);
  t.check('chats repo commits without a verify stamp', r.status === 0 && /note\.md/.test(gitIn(chats, 'show', '--name-only', '--format=', 'HEAD')), r.out);
  write(chats, 'raw/t.jsonl', 'x\n');
  hook(world, 'hook-preToolUse', edit(world, B, 'Write', join(chats, 'note2.md')));
  write(chats, 'note2.md', 'two\n');
  r = mine(chats, '-m', 'chats: note2 and raw', '--device', 'none: notes only', '--also', 'raw/t.jsonl', '--conversation', B);
  t.check('--also adds generated outputs', r.status === 0 && /raw\/t\.jsonl/.test(gitIn(chats, 'show', '--name-only', '--format=', 'HEAD')), r.out);
  clearLock(app);
}

export async function turnCases(world, t) {
  const { app, plugins } = world.roots;
  const G = 'conv-g';
  const stop = (status) => hook(world, 'hook-stop', stopEvent(world, G, status)).json;
  const reminder = (r) => r?.followup_message || '';

  t.section('Stop gate');
  clearLock(app);
  clearLock(plugins);
  commitAll(app, 'clean');
  clearStamps(app);
  t.eq('prompt: continue', hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'start work')).json, { continue: true });
  t.check('turn-start maps recorded for app and plugins', !!readJsonFile(stateFile(app, 'turnstart', `${G}.json`))?.map && !!readJsonFile(stateFile(plugins, 'turnstart', `${G}.json`))?.map);
  t.eq('no changes: nothing', stop('completed'), {});
  write(app, 'src/g.txt', 'one\n');
  t.eq('aborted turn: nothing', stop('aborted'), {});
  let r = stop('completed');
  t.check('changed files, no stamp: one reminder', reminder(r).startsWith('[verify-gate] ') && /src\/g\.txt/.test(reminder(r)) && /scripts\/verify\.ps1/.test(reminder(r)), JSON.stringify(r));
  t.eq('same state again: nothing (one per hash)', stop('completed'), {});
  write(app, 'src/g.txt', 'two\n');
  r = stop('completed');
  t.check('new hash: second reminder', reminder(r).startsWith('[verify-gate]'), JSON.stringify(r));
  write(app, 'src/g.txt', 'three\n');
  t.eq('third reminder in one user message: nothing', stop('completed'), {});
  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, '[verify-gate] Files changed since the last passing verify: src/g.txt. Run scripts/verify.ps1'));
  write(app, 'src/g.txt', 'four\n');
  t.eq('[verify-gate] prompt does not reset the counter', stop('completed'), {});
  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'next message'));
  write(app, 'src/g.txt', 'five\n');
  t.check('a real user message resets it', reminder(stop('completed')).startsWith('[verify-gate]'));

  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'verify then stop'));
  write(app, 'src/g.txt', 'six\n');
  stamp(app, true);
  t.eq('changed paths match the passing stamp: nothing', stop('completed'), {});
  commitAll(app, 'commit with the same content');
  t.eq('commit leaves content unchanged: nothing', stop('completed'), {});

  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'empty commit turn'));
  commitAll(app, 'empty');
  t.eq('turn with only an empty commit: nothing', stop('completed'), {});

  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'shell write turn'));
  const w = hook(world, 'hook-preToolUse', shell(world, G, `Set-Content ${fwd(join(app, 'src/s.txt'))} hi`)).json;
  write(app, 'src/s.txt', 'hi\n');
  r = stop('completed');
  t.check('Shell write by the holder: reminder', w?.permission === 'allow' && /src\/s\.txt/.test(reminder(r)), JSON.stringify([w, r]));

  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'failing verify turn'));
  write(app, 'src/s.txt', 'changed\n');
  stamp(app, false, ['FAIL app: 2 unit tests failed', 'FAIL rules-check: file too long']);
  r = stop('completed');
  t.check('failed verify: reminder quotes the failures', /verify failed for the current files: FAIL app: 2 unit tests failed \| FAIL rules-check: file too long\./.test(reminder(r)), JSON.stringify(r));

  hook(world, 'hook-beforeSubmitPrompt', prompt(world, G, 'both repos'));
  write(app, 'src/g.txt', 'both\n');
  write(plugins, 'src/p.txt', 'plugin change\n');
  r = stop('completed');
  t.check('changes in both repos: one reminder naming both', /flow-reader: .*src\/g\.txt/.test(reminder(r)) && /flow-reader-plugins: .*src\/p\.txt/.test(reminder(r)), JSON.stringify(r));
  commitAll(app);
  commitAll(plugins);

  t.section('Other hooks');
  t.check('preCompact first compaction explains and points to /handoff', /being compacted.*\/handoff/.test(hook(world, 'hook-preCompact', base(world, G, 'preCompact', { trigger: 'auto', context_usage_percent: 90, message_count: 40, is_first_compaction: true })).json?.user_message || ''));
  t.eq('preCompact later compactions are short', hook(world, 'hook-preCompact', base(world, G, 'preCompact', { message_count: 80, is_first_compaction: false })).json, { user_message: 'Compacted again (80 messages).' });

  t.section('Grep double-check');
  const grep = (extra, env) => hook(world, 'hook-postToolUse-grep', base(world, G, 'postToolUse', { tool_name: 'Grep', tool_input: { pattern: 'zzneedle', path: fwd(app) }, cwd: fwd(app), ...extra }), { env }).json;
  t.eq('payload without a result: neutral', grep({}), {});
  t.eq('non-empty result: neutral', grep({ tool_output: 'src/a.txt:1:zzneedle' }), {});
  t.eq('empty result but rg missing: neutral', grep({ tool_output: '' }, { FLOW_AGENT_RG: join(world.outside, 'no-such-rg.exe') }), {});
  t.eq('empty result, path outside the repos: neutral', grep({ tool_output: '', tool_input: { pattern: 'x', path: fwd(world.outside) } }), {});
  if (spawnSync('rg', ['--version'], { windowsHide: true }).status === 0) {
    write(app, 'src/needle.txt', 'zzneedle here\n');
    r = grep({ tool_output: 'No matches found.' });
    t.check('empty Grep result, rg finds a match: additional_context', /rg found 1 matches.*needle\.txt/.test(r?.additional_context || ''), JSON.stringify(r));
  } else console.log('  skip  rg is not on PATH; the rg match case was not run');
}
