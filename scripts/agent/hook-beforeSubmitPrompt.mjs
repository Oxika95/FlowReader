// Cursor beforeSubmitPrompt hook. Always continues. Handles /takeover, records the turn-start content map, resets the verify-gate counter.
import { run } from './hook-lib.mjs';

run('beforeSubmitPrompt', async (input) => (await import('./hook-turn.mjs')).beforeSubmitPrompt(input));
