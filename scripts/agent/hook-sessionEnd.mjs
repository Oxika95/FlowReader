// Cursor sessionEnd hook. Releases the edit locks on user_close / window_close and deletes state older than 14 days.
import { run } from './hook-lib.mjs';

run('sessionEnd', async (input) => (await import('./hook-turn.mjs')).sessionEnd(input));
