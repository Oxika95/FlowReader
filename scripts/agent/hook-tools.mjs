// Logic behind the preToolUse and beforeShellExecution hooks: edit lock for file tools, Shell guard.
import { resolve } from 'node:path';
import { ALLOW, identityOf, relIn, repoOf } from './hook-lib.mjs';
import { denyBoth } from './guards.mjs';
import { acquire, appendEdits, denyMessages, touchAll } from './hook-state.mjs';
import { evaluateShell, isProtectedPath } from './shell-guard.mjs';

const EDIT_TOOLS = new Set([
  'write', 'strreplace', 'str_replace', 'delete', 'editnotebook', 'edit', 'multiedit', 'applypatch', 'apply_patch',
  'notebookedit', 'searchreplace', 'edit_file', 'delete_file',
]);
const SHELL_TOOLS = new Set(['shell', 'bash', 'powershell', 'run_terminal_cmd']);
const PATH_KEYS = ['path', 'file_path', 'target_file', 'target_notebook', 'notebook_path', 'filePath', 'relative_workspace_path'];
const PATCH_FILE = /^\*\*\* (?:Add|Update|Delete) File: (.+?)\s*$|^\*\*\* Move to: (.+?)\s*$/gm;

const asObject = (v) => {
  if (v && typeof v === 'object') return v;
  if (typeof v === 'string') {
    try {
      const o = JSON.parse(v);
      if (o && typeof o === 'object') return o;
    } catch {
      /* not JSON */
    }
  }
  return {};
};

/** Absolute paths an edit tool call touches: tool_input.path, arguments.path (CallDynamicTool), target_notebook, patch headers. */
export function editPaths(toolInput, base) {
  const found = new Set();
  const take = (o) => {
    for (const k of PATH_KEYS) if (typeof o[k] === 'string' && o[k]) found.add(o[k]);
    for (const k of ['patch', 'input', 'diff']) {
      if (typeof o[k] !== 'string') continue;
      for (const m of o[k].matchAll(PATCH_FILE)) found.add(m[1] || m[2]);
    }
  };
  take(toolInput);
  take(asObject(toolInput.arguments));
  return [...found].map((p) => resolve(base, p));
}

function checkEdits(paths, conv, subagent) {
  for (const p of paths) {
    if (isProtectedPath(p)) return denyBoth('Agents cannot edit the hook installation or its state (hooks.json, the flow-agent folder). Ask the user.');
    const repo = repoOf(p);
    if (!repo) continue;
    const rel = relIn(repo.root, p);
    if (repo.key === 'chats') {
      appendEdits(repo.root, conv, [rel]);
      continue;
    }
    const res = acquire(repo.root, conv, { subagent, editPath: rel });
    if (!res.ok) return denyMessages(repo.root, res);
  }
  return null;
}

const deny = (messages) => ({ permission: 'deny', ...messages });

export function preToolUse(input) {
  const { conv, subagent } = identityOf(input);
  if (!conv) return ALLOW;
  const name = String(input.tool_name || '').toLowerCase();
  const base = input.cwd || (input.workspace_roots || [])[0] || process.cwd();
  const ti = asObject(input.tool_input);
  touchAll(conv);

  if (EDIT_TOOLS.has(name)) {
    const v = checkEdits(editPaths(ti, base), conv, subagent);
    return v ? deny(v) : ALLOW;
  }
  if (name === 'calldynamictool' || name.endsWith(':calldynamictool')) {
    const inner = String(ti.toolName ?? ti.tool_name ?? ti.name ?? '').toLowerCase();
    if (!EDIT_TOOLS.has(inner)) return ALLOW;
    const v = checkEdits(editPaths(ti, base), conv, subagent);
    return v ? deny(v) : ALLOW;
  }
  if (SHELL_TOOLS.has(name)) {
    const cwd = typeof ti.working_directory === 'string' && ti.working_directory ? resolve(base, ti.working_directory) : base;
    const r = evaluateShell({ command: ti.command ?? ti.cmd, cwd, conv, subagent });
    if (r.deny) return deny(r.deny);
    if (r.command !== (ti.command ?? ti.cmd)) return { permission: 'allow', updated_input: { ...ti, command: r.command } };
  }
  return ALLOW;
}

/** Fallback when preToolUse doesn't fire for Shell: guards only, no --conversation injection. */
export function beforeShellExecution(input) {
  const { conv, subagent } = identityOf(input);
  if (!conv) return ALLOW;
  touchAll(conv);
  const r = evaluateShell({ command: input.command, cwd: input.cwd || (input.workspace_roots || [])[0] || process.cwd(), conv, subagent });
  return r.deny ? deny(r.deny) : ALLOW;
}
