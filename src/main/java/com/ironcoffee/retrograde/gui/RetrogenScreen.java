package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import com.ironcoffee.retrograde.retrogen.RetrogenService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.UUID;

/**
 * Lists installed mods that have a known retrogen integration (see
 * RetrogenIntegrations) and lets you run one for a specific chunk. Reached
 * by ctrl-clicking a cell on the chunk map.
 */
public class RetrogenScreen extends Screen {
	private final Screen parent;
	private final MinecraftServer server;
	private final UUID playerId;
	private final ChunkPos target;
	private final List<RetrogenIntegration> integrations;

	public RetrogenScreen(Screen parent, MinecraftServer server, UUID playerId, ChunkPos target, List<RetrogenIntegration> integrations) {
		super(Component.translatable("gui.retrograde.retrogen.title", chunkX(target), chunkZ(target)));
		this.parent = parent;
		this.server = server;
		this.playerId = playerId;
		this.target = target;
		this.integrations = integrations;
	}

	@Override
	protected void init() {
		super.init();
		int y = height / 2 - (integrations.size() * 24 + 10) / 2;
		for (RetrogenIntegration integration : integrations) {
			addRenderableWidget(Button.builder(Component.literal(integration.displayName()), btn -> confirm(integration))
				.bounds(width / 2 - 100, y, 200, 20)
				.build());
			y += 24;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
			.bounds(width / 2 - 100, y + 6, 200, 20)
			.build());
	}

	private void confirm(RetrogenIntegration integration) {
		openScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					ServerPlayer player = server.getPlayerList().getPlayer(playerId);
					if (player != null) {
						RetrogenService.run(server, player, integration, target);
					}
				}
				openScreen(parent);
			},
			Component.translatable("gui.retrograde.retrogen.confirm", integration.displayName(), chunkX(target), chunkZ(target)),
			Component.translatable("gui.retrograde.retrogen.confirm.detail")
		));
	}

	@Override
	public void onClose() {
		openScreen(parent);
	}

	// 26.3 moved screen management off Minecraft onto its new Gui wrapper:
	// Minecraft#setScreen is gone, replaced by minecraft.gui.setScreen(...).
	// 26.1 still has it directly, same as 1.20.1. (Duplicated from
	// ChunkMapScreen since it's a two-line Stonecutter branch either way.)
	private void openScreen(Screen screen) {
		//? if >=26.3 {
		/*minecraft.gui.setScreen(screen);
		*///?} else {
		minecraft.setScreen(screen);
		//?}
	}

	private static int chunkX(ChunkPos pos) {
		//? if >=26 {
		/*return pos.x();
		*///?} else {
		return pos.x;
		//?}
	}

	private static int chunkZ(ChunkPos pos) {
		//? if >=26 {
		/*return pos.z();
		*///?} else {
		return pos.z;
		//?}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
