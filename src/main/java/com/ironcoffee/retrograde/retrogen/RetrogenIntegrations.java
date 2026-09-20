package com.ironcoffee.retrograde.retrogen;

import java.util.List;

/**
 * Known mods with their own retrogen command, keyed by mod id. Only
 * Mekanism is verified here (its /mek retrogen <x> <z> command, confirmed
 * against its actual 1.20.x source rather than guessed), since it's the
 * only one checked so far. Add more the same way: a mod id to gate on and
 * a function building that mod's own command string for a chunk.
 */
public final class RetrogenIntegrations {
	public static final List<RetrogenIntegration> KNOWN = List.of(
		new RetrogenIntegration(
			"mekanism",
			"Mekanism",
			pos -> "mek retrogen " + pos.getMinBlockX() + " " + pos.getMinBlockZ()
		)
	);

	private RetrogenIntegrations() {}
}
