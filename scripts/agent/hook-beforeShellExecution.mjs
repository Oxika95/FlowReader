// Cursor beforeShellExecution hook. Fallback Shell guard for when preToolUse does not fire for Shell (no --conversation injection).
import { run } from './hook-lib.mjs';

run('beforeShellExecution', async (input) => (await import('./hook-tools.mjs')).beforeShellExecution(input));
