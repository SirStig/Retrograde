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
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Full-screen, pannable, zoomable chunk map, one texture per chunk (see
 * ChunkMapDataCache) blitted at whatever scale the current zoom level
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

	// Mouse-button and key numbers, spelled out rather than pulling LWJGL or
	// SDL into a file that otherwise only touches Minecraft classes.
	//
	// 26.3 swapped the input backend from GLFW to SDL, which numbers both the
	// mouse buttons and the non-printable keys differently - vanilla's own
	// AbstractWidget treats button 1 as the left button on 26.3 and button 0
	// on everything before it, which is how this was pinned down. Printable
	// keys are ASCII either way, so '-' and '=' are shared.
	//? if >=26.3 {
	/*private static final int BUTTON_LEFT = 1;
	private static final int BUTTON_MIDDLE = 2;
	private static final int BUTTON_RIGHT = 3;
	private static final int KEY_RIGHT = 1073741903;
	private static final int KEY_LEFT = 1073741904;
	private static final int KEY_DOWN = 1073741905;
	private static final int KEY_UP = 1073741906;
	private static final int KEY_KP_SUBTRACT = 1073741910;
	private static final int KEY_KP_ADD = 1073741911;
	*///?} else {
	private static final int BUTTON_LEFT = 0;
	private static final int BUTTON_RIGHT = 1;
	private static final int BUTTON_MIDDLE = 2;
	private static final int KEY_RIGHT = 262;
	private static final int KEY_LEFT = 263;
	private static final int KEY_DOWN = 264;
	private static final int KEY_UP = 265;
	private static final int KEY_KP_SUBTRACT = 333;
	private static final int KEY_KP_ADD = 334;
	//?}
	private static final int KEY_MINUS = 45;
	private static final int KEY_EQUAL = 61;
	/** How far one arrow-key press slides the map, in screen pixels. */
	private static final int KEY_PAN_PIXELS = 48;

	private static final int PANEL_WIDTH = 152;
	private static final int PANEL_PAD = 6;
	private static final int LINE_H = 10;
	private static final int ORE_ROW_H = 18;
	private static final int ORE_ICON = 16;
	private static final int ORE_COLUMNS = 3;
	private static final int ORE_ROWS = 2;
	private static final int MAX_PANEL_ORES = ORE_COLUMNS * ORE_ROWS;
	private static final int ORE_TOGGLE_W = 13;
	private static final int ORE_TOGGLE_H = 11;
	/** Wider than the info panel: this one spells each ore's name out. */
	private static final int ORE_PANEL_WIDTH = 168;
	private static final int ORE_PANEL_GAP = 6;
	private static final int ACTION_BUTTON_H = 18;
	private static final int ACTION_BUTTON_GAP = 4;
	private static final int ACTION_BUTTONS = 4;

	/**
	 * The filter panel shares the slot to the right of the info panel with
	 * the ore breakdown, so only one of the two is ever open - two floating
	 * panels stacked on the same pixels would be unreadable, and both of
	 * them are about the same thing anyway.
	 */
	private static final int FILTER_PANEL_WIDTH = 176;
	private static final int FILTER_ROW_H = 18;
	private static final int FILTER_ROW_GAP = 3;
	/** How often the filter re-runs and re-derives its biome/ore choices, in ms. */
	private static final long FILTER_REFRESH_MS = 400;

	/** Cap on one selection, so nobody accidentally box-selects a continent. */
	private static final int MAX_SELECTION = 256;
	/** How long the panel keeps showing a one-off notice like the selection cap. */
	private static final long NOTICE_MS = 3000;

	private static final int COLOR_MAP_BG = 0xFF101010;
	private static final int COLOR_CHIP_BG = 0xE0161616;
	private static final int COLOR_CHIP_BORDER_LIGHT = 0x40FFFFFF;
	private static final int COLOR_CHIP_BORDER_DARK = 0x80000000;
	private static final int COLOR_PENDING = 0xFF404040;
	private static final int COLOR_UNGENERATED = 0xFF303030;
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
	// Amber rather than another blue: filter matches sit on the same map as
	// the blue selection, and "found" and "selected" are different answers.
	private static final int COLOR_MATCH_TINT = 0x55D8A03C;
	private static final int COLOR_MATCH_EDGE = 0xFFF0C060;

	private final ChunkMapDataCache dataCache = new ChunkMapDataCache();

	/** Insertion-ordered so the job processes chunks in the order they were picked. */
	private final Set<ChunkPos> selection = new LinkedHashSet<>();

	/**
	 * The selection as it was before the last thing that wiped or replaced
	 * it, so "Restore" can put it back. Clear is one button away from Regen,
	 * and a hand-picked selection is worth more than the one click it takes
	 * to lose it.
	 */
	private List<ChunkPos> lastSelection = List.of();

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
	private int oreListX, oreListY, oreListH;

	/** Whether the full ore breakdown is pinned open beside the info panel. */
	private boolean oreListOpen;

	// ----- chunk filter -----

	/** Which chunks the filter is allowed to consider. */
	private enum Scope { ON_SCREEN, RADIUS_4, RADIUS_8, RADIUS_16, MAPPED }

	/** Whether the filter wants chunks you've been in, ones you haven't, or both. */
	private enum Touched { ANY, YES, NO }

	/** Whether the picked ore has to be present or absent. */
	private enum OreRule { HAS, WITHOUT }

	private boolean filterOpen;
	private int filterX, filterY, filterH;
	private Scope filterScope = Scope.ON_SCREEN;
	private Touched filterTouched = Touched.ANY;
	private OreRule filterOreRule = OreRule.HAS;
	/** Both are -1 for "any", otherwise an index into the list below it. */
	private int filterBiomeIndex = -1;
	private int filterOreIndex = -1;
	private List<ResourceLocation> biomeChoices = List.of();
	private List<Block> oreChoices = List.of();
	/** What currently matches. Highlighted on the map, and what "Select" acts on. */
	private Set<ChunkPos> matches = Set.of();
	private long filterRefreshedAt;

	private Button regenButton;
	private Button undoButton;
	private Button retrogenButton;
	private Button clearButton;
	private Button findButton;
	private Button oreToggle;
	private Button filterScopeButton;
	private Button filterTouchedButton;
	private Button filterBiomeButton;
	private Button filterOreButton;
	private Button filterOreRuleButton;
	private Button filterSelectButton;
	private Button filterRestoreButton;
	private Button filterCloseButton;

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
		infoPanelH = PANEL_PAD * 2 + LINE_H * 4 + 5 + ORE_ROW_H * ORE_ROWS;
		actionPanelY = infoPanelY + infoPanelH + 6;
		actionPanelH = PANEL_PAD * 2 + LINE_H * 2 + 4
			+ ACTION_BUTTONS * ACTION_BUTTON_H + (ACTION_BUTTONS - 1) * ACTION_BUTTON_GAP;

		// Sits on the ore heading row, right-aligned inside the info panel.
		oreToggle = addRenderableWidget(Button.builder(Component.literal(oreListOpen ? "«" : "»"), b -> toggleOreList())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.panel.ores.tip")))
			.bounds(panelX + PANEL_WIDTH - PANEL_PAD - ORE_TOGGLE_W,
				infoPanelY + PANEL_PAD + LINE_H * 3 + 4, ORE_TOGGLE_W, ORE_TOGGLE_H)
			.build());

		oreListX = panelX + PANEL_WIDTH + ORE_PANEL_GAP;
		oreListY = infoPanelY;
		oreListH = 0;

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
		// Find and Clear share the last row at half width each, so adding the
		// filter doesn't push the action panel taller than a 240px window.
		buttonY += ACTION_BUTTON_H + ACTION_BUTTON_GAP;
		int halfW = (buttonW - ACTION_BUTTON_GAP) / 2;
		findButton = addActionButton(buttonX, buttonY, halfW, "gui.retrograde.chunk_map.action.find",
			"gui.retrograde.chunk_map.action.find.tip", b -> toggleFilter());
		clearButton = addActionButton(buttonX + halfW + ACTION_BUTTON_GAP, buttonY,
			buttonW - halfW - ACTION_BUTTON_GAP, "gui.retrograde.chunk_map.action.clear",
			"gui.retrograde.chunk_map.action.clear.tip", b -> clearSelection());

		initFilterPanel();
		refreshSelectionStats();
	}

	/**
	 * The filter's controls are real widgets rather than hand-drawn hit
	 * boxes, so they get vanilla's hover and click feedback for free - but
	 * that means they exist whether or not the panel is open, and are simply
	 * hidden when it isn't.
	 */
	private void initFilterPanel() {
		filterX = panelX + PANEL_WIDTH + ORE_PANEL_GAP;
		filterY = infoPanelY;
		int rowW = FILTER_PANEL_WIDTH - PANEL_PAD * 2;
		int rowX = filterX + PANEL_PAD;
		int y = filterY + PANEL_PAD + LINE_H + 2;

		filterScopeButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.scope.tip", b -> cycleScope());
		y += FILTER_ROW_H + FILTER_ROW_GAP;
		filterTouchedButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.touched.tip", b -> cycleTouched());
		y += FILTER_ROW_H + FILTER_ROW_GAP;
		filterBiomeButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.biome.tip", b -> cycleBiome());
		y += FILTER_ROW_H + FILTER_ROW_GAP;
		filterOreButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.ore.tip", b -> cycleOre());
		y += FILTER_ROW_H + FILTER_ROW_GAP;
		filterOreRuleButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.ore_rule.tip", b -> cycleOreRule());
		y += FILTER_ROW_H + FILTER_ROW_GAP + LINE_H + 2;

		filterSelectButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.select.tip", b -> selectMatches());
		y += FILTER_ROW_H + FILTER_ROW_GAP;
		int half = (rowW - FILTER_ROW_GAP) / 2;
		filterRestoreButton = addFilterButton(rowX, y, half, "gui.retrograde.chunk_map.filter.restore.tip", b -> restoreLastSelection());
		filterCloseButton = addFilterButton(rowX + half + FILTER_ROW_GAP, y, rowW - half - FILTER_ROW_GAP,
			"gui.retrograde.chunk_map.filter.close.tip", b -> toggleFilter());
		y += FILTER_ROW_H;

		filterH = y + PANEL_PAD - filterY;
		// init() runs again on every window resize, so a panel that was open
		// has to come back labelled rather than as a stack of blank buttons.
		filterRefreshedAt = 0;
		if (filterOpen) {
			refreshFilter();
		} else {
			syncFilterButtons();
		}
	}

	private Button addFilterButton(int x, int y, int w, String tooltipKey, Button.OnPress onPress) {
		Button button = addRenderableWidget(Button.builder(Component.empty(), onPress)
			.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
			.bounds(x, y, w, FILTER_ROW_H)
			.build());
		button.visible = false;
		return button;
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
		dataCache.close(minecraft);
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
		// Off the tick rather than the frame: the map keeps reading chunks in
		// while you sit here, so what matches changes under you, but not
		// twenty times a frame and not for free.
		if (filterOpen) {
			refreshFilter();
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
		findButton.setMessage(Component.translatable(filterOpen
			? "gui.retrograde.chunk_map.action.find.close"
			: "gui.retrograde.chunk_map.action.find"));
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
		// The expanded ore list floats over the map, and it describes whichever
		// chunk is in focus - so hovering it must not change what's in focus,
		// or the list would rewrite itself out from under the cursor.
		if (oreListOpen && isOverChip(mouseX, mouseY, oreListX, oreListY, ORE_PANEL_WIDTH, oreListH)) return false;
		if (filterOpen && isOverChip(mouseX, mouseY, filterX, filterY, FILTER_PANEL_WIDTH, filterH)) return false;
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
			dataCache.request(minecraft, server, serverLevel, chunk.pos());
			ChunkMapDataCache.Status status = dataCache.statusOf(chunk.pos());
			int x1 = chunk.screenX();
			int y1 = chunk.screenY();
			int x2 = x1 + chunk.size();
			int y2 = y1 + chunk.size();

			if (status == ChunkMapDataCache.Status.READY) {
				ResourceLocation texture = dataCache.textureOf(chunk.pos());
				painter.chunkTexture(texture, x1, y1, chunk.size());
			} else {
				painter.fill(x1, y1, x2, y2,
					status == ChunkMapDataCache.Status.UNGENERATED ? COLOR_UNGENERATED : COLOR_PENDING);
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

			// Matches under the selection, not over it: once you've selected
			// them the selection is the answer, and two overlapping tints on
			// the same chunk read as a third colour that means nothing.
			if (filterOpen && matches.contains(chunk.pos())) {
				painter.fill(x1, y1, x2, y2, COLOR_MATCH_TINT);
				painter.outline(x1, y1, chunk.size(), chunk.size(), COLOR_MATCH_EDGE);
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
		if (filterOpen) {
			drawFilterPanel(painter);
		}
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
		// Recomputed below if the list actually gets drawn this frame; zeroed
		// here so a chunk with no ore data leaves no phantom hit-box behind.
		oreListH = 0;

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

		ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
		if (inspection != null && inspection.biome() != null) {
			painter.text(font, trimToPanel(biomeDisplayName(inspection.biome())), textX, y, COLOR_BIOME_TEXT);
		} else if (dataCache.statusOf(pos) == ChunkMapDataCache.Status.UNGENERATED) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.not_generated"), textX, y, COLOR_MESSAGE_TEXT);
		} else if (inspection == null) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.loading"), textX, y, COLOR_MESSAGE_TEXT);
		}
		y += LINE_H;

		painter.fill(textX, y + 1, panelX + PANEL_WIDTH - PANEL_PAD, y + 2, COLOR_DIVIDER);
		y += 5;

		List<ChunkResourceInfo.Entry> ores = inspection == null ? null : inspection.ores();
		painter.text(font, Component.translatable("gui.retrograde.chunk_map.panel.ores",
			ores == null ? "?" : String.valueOf(ores.size())), textX, y, COLOR_HEADING);
		y += LINE_H;

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
		// the panel never quietly under-reports what's down there. The full
		// list is one click away on the toggle beside the heading.
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
			painter.text(font, "+" + (ores.size() - shown), cellX, cellY + 4, COLOR_SELECTED_EDGE);
		}

		if (oreListOpen) {
			drawOreListPanel(painter, ores);
		}
	}

	/**
	 * The full breakdown, one ore per row with its name spelled out, in its
	 * own panel to the right of the info panel - so opening it doesn't move
	 * or resize anything that was already on screen.
	 */
	private void drawOreListPanel(Painter painter, List<ChunkResourceInfo.Entry> ores) {
		// Never let the list run off the bottom of the window; whatever doesn't
		// fit is counted on a final line rather than silently clipped.
		int room = height - CHIP_MARGIN - oreListY - PANEL_PAD * 2 - LINE_H;
		int maxRows = Math.max(1, room / ORE_ROW_H);
		boolean clipped = ores.size() > maxRows;
		int rows = clipped ? maxRows - 1 : ores.size();

		oreListH = PANEL_PAD * 2 + LINE_H + (rows + (clipped ? 1 : 0)) * ORE_ROW_H;
		drawChip(painter, oreListX, oreListY, ORE_PANEL_WIDTH, oreListH);

		int textX = oreListX + PANEL_PAD;
		int y = oreListY + PANEL_PAD;
		painter.text(font, Component.translatable("gui.retrograde.chunk_map.panel.ore_list"), textX, y, COLOR_HEADING);
		y += LINE_H;

		for (int i = 0; i < rows; i++) {
			ChunkResourceInfo.Entry entry = ores.get(i);
			ItemStack stack = new ItemStack(entry.block());
			painter.item(stack, textX, y);
			String count = String.valueOf(entry.count());
			int countX = oreListX + ORE_PANEL_WIDTH - PANEL_PAD - font.width(count);
			painter.text(font, count, countX, y + 4, 0xFFFFFFFF);
			// Trim the name against where the count starts, not the panel edge,
			// so a long modded ore name can't overwrite its own number.
			int nameX = textX + ORE_ICON + 4;
			painter.text(font, trimTo(stack.getHoverName().getString(), countX - nameX - 4),
				nameX, y + 4, COLOR_BIOME_TEXT);
			y += ORE_ROW_H;
		}

		if (clipped) {
			painter.text(font, "+" + (ores.size() - rows), textX, y + 4, COLOR_MESSAGE_TEXT);
		}
	}

	private void toggleOreList() {
		oreListOpen = !oreListOpen;
		if (!oreListOpen) oreListH = 0;
		// Both live in the same slot beside the info panel, so opening one
		// puts the other away rather than drawing on top of it.
		if (oreListOpen && filterOpen) closeFilter();
		oreToggle.setMessage(Component.literal(oreListOpen ? "«" : "»"));
	}

	// ----- chunk filter -----

	private void toggleFilter() {
		if (filterOpen) {
			closeFilter();
			return;
		}
		filterOpen = true;
		if (oreListOpen) toggleOreList();
		filterRefreshedAt = 0;
		refreshFilter();
		syncActionButtons();
	}

	private void closeFilter() {
		filterOpen = false;
		matches = Set.of();
		syncFilterButtons();
		syncActionButtons();
	}

	private void cycleScope() {
		filterScope = next(Scope.values(), filterScope.ordinal());
		refreshNow();
	}

	private void cycleTouched() {
		filterTouched = next(Touched.values(), filterTouched.ordinal());
		refreshNow();
	}

	private void cycleOreRule() {
		filterOreRule = next(OreRule.values(), filterOreRule.ordinal());
		refreshNow();
	}

	/** Steps through the choices derived from what's on the map, with "any" at the end. */
	private void cycleBiome() {
		filterBiomeIndex = filterBiomeIndex + 1 >= biomeChoices.size() ? -1 : filterBiomeIndex + 1;
		refreshNow();
	}

	private void cycleOre() {
		filterOreIndex = filterOreIndex + 1 >= oreChoices.size() ? -1 : filterOreIndex + 1;
		refreshNow();
	}

	private static <T> T next(T[] values, int ordinal) {
		return values[(ordinal + 1) % values.length];
	}

	/** Clicking a filter control should answer immediately, not on the next tick. */
	private void refreshNow() {
		filterRefreshedAt = 0;
		refreshFilter();
	}

	/**
	 * Re-runs the filter and re-derives the biome and ore choices from
	 * whatever the map has read. Offering only what's actually out there
	 * beats a text box: there's nothing to spell, nothing to guess at the
	 * namespace of, and no way to land on a filter that can't match.
	 */
	private void refreshFilter() {
		long now = System.currentTimeMillis();
		if (now - filterRefreshedAt < FILTER_REFRESH_MS) return;
		filterRefreshedAt = now;

		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null) {
			matches = Set.of();
			syncFilterButtons();
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		ChunkTracker tracker = ChunkTracker.forServer(server);

		List<ChunkPos> candidates = filterCandidates(playerChunk(player));
		refreshFilterChoices(candidates);

		ResourceLocation biome = choice(biomeChoices, filterBiomeIndex);
		Block ore = choice(oreChoices, filterOreIndex);

		Set<ChunkPos> found = new LinkedHashSet<>();
		for (ChunkPos pos : candidates) {
			// A chunk the map hasn't read is one we can't answer for - it
			// might be ungenerated, it might be full of diamond. Either way
			// it doesn't belong in a set someone is about to regenerate.
			if (dataCache.statusOf(pos) != ChunkMapDataCache.Status.READY) continue;
			boolean touched = tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED;
			if (filterTouched == Touched.YES && !touched) continue;
			if (filterTouched == Touched.NO && touched) continue;

			ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
			if (biome != null && (inspection == null || !biome.equals(inspection.biome()))) continue;
			if (ore != null && hasOre(inspection, ore) != (filterOreRule == OreRule.HAS)) continue;
			found.add(pos);
		}
		matches = found;
		syncFilterButtons();
	}

	private static <T> T choice(List<T> choices, int index) {
		return index >= 0 && index < choices.size() ? choices.get(index) : null;
	}

	private static boolean hasOre(ChunkResourceInfo.Inspection inspection, Block ore) {
		if (inspection == null) return false;
		for (ChunkResourceInfo.Entry entry : inspection.ores()) {
			if (entry.block() == ore) return true;
		}
		return false;
	}

	/**
	 * Ordered nearest-first, because the selection cap means a filter can
	 * match more chunks than it's allowed to select - and when it does,
	 * keeping the ones around you is a far better guess than keeping
	 * whichever corner of the map happened to be iterated first.
	 */
	private List<ChunkPos> filterCandidates(ChunkPos center) {
		List<ChunkPos> candidates = new ArrayList<>();
		switch (filterScope) {
			case ON_SCREEN -> {
				for (VisibleChunk chunk : computeVisibleChunks()) candidates.add(chunk.pos());
			}
			case MAPPED -> candidates.addAll(dataCache.mappedChunks());
			default -> {
				int radius = filterRadius();
				for (int cz = -radius; cz <= radius; cz++) {
					for (int cx = -radius; cx <= radius; cx++) {
						candidates.add(new ChunkPos(chunkX(center) + cx, chunkZ(center) + cz));
					}
				}
			}
		}
		candidates.sort(Comparator.comparingLong(pos -> distanceSquared(center, pos)));
		return candidates;
	}

	private int filterRadius() {
		return switch (filterScope) {
			case RADIUS_4 -> 4;
			case RADIUS_8 -> 8;
			default -> 16;
		};
	}

	private static long distanceSquared(ChunkPos from, ChunkPos to) {
		long dx = chunkX(to) - (long) chunkX(from);
		long dz = chunkZ(to) - (long) chunkZ(from);
		return dx * dx + dz * dz;
	}

	/** Keeps whatever was picked selected if it's still out there, rather than resetting to "any". */
	private void refreshFilterChoices(List<ChunkPos> candidates) {
		ResourceLocation keepBiome = choice(biomeChoices, filterBiomeIndex);
		Block keepOre = choice(oreChoices, filterOreIndex);

		Set<ResourceLocation> biomes = new LinkedHashSet<>();
		Map<Block, String> ores = new LinkedHashMap<>();
		for (ChunkPos pos : candidates) {
			ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
			if (inspection == null) continue;
			if (inspection.biome() != null) biomes.add(inspection.biome());
			for (ChunkResourceInfo.Entry entry : inspection.ores()) {
				ores.computeIfAbsent(entry.block(), block -> new ItemStack(block).getHoverName().getString());
			}
		}

		List<ResourceLocation> sortedBiomes = new ArrayList<>(biomes);
		sortedBiomes.sort(Comparator.comparing(ChunkMapScreen::biomeDisplayName));
		List<Block> sortedOres = new ArrayList<>(ores.keySet());
		sortedOres.sort(Comparator.comparing(ores::get));

		biomeChoices = sortedBiomes;
		oreChoices = sortedOres;
		filterBiomeIndex = keepBiome == null ? -1 : biomeChoices.indexOf(keepBiome);
		filterOreIndex = keepOre == null ? -1 : oreChoices.indexOf(keepOre);
	}

	private void syncFilterButtons() {
		for (Button button : List.of(filterScopeButton, filterTouchedButton, filterBiomeButton,
				filterOreButton, filterOreRuleButton, filterSelectButton, filterRestoreButton, filterCloseButton)) {
			button.visible = filterOpen;
		}
		if (!filterOpen) return;

		filterScopeButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.scope",
			Component.translatable("gui.retrograde.chunk_map.filter.scope." + filterScope.name().toLowerCase(java.util.Locale.ROOT))));
		filterTouchedButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.touched",
			Component.translatable("gui.retrograde.chunk_map.filter.touched." + filterTouched.name().toLowerCase(java.util.Locale.ROOT))));

		ResourceLocation biome = choice(biomeChoices, filterBiomeIndex);
		filterBiomeButton.setMessage(biome == null
			? Component.translatable("gui.retrograde.chunk_map.filter.biome.any")
			: Component.literal(trimTo(biomeDisplayName(biome), FILTER_PANEL_WIDTH - PANEL_PAD * 2 - 8)));
		filterBiomeButton.active = !biomeChoices.isEmpty();

		Block ore = choice(oreChoices, filterOreIndex);
		filterOreButton.setMessage(ore == null
			? Component.translatable("gui.retrograde.chunk_map.filter.ore.any")
			: Component.literal(trimTo(new ItemStack(ore).getHoverName().getString(), FILTER_PANEL_WIDTH - PANEL_PAD * 2 - 8)));
		filterOreButton.active = !oreChoices.isEmpty();

		filterOreRuleButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.ore_rule",
			Component.translatable("gui.retrograde.chunk_map.filter.ore_rule." + filterOreRule.name().toLowerCase(java.util.Locale.ROOT))));
		// The rule only says anything once an ore is picked; greying it out
		// says so more plainly than leaving it live and inert.
		filterOreRuleButton.active = ore != null;

		filterSelectButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.select", matches.size()));
		filterSelectButton.active = !matches.isEmpty();
		filterRestoreButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.restore"));
		filterRestoreButton.active = !lastSelection.isEmpty();
		filterCloseButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.close"));
	}

	private void drawFilterPanel(Painter painter) {
		drawChip(painter, filterX, filterY, FILTER_PANEL_WIDTH, filterH);
		int textX = filterX + PANEL_PAD;
		painter.text(font, Component.translatable("gui.retrograde.chunk_map.filter.title"),
			textX, filterY + PANEL_PAD, COLOR_HEADING);

		// The count line sits in the gap between the five filter rows and the
		// two action rows, which is the reason that gap is there.
		int countY = filterSelectButton.getY() - LINE_H - 2;
		Component count = matches.size() > MAX_SELECTION
			? Component.translatable("gui.retrograde.chunk_map.filter.matches_capped", matches.size(), MAX_SELECTION)
			: Component.translatable("gui.retrograde.chunk_map.filter.matches", matches.size());
		painter.text(font, count, textX, countY, matches.isEmpty() ? COLOR_MESSAGE_TEXT : COLOR_MATCH_EDGE);
	}

	private void selectMatches() {
		if (matches.isEmpty()) return;
		rememberSelection();
		for (ChunkPos pos : matches) {
			if (!addToSelection(pos)) break;
		}
		refreshSelectionStats();
	}

	private void restoreLastSelection() {
		if (lastSelection.isEmpty()) return;
		List<ChunkPos> restoring = lastSelection;
		rememberSelection();
		selection.clear();
		for (ChunkPos pos : restoring) {
			if (!addToSelection(pos)) break;
		}
		refreshSelectionStats();
	}

	/** Snapshots the selection before something is about to overwrite it. */
	private void rememberSelection() {
		if (!selection.isEmpty()) {
			lastSelection = List.copyOf(selection);
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
		return trimTo(text, PANEL_WIDTH - PANEL_PAD * 2);
	}

	private String trimTo(String text, int budget) {
		if (font.width(text) <= budget) return text;
		return font.plainSubstrByWidth(text, Math.max(0, budget - font.width("..."))) + "...";
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
		rememberSelection();
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

	/**
	 * Regen goes through a preview rather than a yes/no dialog. A confirm
	 * box can say "42 chunks" and nothing more; this is the one action in
	 * the mod that destroys work, and the numbers that decide whether you
	 * want it - how many you've built in, how many can be undone afterwards,
	 * what ore is down there, how long you'll be sat watching - are all
	 * knowable before it starts.
	 */
	private void beginRegen() {
		if (selection.isEmpty()) return;
		List<ChunkPos> targets = List.copyOf(selection);
		openScreen(new RegenPreviewScreen(this, targets, selectedTouchedCount, selectedUndoCount,
			aggregateOres(targets), unscannedCount(targets)));
	}

	/** Called by RegenPreviewScreen once the numbers have been looked at and accepted. */
	public void startRegenJob(List<ChunkPos> targets) {
		startJob(ChunkRegenJob.Mode.REGENERATE, targets, null);
	}

	/** Every ore across the selection, biggest total first. */
	private List<ChunkResourceInfo.Entry> aggregateOres(List<ChunkPos> targets) {
		Map<Block, Integer> tally = new LinkedHashMap<>();
		for (ChunkPos pos : targets) {
			ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
			if (inspection == null) continue;
			for (ChunkResourceInfo.Entry entry : inspection.ores()) {
				tally.merge(entry.block(), entry.count(), Integer::sum);
			}
		}
		List<ChunkResourceInfo.Entry> entries = new ArrayList<>();
		tally.forEach((block, count) -> entries.add(new ChunkResourceInfo.Entry(block, count)));
		entries.sort((a, b) -> b.count() - a.count());
		return entries;
	}

	/**
	 * How many of the targets the map never got a look inside. The ore
	 * figures on the preview are a floor, not a total, whenever this isn't
	 * zero - and saying so is better than quietly under-reporting.
	 */
	private int unscannedCount(List<ChunkPos> targets) {
		int unscanned = 0;
		for (ChunkPos pos : targets) {
			if (dataCache.inspectionOf(pos) == null) unscanned++;
		}
		return unscanned;
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
			dataCache.invalidate(minecraft, pos);
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
		return handlePress(event.x(), event.y(), event.button(), event.hasControlDown());
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
		return handlePress(mouseX, mouseY, button, Screen.hasControlDown());
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

	// keyPressed needs its own three-way split rather than riding along with
	// the mouse block: the key value to test lives in a different accessor in
	// each era. 26.3's KeyEvent.key() is the physical key, and keycode() is
	// the one vanilla's own arrow handling compares against - so read the
	// same field vanilla does rather than the similarly-named one.
	//? if >=26.3 {
	/*@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (handleKey(event.keycode())) return true;
		return super.keyPressed(event);
	}
	*///?}
	//? if >=26 && <26.3 {
	/*@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (handleKey(event.key())) return true;
		return super.keyPressed(event);
	}
	*///?}
	//? if <26 {
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (handleKey(keyCode)) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	//?}

	/**
	 * Left button selects, right and middle pan. Selecting is the thing this
	 * screen exists for, so it gets the button people reach for first, and
	 * panning moves to the one every map and RTS puts it on anyway. Ctrl
	 * flips a left-drag from selecting to deselecting.
	 */
	private boolean handlePress(double mouseX, double mouseY, int button, boolean ctrlDown) {
		if (!isOverMap(mouseX, mouseY)) return false;
		pressScreenX = dragScreenX = mouseX;
		pressScreenY = dragScreenY = mouseY;
		dragTotalDistance = 0;
		if (button == BUTTON_RIGHT || button == BUTTON_MIDDLE) {
			dragMode = DragMode.PAN;
		} else if (button == BUTTON_LEFT) {
			dragMode = ctrlDown ? DragMode.SELECT_REMOVE : DragMode.SELECT_ADD;
		} else {
			return false;
		}
		return true;
	}

	/** Keyboard panning and zoom, so the map is usable without a mouse at all. */
	private boolean handleKey(int keyCode) {
		double step = KEY_PAN_PIXELS / pixelsPerBlock();
		switch (keyCode) {
			case KEY_LEFT -> cameraBlockX -= step;
			case KEY_RIGHT -> cameraBlockX += step;
			case KEY_UP -> cameraBlockZ -= step;
			case KEY_DOWN -> cameraBlockZ += step;
			case KEY_EQUAL, KEY_KP_ADD -> adjustZoom(1);
			case KEY_MINUS, KEY_KP_SUBTRACT -> adjustZoom(-1);
			default -> {
				return false;
			}
		}
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

		if (mode == DragMode.PAN) return true;

		// A drag that never really moved is a click, and a click on one chunk
		// toggles it - otherwise clicking an already-selected chunk to drop it
		// would be impossible, since the box only ever adds.
		if (dragTotalDistance <= DRAG_THRESHOLD) {
			toggleSelection(chunkAt(mouseX, mouseY));
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
