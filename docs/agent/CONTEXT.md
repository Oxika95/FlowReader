# Agent reference: routing

Load the one file and section that answers the question. The task workflow (plan, implement,
verify, handoff) lives in `../flow-reader-chats/CONTEXT.md`; when that repo is absent, use this
page directly.

| Question | File | Section/Scope |
| --- | --- | --- |
| Which files to edit for a change in area X | [map.md](map.md) | `## <area>` |
| Which files are too big to read whole | [map.md](map.md) | `## Large files` |
| Line number of a declaration | [index.md](index.md) | Router, then one package file |
| App singletons, screen flow, repo roles | [architecture.md](architecture.md) | Full |
| Plugin-visible change (API, host, contract) | [cross-repo.md](cross-repo.md) | Full |
| Build and test commands, hard rules | `../../AGENTS.md` | `## Build and test`, `## Hard rules` |
| Feature behavior | [../README.md](../README.md) | Hub, then one feature doc |
| UI components and tokens | [../ui-system/README.md](../ui-system/README.md) | Hub, then one page |
| Plugin contract | [../plugins/api.md](../plugins/api.md) | Router, then one page |
