package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.regen.ChunkRegenJob;
import com.ironcoffee.retrograde.regen.ChunkRegenService;
import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import com.ironcoffee.retrograde.retrogen.RetrogenService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Full-screen, pannable, zoomable chunk map, one texture per chunk (see
 * ChunkTerrainTextureCache) blitted at whatever scale the current zoom level
 * calls for - one draw call per chunk regardless of zoom.
 *
 * The interaction model is select-then-act, not click-to-act. Clicking a
 * chunk selects it, shift-dragging boxes in a whole region, and the panel
 * down the left side says what's selected and what can be done with it.
 * That replaces the old "click a chunk and it regenerates behind a confirm
 * dialog" flow, which could only ever do one chunk, gave no indication of
 * which chunk you were about to hit, and left you to guess whether anything
 * had happened afterwards.
 *
 * The left column is two fixed panels: chunk info up top (whatever's under
 * the cursor - coordinates, status, biome, ores) and selection actions
 * below it. Fixed because a tooltip that follows the cursor and resizes
 * itself around its contents is unreadable on a map you're dragging around,
 * and it covers the very chunks you're trying to look at.
 *
 * Acting on a selection hands off to ChunkRegenJob and a progress screen -
 * regen needs chunks unloaded, which needs the player moved out of range,
 * which takes long enough to need real feedback.
 *
 * Singleplayer only: reads chunk status via the local integrated server,
 * and the job reads its progress straight back off the server object. A
 * remote server would need a network round trip that doesn't exist yet.
 * Panning past the server's loaded radius shows those chunks as unloaded -
 * this only ever reads live chunk data, not saved-but-unloaded terrain (see
 * ROADMAP.md).
 *
 * Both render eras funnel into one draw(Painter, ...) - see Painter for why.
 */
public class ChunkMapScreen extends Screen {
	private static final int CHUNK_BLOCKS = 16;
	private static final int CHIP_MARGIN = 8;
	private static final int ICON_SIZE = 20;
	private static final int ICON_GAP = 4;
	private static final int TITLE_PAD_H = 8;
	private static final int TITLE_PAD_V = 4;
	private static final int MIN_ZOOM = -2;
	private static final int MAX_ZOOM = 4;
	private static final int DRAG_THRESHOLD = 5;

	private static final int PANEL_WIDTH = 152;
	private static final int PANEL_PAD = 6;
	private static final int LINE_H = 10;
	private static final int ORE_ROW_H = 18;
	private static final int ORE_ICON = 16;
	private static final int ORE_COLUMNS = 3;
	private static final int ORE_ROWS = 2;
	private static final int MAX_PANEL_ORES = ORE_COLUMNS * ORE_ROWS;
	private static final int ACTION_BUTTON_H = 18;
	private static final int ACTION_BUTTON_GAP = 4;
	private static final int ACTION_BUTTONS = 4;

	/** Cap on one selection, so nobody accidentally box-selects a continent. */
	private static final int MAX_SELECTION = 256;
	/** How long the panel keeps showing a one-off notice like the selection cap. */
	private static final long NOTICE_MS = 3000;

	private static final int COLOR_MAP_BG = 0xFF101010;
	private static final int COLOR_CHIP_BG = 0xE0161616;
	private static final int COLOR_CHIP_BORDER_LIGHT = 0x40FFFFFF;
	private static final int COLOR_CHIP_BORDER_DARK = 0x80000000;
	private static final int COLOR_PENDING = 0xFF404040;
	private static final int COLOR_UNLOADED = 0xFF303030;
	private static final int COLOR_TOUCHED_TINT = 0x503D8B3D;
	private static final int COLOR_PLAYER_TINT = 0x80E0C040;
	private static final int COLOR_GRIDLINE = 0x50000000;
	private static final int COLOR_SELECTED_TINT = 0x553C7DD4;
	private static final int COLOR_SELECTED_EDGE = 0xFF6FA8F0;
	private static final int COLOR_BOX_FILL = 0x303C7DD4;
	private static final int COLOR_BOX_EDGE = 0xFF9FC8FF;
	private static final int COLOR_STATUS_YOU = 0xFFE0C040;
	private static final int COLOR_STATUS_TOUCHED = 0xFF6FCF6F;
	private static final int COLOR_STATUS_UNTOUCHED = 0xFFA0A0A0;
	private static final int COLOR_HEADING = 0xFF8AA0B4;
	private static final int COLOR_BIOME_TEXT = 0xFFC0C0C0;
	private static final int COLOR_MESSAGE_TEXT = 0xFFA0A0A0;
	private static final int COLOR_DIVIDER = 0x30FFFFFF;
	private static final int COLOR_NOTICE = 0xFFD8B24C;

	private final ChunkTerrainTextureCache textureCache = new ChunkTerrainTextureCache();
	private final Map<ChunkPos, Optional<ChunkResourceInfo.Inspection>> inspectionCache = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pendingInspections = ConcurrentHashMap.newKeySet();

	/** Insertion-ordered so the job processes chunks in the order they were picked. */
	private final Set<ChunkPos> selection = new LinkedHashSet<>();

	private double cameraBlockX;
	private double cameraBlockZ;
	private int zoomLevel = 0;

	private enum DragMode { NONE, PAN, SELECT_ADD, SELECT_REMOVE }

	private DragMode dragMode = DragMode.NONE;
	private double dragTotalDistance;
	private double pressScreenX, pressScreenY;
	private double dragScreenX, dragScreenY;

	/** Last chunk the cursor was over, kept so the info panel doesn't blank out. */
	private ChunkPos focusChunk;

	/** True while switching to one of our own screens, which will come back. */
	private boolean handingOff;

	private Component controlsHint;
	private int selectedTouchedCount;
	private volatile int selectedUndoCount;
	private final AtomicInteger selectionGeneration = new AtomicInteger();
	private String notice;
	private long noticeUntil;

	private int clusterX, clusterY, clusterW, clusterH;
	private int titleX, titleY, titleW, titleH;
	private int hintX, hintY, hintW, hintH;
	private int panelX, infoPanelY, infoPanelH, actionPanelY, actionPanelH;

	private Button regenButton;
	private Button undoButton;
	private Button retrogenButton;
	private Button clearButton;

	public ChunkMapScreen() {
		super(Component.translatable("gui.retrograde.chunk_map.title"));
	}

	@Override
	protected void init() {
		super.init();
		// Set on the way out to a sub-screen, cleared on the way back in, so
		// a hand-off that never returns here can't leave it stuck on.
		handingOff = false;
		if (focusChunk == null) {
			recenterOnPlayer();
		}

		clusterW = ICON_SIZE * 4 + ICON_GAP * 3;
		clusterH = ICON_SIZE;
		clusterX = width - CHIP_MARGIN - clusterW;
		clusterY = CHIP_MARGIN;

		int x = clusterX;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustZoom(1))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.zoom_in")))
			.bounds(x, clusterY, ICON_SIZE, ICON_SIZE).build());
		x += ICON_SIZE + ICON_GAP;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustZoom(-1))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.zoom_out")))
			.bounds(x, clusterY, ICON_SIZE, ICON_SIZE).build());
		x += ICON_SIZE + ICON_GAP;
		addRenderableWidget(Button.builder(Component.literal("R"), b -> recenterOnPlayer())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.recenter")))
			.bounds(x, clusterY, ICON_SIZE, ICON_SIZE).build());
		x += ICON_SIZE + ICON_GAP;
		addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.close")))
			.bounds(x, clusterY, ICON_SIZE, ICON_SIZE).build());

		titleW = font.width(title) + TITLE_PAD_H * 2;
		titleH = font.lineHeight + TITLE_PAD_V * 2;
		titleX = (width - titleW) / 2;
		titleY = CHIP_MARGIN;

		// The full hint lists every modifier, which is more than fits across a
		// small window; drop to the three controls you can't do without
		// rather than let it run off both edges.
		controlsHint = Component.translatable("gui.retrograde.chunk_map.controls_hint");
		hintW = font.width(controlsHint) + TITLE_PAD_H * 2;
		if (hintW > width - CHIP_MARGIN * 2) {
			controlsHint = Component.translatable("gui.retrograde.chunk_map.controls_hint_short");
			hintW = font.width(controlsHint) + TITLE_PAD_H * 2;
		}
		hintH = font.lineHeight + TITLE_PAD_V * 2;
		hintX = (width - hintW) / 2;
		hintY = height - CHIP_MARGIN - hintH;

		panelX = CHIP_MARGIN;
		infoPanelY = CHIP_MARGIN;
		infoPanelH = PANEL_PAD * 2 + LINE_H * 3 + 5 + ORE_ROW_H * ORE_ROWS;
		actionPanelY = infoPanelY + infoPanelH + 6;
		actionPanelH = PANEL_PAD * 2 + LINE_H * 2 + 4
			+ ACTION_BUTTONS * ACTION_BUTTON_H + (ACTION_BUTTONS - 1) * ACTION_BUTTON_GAP;

		int buttonX = panelX + PANEL_PAD;
		int buttonW = PANEL_WIDTH - PANEL_PAD * 2;
		int buttonY = actionPanelY + PANEL_PAD + LINE_H * 2 + 4;
		regenButton = addActionButton(buttonX, buttonY, buttonW, "gui.retrograde.chunk_map.action.regen",
			"gui.retrograde.chunk_map.action.regen.tip", b -> beginRegen());
		buttonY += ACTION_BUTTON_H + ACTION_BUTTON_GAP;
		undoButton = addActionButton(buttonX, buttonY, buttonW, "gui.retrograde.chunk_map.action.undo",
			"gui.retrograde.chunk_map.action.undo.tip", b -> beginUndo());
		buttonY += ACTION_BUTTON_H + ACTION_BUTTON_GAP;
		retrogenButton = addActionButton(buttonX, buttonY, buttonW, "gui.retrograde.chunk_map.action.retrogen",
			"gui.retrograde.chunk_map.action.retrogen.tip", b -> beginRetrogen());
		buttonY += ACTION_BUTTON_H + ACTION_BUTTON_GAP;
		clearButton = addActionButton(buttonX, buttonY, buttonW, "gui.retrograde.chunk_map.action.clear",
			"gui.retrograde.chunk_map.action.clear.tip", b -> clearSelection());

		refreshSelectionStats();
	}

	private Button addActionButton(int x, int y, int w, String labelKey, String tooltipKey, Button.OnPress onPress) {
		return addRenderableWidget(Button.builder(Component.translatable(labelKey, 0), onPress)
			.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
			.bounds(x, y, w, ACTION_BUTTON_H)
			.build());
	}

	/**
	 * Every hand-off to a confirm dialog or the progress screen calls this,
	 * and those screens all come straight back here - so dropping the
	 * terrain textures on the way out would mean re-sampling the whole
	 * visible map, and staring at a grey grid, every time someone opened a
	 * dialog and thought better of it.
	 */
	@Override
	public void removed() {
		super.removed();
		if (handingOff) {
			handingOff = false;
			return;
		}
		textureCache.close(minecraft);
	}

	@Override
	public void tick() {
		super.tick();

		// If a job is somehow still running - reopened map, screen swapped out
		// from under it - get back on the progress screen, since that screen is
		// the only thing that drives the job forward.
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server != null) {
			ChunkRegenJob running = ChunkRegenJob.active(server);
			if (running != null) {
				openScreen(new RegenProgressScreen(this, running));
				return;
			}
		}
		syncActionButtons();
	}

	private void syncActionButtons() {
		int count = selection.size();
		boolean any = count > 0;
		regenButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.regen", count));
		undoButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.undo", selectedUndoCount));
		retrogenButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.retrogen", count));
		clearButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.clear"));
		regenButton.active = any;
		undoButton.active = selectedUndoCount > 0;
		retrogenButton.active = any;
		clearButton.active = any;
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
		return width / 2.0;
	}

	private double mapCenterY() {
		return height / 2.0;
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

	private boolean isOverChip(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private boolean isOverMap(double mouseX, double mouseY) {
		if (isOverChip(mouseX, mouseY, titleX, titleY, titleW, titleH)) return false;
		if (isOverChip(mouseX, mouseY, clusterX, clusterY, clusterW, clusterH)) return false;
		if (isOverChip(mouseX, mouseY, hintX, hintY, hintW, hintH)) return false;
		int columnH = actionPanelY + actionPanelH - infoPanelY;
		if (isOverChip(mouseX, mouseY, panelX, infoPanelY, PANEL_WIDTH, columnH)) return false;
		return true;
	}

	private ChunkPos chunkAt(double screenX, double screenY) {
		int blockX = (int) Math.floor(screenToBlockX(screenX));
		int blockZ = (int) Math.floor(screenToBlockZ(screenY));
		return new ChunkPos(Math.floorDiv(blockX, CHUNK_BLOCKS), Math.floorDiv(blockZ, CHUNK_BLOCKS));
	}

	private record VisibleChunk(ChunkPos pos, int screenX, int screenY, int size) {}

	private List<VisibleChunk> computeVisibleChunks() {
		double ppb = pixelsPerBlock();
		int chunkPixelSize = Math.max(1, (int) Math.round(CHUNK_BLOCKS * ppb));

		int minChunkX = Math.floorDiv((int) Math.floor(screenToBlockX(0)), CHUNK_BLOCKS) - 1;
		int maxChunkX = Math.floorDiv((int) Math.ceil(screenToBlockX(width)), CHUNK_BLOCKS) + 1;
		int minChunkZ = Math.floorDiv((int) Math.floor(screenToBlockZ(0)), CHUNK_BLOCKS) - 1;
		int maxChunkZ = Math.floorDiv((int) Math.ceil(screenToBlockZ(height)), CHUNK_BLOCKS) + 1;

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

	// Screen's own render entry point is the one thing that genuinely differs
	// between eras: 26.x replaced render(GuiGraphics, ...) with
	// extractRenderState(GuiGraphicsExtractor, ...), and renamed the
	// background hook from renderBackground to extractBackground (which it
	// now draws in its own lower stratum). Everything past this point is
	// shared.
	//
	// Map first, widgets second: super draws the buttons, and painting the
	// map afterwards would bury them. The background hook is a no-op in both
	// eras - the map *is* the background, and there's no sense building a
	// blurred panorama that's painted over before anyone sees it.
	//? if <26 {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics), mouseX, mouseY);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics) {
	}
	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics), mouseX, mouseY);
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}
	*///?}

	private void draw(Painter painter, int mouseX, int mouseY) {
		painter.fill(0, 0, width, height, COLOR_MAP_BG);

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) {
			painter.centeredText(font, Component.translatable("gui.retrograde.chunk_map.singleplayer_only"),
				width / 2, height / 2, 0xFFFF8080);
			return;
		}
		var player = minecraft.player;
		if (player == null) return;
		ResourceKey<Level> dimension = player.level().dimension();
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null) return;
		ChunkTracker tracker = ChunkTracker.forServer(server);
		ChunkPos playerChunk = playerChunk(player);

		for (VisibleChunk chunk : computeVisibleChunks()) {
			textureCache.request(minecraft, server, serverLevel, chunk.pos());
			ChunkTerrainTextureCache.Status status = textureCache.statusOf(chunk.pos());
			int x1 = chunk.screenX();
			int y1 = chunk.screenY();
			int x2 = x1 + chunk.size();
			int y2 = y1 + chunk.size();

			if (status == ChunkTerrainTextureCache.Status.READY) {
				ResourceLocation texture = textureCache.textureOf(chunk.pos());
				painter.chunkTexture(texture, x1, y1, chunk.size());
			} else {
				painter.fill(x1, y1, x2, y2,
					status == ChunkTerrainTextureCache.Status.UNLOADED ? COLOR_UNLOADED : COLOR_PENDING);
			}

			if (chunk.pos().equals(playerChunk)) {
				painter.fill(x1, y1, x2, y2, COLOR_PLAYER_TINT);
			} else if (tracker.statusOf(dimension, chunk.pos()) == ChunkTracker.Status.TOUCHED) {
				painter.fill(x1, y1, x2, y2, COLOR_TOUCHED_TINT);
			}

			if (chunk.size() >= CHUNK_BLOCKS) {
				painter.fill(x1, y1, x2, y1 + 1, COLOR_GRIDLINE);
				painter.fill(x1, y1, x1 + 1, y2, COLOR_GRIDLINE);
			}

			if (selection.contains(chunk.pos())) {
				painter.fill(x1, y1, x2, y2, COLOR_SELECTED_TINT);
				painter.outline(x1, y1, chunk.size(), chunk.size(), COLOR_SELECTED_EDGE);
			}
		}

		if (dragMode == DragMode.SELECT_ADD || dragMode == DragMode.SELECT_REMOVE) {
			drawDragBox(painter);
		}

		drawChip(painter, titleX, titleY, titleW, titleH);
		painter.centeredText(font, title, titleX + titleW / 2, titleY + TITLE_PAD_V, 0xFFFFFFFF);
		drawChip(painter, clusterX - 1, clusterY - 1, clusterW + 2, clusterH + 2);
		drawChip(painter, hintX, hintY, hintW, hintH);
		painter.text(font, controlsHint, hintX + TITLE_PAD_H, hintY + TITLE_PAD_V, 0xFFFFFFFF);

		if (isOverMap(mouseX, mouseY)) {
			focusChunk = chunkAt(mouseX, mouseY);
		}
		drawInfoPanel(painter, server, dimension, tracker, playerChunk);
		drawActionPanel(painter);
	}

	private void drawDragBox(Painter painter) {
		int x1 = (int) Math.min(pressScreenX, dragScreenX);
		int y1 = (int) Math.min(pressScreenY, dragScreenY);
		int x2 = (int) Math.max(pressScreenX, dragScreenX);
		int y2 = (int) Math.max(pressScreenY, dragScreenY);
		painter.fill(x1, y1, x2, y2, COLOR_BOX_FILL);
		painter.outline(x1, y1, x2 - x1, y2 - y1, COLOR_BOX_EDGE);
	}

	private void drawChip(Painter painter, int x, int y, int w, int h) {
		painter.panel(x, y, w, h, COLOR_CHIP_BG, COLOR_CHIP_BORDER_LIGHT, COLOR_CHIP_BORDER_DARK);
	}

	/**
	 * Fixed panel, fixed height, top-left: whatever chunk the cursor is over.
	 * Every row has a reserved slot whether or not it has content, so the
	 * panel never resizes or jumps while the cursor moves across the map.
	 */
	private void drawInfoPanel(Painter painter, MinecraftServer server, ResourceKey<Level> dimension,
			ChunkTracker tracker, ChunkPos playerChunk) {
		drawChip(painter, panelX, infoPanelY, PANEL_WIDTH, infoPanelH);

		int textX = panelX + PANEL_PAD;
		int y = infoPanelY + PANEL_PAD;
		ChunkPos pos = focusChunk;

		if (pos == null) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.panel.no_chunk"),
				textX, y, COLOR_MESSAGE_TEXT);
			return;
		}

		painter.text(font, "(" + chunkX(pos) + ", " + chunkZ(pos) + ")", textX, y, 0xFFFFFFFF);
		if (selection.contains(pos)) {
			Component tag = Component.translatable("gui.retrograde.chunk_map.panel.selected");
			painter.text(font, tag, panelX + PANEL_WIDTH - PANEL_PAD - font.width(tag), y, COLOR_SELECTED_EDGE);
		}
		y += LINE_H;

		boolean isPlayerChunk = pos.equals(playerChunk);
		ChunkTracker.Status status = tracker.statusOf(dimension, pos);
		String statusKey = isPlayerChunk
			? "gui.retrograde.chunk_map.tooltip.you"
			: (status == ChunkTracker.Status.TOUCHED
				? "gui.retrograde.chunk_map.tooltip.touched"
				: "gui.retrograde.chunk_map.tooltip.unknown");
		int statusColor = isPlayerChunk ? COLOR_STATUS_YOU
			: (status == ChunkTracker.Status.TOUCHED ? COLOR_STATUS_TOUCHED : COLOR_STATUS_UNTOUCHED);
		painter.fill(textX, y + 2, textX + 4, y + 6, statusColor);
		painter.text(font, Component.translatable(statusKey), textX + 8, y, statusColor);
		y += LINE_H;

		Optional<ChunkResourceInfo.Inspection> cached = requestInspection(server, dimension, pos);
		ChunkResourceInfo.Inspection inspection = cached == null ? null : cached.orElse(null);

		if (inspection != null && inspection.biome() != null) {
			painter.text(font, trimToPanel(biomeDisplayName(inspection.biome())), textX, y, COLOR_BIOME_TEXT);
		} else if (cached == null) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.loading"), textX, y, COLOR_MESSAGE_TEXT);
		} else if (cached.isEmpty()) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.not_loaded"), textX, y, COLOR_MESSAGE_TEXT);
		}
		y += LINE_H;

		painter.fill(textX, y + 1, panelX + PANEL_WIDTH - PANEL_PAD, y + 2, COLOR_DIVIDER);
		y += 5;

		List<ChunkResourceInfo.Entry> ores = inspection == null ? null : inspection.ores();
		if (ores == null || ores.isEmpty()) {
			painter.text(font, Component.translatable(ores == null
					? "gui.retrograde.chunk_map.panel.ores_unknown"
					: "gui.retrograde.chunk_map.tooltip.no_ores"),
				textX, y + 4, COLOR_MESSAGE_TEXT);
			return;
		}

		// Ores as a compact icon grid rather than one labelled row each: the
		// icon already says which ore it is, so the name was just width.
		int cellWidth = (PANEL_WIDTH - PANEL_PAD * 2) / ORE_COLUMNS;
		// Entries come sorted by count, so when there are more than fit, the
		// ones dropped are the rarest - and the last cell says how many, so
		// the panel never quietly under-reports what's down there.
		boolean overflow = ores.size() > MAX_PANEL_ORES;
		int shown = overflow ? MAX_PANEL_ORES - 1 : ores.size();
		for (int i = 0; i < shown; i++) {
			ChunkResourceInfo.Entry entry = ores.get(i);
			int cellX = textX + (i % ORE_COLUMNS) * cellWidth;
			int cellY = y + (i / ORE_COLUMNS) * ORE_ROW_H;
			painter.item(new ItemStack(entry.block()), cellX, cellY);
			painter.text(font, String.valueOf(entry.count()), cellX + ORE_ICON + 2, cellY + 4, 0xFFFFFFFF);
		}
		if (overflow) {
			int cellX = textX + (shown % ORE_COLUMNS) * cellWidth;
			int cellY = y + (shown / ORE_COLUMNS) * ORE_ROW_H;
			painter.text(font, "+" + (ores.size() - shown), cellX, cellY + 4, COLOR_MESSAGE_TEXT);
		}
	}

	/** Fixed panel under the info panel: what's selected and what can be done to it. */
	private void drawActionPanel(Painter painter) {
		drawChip(painter, panelX, actionPanelY, PANEL_WIDTH, actionPanelH);

		int textX = panelX + PANEL_PAD;
		int y = actionPanelY + PANEL_PAD;
		painter.text(font, Component.translatable("gui.retrograde.chunk_map.panel.selection"), textX, y, COLOR_HEADING);
		y += LINE_H;

		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			painter.text(font, notice, textX, y, COLOR_NOTICE);
			return;
		}
		notice = null;

		Component summary = selection.isEmpty()
			? Component.translatable("gui.retrograde.chunk_map.panel.nothing_selected")
			: Component.translatable("gui.retrograde.chunk_map.panel.summary", selection.size(), selectedTouchedCount);
		painter.text(font, summary, textX, y, selection.isEmpty() ? COLOR_MESSAGE_TEXT : 0xFFFFFFFF);
	}

	private String trimToPanel(String text) {
		int budget = PANEL_WIDTH - PANEL_PAD * 2;
		if (font.width(text) <= budget) return text;
		return font.plainSubstrByWidth(text, budget - font.width("...")) + "...";
	}

	private static String biomeDisplayName(ResourceLocation biomeId) {
		return Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath()).getString();
	}

	private ChunkPos playerChunk(net.minecraft.world.entity.player.Player player) {
		//? if >=26 {
		/*return ChunkPos.containing(player.blockPosition());
		*///?} else {
		return new ChunkPos(player.blockPosition());
		//?}
	}

	private Optional<ChunkResourceInfo.Inspection> requestInspection(MinecraftServer server, ResourceKey<Level> dimension, ChunkPos pos) {
		Optional<ChunkResourceInfo.Inspection> cached = inspectionCache.get(pos);
		if (cached != null) {
			return cached;
		}
		if (pendingInspections.add(pos)) {
			server.execute(() -> {
				ServerLevel serverLevel = server.getLevel(dimension);
				ChunkResourceInfo.Inspection result = serverLevel == null ? null : ChunkResourceInfo.scan(serverLevel, pos);
				inspectionCache.put(pos, Optional.ofNullable(result));
				pendingInspections.remove(pos);
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

	// ----- selection -----

	private void toggleSelection(ChunkPos pos) {
		if (!selection.remove(pos) && !addToSelection(pos)) {
			return;
		}
		refreshSelectionStats();
	}

	/** False if the cap stopped it. */
	private boolean addToSelection(ChunkPos pos) {
		if (selection.contains(pos)) return true;
		if (selection.size() >= MAX_SELECTION) {
			showNotice(Component.translatable("gui.retrograde.chunk_map.panel.limit", MAX_SELECTION).getString());
			return false;
		}
		selection.add(pos);
		return true;
	}

	private void applyDragBox(boolean add) {
		ChunkPos from = chunkAt(pressScreenX, pressScreenY);
		ChunkPos to = chunkAt(dragScreenX, dragScreenY);
		int minX = Math.min(chunkX(from), chunkX(to));
		int maxX = Math.max(chunkX(from), chunkX(to));
		int minZ = Math.min(chunkZ(from), chunkZ(to));
		int maxZ = Math.max(chunkZ(from), chunkZ(to));
		for (int cz = minZ; cz <= maxZ; cz++) {
			for (int cx = minX; cx <= maxX; cx++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				if (add) {
					if (!addToSelection(pos)) {
						refreshSelectionStats();
						return;
					}
				} else {
					selection.remove(pos);
				}
			}
		}
		refreshSelectionStats();
	}

	private void clearSelection() {
		selection.clear();
		refreshSelectionStats();
	}

	private void showNotice(String text) {
		notice = text;
		noticeUntil = System.currentTimeMillis() + NOTICE_MS;
	}

	/**
	 * Recomputed only when the selection changes, not per frame: the touched
	 * count is a cheap in-memory lookup but the undo count is a directory
	 * check per chunk, which has no business running every frame.
	 */
	private void refreshSelectionStats() {
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null) {
			selectedTouchedCount = 0;
			selectedUndoCount = 0;
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		ChunkTracker tracker = ChunkTracker.forServer(server);
		int touched = 0;
		for (ChunkPos pos : selection) {
			if (tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED) touched++;
		}
		selectedTouchedCount = touched;

		List<ChunkPos> snapshot = List.copyOf(selection);
		int generation = selectionGeneration.incrementAndGet();
		server.execute(() -> {
			ServerLevel serverLevel = server.getLevel(dimension);
			if (serverLevel == null) return;
			int undoable = 0;
			for (ChunkPos pos : snapshot) {
				if (ChunkRegenService.hasUndo(serverLevel, pos)) undoable++;
			}
			// Drag-selecting queues one of these per chunk added, and they can
			// finish out of order; only the newest one still describes the
			// selection the buttons are labelled for.
			if (selectionGeneration.get() == generation) {
				selectedUndoCount = undoable;
			}
		});
		syncActionButtons();
	}

	// ----- actions -----

	private void beginRegen() {
		if (selection.isEmpty()) return;
		List<ChunkPos> targets = List.copyOf(selection);
		int touched = selectedTouchedCount;
		Component question = touched > 0
			? Component.translatable("gui.retrograde.confirm_regen_touched", targets.size(), touched)
			: Component.translatable("gui.retrograde.confirm_regen", targets.size());
		Component detail = Component.translatable(touched > 0
			? "gui.retrograde.confirm_regen_touched.detail"
			: "gui.retrograde.confirm_regen.detail");
		confirmThen(question, detail, () -> startJob(ChunkRegenJob.Mode.REGENERATE, targets, null));
	}

	private void beginUndo() {
		if (selection.isEmpty()) return;
		List<ChunkPos> targets = List.copyOf(selection);
		confirmThen(
			Component.translatable("gui.retrograde.confirm_undo", selectedUndoCount),
			Component.translatable("gui.retrograde.confirm_undo.detail"),
			() -> startJob(ChunkRegenJob.Mode.UNDO, targets, null));
	}

	private void beginRetrogen() {
		if (selection.isEmpty()) return;
		List<RetrogenIntegration> integrations = RetrogenService.available();
		if (integrations.isEmpty()) {
			showNotice(Component.translatable("gui.retrograde.retrogen.none_available").getString());
			return;
		}
		openScreen(new RetrogenScreen(this, List.copyOf(selection), integrations));
	}

	/** Called by RetrogenScreen once a mod has been picked and confirmed. */
	public void startRetrogenJob(List<ChunkPos> targets, RetrogenIntegration integration) {
		startJob(ChunkRegenJob.Mode.RETROGEN, targets, integration);
	}

	private void confirmThen(Component question, Component detail, Runnable action) {
		openScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				action.run();
			} else {
				openScreen(this);
			}
		}, question, detail));
	}

	private void startJob(ChunkRegenJob.Mode mode, List<ChunkPos> targets, RetrogenIntegration integration) {
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null) {
			openScreen(this);
			return;
		}
		ServerLevel serverLevel = server.getLevel(player.level().dimension());
		if (serverLevel == null) {
			openScreen(this);
			return;
		}
		ChunkRegenJob job = ChunkRegenJob.start(server, serverLevel, player.getUUID(), mode, targets, integration);
		if (job == null) {
			showNotice(Component.translatable("gui.retrograde.job.already_running").getString());
			openScreen(this);
			return;
		}
		openScreen(new RegenProgressScreen(this, job));
	}

	/**
	 * Called by the progress screen when a job ends. The terrain and ore
	 * data we cached for those chunks describe a world that no longer
	 * exists, so drop it and let the map re-read them.
	 */
	public void onJobFinished(ChunkRegenJob job) {
		for (ChunkPos pos : job.touchedByJob()) {
			textureCache.invalidate(minecraft, pos);
			inspectionCache.remove(pos);
		}
		// Selection is deliberately kept: the most common thing to want right
		// after a regen is to undo it.
		refreshSelectionStats();
	}

	// 26.3 moved screen management off Minecraft onto its new Gui wrapper:
	// Minecraft#setScreen is gone, replaced by minecraft.gui.setScreen(...).
	// 26.1 still has it directly, same as 1.20.1.
	private void openScreen(Screen screen) {
		handingOff = screen != this;
		//? if >=26.3 {
		/*minecraft.gui.setScreen(screen);
		*///?} else {
		minecraft.setScreen(screen);
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
		return handlePress(event.x(), event.y(), event.hasShiftDown(), event.hasControlDown());
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
		if (handleDrag(event.x(), event.y(), dragX, dragY)) return true;
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (handleRelease(event.x(), event.y())) return true;
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
		return handlePress(mouseX, mouseY, Screen.hasShiftDown(), Screen.hasControlDown());
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (handleDrag(mouseX, mouseY, dragX, dragY)) return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (handleRelease(mouseX, mouseY)) return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
		if (handleScroll(mouseX, mouseY, scrollDelta)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollDelta);
	}
	//?}

	private boolean handlePress(double mouseX, double mouseY, boolean shiftDown, boolean ctrlDown) {
		if (!isOverMap(mouseX, mouseY)) return false;
		pressScreenX = dragScreenX = mouseX;
		pressScreenY = dragScreenY = mouseY;
		dragTotalDistance = 0;
		// Plain drag pans, because panning is what you do most; the modifiers
		// turn the same drag into a selection box.
		dragMode = shiftDown ? DragMode.SELECT_ADD : ctrlDown ? DragMode.SELECT_REMOVE : DragMode.PAN;
		return true;
	}

	private boolean handleDrag(double mouseX, double mouseY, double dragX, double dragY) {
		if (dragMode == DragMode.NONE) return false;
		dragScreenX = mouseX;
		dragScreenY = mouseY;
		dragTotalDistance += Math.abs(dragX) + Math.abs(dragY);
		if (dragMode == DragMode.PAN) {
			cameraBlockX -= dragX / pixelsPerBlock();
			cameraBlockZ -= dragY / pixelsPerBlock();
		}
		return true;
	}

	private boolean handleScroll(double mouseX, double mouseY, double scrollDelta) {
		if (!isOverMap(mouseX, mouseY) || scrollDelta == 0) return false;
		setZoom(zoomLevel + (scrollDelta > 0 ? 1 : -1), mouseX, mouseY);
		return true;
	}

	private boolean handleRelease(double mouseX, double mouseY) {
		DragMode mode = dragMode;
		dragMode = DragMode.NONE;
		if (mode == DragMode.NONE) return false;

		if (mode == DragMode.PAN) {
			// A pan that never really moved is a click.
			if (dragTotalDistance <= DRAG_THRESHOLD) {
				toggleSelection(chunkAt(mouseX, mouseY));
			}
			return true;
		}

		dragScreenX = mouseX;
		dragScreenY = mouseY;
		applyDragBox(mode == DragMode.SELECT_ADD);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
