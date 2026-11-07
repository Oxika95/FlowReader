// Cursor preToolUse hook. Edit lock for file tools (including CallDynamicTool) and the Shell guard, with --conversation injection.
import { run } from './hook-lib.mjs';

run('preToolUse', async (input) => (await import('./hook-tools.mjs')).preToolUse(input));
