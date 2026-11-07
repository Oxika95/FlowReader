// Cursor postToolUse hook for Grep. When Cursor's Grep came back empty, re-run the search with rg
// (which doesn't skip large files) and tell the agent if it finds matches. Does nothing when the
// payload doesn't carry the tool result (capture item 6) or rg isn't available.
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { identityOf, repoOf, run } from './hook-lib.mjs';

const RESULT_KEYS = ['tool_output', 'tool_result', 'result', 'output', 'tool_response'];
const EMPTY = /^\s*(no (matches|results|files|lines)( found)?\.?|0 (matches|results|files)|\[\]|\{\}|null)?\s*$/i;

function resultText(input) {
  for (const k of RESULT_KEYS) {
    if (!(k in input)) continue;
    const v = input[k];
    if (v == null) return '';
    return typeof v === 'string' ? v : JSON.stringify(v);
  }
  return undefined;
}

function rgPath() {
  if (process.env.FLOW_AGENT_RG) return process.env.FLOW_AGENT_RG;
  return 'rg';
}

run('postToolUse', async (input) => {
  if (!identityOf(input).conv) return {};
  const text = resultText(input);
  if (text === undefined || !EMPTY.test(text)) return {};
  const ti = input.tool_input && typeof input.tool_input === 'object' ? input.tool_input : {};
  if (!ti.pattern) return {};
  const base = input.cwd || (input.workspace_roots || [])[0] || process.cwd();
  const target = resolve(base, ti.path || '.');
  if (!repoOf(target)) return {};

  const args = ['--hidden', '--crlf', '-n', '-m', '20', '-g', '!.git/'];
  if (ti['-i']) args.push('-i');
  if (ti.glob) args.push('-g', ti.glob);
  if (ti.multiline) args.push('-U', '--multiline-dotall');
  args.push('-e', ti.pattern, '--', target);
  const r = spawnSync(rgPath(), args, { encoding: 'utf8', timeout: 8000, windowsHide: true, maxBuffer: 8 * 1024 * 1024 });
  if (r.error || r.status !== 0) return {};
  const lines = r.stdout.split(/\r?\n/).filter(Boolean);
  if (!lines.length) return {};
  return {
    additional_context: `Cursor's Grep found nothing, but rg found ${lines.length} matches, for example ${lines[0].slice(0, 200)}. Grep skips large files; use rg through Shell for existence checks.`,
  };
});
