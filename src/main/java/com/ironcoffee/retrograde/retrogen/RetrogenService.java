package com.ironcoffee.retrograde.retrogen;

import com.ironcoffee.retrograde.Main;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
//? if >=26 {
/*import net.minecraft.server.permissions.PermissionSet;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a known mod's own retrogen command for a single chunk, through the
 * normal command dispatcher rather than touching that mod's internals or
 * config. Uses the player's own command source (so success/failure
 * feedback lands in their chat like any other command) with permission
 * bumped to op level, since our map screen is already the actual gate on
 * who can touch this.
 */
public final class RetrogenService {
	private RetrogenService() {}

	public static List<RetrogenIntegration> available() {
		List<RetrogenIntegration> result = new ArrayList<>();
		for (RetrogenIntegration integration : RetrogenIntegrations.KNOWN) {
			if (Main.platform().isModLoaded(integration.modId())) {
				result.add(integration);
			}
		}
		return result;
	}

	public static void run(MinecraftServer server, ServerPlayer player, RetrogenIntegration integration, ChunkPos pos) {
		String command = integration.commandFor().apply(pos);
		//? if >=26 {
		/*CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
		*///?} else {
		CommandSourceStack source = player.createCommandSourceStack().withPermission(4);
		//?}
		try {
			ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
			server.getCommands().performCommand(parsed, command);
		} catch (Exception e) {
			Main.LOGGER.error("[{}] Failed running {}'s retrogen command '{}'", Main.MOD_ID, integration.displayName(), command, e);
			source.sendFailure(net.minecraft.network.chat.Component.translatable("gui.retrograde.retrogen.dispatch_failed", integration.displayName()));
		}
	}
}
