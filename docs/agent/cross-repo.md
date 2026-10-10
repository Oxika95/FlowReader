# Cross-repo checklist (plugin-visible changes)

App-side sync points. Plugin-side release rules (ids, versions, index) live in
`../flow-reader-plugins/CONTEXT.md` `## Release rules`.

1. **Versions:** app `PLUGIN_HOST_API_VERSION` / `PLUGIN_MIN_API_VERSION`
   (`plugin/api/PluginManifest.kt`) and plugins `HOST_API_VERSION` / `MIN_API_VERSION`
   (`../flow-reader-plugins/scripts/build-index.mjs`) move together. Alpha: only the current
   version is accepted, so a bump means republishing every plugin.
2. **Host API:** any `flow.*` change in `plugin/runtime/*` is mirrored in
   `../flow-reader-plugins/test/host.mjs`.
3. **Contract:** [api.md](../plugins/api.md), [ui-contract.md](../plugins/ui-contract.md),
   `schema/*.json`, `examples/`. Icon tokens (`ui/design/FlowIcons.kt`) are append-only.
4. **Tests:** app commands in `AGENTS.md` `## Build and test`; plugins commands in
   `../flow-reader-plugins/AGENTS.md` `## Commands`.
5. **Order:** ship the app change first. Pushing plugins `main` publishes to users immediately via
   Pages; apps on an older `apiVersion` stop being offered the bumped plugin until they update.
6. **Device test:** `.\gradlew.bat :app:installDebug` bundles the sibling plugins checkout.
