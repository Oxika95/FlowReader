// Temporary capture hook: appends the raw stdin of every event to
// %USERPROFILE%\.cursor\hooks\flow-capture\<event>.jsonl and prints the neutral response.
// Usage in hooks.json: node ".../hook-capture.mjs" <event>   (install with install-hooks.mjs --capture)
// Node built-ins only and self-contained, so it can be installed without the selftest.
import { appendFileSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const PERMISSION = new Set(['preToolUse', 'beforeShellExecution', 'subagentStart', 'beforeMCPExecution', 'beforeReadFile']);
const neutral = (event) => (PERMISSION.has(event) ? { permission: 'allow' } : event === 'beforeSubmitPrompt' ? { continue: true } : {});
const eventArg = process.argv[2] || 'unknown';

function readAll(deadlineMs) {
  return new Promise((done) => {
    const chunks = [];
    const finish = () => {
      clearTimeout(timer);
      done(Buffer.concat(chunks));
    };
    const timer = setTimeout(finish, deadlineMs);
    process.stdin.on('data', (c) => chunks.push(c));
    process.stdin.on('end', finish);
    process.stdin.on('error', finish);
    process.stdin.resume();
  });
}

let response = neutral(eventArg);
try {
  const buf = await readAll(2000);
  const raw = buf.toString('utf8');
  let parsed = null;
  try {
    parsed = JSON.parse(raw.replace(/^\uFEFF/, ''));
  } catch {
    /* keep the raw text */
  }
  const event = eventArg !== 'unknown' ? eventArg : parsed?.hook_event_name || 'unknown';
  response = neutral(event);
  const nonAscii = [...raw].filter((c) => c.charCodeAt(0) > 127).slice(0, 40).map((c) => c.codePointAt(0).toString(16));
  const dir = join(process.env.FLOW_AGENT_USERHOME || homedir(), '.cursor', 'hooks', 'flow-capture');
  mkdirSync(dir, { recursive: true });
  const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => /^CURSOR/i.test(k)));
  const record = {
    captured_at: new Date().toISOString(),
    event,
    bytes: buf.length,
    starts_with_bom: raw.charCodeAt(0) === 0xfeff,
    replacement_chars: raw.includes('\ufffd'),
    non_ascii_code_points: nonAscii,
    env,
    input: parsed ?? raw,
  };
  appendFileSync(join(dir, `${event}.jsonl`), JSON.stringify(record) + '\n');
} catch {
  /* capture is best effort; the neutral response still goes out */
}
process.stdout.write(JSON.stringify(response) + '\n');
process.exit(0);
