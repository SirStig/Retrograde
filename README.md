# Retrograde

![Retrograde](docs/assets/banner.png)

Minecraft mod that tracks which chunks you've actually touched vs. chunks
that are just sitting there unexplored, shows a real top-down terrain map
with an ore tally per chunk, and lets you select chunks on that map and
regen them (with undo) or hand them to another mod's own retrogen.

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

The map is full-screen now: drag to pan, scroll to zoom toward the cursor,
with a small floating title pill top-center and a compact recenter/zoom/close
icon cluster top-right - both styled like a normal minimap mod's chrome
(bordered chips over the map) instead of the flat full-height side panel
earlier versions had. Renders real terrain (same color-per-block approach
vanilla's held map item uses) with a translucent tint over touched chunks
and your own position, chunk grid lines, and only shows chunks the server
has actually loaded - it doesn't read unloaded chunks off disk yet, so
walking closer fills the map in as you go.

Down the left side are two fixed panels. The top one describes whatever
chunk is under the cursor - coordinates, touched/untouched, biome, ore tally
as icons - at a fixed size, in a fixed place, so it isn't covering the
chunks you're trying to read. The ore tally only has room for six, so a
toggle beside its heading opens the full breakdown - every ore with its name
and count - in its own panel alongside, rather than leaving the rarest ones
hidden behind a "+4" you can't do anything with.

The one below it is the selection: left-drag to box-select a region,
ctrl-drag to deselect, click to toggle a single chunk, then pick Regenerate,
Undo, Retrogen, or Clear. Selected chunks are tinted and outlined on the map,
and the panel says how many are selected and how many of those you've
touched. Panning is on right-drag and middle-drag (and the arrow keys, with
+/- to zoom) rather than left-drag, since selecting is the thing you came to
this screen to do. A click only counts if the mouse didn't move far enough to
register as a drag, so selecting and panning don't fight each other.

All three actions run over the whole selection behind a progress screen -
phase, progress bar, which chunk it's on, and done/skipped/failed counts,
with a Cancel that stops cleanly instead of abandoning you mid-run. Regen
can't touch a chunk that's currently loaded (a loaded chunk saves itself
back over the edit), so rather than refusing, the job parks you above the
build height clear of the selection, waits for the chunks to unload, does
the work, and puts you back exactly where you were. Anything still loaded
after 30 seconds - forced chunks, spawn chunks - is reported as skipped
rather than forced.

The keybinding to open the map is unbound by default on purpose - didn't
want to guess a key that collides with whatever minimap mod you're probably
already running.

Not done: more retrogen integrations beyond Mekanism, multiplayer support,
panning to see explored-but-unloaded terrain (the map only ever reads live
chunk data, see ROADMAP.md).

### What's actually been verified

Earlier versions were run in a real game, not just compiled - that's how the
original "everything shows as unloaded" bug got found and fixed (the map
screen was reading chunk data straight off the render thread; only the
server's own thread can do that), how the zoom buttons got confirmed
working, and how the title chip, icon cluster, chunk grid, and ore readout
were confirmed to render, screenshotted live in a 1.20.1 world.

The selection UI, the progress screen, and the teleport-out-and-back regen
job are new and have a clean compile on all six targets behind them, nothing
more. Anything needing a mouse is unverified in general: the environment I
can test in has keyboard input only, so drag-to-pan, scroll-to-zoom, and now
click-to-select, box-select and the ore breakdown toggle have only compiling
and a careful read of the code behind them. The 26.x `GuiGraphicsExtractor`
render path is compile-only as always - no Wayland-friendly way to launch
26.1/26.3 and look at it yet.

One thing that is verified, because it was checked against the shipped
classes rather than guessed: 26.3 moved its input backend from GLFW to SDL,
which renumbers both the mouse buttons (left is 1, not 0) and the
non-printable keys (arrows are in SDL's 0x40000000 range). `ChunkMapScreen`
carries a separate set of constants for 26.3 because of it. Anything else
reading raw button or key numbers on 26.3 needs the same treatment.

## Credit

Started from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT licensed).
