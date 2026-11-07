// Self-test for the Cursor hooks: feeds realistic payloads to each hook in throwaway repos and checks the responses.
// Usage: node scripts/agent/selftest.mjs   (install-hooks.mjs runs it first and installs only if it passes)
// Cursor starts hooks through powershell.exe, so also run it as: powershell.exe -Command "node scripts/agent/selftest.mjs"
// (without -NoProfile, so a PowerShell profile that prints text is caught).
import { lockCases } from './selftest-lock.mjs';
import { makeTester, makeWorld } from './selftest-lib.mjs';
import { installCases, robustCases } from './selftest-robust.mjs';
import { shellCases } from './selftest-shell.mjs';
import { commitCases, turnCases } from './selftest-turn.mjs';

const t = makeTester();
const world = makeWorld();
const started = Date.now();
try {
  await lockCases(world, t);
  await shellCases(world, t);
  await commitCases(world, t);
  await turnCases(world, t);
  await robustCases(world, t);
  await installCases(t);
} catch (e) {
  t.check(`selftest crashed: ${e.stack || e}`, false);
} finally {
  world.cleanup();
}
const ok = t.summary();
console.log(`(${((Date.now() - started) / 1000).toFixed(1)} s)`);
process.exit(ok ? 0 : 1);
