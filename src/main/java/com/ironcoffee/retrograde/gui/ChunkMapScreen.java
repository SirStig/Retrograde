package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.regen.ChunkRegenService;
import net.minecraft.client.gui.screens.ConfirmScreen;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Chunk-status map: grid of squares centered on the player, color-coded
 * TOUCHED (green) / UNKNOWN (gray). Click a cell to regenerate that chunk;
 * shift-click to undo the most recent regen (see ChunkRegenService for why
 * regen needs the player to step away first).
 *
 * Singleplayer only: reads chunk status via the local integrated server.
 * A remote server would need a network round trip that doesn't exist yet.
 *
 * Two render paths: 26.x replaced Screen's render(GuiGraphics, ...) with
 * extractRenderState(GuiGraphicsExtractor, ...), a real rendering pipeline
 * change, not a rename. fill(...) kept its signature; drawCenteredString /
 * drawString became centeredText / text.
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

	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.centeredText(font, title, width / 2, 12, 0xFFFFFF);

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) {
			guiGraphics.centeredText(
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
		ChunkPos centerChunk = ChunkPos.containing(player.blockPosition());

		int gridPixelSize = (GRID_RADIUS_CHUNKS * 2 + 1) * CELL_SIZE;
		int originX = (width - gridPixelSize) / 2;
		int originY = (height - gridPixelSize) / 2;

		for (int dz = -GRID_RADIUS_CHUNKS; dz <= GRID_RADIUS_CHUNKS; dz++) {
			for (int dx = -GRID_RADIUS_CHUNKS; dx <= GRID_RADIUS_CHUNKS; dx++) {
				ChunkPos pos = new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz);
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

		guiGraphics.text(font,
			Component.translatable("gui.retrograde.chunk_map.legend", GRID_RADIUS_CHUNKS * 2 + 1),
			originX, originY + gridPixelSize + 8, 0xA0A0A0);
	}
	*///?}

	// 26.x reworked input handling: mouseClicked(double, double, int) became
	// mouseClicked(MouseButtonEvent, boolean), and Screen.hasShiftDown()
	// moved onto the event (MouseButtonEvent implements InputWithModifiers).
	// Both eras funnel into handleClick below so the picking logic isn't duplicated.
	//? if >=26 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (handleClick(event.x(), event.y(), event.button(), event.hasShiftDown())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
	*///?} else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (handleClick(mouseX, mouseY, button, Screen.hasShiftDown())) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	//?}

	private boolean handleClick(double mouseX, double mouseY, int button, boolean shiftDown) {
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null) {
			return false;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) {
			return false;
		}

		//? if >=26 {
		/*ChunkPos centerChunk = ChunkPos.containing(player.blockPosition());
		*///?} else {
		ChunkPos centerChunk = new ChunkPos(player.blockPosition());
		//?}
		int gridPixelSize = (GRID_RADIUS_CHUNKS * 2 + 1) * CELL_SIZE;
		int originX = (width - gridPixelSize) / 2;
		int originY = (height - gridPixelSize) / 2;

		int cellCol = (int) Math.floor((mouseX - originX) / CELL_SIZE);
		int cellRow = (int) Math.floor((mouseY - originY) / CELL_SIZE);
		if (cellCol < 0 || cellCol > GRID_RADIUS_CHUNKS * 2 || cellRow < 0 || cellRow > GRID_RADIUS_CHUNKS * 2) {
			return false;
		}

		int dx = cellCol - GRID_RADIUS_CHUNKS;
		int dz = cellRow - GRID_RADIUS_CHUNKS;
		if (dx == 0 && dz == 0) {
			return false;
		}

		//? if >=26 {
		/*ChunkPos target = new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz);
		*///?} else {
		ChunkPos target = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
		//?}
		confirmRegenAction(serverLevel, target, shiftDown);
		return true;
	}

	private void confirmRegenAction(ServerLevel serverLevel, ChunkPos target, boolean undo) {
		if (undo && !ChunkRegenService.hasUndo(serverLevel, target)) {
			return;
		}
		String titleKey = undo ? "gui.retrograde.confirm_undo" : "gui.retrograde.confirm_regen";
		//? if >=26 {
		/*int targetX = target.x();
		int targetZ = target.z();
		*///?} else {
		int targetX = target.x;
		int targetZ = target.z;
		//?}
		openScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					ChunkRegenService.Result result = undo
						? ChunkRegenService.undo(serverLevel, target)
						: ChunkRegenService.regenerate(serverLevel, target);
					reportRegenResult(result);
				}
				openScreen(new ChunkMapScreen());
			},
			Component.translatable(titleKey, targetX, targetZ),
			Component.translatable("gui.retrograde.confirm_regen.detail")
		));
	}

	// 26.3 moved screen management off Minecraft onto its new Gui wrapper:
	// Minecraft#setScreen is gone, replaced by minecraft.gui.setScreen(...).
	// 26.1 still has it directly, same as 1.20.1.
	private void openScreen(Screen screen) {
		//? if >=26.3 {
		/*minecraft.gui.setScreen(screen);
		*///?} else {
		minecraft.setScreen(screen);
		//?}
	}

	private void reportRegenResult(ChunkRegenService.Result result) {
		var player = minecraft.player;
		if (player == null) return;
		Component message = switch (result) {
			case OK -> Component.translatable("gui.retrograde.regen_result.ok");
			case PLAYER_TOO_CLOSE -> Component.translatable("gui.retrograde.regen_result.too_close");
			case NOTHING_TO_UNDO -> Component.translatable("gui.retrograde.regen_result.nothing_to_undo");
			case IO_ERROR -> Component.translatable("gui.retrograde.regen_result.io_error");
		};
		//? if >=26 {
		/*player.sendOverlayMessage(message);
		*///?} else {
		player.displayClientMessage(message, true);
		//?}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
