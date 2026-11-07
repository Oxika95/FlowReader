// Cursor preCompact hook. Tells the user the chat is being compacted.
import { run } from './hook-lib.mjs';

run('preCompact', async (input) => (await import('./hook-turn.mjs')).preCompact(input));
