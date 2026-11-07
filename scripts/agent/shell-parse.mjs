// Best-effort parser for the Shell tool's PowerShell commands: segments, tokens, redirects and the
// working directory each segment runs in. A guard rail for the hooks, not a security boundary.
import { homedir } from 'node:os';
import { basename, isAbsolute, resolve } from 'node:path';

const CD_WORDS = new Set(['cd', 'chdir', 'set-location', 'sl', 'push-location', 'pushd']);
const POP_WORDS = new Set(['pop-location', 'popd']);
const SHELLS = new Set(['powershell', 'pwsh', 'cmd', 'bash', 'sh']);
const EVAL_WORDS = new Set(['invoke-expression', 'iex']);

/** Split [command] on ; && || | newlines and unquoted ( ) { }. Returns [{text, start, end}] offsets into [command]. */
export function scanSegments(command) {
  const out = [];
  const n = command.length;
  let segStart = 0;
  let quote = null;
  const push = (end) => {
    let s = segStart;
    let e = end;
    while (s < e && /\s/.test(command[s])) s++;
    while (e > s && /\s/.test(command[e - 1])) e--;
    if (e > s) out.push({ text: command.slice(s, e), start: s, end: e });
  };
  for (let i = 0; i < n; i++) {
    const c = command[i];
    if (quote) {
      if (c === '`' && quote === '"') i++;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === '`') {
      i++;
      continue;
    }
    if (c === '@' && (command[i + 1] === "'" || command[i + 1] === '"') && /^[ \t]*\r?\n/.test(command.slice(i + 2))) {
      const close = command.indexOf(`\n${command[i + 1]}@`, i + 2);
      if (close < 0) break;
      i = close + 2;
      continue;
    }
    if (c === "'" || c === '"') {
      quote = c;
      continue;
    }
    let sepLen = 0;
    if (c === ';' || c === '\n' || c === '\r' || c === '(' || c === ')' || c === '{' || c === '}') sepLen = 1;
    else if (c === '|') sepLen = command[i + 1] === '|' ? 2 : 1;
    else if (c === '&') {
      if (command[i + 1] === '&') sepLen = 2;
      else if (command[i - 1] === '>' || command[i + 1] === '>') sepLen = 0;
      else if (command.slice(segStart, i).trim() === '') sepLen = 0;
      else sepLen = 1;
    }
    if (sepLen) {
      push(i);
      i += sepLen - 1;
      segStart = i + 1;
    }
  }
  push(n);
  return out;
}

const isTargetIgnored = (t) => /^&/.test(t) || ['$null', 'nul', '/dev/null'].includes(t.toLowerCase());

/** Tokens of one segment: [{v}] with quotes removed, plus the redirect targets (`> $null` and `2>&1` excluded). */
export function tokenize(text) {
  const raw = [];
  let cur = null;
  let quote = null;
  const start = () => (cur ??= { v: '', q: '' });
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quote) {
      if (c === '`' && quote === '"' && i + 1 < text.length) {
        start();
        cur.v += text[++i];
        cur.q += '1';
      } else if (c === quote) quote = null;
      else {
        cur.v += c;
        cur.q += '1';
      }
      continue;
    }
    if (c === "'" || c === '"') {
      start();
      quote = c;
    } else if (c === '`' && i + 1 < text.length) {
      start();
      cur.v += text[++i];
      cur.q += '1';
    } else if (/\s/.test(c)) {
      if (cur) raw.push(cur);
      cur = null;
    } else {
      start();
      cur.v += c;
      cur.q += '0';
    }
  }
  if (cur) raw.push(cur);

  const tokens = [];
  const redirects = [];
  for (let i = 0; i < raw.length; i++) {
    const { v, q } = raw[i];
    let k = -1;
    for (let j = 0; j < v.length; j++) {
      if (v[j] === '>' && q[j] === '0') {
        k = j;
        break;
      }
    }
    if (k < 0) {
      tokens.push({ v });
      continue;
    }
    const left = v.slice(0, k);
    if (left && !/^(\d+|\*|&)$/.test(left)) tokens.push({ v: left });
    let rest = v.slice(k + 1);
    if (rest.startsWith('>')) rest = rest.slice(1);
    const target = rest || (raw[i + 1] ? raw[++i].v : '');
    if (target && !isTargetIgnored(target)) redirects.push(target);
  }
  return { tokens, redirects };
}

export function commandWord(tokens) {
  let i = 0;
  while (i < tokens.length && (tokens[i].v === '&' || tokens[i].v === '.' || tokens[i].v.toLowerCase() === 'call')) i++;
  const t = tokens[i];
  if (!t) return { cmd: '', index: -1 };
  const cmd = basename(t.v.replace(/\\/g, '/')).toLowerCase().replace(/\.(exe|cmd|bat|ps1)$/, '');
  return { cmd, index: i };
}

const unresolvable = (s) => /[$%]/.test(s);

/** Resolve [target] against [cwd]. Returns null when it holds a variable. */
export function resolveTarget(cwd, target, home = homedir()) {
  if (!target || unresolvable(target)) return null;
  let t = target;
  if (t === '~' || t.startsWith('~/') || t.startsWith('~\\')) t = home + t.slice(1);
  if (/^\/[a-zA-Z]\//.test(t)) t = `${t[1]}:${t.slice(2)}`;
  return isAbsolute(t) ? resolve(t) : cwd ? resolve(cwd, t) : null;
}

function cdTarget(tokens, from) {
  for (let i = from; i < tokens.length; i++) {
    const v = tokens[i].v;
    if (/^-(path|literalpath)$/i.test(v)) return tokens[i + 1]?.v ?? '';
    if (v.startsWith('-') || /^\/d$/i.test(v)) continue;
    return v;
  }
  return '';
}

function innerCommand(cmd, tokens, index) {
  if (EVAL_WORDS.has(cmd)) return tokens.slice(index + 1).map((t) => t.v).join(' ');
  if (!SHELLS.has(cmd)) return null;
  for (let i = index + 1; i < tokens.length; i++) {
    if (/^(-c|-command|\/c|\/k)$/i.test(tokens[i].v)) return tokens.slice(i + 1).map((t) => t.v).join(' ');
  }
  return null;
}

/**
 * Parse [command] into segments, in order. Each has {text, start, end, tokens, redirects, cmd, index, cwd, nested}.
 * `cwd` is the directory the segment runs in after earlier cd / Set-Location / pushd segments. Commands run
 * through `powershell -Command "..."`, `bash -c` and `iex` are parsed too and marked `nested` (no offsets).
 */
export function parseShellSegments(command, cwd, { home = homedir() } = {}) {
  const out = [];
  const walk = (cmdText, startCwd, nested, depth) => {
    let dir = startCwd;
    const stack = [];
    for (const seg of scanSegments(cmdText)) {
      const { tokens, redirects } = tokenize(seg.text);
      const { cmd, index } = commandWord(tokens);
      out.push({ ...seg, tokens, redirects, cmd, index, cwd: dir, nested });
      if (CD_WORDS.has(cmd)) {
        const raw = cdTarget(tokens, index + 1);
        const next = raw === '' ? home : resolveTarget(dir, raw, home);
        if (next) {
          if (cmd === 'push-location' || cmd === 'pushd') stack.push(dir);
          dir = next;
        }
      } else if (POP_WORDS.has(cmd) && stack.length) dir = stack.pop();
      const inner = depth < 3 ? innerCommand(cmd, tokens, index) : null;
      if (inner) walk(inner, dir, true, depth + 1);
    }
  };
  walk(command, cwd, false, 0);
  return out;
}
