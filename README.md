# Retrograde

Tracks which Minecraft chunks have been player-modified vs. left untouched
since this mod was installed, shows it on a map screen, and lets you
regen (untouched chunks, with undo) or retrogen (wraps each other installed
mod's own native retrogen config into one place) from there.

Not a universal "auto-detect what's missing for any mod" tool — that isn't
reliably knowable without re-simulating generation. See the project plan
for the full design rationale.

## Targets

| MC version | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge (NeoForge is beta-only for 26.3 as of this writing) |

NeoForge doesn't exist for 1.20.1 — the Forge/NeoForge split happened at
1.20.2 — so 1.20.1 pairs with plain Forge instead.

## Building

This is a [Stonecutter](https://stonecutter.kikugie.dev/) multi-version,
multi-loader project. Only one version's source is "active" (uncommented) in
`src/` at a time — switch before building a different target:

```
./gradlew "Set active project to <version>-<loader>"   # e.g. 1.20.1-fabric
./gradlew :<version>-<loader>:build
```

Running the switch and the build in the same Gradle invocation can hit a
task-ordering validation error — keep them as two separate commands.

## Status

Scaffold complete for all 6 version/loader combinations (project
configuration succeeds for all of them). Full build verified for
`1.20.1-fabric` and `26.1-fabric`; the rest are configured but not yet
build-verified. No mod features are implemented yet beyond the template's
own example event handler/mixin — chunk tracking, the map screen, and the
regen/retrogen engine are still to come.

## Credit

Scaffolded from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT licensed), a Stonecutter + custom platform-abstraction multi-loader
template.
