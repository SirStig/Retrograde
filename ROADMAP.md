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

## Ore editing

The settings screen has a toggle for it; the Chunk Manipulation menu has no
row for it yet, on purpose. The obvious implementation - scatter ore blocks
into an already-generated chunk - produces something that doesn't look like
worldgen (wrong vein shapes, wrong depth distribution, no respect for the
biome or the surrounding stone type) and can't be undone precisely, since
regen's undo snapshot is per-chunk and would roll back everything else too.

The honest version is the vanilla-ore retrogen described above: re-run just
the vanilla ore configured features against the existing chunk, the same way
the Mekanism integration re-runs Mekanism's. That gives real veins in real
places, and "more ore" becomes "run it again" rather than a number to type
in. "Less ore" has no equivalent and probably shouldn't - removing ore from
a chunk you may have already mined is a diff problem, not a generation one.

## Progress screen hardening

Still outstanding, and the reason the settings screen already carries the
timeouts and the opaque-backdrop toggle that these will use:

- The backdrop setting exists and persists but RegenProgressScreen doesn't
  read it yet, so the screen is still whatever it was. The point of it is
  that regen parks you above the build height and brings you back, and
  watching that happen through a translucent screen reads as the game
  having broken.
- `watchdogTimeoutSeconds` is stored and clamped but nothing watches. What
  it's for: a job that stops making progress at all (a chunk that won't
  unload for a reason the 30-second unload timeout doesn't cover, a step
  that throws) should abort itself, restore the saved player position and
  flags, and say so - rather than leaving the screen spinning on a job
  that will never advance.
- Cancel finishes the current chunk cleanly, which is right, but there's no
  second press that means "stop now, I'll take the mess" for when the
  clean path is itself what's wedged.
- None of this covers a hard client kill mid-regen, which still strands the
  player at the staging position. That needs the persisted-job work in the
  gaps list below.

## Other known gaps

- Retrogen wrapper only has Mekanism so far (see RetrogenIntegrations).
- SavedChunkReader decodes the on-disk chunk NBT by hand rather than
  calling a vanilla deserializer, because the only shared entry point
  across all six targets needs a PoiManager and has side effects. It reads
  the packed block and biome palettes directly. The container and the
  packed-long encoding have held since 1.18 (DataVersion 2825, which it
  checks), but the palette entries have not: 26.3 made default block states
  serialize as bare strings and moved the rest from "Name" to "id", which
  this reader assumed away and which showed up as solid black chunks with no
  biome or ore data. It now handles all four shapes. The lesson is that a
  format change here surfaces as a silently wrong map rather than a crash,
  so palette shape is worth re-checking against a real save on each new MC
  version rather than assumed.
- The chunk filter can only search chunks the map has already read, since
  nothing else has been looked inside. Searching a whole save would mean
  walking every region file on disk, which wants a background index rather
  than a GUI doing it inline.
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
