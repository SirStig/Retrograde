## 1.0.0-alpha

- Chunk tracking (touched vs. unknown) across all 6 version/loader targets
- Map screen showing chunk status, singleplayer only
- Chunk regen with undo
- Keybinding to open the map screen on every target, including 26.3
- Per-chunk ore tally on hover
- Stronger confirmation when regenerating chunks with recorded player changes
- Run a known mod's own retrogen command over chunks (Mekanism so far)
- Map now renders real terrain (color per block, same approach as vanilla's held map item) instead of flat chunk-colored squares
- Fixed chunk data always reading as "not loaded" - was being read from the client render thread instead of the server thread
- Map keybinding is unbound by default instead of M, to avoid colliding with minimap mods
- Map is full-screen now with drag-to-pan and scroll-to-zoom
- Chunk rendering moved to one GPU texture per chunk instead of a fill() call per block, needed to make zoom/pan perform well
- Replaced the map's flat full-height header bar and side panel with a small
  centered title chip and a compact top-right icon cluster (recenter/zoom
  in/zoom out/close), bordered like normal minimap-mod chrome instead of two
  opaque gray slabs
- Replaced the cursor-following chunk tooltip with a fixed panel in the
  top-left corner: same information (coordinates, status, biome, ore tally),
  always the same size, never covering the chunks you're trying to look at
- Chunk actions are select-then-act instead of click-to-act. Click chunks to
  select, shift-drag to box-select a region, ctrl-drag to deselect, then pick
  Regenerate / Undo / Retrogen from the selection panel. All three run over
  the whole selection at once
- Added a progress screen for chunk jobs: current phase, progress bar, live
  per-chunk position, done/skipped/failed counts, and a Cancel that finishes
  cleanly rather than abandoning you mid-run
- Regen moves you clear of the work area and puts you back afterwards, rather
  than refusing because you're standing too close. Chunks that stay loaded
  anyway are reported as skipped instead of silently doing nothing
- Fixed regen's precondition: it tested whether a player was nearby, when what
  actually clobbers the write is the chunk being loaded at all - which happens
  well past a player's immediate neighbours (view distance, forced chunks,
  spawn chunks)
- Fixed chunks staying grey on the map for the rest of the session after one
  failed read; they're retried a couple of seconds later, and dropped outright
  once a job has regenerated them
- Fixed the entire visible map re-sampling every time a dialog was opened and
  dismissed, and fixed map textures being closed without being unregistered
- Left-drag now box-selects instead of panning, since selecting is what the
  screen is for. Panning moved to right-drag, middle-drag, and the arrow keys;
  ctrl-drag still deselects and a left-click still toggles one chunk
- Arrow keys pan and +/- zoom, so the map is usable without a mouse
- Added a toggle beside the ore heading that opens the full ore breakdown in
  its own panel next to the info panel - every ore with its name and count,
  instead of the "+N" that previously hid the rarest ones with no way to see
  them
- Fixed mouse buttons and arrow keys being wrong on 26.3, which moved its input
  backend from GLFW to SDL and renumbered both
- The map reads unloaded chunks straight out of the region file, so it shows
  everywhere you've explored instead of only the chunks the server currently
  has in memory. Grey now means "nothing generated there", not "walk closer"
- Biome and ore tally are cached alongside each chunk's terrain rather than
  read separately on hover, so every chunk the map has drawn can also be
  described - and searched
- Added a chunk filter: pick a scope (on screen, within 4/8/16 of you, or
  everywhere mapped), then narrow by touched/untouched, by biome, and by an
  ore being present or absent. Matches are highlighted on the map as you
  change the filter, with one button to select all of them
- Added Restore, which puts back the selection as it was before the last thing
  that replaced it - so clearing or overwriting a hand-picked selection is no
  longer final
- Regen now opens a preview instead of a yes/no dialog: chunk count, how many
  you've built in, how many can be undone afterwards, an estimated runtime, and
  the ore tallied across the whole selection
- Map keybinding now defaults to O instead of being unbound, picked for not
  colliding with the common minimap/JEI/Curios bindings
- WASD pans the map alongside the arrow keys
- A bare left-drag pans again, the way dragging a map does everywhere else;
  box-select moved onto shift-drag. Click-to-toggle and ctrl-drag-to-deselect
  are unchanged
- Fixed every chunk read off disk on 26.3 drawing as a black square with no
  biome and no ore tally. 26.3 changed how block palette entries are written -
  default states are bare strings now, and the rest are keyed `id` instead of
  `Name` - so the reader resolved every one of them to air. It handles all four
  shapes now, verified by decoding real 1.20.1 and 26.3 saves
- Map panels size themselves to the window instead of to fixed constants.
  The old layout wanted 253 of the 256 rows a 1366x768 screen has at GUI
  scale 3 for the left column alone, and 342 of its 455 columns once the
  filter was open; at scale 4 it ran off the bottom outright. Panels now
  shrink toward a floor, the ore grid drops rows and then disappears when
  the window is short, and the ore/filter panel moves to the right edge -
  or over the left column - rather than eating the middle of the map
- Regenerate, Undo and Retrogen moved off the map into one Chunk
  Manipulation menu. The action panel is two rows tall whatever ends up in
  that menu, and each operation gets a screen with room to say what it does
  before it does it
- Added a settings screen behind a gear in the icon cluster: slime chunk
  display, confirmation on destructive actions, the opaque progress
  backdrop, the unload and watchdog timeouts, and a master switch for
  world-altering extras. Settings persist to config/retrograde.properties
- Anything that hands you resources you didn't earn is off by default and
  hidden rather than greyed out while the master switch is off, so a world
  played straight never shows the buttons
- Added slime chunks: tinted green on the map and called out on the info
  panel, using vanilla's own seedSlimeChunk rule rather than a
  reimplementation of it. Overworld only, since the same maths produces a
  convincing and meaningless pattern anywhere else
- Added biome editing (behind the extras switch): pick from every biome the
  world registered and rewrite the selection in place via vanilla's
  fillbiome, against a permission-4 command source so it works in a world
  without cheats enabled. Blocks don't move - only fog, grass colour,
  weather and mob spawns change
- Progress screen backdrop is now opaque by default (settings toggle), so a
  regen's teleport out and back isn't visible behind it and read as a crash
- Cancel is now a ladder: Cancel stops at the next clean chunk boundary,
  pressing it again force-cancels and restores you immediately, and Leave
  anyway appears if even that doesn't land within three seconds. Escape
  climbs the same ladder. The old behaviour greyed the only button out
  while cancelling, which left nothing to press if the thing being
  cancelled was itself what had stopped responding
- Added a watchdog that aborts a job and puts the player back when nothing
  has moved for the configured interval. It runs on the client tick on
  purpose: the failure modes that strand a player are ones where the server
  thread stopped answering, so a server-thread watchdog would queue behind
  the hang it exists to rescue you from. If its restore doesn't land
  either, the screen says the player may still be staged and offers to
  retry, rather than reporting a clean finish
- The unload and watchdog timeouts now come from settings instead of being
  hardcoded at 30s and nothing
- A chunk that won't unload because it's force-loaded now gets its ticket
  dropped for the duration and handed back when the job ends, however it
  ends. Anything still loaded after that is still reported as skipped and
  never forced - forcing it would write a silently half-regenerated chunk
- Added map modes behind an icon in the cluster that cycles Terrain, Biomes,
  Ore density and Where you've been. The three data modes replace the terrain
  rather than tinting over it: a biome map is only useful if two chunks of the
  same biome look identical, which they can't while the terrain underneath is
  doing its job. Biome colours are hashed from the biome id so modded biomes
  get one too, and ore density normalises against the densest chunk on screen
  so a poor region still separates instead of reading as uniformly cold
- Added a slime row to the chunk filter: slime only, non-slime only, or
  either. Greyed out rather than hidden outside the overworld, where the
  pattern is meaningless
- The filter panel's rows shrink on a short window down to a legibility floor,
  and the panel starts from the top margin rather than below the icon cluster
  if that's what it takes to fit. No filter row is droppable the way the ore
  grid's were - a filter missing a criterion quietly can't answer a question
- Fixed slime and touched tints (and the slime edge outline) painting over
  chunks the map hasn't actually read yet - a grey "nothing generated" or
  still-loading square no longer wears a green wash that describes data it
  doesn't have. The player-position marker is unaffected; it answers "where
  am I", not "what's in this chunk"
- Redrew the settings gear as an actual gear (Painter's new gearIcon) instead
  of the ⚙ font glyph, which went fuzzy or lopsided depending on the font pack
- Chunk Manipulation is now an overlay drawn over the still-visible map
  instead of a screen swap, and shows the whole selection at a glance:
  chunk/touched/undoable counts, ore tallied across the entire selection, and
  a biome breakdown, above a compact row of actions. Replaces the old
  Chunk Manipulation screen
- Settings is now an overlay too, in the same visual language, grouped into
  sections with dividers instead of one flat list of rows. Replaces the old
  settings screen
- Removed the ore breakdown toggle and its side panel; the info panel's ore
  list is scrollable in place instead, with a visible scrollbar
- Escape now closes the topmost open overlay before it closes the map
- Extended Painter with roundedPanel, dropShadow, glowEdge, divider,
  scrollbar and gearIcon primitives, plus scissor clipping, so every panel
  and overlay in the mod is drawn through the same reusable, code-drawn
  building blocks instead of ad hoc fill() calls per screen
- Rewrote the settings gear again. The first "real gear" was a disc with eight
  squares stamped round its circumference, which reads as a lump: square teeth
  centred on a curve merge into the disc on the diagonals and stick out on the
  axes. It's now rasterised in polar coordinates with 4x4 supersampled
  coverage, so the edge antialiases, every tooth is the same shape, and the
  silhouette holds at any radius. Six teeth rather than eight, because at the
  ~14px a toolbar button gives you eight land under 2px apart
- Terrain and Ores map modes use real item icons (grass block, diamond ore)
  instead of letters. Terrain in particular was U+25A0, which renders as an
  anonymous white square. Biome and Touched keep their letters - they're
  abstractions with no block to point at
- Fixed the map's own buttons drawing over the Chunk Manipulation and Settings
  overlays. Vanilla renders every widget of a screen in one pass after the
  screen's own drawing, so an overlay panel could never be on top of one: at a
  large GUI scale, where the toolbar and action panel get pushed inward far
  enough to collide with the centred overlay, they punched straight through
  it and stayed clickable. Widgets the overlay covers are now taken out of the
  render pass, which takes click-through with them. Per-widget rather than
  all-or-nothing, so at a normal GUI scale nothing disappears and the gear
  still toggles the settings panel shut
- Added Ore editing, as vanilla-ore retrogen: a "Regen ores" row in Chunk
  Manipulation that re-runs the world's own underground-ore features over the
  selection through vanilla's placement code. Real veins, real depths, right
  stone type, biome-checked - not ore blocks sprinkled into a generated chunk.
  Seeded from the world seed and chunk position the way worldgen seeds its own
  decoration pass, but not a bit-exact replay of it - vanilla's feature
  ordering lives in a private field - so ore that already generated gets a
  second, separate set of veins rather than the same ones again. Re-running is
  deterministic, so it can't be stacked by pressing it repeatedly. Edits chunks
  in place, so unlike regen there's no move-out wait, and no undo. Still off by
  default behind the cheats switch
