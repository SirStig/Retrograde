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
