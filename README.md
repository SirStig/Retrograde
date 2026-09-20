# Retrograde

![Retrograde](docs/assets/banner.png)

Minecraft mod that tracks which chunks you've actually touched vs. chunks
that are just sitting there unexplored, shows a real top-down terrain map
with an ore tally per chunk, and lets you regen a chunk (with undo) or hand
it off to another mod's own retrogen right from that screen.

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

All 6 version/loader combos build. Chunk tracking, the terrain map, the
keybinding to open it, regen + undo, per-chunk ore info, and the Mekanism
retrogen integration all work everywhere, including 26.3.

The map renders real terrain (same color-per-block approach vanilla's held
map item uses) with a translucent tint over touched chunks and your own
position, chunk grid lines, and only shows chunks the server has actually
loaded - it doesn't read unloaded chunks off disk yet, so walking closer
fills the map in as you go.

Map controls: click a chunk to regen it (touched chunks get a stronger
warning), shift-click to undo, ctrl-click to hand it to another mod's
retrogen if one's installed, hover to see its ore tally. The keybinding to
open the map is unbound by default on purpose - didn't want to guess a key
that collides with whatever minimap mod you're probably already running.

Not done: more retrogen integrations beyond Mekanism, multiplayer support,
panning the map to see explored-but-unloaded terrain.

This mod has now actually been run in-game for the first time, which
surfaced a real bug (every chunk showed as unloaded - the map screen was
reading chunk data straight off the render thread, and only the server's
own thread can actually do that) that's fixed now. This fix itself hasn't
been played-tested yet though, just compiled, so still worth trying in a
world you don't care about first.

## Credit

Started from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT licensed).
