package com.ironcoffee.retrograde.retrogen;

import net.minecraft.world.level.ChunkPos;

import java.util.function.Function;

/**
 * A known mod's own retrogen command, wrapped so it can be run for a single
 * chunk from our map screen instead of the mod's own command line syntax.
 * We don't touch the mod's internals or config files, just its normal
 * player-facing command, run with elevated permission through the command
 * dispatcher (see RetrogenService).
 */
public record RetrogenIntegration(String modId, String displayName, Function<ChunkPos, String> commandFor) {
}
