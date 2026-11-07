# Git hooks (Flow Reader)

## Enable

Once per clone, with the owner's OK:

```sh
git config core.hooksPath scripts/git-hooks
```

Do not set this from an agent chat. Hooks under this path run instead of `.git/hooks`.

## `pre-push`

1. If the working tree is clean and `.git/flow-agent/stamp-pass.json` (or `stamp.json`) is a
   passing `-Full` stamp whose hash matches the current content map, skip verify.
2. Otherwise run `powershell -NoProfile -File scripts/verify.ps1 -Full` (includes smoke when
   app code changed).
3. Secret-scan the commits being pushed (`git diff <remote>..<local>` per ref). Looks for
   session/clearance cookie assignments, password literals, `Authorization` / `Bearer`,
   private keys, and long `token` / `secret` values. Hits are printed with values masked.
   Known placeholders such as `KEY_PASSWORD = "password"` are allowlisted.
4. Fail the push on any secret hit or verify failure.

## Disable

```sh
git config --unset core.hooksPath
```
