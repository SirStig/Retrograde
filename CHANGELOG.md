# Changelog

## 0.1.1-beta

Performance rework for the chunk map, aimed squarely at large explored worlds
and low zoom levels, where it was previously close to unusable.

- Terrain no longer gets a unique GPU texture per chunk. Chunks now share one
  texture per 16x16 region, the same approach other chunk-grid map mods use,
  so the map's draw-call count scales with screen area instead of with how
  small zooming out has made an individual chunk.
- The map's rendered terrain is now mirrored to a small per-region cache file
  under the world's save folder, so reopening the map (or a previous
  session's explored area) shows a picture immediately instead of re-reading
  and re-decoding every chunk from scratch. Chunk status used for filtering
  and regen targeting is never taken from this cache — only ever from a read
  verified in the current session.
- Non-terrain modes (biome, ore density, visited) no longer pay for a terrain
  read at all, since they never displayed one.
- The chunk grid no longer disappears below zoom level 0 — it now thins to
  wider spacing instead of vanishing, so chunk boundaries stay visible at
  every zoom.
- At extreme zoom-out, the map now groups chunks into small blocks for
  overlay decisions (pending/ungenerated shading, visited/slime tint)
  instead of recomputing every individual chunk's state every frame -
  several hundred thousand chunks on screen no longer means several hundred
  thousand hashmap lookups a frame. Selection and filter-match highlighting
  no longer scale with how many chunks are on screen at all.

## 0.1.0-beta

First release. Singleplayer only.

### The map

- Full-screen chunk map, opened with **O** by default. Drag or WASD to pan,
  scroll or `+`/`-` to zoom toward the cursor.
- Real top-down terrain, drawn with a colour per block — the same approach
  vanilla uses for the held map item — rather than flat coloured squares.
- Covers everywhere you've explored, not just the chunks around you. Loaded
  chunks are read from memory, the rest straight out of the region files.
- Chunks you've been in wear a translucent wash. Chunks that have never
  generated are grey.
- Four colour modes, cycled from the toolbar: terrain, biome, ore density,
  and visited-versus-not. The three data modes replace the terrain rather
  than tinting over it, so two chunks of the same biome look identical.
- Biome colours are hashed from the biome id, so biomes added by other mods
  get a stable colour instead of falling off the end of a palette.
- Ore density is normalised against the densest chunk on screen, so an
  ore-poor region still separates out instead of reading as uniformly cold.
- Slime chunks are tinted and called out in the info panel, using vanilla's
  own `seedSlimeChunk` rule. Overworld only.

### Reading and finding chunks

- A fixed panel describes whatever chunk is under the cursor: coordinates,
  visited or not, biome, and a full ore tally. It stays the same size in the
  same place so it never covers what you're trying to look at. The ore list
  scrolls in place with a scrollbar.
- **Find** filters by scope (on screen, within 4/8/16 chunks, or everywhere
  the map has read), visited or unvisited, slime chunk, biome, and an ore
  being present or absent. Biome and ore choices cycle through what's
  actually out there, so there's nothing to spell and no way to land on a
  filter that can't match.
- Matches light up on the map as you change the filter, with a live count and
  a button to select all of them, nearest first.

### Selecting and changing chunks

- Click to toggle a chunk, shift-drag to box-select, ctrl-drag to deselect.
  Up to 256 chunks at a time.
- **Chunk Manipulation** opens over the map rather than switching away from
  it, so the selection stays visible while you decide. It reports the shape
  of the whole selection: chunk and visited counts, how many can be undone,
  ore tallied across every chunk, and which biomes you've picked.
- **Regenerate** rewrites chunks from scratch through vanilla's own
  generator, so they blend with their neighbours. It opens a preview first —
  counts, rough duration, and the ore you'd be losing — because it's the one
  action here that destroys work.
- **Undo** restores from the snapshot taken before a regen. Five snapshots
  are kept per chunk.
- **Retrogen** runs another mod's own retrogen command over the selection
  without leaving the map. Mekanism is the only integration so far.
- **Regen ores** re-runs the world's underground-ore features over the
  selection, so ore from a mod or datapack added after those chunks
  generated appears in real veins at real depths. Off by default.
- **Set biome** rewrites the selection's biome in place, chosen from every
  biome the world registered. Nothing moves — a desert turned swamp is a
  desert with swamp fog, spawns and grass colour. Off by default.

### Running jobs

- Every operation runs behind a progress screen: phase, progress bar, current
  chunk, and done/skipped/failed counts.
- A loaded chunk saves itself back over any edit, so rather than refusing to
  run near you, a job parks you above the build height clear of the
  selection, waits for the chunks to unload, works, and puts you back exactly
  where you were.
- A chunk that won't leave memory gets one real attempt — if it's
  force-loaded the job drops the ticket, waits another round, and restores it
  afterwards. Anything still loaded is reported as skipped by name rather
  than forced out, since forcing it would leave a half-regenerated chunk.
- Cancel is a ladder: **Cancel** stops at the next clean chunk boundary,
  again is **Force cancel**, and if that doesn't land within three seconds
  **Leave anyway** lets go of the job so the screen can close. Escape climbs
  the same ladder.
- A watchdog on the client tick aborts and returns you if nothing moves for a
  configurable interval — on the client tick specifically, because the
  failures that strand you above the build height are the ones where the
  server thread stopped answering.

### Settings

- Reached from the gear on the map: slime chunk display, confirmation on
  destructive actions, the opaque progress backdrop, how long a job waits for
  a chunk to unload, and how long it may stall before the watchdog steps in.
  Stored in `config/retrograde.properties`.
- Operations that can hand you things you didn't earn sit behind a master
  switch that's off by default. While it's off those buttons aren't greyed
  out, they're absent.

### Supported versions

| Minecraft | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge |

### Known limitations

- Singleplayer only.
- Find only covers chunks the map has read. Chunks that have never generated
  can't be filtered on.
- Regen is all-or-nothing — it can't keep your builds and redo the terrain
  around them.
- Mekanism is the only mod-retrogen integration.
