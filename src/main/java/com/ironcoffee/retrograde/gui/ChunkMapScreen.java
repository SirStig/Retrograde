package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.regen.ChunkRegenService;
import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import com.ironcoffee.retrograde.retrogen.RetrogenService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-screen, pannable, zoomable chunk map, one texture per chunk (see
 * ChunkTerrainTextureCache) blitted at whatever scale the current zoom
 * level calls for - one draw call per chunk regardless of zoom, unlike the
 * old fixed small grid which drew one fill() per block and could only
 * afford a handful of chunks on screen at once.
 *
 * Drag to pan, scroll to zoom (toward the cursor, like Xaero/JourneyMap).
 * Click a chunk to regenerate it (touched chunks get a stronger warning);
 * shift-click to undo the most recent regen; ctrl-click to hand it to
 * another mod's own retrogen if one's installed. A click only fires if the
 * mouse didn't move far enough to count as a drag. Hovering a loaded chunk
 * shows its ore tally.
 *
 * Right-side panel: recenter on the player, zoom in/out, close. Header bar
 * up top just holds the title, kept separate from the panel so neither
 * competes with the map for space.
 *
 * Singleplayer only: reads chunk status via the local integrated server.
 * A remote server would need a network round trip that doesn't exist yet.
 * Panning past the server's loaded radius shows those chunks as unloaded -
 * this only ever reads live chunk data, not saved-but-unloaded terrain (see
 * ROADMAP.md).
 *
 * Two render paths: 26.x replaced Screen's render(GuiGraphics, ...) with
 * extractRenderState(GuiGraphicsExtractor, ...), a real rendering pipeline
 * change, not a rename, and blit()'s parameter shape changed with it (goes
 * through a RenderPipeline now, and takes destination corners + UV
 * fractions instead of destination size + pixel UV offsets). The chunk
 * visibility math and everything that isn't a direct draw call is shared
 * between both (see the private helpers below render/extractRenderState);
 * only the actual fill/blit/text calls are duplicated.
 */
public class ChunkMapScreen extends Screen {
	private static final int CHUNK_BLOCKS = 16;
	private static final int HEADER_HEIGHT = 24;
	private static final int PANEL_WIDTH = 64;
	private static final int PANEL_MARGIN = 6;
	private static final int MIN_ZOOM = -2;
	private static final int MAX_ZOOM = 4;
	private static final int DRAG_THRESHOLD = 5;
	private static final int MAX_TOOLTIP_ORES = 6;

	private static final int COLOR_HEADER_BG = 0xE0202020;
	private static final int COLOR_PANEL_BG = 0xC0202020;
	private static final int COLOR_PENDING = 0xFF404040;
	private static final int COLOR_UNLOADED = 0xFF303030;
	private static final int COLOR_TOUCHED_TINT = 0x503D8B3D;
	private static final int COLOR_PLAYER_TINT = 0x80E0C040;
	private static final int COLOR_GRIDLINE = 0x50000000;

	private final ChunkTerrainTextureCache textureCache = new ChunkTerrainTextureCache();
	private final Map<ChunkPos, Optional<List<ChunkResourceInfo.Entry>>> resourceCache = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pendingResources = ConcurrentHashMap.newKeySet();

	private double cameraBlockX;
	private double cameraBlockZ;
	private int zoomLevel = 0;
	private boolean draggingCamera;
	private double dragTotalDistance;

	public ChunkMapScreen() {
		super(Component.translatable("gui.retrograde.chunk_map.title"));
	}

	@Override
	protected void init() {
		super.init();
		recenterOnPlayer();

		int panelX = width - PANEL_WIDTH - PANEL_MARGIN;
		int y = HEADER_HEIGHT + PANEL_MARGIN;
		addRenderableWidget(Button.builder(Component.translatable("gui.retrograde.chunk_map.recenter"), b -> recenterOnPlayer())
			.bounds(panelX, y, PANEL_WIDTH, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustZoom(1))
			.bounds(panelX, y, PANEL_WIDTH, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustZoom(-1))
			.bounds(panelX, y, PANEL_WIDTH, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.translatable("gui.retrograde.chunk_map.close"), b -> onClose())
			.bounds(panelX, y, PANEL_WIDTH, 20).build());
	}

	@Override
	public void removed() {
		super.removed();
		textureCache.close();
	}

	private void recenterOnPlayer() {
		var player = minecraft == null ? null : minecraft.player;
		if (player == null) return;
		cameraBlockX = player.getX();
		cameraBlockZ = player.getZ();
	}

	private void adjustZoom(int delta) {
		setZoom(zoomLevel + delta, mapCenterX(), mapCenterY());
	}

	private void setZoom(int newZoomLevel, double pivotScreenX, double pivotScreenY) {
		newZoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoomLevel));
		if (newZoomLevel == zoomLevel) return;
		double worldXAtPivot = screenToBlockX(pivotScreenX);
		double worldZAtPivot = screenToBlockZ(pivotScreenY);
		zoomLevel = newZoomLevel;
		cameraBlockX = worldXAtPivot - (pivotScreenX - mapCenterX()) / pixelsPerBlock();
		cameraBlockZ = worldZAtPivot - (pivotScreenY - mapCenterY()) / pixelsPerBlock();
	}

	private double pixelsPerBlock() {
		return zoomLevel >= 0 ? (1 << zoomLevel) : 1.0 / (1 << -zoomLevel);
	}

	private double mapCenterX() {
		return (width - PANEL_WIDTH - PANEL_MARGIN * 2) / 2.0;
	}

	private double mapCenterY() {
		return HEADER_HEIGHT + (height - HEADER_HEIGHT) / 2.0;
	}

	private double screenToBlockX(double screenX) {
		return cameraBlockX + (screenX - mapCenterX()) / pixelsPerBlock();
	}

	private double screenToBlockZ(double screenY) {
		return cameraBlockZ + (screenY - mapCenterY()) / pixelsPerBlock();
	}

	private double blockToScreenX(double blockX) {
		return mapCenterX() + (blockX - cameraBlockX) * pixelsPerBlock();
	}

	private double blockToScreenY(double blockZ) {
		return mapCenterY() + (blockZ - cameraBlockZ) * pixelsPerBlock();
	}

	private boolean isOverMap(double mouseX, double mouseY) {
		return mouseX < width - PANEL_WIDTH - PANEL_MARGIN * 2 && mouseY > HEADER_HEIGHT;
	}

	private ChunkPos chunkAt(double screenX, double screenY) {
		int blockX = (int) Math.floor(screenToBlockX(screenX));
		int blockZ = (int) Math.floor(screenToBlockZ(screenY));
		return new ChunkPos(Math.floorDiv(blockX, CHUNK_BLOCKS), Math.floorDiv(blockZ, CHUNK_BLOCKS));
	}

	private record VisibleChunk(ChunkPos pos, int screenX, int screenY, int size) {}

	private List<VisibleChunk> computeVisibleChunks() {
		int mapWidth = width - PANEL_WIDTH - PANEL_MARGIN * 2;
		int mapHeight = height - HEADER_HEIGHT;
		double ppb = pixelsPerBlock();
		int chunkPixelSize = Math.max(1, (int) Math.round(CHUNK_BLOCKS * ppb));

		int minChunkX = Math.floorDiv((int) Math.floor(screenToBlockX(0)), CHUNK_BLOCKS) - 1;
		int maxChunkX = Math.floorDiv((int) Math.ceil(screenToBlockX(mapWidth)), CHUNK_BLOCKS) + 1;
		int minChunkZ = Math.floorDiv((int) Math.floor(screenToBlockZ(HEADER_HEIGHT)), CHUNK_BLOCKS) - 1;
		int maxChunkZ = Math.floorDiv((int) Math.ceil(screenToBlockZ(HEADER_HEIGHT + mapHeight)), CHUNK_BLOCKS) + 1;

		List<VisibleChunk> result = new ArrayList<>();
		for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
			for (int cx = minChunkX; cx <= maxChunkX; cx++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				int sx = (int) Math.round(blockToScreenX(cx * (double) CHUNK_BLOCKS));
				int sy = (int) Math.round(blockToScreenY(cz * (double) CHUNK_BLOCKS));
				result.add(new VisibleChunk(pos, sx, sy, chunkPixelSize));
			}
		}
		return result;
	}

	//? if <26 {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) {
			guiGraphics.drawCenteredString(font, Component.translatable("gui.retrograde.chunk_map.singleplayer_only"), width / 2, height / 2, 0xFF8080);
			return;
		}
		var player = minecraft.player;
		if (player == null) return;
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) return;
		ChunkTracker tracker = ChunkTracker.forServer(server);

		guiGraphics.fill(0, HEADER_HEIGHT, width, height, 0xFF101010);
		for (VisibleChunk chunk : computeVisibleChunks()) {
			textureCache.request(minecraft, server, serverLevel, chunk.pos());
			ChunkTerrainTextureCache.Status status = textureCache.statusOf(chunk.pos());
			if (status == ChunkTerrainTextureCache.Status.READY) {
				ResourceLocation texture = textureCache.textureOf(chunk.pos());
				guiGraphics.blit(texture, chunk.screenX(), chunk.screenY(), chunk.size(), chunk.size(), 0.0F, 0.0F, CHUNK_BLOCKS, CHUNK_BLOCKS, CHUNK_BLOCKS, CHUNK_BLOCKS);
			} else {
				int color = status == ChunkTerrainTextureCache.Status.UNLOADED ? COLOR_UNLOADED : COLOR_PENDING;
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), color);
			}

			boolean isPlayerChunk = chunk.pos().equals(new ChunkPos(player.blockPosition()));
			if (isPlayerChunk) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), COLOR_PLAYER_TINT);
			} else if (tracker.statusOf(dimension, chunk.pos()) == ChunkTracker.Status.TOUCHED) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), COLOR_TOUCHED_TINT);
			}

			if (chunk.size() >= CHUNK_BLOCKS) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + 1, COLOR_GRIDLINE);
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + 1, chunk.screenY() + chunk.size(), COLOR_GRIDLINE);
			}
		}

		guiGraphics.fill(0, 0, width, HEADER_HEIGHT, COLOR_HEADER_BG);
		guiGraphics.drawCenteredString(font, title, width / 2, (HEADER_HEIGHT - 8) / 2, 0xFFFFFF);
		guiGraphics.fill(width - PANEL_WIDTH - PANEL_MARGIN * 2, HEADER_HEIGHT, width, height, COLOR_PANEL_BG);

		if (isOverMap(mouseX, mouseY)) {
			ChunkPos hovered = chunkAt(mouseX, mouseY);
			List<Component> tooltip = buildTooltip(server, player, dimension, tracker, hovered);
			guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
		}
	}
	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) {
			guiGraphics.centeredText(font, Component.translatable("gui.retrograde.chunk_map.singleplayer_only"), width / 2, height / 2, 0xFF8080);
			return;
		}
		var player = minecraft.player;
		if (player == null) return;
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) return;
		ChunkTracker tracker = ChunkTracker.forServer(server);

		guiGraphics.fill(0, HEADER_HEIGHT, width, height, 0xFF101010);
		for (VisibleChunk chunk : computeVisibleChunks()) {
			textureCache.request(minecraft, server, serverLevel, chunk.pos());
			ChunkTerrainTextureCache.Status status = textureCache.statusOf(chunk.pos());
			if (status == ChunkTerrainTextureCache.Status.READY) {
				ResourceLocation texture = textureCache.textureOf(chunk.pos());
				guiGraphics.blit(texture, chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), 0.0F, 1.0F, 0.0F, 1.0F);
			} else {
				int color = status == ChunkTerrainTextureCache.Status.UNLOADED ? COLOR_UNLOADED : COLOR_PENDING;
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), color);
			}

			boolean isPlayerChunk = chunk.pos().equals(ChunkPos.containing(player.blockPosition()));
			if (isPlayerChunk) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), COLOR_PLAYER_TINT);
			} else if (tracker.statusOf(dimension, chunk.pos()) == ChunkTracker.Status.TOUCHED) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + chunk.size(), COLOR_TOUCHED_TINT);
			}

			if (chunk.size() >= CHUNK_BLOCKS) {
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + chunk.size(), chunk.screenY() + 1, COLOR_GRIDLINE);
				guiGraphics.fill(chunk.screenX(), chunk.screenY(), chunk.screenX() + 1, chunk.screenY() + chunk.size(), COLOR_GRIDLINE);
			}
		}

		guiGraphics.fill(0, 0, width, HEADER_HEIGHT, COLOR_HEADER_BG);
		guiGraphics.centeredText(font, title, width / 2, (HEADER_HEIGHT - 8) / 2, 0xFFFFFF);
		guiGraphics.fill(width - PANEL_WIDTH - PANEL_MARGIN * 2, HEADER_HEIGHT, width, height, COLOR_PANEL_BG);

		if (isOverMap(mouseX, mouseY)) {
			ChunkPos hovered = chunkAt(mouseX, mouseY);
			List<Component> tooltip = buildTooltip(server, player, dimension, tracker, hovered);
			guiGraphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}
	*///?}

	private List<Component> buildTooltip(MinecraftServer server, net.minecraft.world.entity.player.Player player, ResourceKey<Level> dimension, ChunkTracker tracker, ChunkPos pos) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("(" + chunkX(pos) + ", " + chunkZ(pos) + ")"));
		boolean isPlayerChunk = pos.equals(playerChunk(player));
		if (isPlayerChunk) {
			lines.add(Component.translatable("gui.retrograde.chunk_map.tooltip.you"));
		} else {
			ChunkTracker.Status status = tracker.statusOf(dimension, pos);
			lines.add(Component.translatable(status == ChunkTracker.Status.TOUCHED
				? "gui.retrograde.chunk_map.tooltip.touched"
				: "gui.retrograde.chunk_map.tooltip.unknown"));
		}

		Optional<List<ChunkResourceInfo.Entry>> cached = requestResources(server, dimension, pos);
		if (cached == null || cached.isEmpty()) {
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

	private ChunkPos playerChunk(net.minecraft.world.entity.player.Player player) {
		//? if >=26 {
		/*return ChunkPos.containing(player.blockPosition());
		*///?} else {
		return new ChunkPos(player.blockPosition());
		//?}
	}

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

	// 26.x reworked input handling: the double/double/int mouse methods
	// became ones taking a MouseButtonEvent (with hasShiftDown()/
	// hasControlDown() on the event itself), and mouseScrolled gained a
	// second axis for horizontal scroll. Both eras funnel into the shared
	// handlePress/handleDrag/handleRelease/handleScroll below.
	//? if >=26 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		return handlePress(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
		if (handleDrag(dragX, dragY)) return true;
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (handleRelease(event.x(), event.y(), event.hasShiftDown(), event.hasControlDown())) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (handleScroll(mouseX, mouseY, scrollY)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
	*///?} else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		return handlePress(mouseX, mouseY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (handleDrag(dragX, dragY)) return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (handleRelease(mouseX, mouseY, Screen.hasShiftDown(), Screen.hasControlDown())) return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
		if (handleScroll(mouseX, mouseY, scrollDelta)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollDelta);
	}
	//?}

	private boolean handlePress(double mouseX, double mouseY) {
		if (!isOverMap(mouseX, mouseY)) return false;
		draggingCamera = true;
		dragTotalDistance = 0;
		return true;
	}

	private boolean handleDrag(double dragX, double dragY) {
		if (!draggingCamera) return false;
		cameraBlockX -= dragX / pixelsPerBlock();
		cameraBlockZ -= dragY / pixelsPerBlock();
		dragTotalDistance += Math.abs(dragX) + Math.abs(dragY);
		return true;
	}

	private boolean handleScroll(double mouseX, double mouseY, double scrollDelta) {
		if (!isOverMap(mouseX, mouseY) || scrollDelta == 0) return false;
		setZoom(zoomLevel + (scrollDelta > 0 ? 1 : -1), mouseX, mouseY);
		return true;
	}

	private boolean handleRelease(double mouseX, double mouseY, boolean shiftDown, boolean ctrlDown) {
		boolean wasDragging = draggingCamera;
		draggingCamera = false;
		if (!wasDragging) return false;
		if (dragTotalDistance > DRAG_THRESHOLD) return true;

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null) return true;
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) return true;

		ChunkPos target = chunkAt(mouseX, mouseY);
		if (target.equals(playerChunk(player))) return true;

		if (ctrlDown) {
			openRetrogenScreen(server, player.getUUID(), target);
		} else {
			confirmRegenAction(server, serverLevel, dimension, target, shiftDown);
		}
		return true;
	}

	private void openRetrogenScreen(MinecraftServer server, UUID playerId, ChunkPos target) {
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
				openScreen(this);
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
