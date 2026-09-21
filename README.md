# Retrograde

![Retrograde](docs/assets/banner.png)

Retrograde keeps track of which chunks you've actually explored, draws them
as a real top-down terrain map, and lets you select chunks on that map and
regenerate them — with undo — or hand them to another mod's retrogen.

It doesn't try to work out what's "missing" from a chunk after you add a mod
to an existing world. That isn't knowable without re-running world
generation. What it does instead is show you where you've been, what's in
it, and give you a way to act on a selection of it.

**Beta. Singleplayer only.**

## Supported versions

| Minecraft | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge |

1.20.1 pairs with Forge rather than NeoForge because the split didn't happen
until 1.20.2.

## The map

Press **O** to open it. The default is O for being free rather than
memorable — M and Y are minimap territory, J is JourneyMap, R and U belong
to JEI, G to Curios. Rebindable like anything else.

The map is full screen. Drag to pan, scroll to zoom toward the cursor, WASD
or the arrow keys to pan, `+`/`-` to zoom. A click only registers as a click
if the mouse didn't travel far enough to count as a drag, which lets the
left button both pan and toggle a chunk without the two fighting.

Terrain is drawn with a colour per block — the same approach vanilla uses
for the held map item — with a translucent tint over chunks you've visited,
your own position marked, and grid lines between chunks.

Chunks the server has in memory are read from memory; everything else is
decoded straight out of the region files, so the map covers everywhere
you've explored rather than the few hundred blocks around you. Grey means
nothing has ever generated there. Biome and ore data come out of that same
read and are cached alongside the terrain, so any chunk the map has drawn
can also be described and searched.

The visited wash and slime tint only appear once a chunk has actually been
read — a grey or still-loading square never wears either, since both
describe what's in the chunk and a grey square has nothing to describe yet.
Your own position is the exception: it's marked the moment you're standing
there, read or not.

### Colour modes

An icon in the top-right cluster cycles what the squares are coloured by:

- **Terrain** — block colours, as above.
- **Biomes** — colours are hashed from the biome id, so a biome added by
  another mod gets a stable colour of its own rather than falling off the
  end of a hand-written palette.
- **Ore density** — normalised against the densest chunk currently on
  screen, so an ore-poor region still separates out instead of reading as
  uniformly cold.
- **Where you've been** — visited against unvisited.

The three data modes replace the terrain rather than washing over it. A
biome map is only useful if two chunks of the same biome look identical,
which they can't while the terrain underneath is still doing its job.

## Chunk info and selection

Two panels sit down the left side.

The upper one describes whichever chunk is under the cursor: coordinates,
visited or not, biome, and an ore tally. It stays a fixed size in a fixed
place so it never covers the chunks you're trying to read. The ore list is
every ore in the chunk, not a capped preview — hover it and scroll to move
through the list, with a scrollbar down the side so there's something to
grab if you'd rather drag than scroll.

The lower one is the selection. Shift-drag to box-select a region, ctrl-drag
to deselect, click to toggle a single chunk, then pick **Chunk
Manipulation**, **Find** or **Clear**. Selected chunks are tinted and
outlined, and the panel reports how many are selected and how many of those
you've visited. The cap is 256 chunks per selection.

Both panels size themselves to the window rather than to fixed numbers: they
shrink toward a floor, the ore list's visible rows drop to a minimum on a
short window, and the filter panel moves to the right edge — or over the
left column on a very small window — instead of taking another bite out of
the middle.

### Find

**Find** opens a filter beside the info panel. Pick a scope — what's on
screen, within 4/8/16 chunks of you, or everywhere the map has read — then
narrow by visited or unvisited, by slime chunk, by biome, and by an ore
being present or absent. The slime row greys out outside the Overworld
rather than vanishing, since a row that comes and goes as you walk through a
portal is a row you can't find again.

Biome and ore choices are cycled from what's actually out there rather than
typed, so there's nothing to spell and no way to land on a filter that can't
match. Matches light up amber on the map as you change the filter, with a
live count and a button to select all of them, nearest first. **Clear** and
the filter both remember the previous selection, and **Restore** puts it
back.

## Chunk Manipulation

Everything that changes a chunk lives behind one button, which opens an
inspector panel over the map rather than switching away to another screen —
the map, and the selection tinted on it, stay visible the whole time. It
reports the shape of the whole selection: how many chunks, how many you've
built in, how many have an undo snapshot, the ore tallied across every
chunk in the selection (not per chunk), and a breakdown of which biomes
you've selected — then a compact row of actions: Regenerate, Undo, another
mod's Retrogen, and — when they're switched on — ore and biome editing.
Escape, or the panel's own close button, puts it away without touching the
selection underneath.

**Regenerate** opens a preview rather than a yes/no box, since it's the one
action here that destroys work: how many chunks, how many you've built in,
how many have an undo snapshot behind them, roughly how long it will take,
and the ore tallied across the whole selection.

**Retrogen** wraps another mod's own retrogen command so you don't have to
leave the map to use it. Mekanism is the only integration so far.

Everything runs over the whole selection behind a progress screen — phase,
progress bar, current chunk, and done/skipped/failed counts. A loaded chunk
saves itself back over any edit, so rather than refusing to run, the job
parks you above the build height clear of the selection, waits for the
chunks to unload, does the work, and returns you exactly where you were. The
progress backdrop is opaque by default, because watching that happen through
a translucent screen reads as the game having broken.

Cancel is a ladder rather than a single button. **Cancel** stops at the next
clean chunk boundary. Pressing it again is **Force cancel**: stop now, put me
back, a half-written chunk is acceptable. If that doesn't land within three
seconds, **Leave anyway** appears and lets go of the job so the screen can
close — the one option here that can leave a mess, and it says so on screen
rather than in a log. Escape climbs the same ladder.

Behind that, a watchdog on the client tick aborts the job and returns you if
nothing has moved for a configurable interval. It runs on the client tick
specifically because the failure modes that strand you above the build
height are the ones where the server thread stopped answering — a watchdog
scheduled onto that thread would be queued behind the problem it exists to
rescue you from.

A chunk that won't leave memory gets one real attempt before being written
off: if it's force-loaded, the job drops the ticket, waits another round, and
restores the ticket when the job ends however it ends. Anything still loaded
after that is reported as skipped by name, never forced out, since forcing it
would produce a silently half-regenerated chunk instead of an honest skip.

## Settings

The gear in the icon cluster opens a settings panel over the map: slime
chunk display, confirmation on destructive actions, the opaque progress
backdrop, how long a job waits for a chunk to unload, and how long it may
stall before the watchdog intervenes. Settings are stored in
`config/retrograde.properties`.

There's also a master switch for operations that can hand you things you
didn't earn. It's off by default, and while it's off those operations aren't
greyed out, they're absent — a world you meant to play straight never shows
you the button.

**Slime chunks** are tinted green on the map and called out in the info
panel, using vanilla's own `seedSlimeChunk` rule rather than a
reimplementation. Overworld only; the same maths produces a perfectly
convincing and entirely meaningless pattern in the Nether.

**Biome editing** (behind the extras switch) rewrites the selection's biome
in place through vanilla's `fillbiome`, chosen from every biome the world
registered rather than only the ones you've visited. It runs against a
permission-4 command source built from the server, so it works in a world
without cheats enabled — the gate is Retrograde's setting, not the world's.
Nothing moves: a desert turned swamp is a desert with swamp fog, mob spawns
and grass colour. Reshaping the terrain to match is a regen, which is a
different button in the same menu.

**Ore editing** (behind the same switch) adds a **Regen ores** action that
re-runs the world's own underground-ore features over the selection. It
doesn't scatter ore blocks into a finished chunk — it asks the biomes in
that chunk which ore features they generate and runs exactly those through
vanilla's placement code, so you get real veins at real depths in the right
stone type, checked against the biome at each position.

What it's for is ore from a mod or datapack you installed after those chunks
were written. It isn't a bit-exact replay of the original pass — Minecraft
keeps the feature ordering that would make that possible behind a private
field — so ore that already generated gets a second, separate set of veins
rather than the same ones back. On an established world, expect more ore
than it rolled. Running it twice places the same veins rather than stacking
more, and there's no undo for it, which is why it's off by default.

## Controls

| Input | Action |
|---|---|
| `O` | Open the map (rebindable) |
| Left-drag | Pan |
| Right-drag / middle-drag | Pan |
| WASD / arrow keys | Pan |
| Scroll, `+` / `-` | Zoom |
| Click | Toggle a chunk's selection |
| Shift-drag | Box-select |
| Ctrl-drag | Box-deselect |
| Escape | Close the topmost open panel, then the map, or climb the cancel ladder during a job |

## Building

This is a [Stonecutter](https://stonecutter.kikugie.dev/) project, so only
one version's source tree is active at a time. Switch before building:

```
./gradlew "Set active project to <version>-<loader>"   # e.g. 1.20.1-fabric
./gradlew :<version>-<loader>:build
```

Keep those as two separate invocations — chaining them trips a Gradle
task-ordering check.

## Limitations

- Singleplayer only.
- Search covers chunks the map has read. Chunks that have never been
  generated or read can't be filtered on.
- Mekanism is the only retrogen integration.
- Regen is all-or-nothing. It can't keep your builds and redo the terrain
  around them; see [ROADMAP.md](ROADMAP.md) for what that would take.
- Regen ores has no undo, and can't remove ore.

## Credit

Built from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate),
MIT licensed. Retrograde is MIT licensed as well — see [LICENSE](LICENSE).
