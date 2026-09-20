# Retrograde

Minecraft mod that tracks which chunks you've actually touched vs. chunks
that are just sitting there unexplored, shows it on a map screen with an
ore tally per chunk, and lets you regen a chunk (with undo) or hand it off
to another mod's own retrogen right from that screen.

Not trying to auto-detect "what's missing" for every mod out there, that's
not something you can know without re-running world generation. Instead it
wraps other mods' own retrogen commands (currently just Mekanism) so you
don't have to leave the map screen to use them.

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
keybinding to open it, regen + undo, per-chunk ore info, and the Mekanism
retrogen integration all work everywhere, including 26.3.

Map controls: click a chunk to regen it (touched chunks get a stronger
warning), shift-click to undo, ctrl-click to hand it to another mod's
retrogen if one's installed, hover to see its ore tally.

Not done: more retrogen integrations beyond Mekanism, multiplayer support
(map screen is singleplayer only for now).

I haven't been able to test any of this in an actual running game yet, just
verified it all compiles. Try it in a world you don't care about first.

## Credit

Started from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT licensed).
