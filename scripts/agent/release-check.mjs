// Lists the Device-check: and Verify-skipped: commit trailers since the last tag in the app and plugins repos.
// Usage: node scripts/agent/release-check.mjs [--strict]
// Prints every item left for the owner. Exit 0 always, except with --strict when an item is unconfirmed (exit 1).
// A Device-check value that starts with pass, done, ok, or confirmed counts as confirmed; any other
// Device-check value and every Verify-skipped value is left for the owner.
import { existsSync } from 'node:fs';
import { git } from './lib.mjs';
import { APP_ROOT, PLUGINS_ROOT } from './paths.mjs';
import { isMain } from './lib.mjs';

const CONFIRMED = /^\s*(pass|passed|done|ok|confirmed)\b/i;
const RS = '\x1e';
const US = '\x1f';
const END = '\x1d';

/** Trailers and "left for owner" lines for each commit since [tag] in [repo]. */
function collect(repo, tag) {
  const range = tag ? `${tag}..HEAD` : 'HEAD';
  const trailer = (key) => `%(trailers:key=${key},valueonly,unfold,separator=%x1e)`;
  const format = ['%h', '%s', trailer('Device-check'), trailer('Verify-skipped'), '%B'].join(`%x1f`) + '%x1d';
  const out = git(repo, ['log', range, `--format=${format}`], { allowFail: true });
  if (out === null) return [];
  const items = [];
  for (const record of out.split(END)) {
    const fields = record.replace(/^\s+/, '').split(US);
    if (fields.length < 5) continue;
    const [hash, subject, device, skipped, body] = fields;
    const values = (text) => text.split(RS).map((v) => v.trim()).filter(Boolean);
    for (const v of values(device)) {
      items.push({ hash, subject, kind: 'Device-check', value: v, owner: !CONFIRMED.test(v) });
    }
    for (const v of values(skipped)) {
      items.push({ hash, subject, kind: 'Verify-skipped', value: v, owner: true });
    }
    for (const line of body.split(/\r?\n/)) {
      if (/left for owner/i.test(line) && !/^(Device-check|Verify-skipped):/i.test(line)) {
        items.push({ hash, subject, kind: 'Note', value: line.trim(), owner: true });
      }
    }
  }
  return items;
}

function report(name, repo) {
  if (!existsSync(repo)) {
    console.log(`[${name}] repo not found at ${repo}`);
    return [];
  }
  const tag = git(repo, ['describe', '--tags', '--abbrev=0'], { allowFail: true })?.trim() || '';
  const count = git(repo, ['rev-list', '--count', tag ? `${tag}..HEAD` : 'HEAD'], { allowFail: true })?.trim() ?? '?';
  console.log(`[${name}] ${count} commit(s) since ${tag || 'the beginning (no tag)'}`);
  const items = collect(repo, tag);
  if (items.length === 0) console.log('  no Device-check or Verify-skipped trailers');
  for (const i of items) {
    const mark = i.owner ? 'left for owner' : 'confirmed';
    console.log(`  ${i.hash} ${i.kind}: ${i.value}  [${mark}]`);
    console.log(`    ${i.subject}`);
  }
  return items.map((i) => ({ ...i, repo: name }));
}

if (isMain(import.meta.url)) {
  const strict = process.argv.includes('--strict');
  const all = [...report('app', APP_ROOT), ...report('plugins', PLUGINS_ROOT)];
  const owner = all.filter((i) => i.owner);
  console.log('');
  if (owner.length === 0) {
    console.log('Left for owner: none');
  } else {
    console.log(`Left for owner (${owner.length}):`);
    for (const i of owner) console.log(`  [${i.repo}] ${i.hash} ${i.kind}: ${i.value}`);
  }
  if (strict && owner.length > 0) process.exit(1);
}
