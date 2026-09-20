# Retrograde

Minecraft mod that tracks which chunks you've actually touched vs. chunks
that are just sitting there unexplored, shows it on a map screen, and lets
you regen untouched chunks (with undo) right from that screen.

Not trying to auto-detect "what's missing" for every mod out there, that's
not something you can know without re-running world generation. The plan is
to eventually wrap other mods' own retrogen configs into the same UI instead
of inventing something universal.

## Targets

| MC version | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge (NeoForge is still beta for 26.3) |

1.20.1 pairs with Forge instead of NeoForge since the Forge/NeoForge split
didn't happen until 1.20.2.

## Building

Stonecutter project, so only one version's source tree is active at a time.
Switch before building:

```
./gradlew "Set active project to <version>-<loader>"   # e.g. 1.20.1-fabric
./gradlew :<version>-<loader>:build
```

Keep those as two separate commands, chaining them in one invocation trips
a Gradle task-ordering check.

## Status

All 6 version/loader combos build. Chunk tracking, the map screen, the
keybinding to open it, and regen + undo all work everywhere, including 26.3.

Not done: retrogen wrapping for other mods, per-chunk resource info,
multiplayer support (map screen is singleplayer only for now).

I haven't been able to test any of this in an actual running game yet, just
verified it all compiles. Try it in a world you don't care about first.

## Credit

Started from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT licensed).
