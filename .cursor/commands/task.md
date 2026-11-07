# Task

Arguments: the request, in the owner's words. Use those words for searching and in the plan; don't rename things.
Follow `AGENTS.md` (lookup order, search rules, hooks) throughout. One task per chat.

1. **Find existing code** with the lookup order in `AGENTS.md`: `rg -n -i "<words>" docs/agent/registry.md`, then
   `rg -n "<FileName>" docs/agent/seams.md` for each file in play, then `rg` for symbols, related literals and
   `FlowApp` properties in `app/src` (and `../flow-reader-plugins/` when the change is plugin-visible). Find
   callers with the **Finding callers** steps in `AGENTS.md`.
   Output an **Existing code found** block listing the registry entries, paths and symbols found, and every
   search that found nothing, so the owner can spot vocabulary gaps. If the words matched no registry entry, propose
   one in the plan.
2. **For bugs**, find the cause from logs or code. Where the logic is pure or can be made pure, write a failing JVM
   test first, using constructor seams, a fake clock or a log wrapper.
3. **Affected code.** For each file to change, list: its `seams.md` lines; the callers of each changed symbol
   (Finding callers in `AGENTS.md`); the tests that cover them; `git log -S '<value>' -- <file>` results for any
   hard-coded value being changed.
4. **Write the plan** with these sections: Goal (owner's words), Existing code found, Affected code, Rules that
   apply (from the `.cursor/rules/*.mdc` files whose globs match the files to change), Approach, Tests, Device
   checks, Out of scope.
   If the plan adds a class, screen or store, or touches an owner or coupling, first run a read-only critic subagent
   on it and fold its findings in.
5. **Save it** to `../flow-reader-chats/tasks/<YYYY-MM-DD>-<slug>.md` (see that folder's README).
6. **Wait for approval.** Don't edit code before it.
7. **Implement**, then run `scripts\verify.ps1`. Fix failures at the cause.
8. **Device checks** (only those the plan lists):
   1. read the rule files of the features the check exercises;
   2. warn the owner before using the emulator;
   3. run `.\gradlew.bat :app:installDebug`;
   4. run the steps with logcat filters, taking at most 3 screenshots;
   5. report what ran and what was seen.
9. **Finish** with `/handoff`.

If `../flow-reader-chats` is absent, keep the plan in the chat and use `docs/agent/CONTEXT.md` and `AGENTS.md`.
