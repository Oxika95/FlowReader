[flow-takeover]

Take over the edit lock held by another chat. The `beforeSubmitPrompt` hook has already taken the lock for this
chat when this command ran. Don't commit the previous holder's files unless the owner asks.

1. Run `node $HOME/.cursor/hooks/flow-agent/lock.mjs status` and report, per repo: who held the lock, when it was
   last active, and the previous holder's uncommitted files (the hook logs them when it takes over).
2. Check each repo with `git status --short` and say which changed files belong to the previous holder's edit log and
   which don't.
3. Ask the owner what to do with those files: leave them, include them with `--also` in this chat's commit, or hand
   them back. Don't edit them until told.
