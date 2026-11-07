// Cursor stop hook. Verify reminder: one follow-up per state hash, at most two per user message.
import { run } from './hook-lib.mjs';

run('stop', async (input) => (await import('./hook-turn.mjs')).stop(input), { deadlineMs: 9000 });
