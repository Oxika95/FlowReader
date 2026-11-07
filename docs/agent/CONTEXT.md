# Agent hub

Start here for where code lives and which rules apply. Follow the lookup order in `../../AGENTS.md`
(registry, then couplings, then `rg`). Load only the file and section that answers the question.

## Knowledge files

| Question | File | Scope |
| --- | --- | --- |
| Which feature, owner file, tests and pitfalls match the owner's words | [registry.md](registry.md) | `rg -n -i` the words, read matching `###` entries |
| Which other places must change with this file | [seams.md](seams.md) | `rg -n "<FileName>"`, read matching lines |
| App singletons, screen flow, repo roles | [architecture.md](architecture.md) | Full |
| Plugin-visible change (API, host, contract) | [cross-repo.md](cross-repo.md) | Full |
| Which files are too big to read whole | `../../scripts/size-baseline.json` | Full; find members with `rg -n`, read by offset |
| Dependency licenses | [dependency-licenses.md](dependency-licenses.md) | Full |

## Rule files (`.cursor/rules/`)

`working-agreement.mdc` is always on. The others load when a file matching their glob is touched.

| Rule file | Covers |
| --- | --- |
| `working-agreement.mdc` | Planning, fixing, scope, devices, git, definition of done |
| `ui-system.mdc` | UI files: design system, tokens, forbidden components |
| `plugin-api.mdc` | Plugin runtime and contract files |
| `tts.mdc` | Text to speech |
| `reader.mdc` | Reader screen and rendering |
| `data.mdc` | Room, DataStore, catalog |
| `share.mdc` | Share and import entry points |

## Reference docs

| Need | File |
| --- | --- |
| Build and test commands, checks, hooks, hard rules | `../../AGENTS.md` |
| Hook and check scripts | `../../scripts/agent/README.md` |
| Feature behavior | [../README.md](../README.md), then one feature doc |
| UI components and tokens | [../ui-system/README.md](../ui-system/README.md), then one page |
| Plugin contract | [../plugins/api.md](../plugins/api.md), then one page |
| Task workflow, backlog, saved plans | `../../../flow-reader-chats/CONTEXT.md` (private sibling repo; absent on some machines) |
