## 1.0.0-alpha

- Chunk tracking (touched vs. unknown) across all 6 version/loader targets
- Map screen showing chunk status, singleplayer only
- Chunk regen with undo, click a chunk on the map to regenerate it, shift-click to undo
- Keybinding to open the map screen on every target, including 26.3
- Per-chunk ore tally on hover
- Stronger confirmation when regenerating a chunk with recorded player changes
- Ctrl-click a chunk to run a known mod's own retrogen command for it (Mekanism so far)
- Map now renders real terrain (color per block, same approach as vanilla's held map item) instead of flat chunk-colored squares
- Fixed chunk data always reading as "not loaded" - was being read from the client render thread instead of the server thread
- Map keybinding is unbound by default instead of M, to avoid colliding with minimap mods
- Map is full-screen now with drag-to-pan and scroll-to-zoom
- Chunk rendering moved to one GPU texture per chunk instead of a fill() call per block, needed to make zoom/pan perform well
- Replaced the map's flat full-height header bar and side panel with a small
  centered title chip and a compact top-right icon cluster (recenter/zoom
  in/zoom out/close), bordered like normal minimap-mod chrome instead of two
  opaque gray slabs
