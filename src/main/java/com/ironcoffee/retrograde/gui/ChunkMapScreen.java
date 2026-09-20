package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkTracker;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Minimal chunk-status map: a grid of squares centered on the player,
 * color-coded TOUCHED (green) / UNKNOWN (gray). No selection, no regen —
 * that's follow-up work; this is purely the "can I see chunk state at all"
 * slice.
 *
 * Singleplayer only for now: reads chunk status via the local integrated
 * server directly (Minecraft#getSingleplayerServer). A remote multiplayer
 * server has no such local access — the client would need to ask the
 * server over the network for this, which doesn't exist yet.
 *
 * Rendering only implemented for <26 right now: 26.x replaced Screen's
 * render(GuiGraphics, int, int, float) with an extractRenderState(...)-based
 * pipeline (a real, fairly deep rendering architecture change, not a simple
 * rename) that needs dedicated research to port correctly rather than a
 * guess. On >=26 this screen falls back to Screen's own default rendering
 * (just the background, no grid) until that's done — an honest placeholder
 * rather than code that pretends to work.
 */
public class ChunkMapScreen extends Screen {
	private static final int GRID_RADIUS_CHUNKS = 8; // 17x17 grid
	private static final int CELL_SIZE = 16;
	private static final int COLOR_UNKNOWN = 0xFF505050;
	private static final int COLOR_TOUCHED = 0xFF3D8B3D;
	private static final int COLOR_PLAYER = 0xFFE0C040;
	private static final int COLOR_GRIDLINE = 0xFF202020;

	public ChunkMapScreen() {
		super(Component.translatable("gui.retrograde.chunk_map.title"));
	}

	@Override
	protected void init() {
		super.init();
	}

	//? if <26 {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) {
			guiGraphics.drawCenteredString(
				font,
				Component.translatable("gui.retrograde.chunk_map.singleplayer_only"),
				width / 2, height / 2, 0xFF8080);
			return;
		}

		var player = minecraft.player;
		if (player == null) {
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) {
			return;
		}

		ChunkTracker tracker = ChunkTracker.forServer(server);
		ChunkPos centerChunk = new ChunkPos(player.blockPosition());

		int gridPixelSize = (GRID_RADIUS_CHUNKS * 2 + 1) * CELL_SIZE;
		int originX = (width - gridPixelSize) / 2;
		int originY = (height - gridPixelSize) / 2;

		for (int dz = -GRID_RADIUS_CHUNKS; dz <= GRID_RADIUS_CHUNKS; dz++) {
			for (int dx = -GRID_RADIUS_CHUNKS; dx <= GRID_RADIUS_CHUNKS; dx++) {
				ChunkPos pos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
				int cellX = originX + (dx + GRID_RADIUS_CHUNKS) * CELL_SIZE;
				int cellY = originY + (dz + GRID_RADIUS_CHUNKS) * CELL_SIZE;

				boolean isPlayerChunk = dx == 0 && dz == 0;
				int color = isPlayerChunk
					? COLOR_PLAYER
					: (tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED ? COLOR_TOUCHED : COLOR_UNKNOWN);

				guiGraphics.fill(cellX + 1, cellY + 1, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1, color);
				guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + 1, COLOR_GRIDLINE);
				guiGraphics.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE, COLOR_GRIDLINE);
			}
		}

		guiGraphics.drawString(font,
			Component.translatable("gui.retrograde.chunk_map.legend", GRID_RADIUS_CHUNKS * 2 + 1),
			originX, originY + gridPixelSize + 8, 0xA0A0A0);
	}
	//?}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
