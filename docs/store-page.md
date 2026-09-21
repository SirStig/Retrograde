# Retrograde

Retrograde keeps track of which chunks you've actually explored, draws them as a real top-down terrain map, and lets you select chunks on that map and regenerate them — with undo — or hand them to another mod's retrogen.

It doesn't try to work out what's "missing" from a chunk after you add a mod to an existing world. That isn't knowable without re-running world generation. What it does instead is show you where you've been, what's in it, and give you a way to act on a selection of it.

**Singleplayer only.** This is beta — regen rewrites chunks, so back up a world you care about before pointing it at one.

## The map

Press **O** to open it. Drag or WASD to pan, scroll or `+`/`-` to zoom toward the cursor.

Terrain is drawn with a colour per block — the same approach vanilla uses for the held map item. It covers everywhere you've explored, not just the few hundred blocks around you: chunks the server has in memory are read from memory, and everything else is decoded straight out of the region files. Grey means nothing has ever generated there.

Four colour modes cycle from the toolbar:

- **Terrain** — block colours, as above.
- **Biome** — colours are hashed from the biome id, so a biome added by another mod gets a stable colour of its own rather than falling off the end of a hand-written palette.
- **Ore density** — normalised against the densest chunk currently on screen, so an ore-poor region still separates out instead of reading as uniformly cold.
- **Where you've been** — visited against unvisited.

The three data modes replace the terrain rather than washing over it. A biome map is only useful if two chunks of the same biome look identical, which they can't while the terrain underneath is still doing its job.

Slime chunks are tinted and called out in the info panel, using vanilla's own `seedSlimeChunk` rule. Overworld only — the same maths produces a perfectly convincing and entirely meaningless pattern in the Nether.

## Reading and finding chunks

A fixed panel describes whatever chunk is under the cursor: coordinates, visited or not, biome, and an ore tally. It stays the same size in the same place so it never covers the chunks you're trying to read. The ore list is every ore in the chunk, not a capped preview — hover it and scroll, with a scrollbar down the side if you'd rather drag.

**Find** filters by scope — what's on screen, within 4/8/16 chunks of you, or everywhere the map has read — then narrows by visited or unvisited, by slime chunk, by biome, and by an ore being present or absent. Biome and ore choices are cycled from what's actually out there rather than typed, so there's nothing to spell and no way to land on a filter that can't match. Matches light up on the map as you change the filter, with a live count and a button to select all of them, nearest first.

## Changing chunks

Click to toggle a chunk, shift-drag to box-select, ctrl-drag to deselect. Up to 256 at a time.

**Chunk Manipulation** opens over the still-visible map rather than switching away from it, and reports the shape of the whole selection: how many chunks, how many you've built in, how many have an undo snapshot, the ore tallied across every chunk, and which biomes you've picked.

- **Regenerate** rewrites chunks from scratch through vanilla's own generator, so they blend with their neighbours. It opens a preview first — counts, rough duration, and the ore you'd be losing — because it's the one action here that destroys work.
- **Undo** puts a chunk back from the snapshot taken before its last regen.
- **Retrogen** runs another mod's own retrogen command over the selection without leaving the map. Mekanism is the only integration so far.
- **Regen ores** re-runs the world's underground-ore features over the selection, so ore from a mod or datapack added after those chunks generated turns up in real veins at real depths. Off by default.
- **Set biome** rewrites the selection's biome in place, chosen from every biome the world registered. Nothing moves — a desert turned swamp is a desert with swamp fog, mob spawns and grass colour.

## Jobs you can get out of

A loaded chunk saves itself back over any edit, so rather than refusing to run because you're standing too close, a job parks you above the build height clear of the selection, waits for the chunks to unload, does the work, and returns you exactly where you were. Progress shows the phase, a bar, the current chunk, and done/skipped/failed counts.

Cancel is a ladder. **Cancel** stops at the next clean chunk boundary. Again is **Force cancel**: stop now, put me back, a half-written chunk is acceptable. If that doesn't land within three seconds, **Leave anyway** appears and lets go of the job so the screen can close — the one option that can leave a mess, and it says so on screen rather than in a log.

Behind that, a watchdog on the client tick aborts and returns you if nothing has moved for a configurable interval. It runs on the client tick specifically because the failures that strand you above the build height are the ones where the server thread stopped answering, and a watchdog scheduled onto that thread would be queued behind the problem it exists to rescue you from.

A chunk that won't leave memory gets one real attempt before being written off, and anything still loaded after that is reported as skipped by name rather than forced out — forcing it would produce a silently half-regenerated chunk instead of an honest skip.

## Settings

The gear on the map opens slime chunk display, confirmation on destructive actions, the opaque progress backdrop, and the two job timeouts. Stored in `config/retrograde.properties`.

Operations that can hand you things you didn't earn sit behind a master switch that's off by default. While it's off those buttons aren't greyed out, they're absent — a world you meant to play straight never shows you the button.

## Versions

| Minecraft | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge |

1.20.1 pairs with Forge rather than NeoForge because the split didn't happen until 1.20.2. Fabric builds need Fabric API.

## Known limitations

- Singleplayer only.
- Find only covers chunks the map has read. Chunks that have never generated can't be filtered on.
- Regen is all-or-nothing — it can't keep your builds and redo the terrain around them.
- Mekanism is the only mod-retrogen integration.
- Regen ores has no undo, and can't remove ore.

Source and issues: [github.com/SirStig/Retrograde](https://github.com/SirStig/Retrograde). MIT licensed.
