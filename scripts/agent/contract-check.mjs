// Plugin contract checks between the app and the plugins repo. Prints failures (and warnings with --warn).
// Fails on: API version mismatch; a `flow.*` or Node member that exists in the app runtime but not in
// plugins test/host.mjs (or the other way round); a `__*` binding the prelude calls but the app never binds.
// Warns on: names missing from docs/plugins/api-host.md, bound functions the prelude never calls.
// Usage: node scripts/agent/contract-check.mjs [--warn]
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { isMain, readText } from './lib.mjs';
import { APP_ROOT, KOTLIN_ROOT, PLUGINS_ROOT } from './paths.mjs';

const MANIFEST_KT = join(KOTLIN_ROOT, 'plugin/api/PluginManifest.kt');
const RUNTIME_KT = join(KOTLIN_ROOT, 'plugin/runtime/JsPluginRuntime.kt');
const BUILD_INDEX = join(PLUGINS_ROOT, 'scripts/build-index.mjs');
const HOST_MJS = join(PLUGINS_ROOT, 'test/host.mjs');
const API_HOST_MD = join(APP_ROOT, 'docs/plugins/api-host.md');

const isQuote = (c) => c === '"' || c === "'" || c === '`';

/** Index just past the closing quote of the string that starts at [i]. */
function skipString(text, i) {
  const q = text[i];
  for (i++; i < text.length && text[i] !== q; i++) if (text[i] === '\\') i++;
  return i + 1;
}

/** Index of the `}` that closes the `{` at [open]. */
function matchBrace(text, open) {
  let depth = 0;
  for (let i = open; i < text.length; ) {
    const c = text[i];
    if (isQuote(c)) i = skipString(text, i);
    else if (c === '/' && text[i + 1] === '/') i = text.indexOf('\n', i) < 0 ? text.length : text.indexOf('\n', i);
    else if (c === '/' && text[i + 1] === '*') i = text.indexOf('*/', i + 2) + 2;
    else {
      if (c === '{') depth++;
      else if (c === '}' && --depth === 0) return i;
      i++;
    }
  }
  return -1;
}

/** Advance from [i] to the `,` that ends a member (consumed) or to the `}` that closes the object (not consumed). */
function skipValue(text, i) {
  let depth = 0;
  while (i < text.length) {
    const c = text[i];
    if (isQuote(c)) {
      i = skipString(text, i);
      continue;
    }
    if ('{(['.includes(c)) depth++;
    else if ('})]'.includes(c)) {
      if (depth === 0) return i;
      depth--;
    } else if (c === ',' && depth === 0) return i + 1;
    i++;
  }
  return i;
}

/**
 * Leaf member paths of the object literal whose `{` is at [open]: `{a: {b: 1}, c() {}, d}` -> a.b, c, d.
 * A value that isn't an object literal is a leaf.
 */
export function objectPaths(text, open, prefix = '') {
  const paths = [];
  let i = open + 1;
  while (i < text.length) {
    const ws = /^(?:\s|\/\/[^\n]*|,)+/.exec(text.slice(i));
    if (ws) i += ws[0].length;
    if (text[i] === '}') break;
    const m = /^(?:(?:async|get|set)\s+(?=[A-Za-z_$][\w$]*\s*\())?([A-Za-z_$][\w$]*)\s*/.exec(text.slice(i));
    if (!m) {
      i++;
      continue;
    }
    const path = prefix ? `${prefix}.${m[1]}` : m[1];
    i += m[0].length;
    paths.push(path);
    if (text[i] === ':') {
      i++;
      while (/\s/.test(text[i])) i++;
      if (text[i] === '{') {
        paths.pop();
        paths.push(...objectPaths(text, i, path));
        i = matchBrace(text, i) + 1;
        continue;
      }
    }
    if (text[i] === ',') i++;
    else if (text[i] !== '}') i = skipValue(text, i);
  }
  return paths;
}

function kotlinVersion(name) {
  const m = new RegExp(`const val ${name}\\s*=\\s*(\\d+)`).exec(readText(MANIFEST_KT));
  return m ? Number(m[1]) : null;
}

function indexVersion(name) {
  const m = new RegExp(`const ${name}\\s*=\\s*(\\d+)`).exec(readText(BUILD_INDEX));
  return m ? Number(m[1]) : null;
}

export function checkContract() {
  const failures = [];
  const warnings = [];

  const pairs = [
    ['PLUGIN_HOST_API_VERSION', 'HOST_API_VERSION'],
    ['PLUGIN_MIN_API_VERSION', 'MIN_API_VERSION'],
  ];
  for (const [kt, js] of pairs) {
    const a = kotlinVersion(kt);
    const b = indexVersion(js);
    if (a === null) failures.push(`contract: ${kt} not found in PluginManifest.kt`);
    else if (b === null) failures.push(`contract: ${js} not found in flow-reader-plugins/scripts/build-index.mjs`);
    else if (a !== b) failures.push(`contract: ${kt} is ${a} in the app but ${js} is ${b} in the plugins repo`);
  }

  if (!existsSync(HOST_MJS)) {
    warnings.push('contract: plugins repo not found; host parity skipped');
    return { failures, warnings };
  }

  const runtime = readText(RUNTIME_KT);
  const preludeStart = runtime.indexOf('PRELUDE = """');
  const prelude = runtime.slice(preludeStart, runtime.indexOf('""".trimIndent()', preludeStart));
  const bound = new Set([...runtime.matchAll(/engine\.(?:async)?[Ff]unction\("(__\w+)"/g)].map((m) => m[1]));
  const called = new Set([...prelude.matchAll(/\b(__\w+)\s*\(/g)].map((m) => m[1]));
  const definedInPrelude = new Set([...prelude.matchAll(/\bg\.(__\w+)\s*=/g)].map((m) => m[1]));
  for (const n of called) {
    if (!bound.has(n) && !definedInPrelude.has(n)) failures.push(`contract: prelude calls ${n} but JsPluginRuntime.kt never binds it`);
  }
  for (const n of bound) {
    if (!called.has(n)) warnings.push(`contract: ${n} is bound in JsPluginRuntime.kt but the prelude never calls it`);
  }

  const runtimeFlow = new Set(objectPaths(prelude, prelude.indexOf('{', prelude.indexOf('g.flow = {'))));
  const runtimeNode = new Set([...prelude.matchAll(/Node\.prototype\.(\w+)\s*=/g)].map((m) => m[1]));

  const host = readText(HOST_MJS);
  const hostFlow = new Set(objectPaths(host, host.indexOf('{', host.indexOf('const flow = {'))));
  const nodeStart = host.indexOf('class Node {');
  const nodeBody = host.slice(nodeStart, matchBrace(host, host.indexOf('{', nodeStart)));
  const hostNode = new Set(
    [...nodeBody.matchAll(/^\s{4}(?:async\s+)?(\w+)\s*\([^)]*\)\s*\{/gm)].map((m) => m[1]).filter((n) => n !== 'constructor'),
  );

  // `html: makeHtml()` is a leaf in host.mjs but `html.parse` in the runtime: a leaf on either side covers
  // the paths under it on the other side.
  const covered = (set, path) => [...set].some((p) => p === path || path.startsWith(`${p}.`) || p.startsWith(`${path}.`));
  for (const p of runtimeFlow) if (!covered(hostFlow, p)) failures.push(`contract: flow.${p} exists in the app runtime but not in test/host.mjs`);
  for (const p of hostFlow) if (!covered(runtimeFlow, p)) failures.push(`contract: flow.${p} exists in test/host.mjs but not in the app runtime`);
  for (const n of runtimeNode) if (!hostNode.has(n)) failures.push(`contract: Node.${n} exists in the app runtime but not in test/host.mjs`);
  for (const n of hostNode) if (!runtimeNode.has(n)) failures.push(`contract: Node.${n} exists in test/host.mjs but not in the app runtime`);

  if (existsSync(API_HOST_MD)) {
    const doc = readText(API_HOST_MD);
    const lines = doc.split('\n');
    for (const p of runtimeFlow) {
      const parts = p.split('.');
      const ok = parts.length === 1
        ? doc.includes(`flow.${p}`)
        : lines.some((l) => l.includes(`flow.${parts[0]}.`) && new RegExp(`\\b${parts[1]}\\b`).test(l));
      if (!ok) warnings.push(`contract: flow.${p} is not mentioned in docs/plugins/api-host.md`);
    }
    for (const n of runtimeNode) if (!new RegExp(`\\b${n}\\b`).test(doc)) warnings.push(`contract: Node.${n} is not mentioned in docs/plugins/api-host.md`);
    const topLevel = new Set([...runtimeFlow].map((p) => p.split('.')[0]));
    for (const m of doc.matchAll(/\bflow\.(\w+)/g)) {
      if (!topLevel.has(m[1])) warnings.push(`contract: docs/plugins/api-host.md mentions flow.${m[1]}, which the app runtime doesn't define`);
    }
  }
  return { failures, warnings: [...new Set(warnings)] };
}

if (isMain(import.meta.url)) {
  const { failures, warnings } = checkContract();
  for (const f of failures) console.log(f);
  if (process.argv.includes('--warn')) for (const w of warnings) console.log(w);
  process.exit(failures.length ? 1 : 0);
}
