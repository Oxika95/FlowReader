# Review changes

Report findings only. Change no code, even for a one-line fix, unless the owner approves a finding afterwards.
Review from git and the working tree; don't read other chats.

1. Collect the diffs `origin/master..HEAD` in `flow-reader` and `origin/main..HEAD` in `flow-reader-plugins`, plus
   `git status` in both. If the owner names a range, use that.
2. Run `powershell -NoProfile -File scripts\verify.ps1 -Full`, the duplicate check report
   (`node scripts/agent/dup-check.mjs`) and `node scripts/agent/notes-check.mjs`. Quote failures.
3. Scan changed and untracked files for secrets (keys, tokens, cookies, personal paths), and flag junk files: stray
   root files, screenshots, temp outputs.
4. Look for real bugs first (state, lifecycle, coroutine cancellation, migrations, API compatibility), then rule
   violations against the matching `.cursor/rules/*.mdc` and `AGENTS.md`. Skip style nits the code already tolerates.
5. Report by severity, each with `file:line`, what is wrong and why it matters. List other problems noticed but
   out of scope separately. Fix only what the owner approves.
