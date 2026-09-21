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
and your own position, plus chunk grid lines.

Chunks the server has in memory are read from memory; everything else is
decoded straight out of the region file, so the map covers everywhere
you've explored rather than the few hundred blocks around you. Grey means
nothing has ever been generated there, not "walk closer". Biome and ore
tally come out of that same read and are cached with the terrain, so every
chunk the map has drawn can also be described - and searched.

Down the left side are two fixed panels. The top one describes whatever
chunk is under the cursor - coordinates, touched/untouched, biome, ore tally
as icons - at a fixed size, in a fixed place, so it isn't covering the
chunks you're trying to read. The ore tally only has room for six, so a
toggle beside its heading opens the full breakdown - every ore with its name
and count - in its own panel alongside, rather than leaving the rarest ones
hidden behind a "+4" you can't do anything with.

The one below it is the selection: left-drag to box-select a region,
ctrl-drag to deselect, click to toggle a single chunk, then pick Regenerate,
Undo, Retrogen, Find or Clear. Selected chunks are tinted and outlined on the
map, and the panel says how many are selected and how many of those you've
touched. Panning is on right-drag and middle-drag (and the arrow keys, with
+/- to zoom) rather than left-drag, since selecting is the thing you came to
this screen to do. A click only counts if the mouse didn't move far enough to
register as a drag, so selecting and panning don't fight each other.

Find opens a filter panel beside the info panel: pick a scope (what's on
screen, within 4/8/16 chunks of you, or everywhere the map has read), then
narrow it by touched/untouched, by biome, and by an ore having to be present
or absent. The biome and ore choices are cycled from what's actually out
there rather than typed, so there's nothing to spell and no way to land on a
filter that can't match. Matches light up amber on the map as you change the
filter, with a live count and one button to select all of them - nearest
first, since a filter can match more chunks than the 256 cap allows. Clear
and the filter both keep the previous selection, and Restore puts it back.

Regenerate opens a preview rather than a yes/no box, since it's the one
action here that destroys work: how many chunks, how many you've built in,
how many have an undo snapshot behind them, roughly how long it'll take, and
the ore tallied across the whole selection.

All three actions run over the whole selection behind a progress screen -
phase, progress bar, which chunk it's on, and done/skipped/failed counts,
with a Cancel that stops cleanly instead of abandoning you mid-run. Regen
can't touch a chunk that's currently loaded (a loaded chunk saves itself
back over the edit), so rather than refusing, the job parks you above the
build height clear of the selection, waits for the chunks to unload, does
the work, and puts you back exactly where you were. Anything still loaded
after 30 seconds - forced chunks, spawn chunks - is reported as skipped
rather than forced.

The keybinding to open the map defaults to O, picked for being free rather
than for being memorable: M and Y are minimap territory, J is JourneyMap, R
and U belong to JEI, G to Curios, and JourneyMap takes the brackets for zoom
on top of that. Rebindable like anything else.

Not done: more retrogen integrations beyond Mekanism, multiplayer support,
searching chunks the map has never read (see ROADMAP.md).

### What's actually been verified

Earlier versions were run in a real game, not just compiled - that's how the
original "everything shows as unloaded" bug got found and fixed (the map
screen was reading chunk data straight off the render thread; only the
server's own thread can do that), how the zoom buttons got confirmed
working, and how the title chip, icon cluster, chunk grid, and ore readout
were confirmed to render, screenshotted live in a 1.20.1 world.

The selection UI, the progress screen, the teleport-out-and-back regen job,
the saved-chunk reader, the chunk filter and the regen preview are new and
have a clean compile on all six targets behind them, nothing more. Anything
needing a mouse is unverified in general: the environment I can test in has
keyboard input only, so drag-to-pan, scroll-to-zoom, click-to-select,
box-select, the ore breakdown toggle and every button on the filter panel
have only compiling and a careful read of the code behind them.

The saved-chunk reader is worth singling out: it decodes the region file's
block and biome palettes by hand, and while the format it targets has been
stable since 1.18 and it version-checks before trusting anything, it has not
been pointed at a real world yet. A wrong bit width there shows up as
garbled terrain, not as a crash. The 26.x `GuiGraphicsExtractor`
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
