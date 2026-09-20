package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.chunk.ChunkTerrainSample;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.regen.ChunkRegenService;
import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import com.ironcoffee.retrograde.retrogen.RetrogenService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real top-down chunk map: one screen pixel per block, colored the same way
 * vanilla's own held map item colors terrain (see ChunkTerrainSample), with
 * chunk grid lines and a translucent tint over touched/player chunks so
 * status stays visible on top of real terrain. Click a cell to regenerate
 * that chunk; shift-click to undo the most recent regen; ctrl-click to hand
 * it to another mod's own retrogen if one's installed. Hovering a loaded
 * chunk shows its ore tally.
 *
 * Regenerating a chunk with recorded player changes asks for a stronger
 * confirmation than an untouched one, this is genuinely destructive and
 * shouldn't be one click away from looking the same as a no-op.
 *
 * Terrain and ore sampling both read live chunk data, which only
 * ServerChunkCache#getChunkNow will actually return when called from the
 * server's own thread; it silently returns null from any other thread,
 * including this screen's render loop. Everything that touches chunk data
 * goes through requestAsync below, which dispatches the read onto the
 * server via MinecraftServer#execute and caches the result once it lands,
 * instead of reading it straight off the render thread.
 *
 * Singleplayer only: reads chunk status via the local integrated server.
 * A remote server would need a network round trip that doesn't exist yet.
 * The grid only covers chunks the server has actually loaded around the
 * player, panning to see already-explored-but-unloaded terrain would mean
 * reading saved chunk data straight from disk instead of live chunks - not
 * done yet.
 *
 * Two render paths: 26.x replaced Screen's render(GuiGraphics, ...) with
 * extractRenderState(GuiGraphicsExtractor, ...), a real rendering pipeline
 * change, not a rename. fill(...) kept its signature; drawCenteredString /
 * drawString became centeredText / text, and tooltips moved from an
 * immediate renderComponentTooltip(...) call to a retained
 * setComponentTooltipForNextFrame(...) one.
 */
public class ChunkMapScreen extends Screen {
	private static final int GRID_RADIUS_CHUNKS = 6; // 13x13 chunks, one screen pixel per block
	private static final int CELL_SIZE = ChunkTerrainSample.SIZE;
	private static final int COLOR_UNLOADED = 0xFF303030;
	private static final int COLOR_PENDING = 0xFF404040;
	private static final int COLOR_TOUCHED_TINT = 0x503D8B3D;
	private static final int COLOR_PLAYER_TINT = 0x80E0C040;
	private static final int COLOR_GRIDLINE = 0x50000000;
	private static final int MAX_TOOLTIP_ORES = 6;

	private final Map<ChunkPos, int[]> terrainCache = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pendingTerrain = ConcurrentHashMap.newKeySet();
	private final Map<ChunkPos, Optional<List<ChunkResourceInfo.Entry>>> resourceCache = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pendingResources = ConcurrentHashMap.newKeySet();

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
				drawCell(guiGraphics, server, serverLevel, dimension, tracker, pos, cellX, cellY, dx == 0 && dz == 0);
			}
		}

		guiGraphics.drawString(font,
			Component.translatable("gui.retrograde.chunk_map.legend"),
			originX, originY + gridPixelSize + 8, 0xA0A0A0);

		int[] hovered = hoveredCellOffset(mouseX, mouseY);
		if (hovered != null) {
			ChunkPos hoveredPos = new ChunkPos(centerChunk.x + hovered[0], centerChunk.z + hovered[1]);
			List<Component> tooltip = buildTooltip(server, dimension, tracker, hoveredPos, hovered[0] == 0 && hovered[1] == 0);
			guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawCell(GuiGraphics guiGraphics, MinecraftServer server, ServerLevel serverLevel, ResourceKey<Level> dimension, ChunkTracker tracker, ChunkPos pos, int cellX, int cellY, boolean isPlayerChunk) {
		int[] terrain = requestTerrain(server, serverLevel, pos);
		if (terrain == null) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_PENDING);
		} else if (terrain.length == 0) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_UNLOADED);
		} else {
			for (int lz = 0; lz < CELL_SIZE; lz++) {
				for (int lx = 0; lx < CELL_SIZE; lx++) {
					int color = terrain[lz * CELL_SIZE + lx];
					guiGraphics.fill(cellX + lx, cellY + lz, cellX + lx + 1, cellY + lz + 1, color);
				}
			}
		}

		if (isPlayerChunk) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_PLAYER_TINT);
		} else if (tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_TOUCHED_TINT);
		}

		guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + 1, COLOR_GRIDLINE);
		guiGraphics.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE, COLOR_GRIDLINE);
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
				drawCell(guiGraphics, server, serverLevel, dimension, tracker, pos, cellX, cellY, dx == 0 && dz == 0);
			}
		}

		guiGraphics.text(font,
			Component.translatable("gui.retrograde.chunk_map.legend"),
			originX, originY + gridPixelSize + 8, 0xA0A0A0);

		int[] hovered = hoveredCellOffset(mouseX, mouseY);
		if (hovered != null) {
			ChunkPos hoveredPos = new ChunkPos(centerChunk.x() + hovered[0], centerChunk.z() + hovered[1]);
			List<Component> tooltip = buildTooltip(server, dimension, tracker, hoveredPos, hovered[0] == 0 && hovered[1] == 0);
			guiGraphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawCell(GuiGraphicsExtractor guiGraphics, MinecraftServer server, ServerLevel serverLevel, ResourceKey<Level> dimension, ChunkTracker tracker, ChunkPos pos, int cellX, int cellY, boolean isPlayerChunk) {
		int[] terrain = requestTerrain(server, serverLevel, pos);
		if (terrain == null) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_PENDING);
		} else if (terrain.length == 0) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_UNLOADED);
		} else {
			for (int lz = 0; lz < CELL_SIZE; lz++) {
				for (int lx = 0; lx < CELL_SIZE; lx++) {
					int color = terrain[lz * CELL_SIZE + lx];
					guiGraphics.fill(cellX + lx, cellY + lz, cellX + lx + 1, cellY + lz + 1, color);
				}
			}
		}

		if (isPlayerChunk) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_PLAYER_TINT);
		} else if (tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED) {
			guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, COLOR_TOUCHED_TINT);
		}

		guiGraphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + 1, COLOR_GRIDLINE);
		guiGraphics.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE, COLOR_GRIDLINE);
	}
	*///?}

	/**
	 * Cached 16x16 ARGB sample for this chunk: null if not requested yet
	 * (and a request has just been kicked off), zero-length if the server
	 * has confirmed the chunk isn't loaded, or the real sample otherwise.
	 */
	private int[] requestTerrain(MinecraftServer server, ServerLevel serverLevel, ChunkPos pos) {
		int[] cached = terrainCache.get(pos);
		if (cached != null) {
			return cached;
		}
		if (pendingTerrain.add(pos)) {
			server.execute(() -> {
				int[] result = ChunkTerrainSample.sample(serverLevel, pos);
				terrainCache.put(pos, result == null ? new int[0] : result);
				pendingTerrain.remove(pos);
			});
		}
		return null;
	}

	/** Grid offset {dx, dz} from the player's chunk under the cursor, or null if outside the grid. */
	private int[] hoveredCellOffset(int mouseX, int mouseY) {
		int gridPixelSize = (GRID_RADIUS_CHUNKS * 2 + 1) * CELL_SIZE;
		int originX = (width - gridPixelSize) / 2;
		int originY = (height - gridPixelSize) / 2;

		int cellCol = (int) Math.floor((mouseX - originX) / (double) CELL_SIZE);
		int cellRow = (int) Math.floor((mouseY - originY) / (double) CELL_SIZE);
		if (cellCol < 0 || cellCol > GRID_RADIUS_CHUNKS * 2 || cellRow < 0 || cellRow > GRID_RADIUS_CHUNKS * 2) {
			return null;
		}
		return new int[] { cellCol - GRID_RADIUS_CHUNKS, cellRow - GRID_RADIUS_CHUNKS };
	}

	private List<Component> buildTooltip(MinecraftServer server, ResourceKey<Level> dimension, ChunkTracker tracker, ChunkPos pos, boolean isPlayerChunk) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("(" + chunkX(pos) + ", " + chunkZ(pos) + ")"));
		if (isPlayerChunk) {
			lines.add(Component.translatable("gui.retrograde.chunk_map.tooltip.you"));
		} else {
			ChunkTracker.Status status = tracker.statusOf(dimension, pos);
			lines.add(Component.translatable(status == ChunkTracker.Status.TOUCHED
				? "gui.retrograde.chunk_map.tooltip.touched"
				: "gui.retrograde.chunk_map.tooltip.unknown"));
		}

		Optional<List<ChunkResourceInfo.Entry>> cached = requestResources(server, dimension, pos);
		if (cached == null) {
			lines.add(Component.translatable("gui.retrograde.chunk_map.tooltip.not_loaded"));
		} else if (cached.isEmpty()) {
			lines.add(Component.translatable("gui.retrograde.chunk_map.tooltip.not_loaded"));
		} else if (cached.get().isEmpty()) {
			lines.add(Component.translatable("gui.retrograde.chunk_map.tooltip.no_ores"));
		} else {
			List<ChunkResourceInfo.Entry> resources = cached.get();
			int shown = Math.min(resources.size(), MAX_TOOLTIP_ORES);
			for (int i = 0; i < shown; i++) {
				ChunkResourceInfo.Entry entry = resources.get(i);
				lines.add(Component.literal(entry.count() + "x ").append(entry.block().getName()));
			}
		}
		return lines;
	}

	/** Same not-yet-known vs. confirmed-empty distinction as requestTerrain, wrapped in Optional since the resolved value can itself be null (chunk not loaded). */
	private Optional<List<ChunkResourceInfo.Entry>> requestResources(MinecraftServer server, ResourceKey<Level> dimension, ChunkPos pos) {
		Optional<List<ChunkResourceInfo.Entry>> cached = resourceCache.get(pos);
		if (cached != null) {
			return cached;
		}
		if (pendingResources.add(pos)) {
			server.execute(() -> {
				ServerLevel serverLevel = server.getLevel(dimension);
				List<ChunkResourceInfo.Entry> result = serverLevel == null ? null : ChunkResourceInfo.scan(serverLevel.getChunkSource(), pos);
				resourceCache.put(pos, Optional.ofNullable(result));
				pendingResources.remove(pos);
			});
		}
		return null;
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

	// 26.x reworked input handling: mouseClicked(double, double, int) became
	// mouseClicked(MouseButtonEvent, boolean), and Screen.hasShiftDown()/
	// hasControlDown() moved onto the event (MouseButtonEvent implements
	// InputWithModifiers). Both eras funnel into handleClick below so the
	// picking logic isn't duplicated.
	//? if >=26 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (handleClick(event.x(), event.y(), event.button(), event.hasShiftDown(), event.hasControlDown())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
	*///?} else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (handleClick(mouseX, mouseY, button, Screen.hasShiftDown(), Screen.hasControlDown())) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	//?}

	private boolean handleClick(double mouseX, double mouseY, int button, boolean shiftDown, boolean ctrlDown) {
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

		int[] hovered = hoveredCellOffset((int) mouseX, (int) mouseY);
		if (hovered == null || (hovered[0] == 0 && hovered[1] == 0)) {
			return false;
		}

		//? if >=26 {
		/*ChunkPos centerChunk = ChunkPos.containing(player.blockPosition());
		ChunkPos target = new ChunkPos(centerChunk.x() + hovered[0], centerChunk.z() + hovered[1]);
		*///?} else {
		ChunkPos centerChunk = new ChunkPos(player.blockPosition());
		ChunkPos target = new ChunkPos(centerChunk.x + hovered[0], centerChunk.z + hovered[1]);
		//?}

		if (ctrlDown) {
			openRetrogenScreen(server, player.getUUID(), target);
		} else {
			confirmRegenAction(server, serverLevel, dimension, target, shiftDown);
		}
		return true;
	}

	private void openRetrogenScreen(MinecraftServer server, java.util.UUID playerId, ChunkPos target) {
		List<RetrogenIntegration> integrations = RetrogenService.available();
		if (integrations.isEmpty()) {
			var player = minecraft.player;
			if (player != null) {
				//? if >=26 {
				/*player.sendOverlayMessage(Component.translatable("gui.retrograde.retrogen.none_available"));
				*///?} else {
				player.displayClientMessage(Component.translatable("gui.retrograde.retrogen.none_available"), true);
				//?}
			}
			return;
		}
		openScreen(new RetrogenScreen(this, server, playerId, target, integrations));
	}

	private void confirmRegenAction(MinecraftServer server, ServerLevel serverLevel, ResourceKey<Level> dimension, ChunkPos target, boolean undo) {
		if (undo && !ChunkRegenService.hasUndo(serverLevel, target)) {
			return;
		}
		boolean touched = !undo && ChunkTracker.forServer(server).statusOf(dimension, target) == ChunkTracker.Status.TOUCHED;
		String titleKey = undo
			? "gui.retrograde.confirm_undo"
			: (touched ? "gui.retrograde.confirm_regen_touched" : "gui.retrograde.confirm_regen");
		String detailKey = undo
			? "gui.retrograde.confirm_regen.detail"
			: (touched ? "gui.retrograde.confirm_regen_touched.detail" : "gui.retrograde.confirm_regen.detail");
		int targetX = chunkX(target);
		int targetZ = chunkZ(target);
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
			Component.translatable(detailKey)
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
