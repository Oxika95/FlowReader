// Payload builders and lock-state helpers for the selftest cases. Shapes follow the Cursor hooks docs
// and past transcripts; the capture phase is what confirms them (see README.md).
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fwd, stateFile } from './selftest-lib.mjs';

export const transcript = (conv) => `C:/Users/test/.cursor/projects/p/agent-transcripts/${conv}/${conv}.jsonl`;
export const subTranscript = (parent, sub) => `C:/Users/test/.cursor/projects/p/agent-transcripts/${parent}/subagents/${sub}.jsonl`;

export const base = (world, conv, event, extra = {}) => ({
  conversation_id: conv,
  generation_id: 'gen-1',
  hook_event_name: event,
  workspace_roots: [fwd(world.roots.app)],
  transcript_path: transcript(conv),
  ...extra,
});

export const pre = (world, conv, tool_name, tool_input, extra = {}) =>
  base(world, conv, 'preToolUse', { tool_name, tool_input, cwd: fwd(world.roots.app), ...extra });

export const edit = (world, conv, tool, path) => pre(world, conv, tool, { path: fwd(path) });
export const shell = (world, conv, command, cwd) => pre(world, conv, 'Shell', { command }, { cwd: fwd(cwd || world.roots.app) });
export const prompt = (world, conv, text) => base(world, conv, 'beforeSubmitPrompt', { prompt: text });
export const stopEvent = (world, conv, status = 'completed') => base(world, conv, 'stop', { status, loop_count: 0 });

export function setLock(root, conv, minutesAgo = 0) {
  const at = new Date(Date.now() - minutesAgo * 60000).toISOString();
  mkdirSync(stateFile(root), { recursive: true });
  writeFileSync(stateFile(root, 'lock.json'), JSON.stringify({ conversation_id: conv, acquired_at: at, last_active_at: at }));
}

export function clearLock(root) {
  for (const f of ['lock.json', 'subagent.json', 'lock.mutex']) rmSync(stateFile(root, f), { force: true });
}

export const write = (root, rel, text) => {
  const full = join(root, rel);
  mkdirSync(join(full, '..'), { recursive: true });
  writeFileSync(full, text);
};
