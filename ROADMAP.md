# Roadmap

Ideas that are real but not built yet, with enough of a plan that whoever
picks them up next (possibly me) isn't starting from nothing.

## Selective regen (keep player changes, redo terrain/ores/structures)

The actual ask: add a mod to an existing world, then regen a chunk so the
new mod's stuff generates in, without losing anything you've built or dug
there. Current regen is all-or-nothing - it destroys the whole chunk,
player changes included.

Why this is harder than full regen: full regen works by writing a "never
generated" NBT tag and letting vanilla's own loading pipeline regenerate
the chunk from scratch (see ChunkRegenService). That only works because it
throws away the existing chunk entirely. Keeping some blocks and
regenerating others means having both the current chunk *and* a freshly
generated version of the same coordinates at the same time, then merging
them - vanilla has no built-in way to do that.

Two pieces this needs:

1. **Block-level "did a player touch this" tracking.** ChunkTracker only
   knows per-chunk, not per-block. Would need a per-chunk bitset (16 x
   16 x world-height bits, roughly 12KB per chunk at 1.20.1's height
   range) updated on block place/break, alongside the existing break/place
   hooks in the platform event subscribers.

2. **A way to get "what would generate here" without touching the live
   chunk.** The promising approach: spin up a temporary, unpersisted level
   with the same seed and generator settings, let vanilla generate the
   same chunk coordinates there normally, then read its block data back
   out. That reuses vanilla's real generation pipeline (correct structure
   placement, correct neighbor blending) instead of hand-driving
   ChunkGenerator's stages directly, which is the approach the current
   regen engine deliberately avoided for the same reason. Then: for each
   position, keep the live block if the bitset says a player touched it,
   otherwise take the generated level's block.

Regenerating "only ores" specifically has a narrower, easier path that
doesn't need any of the above: a mod's native retrogen (see
RetrogenIntegration) already works by re-running just that mod's
registered ore features against the existing chunk, not full regen. The
retrogen wrapper already covers this for whatever mods have their own
retrogen command. A vanilla-ore equivalent (re-run just the vanilla ore
configured features) would follow the same shape without needing the
block-tracking or shadow-level machinery at all - a good first step before
attempting the general "regen only X" version above, since it's a much
smaller piece of the eventual full design that's independently useful on
its own. "Only structures" or "only new biomes" would need the harder,
full block-diff version, since those aren't feature-reruns in the same
way, and "only new biomes" would additionally need snapshotting what
generator settings were active when a chunk was first generated, which
isn't tracked anywhere right now.

## Other known gaps

- Retrogen wrapper only has Mekanism so far (see RetrogenIntegrations).
- Map screen doesn't pan past the loaded radius around the player - would
  need to read chunk data straight from the saved region file for
  currently-unloaded chunks instead of live ChunkAccess.
- Multiplayer isn't supported at all - the map screen and regen both go
  straight at the local integrated server.
- Chunk jobs are driven by the progress screen's client tick, because there
  is no server-tick hook in this mod on any of the six targets. That's why
  the progress screen refuses to close while a job is running: it *is* the
  job's clock. The hole this leaves is a client crash or hard kill during
  the regen phase, which would strand the player at the staging position
  above the build height, invulnerable and weightless, with the remaining
  chunks unprocessed. Fixing it properly means persisting the job (target
  list, phase, and the saved return position and player flags) to the world
  folder and resuming it on next load, which in turn wants a real server
  tick hook per loader rather than the screen driving it.
- The selection is capped at 256 chunks, and a regen job walks it at four
  chunks per server tick with a blocking region-file read each. That's fine
  at that size but it's why the cap exists; anything larger wants the reads
  moved off the server thread.
