// Cursor subagentStop hook. Releases the editing-subagent claim.
import { run } from './hook-lib.mjs';

run('subagentStop', async (input) => (await import('./hook-turn.mjs')).subagentStop(input));
