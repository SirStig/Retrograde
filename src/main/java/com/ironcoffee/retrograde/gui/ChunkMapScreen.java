package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.config.RetrogradeConfig;
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
import net.minecraft.world.item.Items;
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

import static com.ironcoffee.retrograde.gui.InputCodes.*;

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
	/** Vanilla renders every item icon at 16x16; nothing lets you ask for another size. */
	private static final int ITEM_ICON_SIZE = 16;
	private static final int TITLE_PAD_H = 8;
	private static final int TITLE_PAD_V = 4;
	private static final int MIN_ZOOM = -2;
	private static final int MAX_ZOOM = 4;
	private static final int DRAG_THRESHOLD = 5;

	// Mouse-button and key numbers now live in InputCodes (see that file for
	// why), imported statically below.
	/** How far one arrow-key press slides the map, in screen pixels. */
	private static final int KEY_PAN_PIXELS = 48;

	/**
	 * Panel sizes are preferences, not promises. Everything below is what a
	 * panel gets when there's room; init() shrinks them toward the MIN_
	 * values, and drops rows, when there isn't.
	 *
	 * The reason this is not a set of fixed constants any more: at 1366x768
	 * with GUI scale 3 the window is 455x256 effective pixels, and the old
	 * fixed layout wanted 253 of those 256 rows for the left column alone,
	 * plus 342 of 455 columns once the filter was open. The map - the thing
	 * the screen is for - was down to a 113px strip, and at scale 4 the
	 * panels ran off the bottom outright.
	 */
	private static final int PANEL_WIDTH = 152;
	private static final int MIN_PANEL_WIDTH = 104;
	private static final int PANEL_PAD = 6;
	private static final int LINE_H = 10;
	private static final int ORE_ROW_H = 18;
	private static final int ORE_ICON = 16;
	/** Preferred visible rows in the info panel's scrollable ore list before the column runs out of room. */
	private static final int ORE_ROWS = 2;
	private static final int ORE_SCROLLBAR_W = 3;
	/** Scroll wheel steps in pixels, one ore row per notch rather than a fixed fraction of the list. */
	private static final int ORE_SCROLL_STEP = ORE_ROW_H;
	private static final int ORE_PANEL_GAP = 6;
	private static final int ACTION_BUTTON_H = 18;
	private static final int ACTION_BUTTON_GAP = 4;
	/** Chunk Manipulation on its own row, then Find and Clear sharing one. */
	private static final int ACTION_ROWS = 2;

	/**
	 * The filter panel and the ore breakdown share one slot, so only one of
	 * the two is ever open - two floating panels stacked on the same pixels
	 * would be unreadable, and both of them are about the same thing anyway.
	 * Where that slot is depends on the window: beside the info panel when
	 * there's room, otherwise pinned to the right edge under the icon
	 * cluster, otherwise on top of the left column.
	 */
	private static final int FILTER_PANEL_WIDTH = 176;
	private static final int MIN_SIDE_PANEL_WIDTH = 116;
	private static final int FILTER_ROW_H = 18;
	/** Floor for a shrinking filter row: below this a vanilla button's label clips. */
	private static final int MIN_FILTER_ROW_H = 13;
	private static final int FILTER_ROW_GAP = 3;

	/**
	 * Most of the window has to stay map. Chrome past this fraction of the
	 * width pushes the secondary panel off the left column and onto the
	 * right edge instead of letting it keep eating the middle.
	 */
	private static final float MAX_CHROME_FRACTION = 0.55f;
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
	// Slime green, and deliberately weaker than the selection and match
	// tints: it's a permanent property of the coordinates, on screen the
	// whole time, so it has to sit behind anything you're actively doing.
	private static final int COLOR_SLIME_TINT = 0x4544AA44;
	private static final int COLOR_SLIME_EDGE = 0x8055CC55;
	private static final int COLOR_MATCH_TINT = 0x55D8A03C;
	private static final int COLOR_MATCH_EDGE = 0xFFF0C060;
	private static final int COLOR_GEAR_ICON = 0xFFD8DEE4;
	private static final int COLOR_GEAR_HOLE = 0xFF232629;

	// ----- overlay system palette, shared by ManipulationOverlay and SettingsOverlay -----
	//
	// Deliberately no full-screen scrim: these overlays are meant to read as
	// panels floating over the still-visible, still-live map - drawn over it
	// rather than replacing it, per the whole point of this being an overlay
	// and not a screen swap - so the only visual weight they get is their own
	// shadow and border, not a dimmed-out world behind them.
	static final int COLOR_OVERLAY_BG = 0xF2181B1F;
	static final int COLOR_OVERLAY_EDGE_LIGHT = 0x50FFFFFF;
	static final int COLOR_OVERLAY_EDGE_DARK = 0x90000000;
	static final int COLOR_OVERLAY_SHADOW = 0xA0000000;
	static final int COLOR_OVERLAY_ACCENT = 0xFF6FA8F0;
	static final int COLOR_OVERLAY_HEADING = 0xFF8AA0B4;
	static final int COLOR_OVERLAY_LABEL = 0xFFAEB6BE;
	static final int COLOR_OVERLAY_VALUE = 0xFFF0F0F0;
	static final int COLOR_OVERLAY_DIVIDER = 0x40FFFFFF;
	static final int COLOR_OVERLAY_WARN = 0xFFE06060;
	static final int COLOR_OVERLAY_OK = 0xFF6FCF6F;
	static final int COLOR_OVERLAY_CAUTION = 0xFFD8B24C;
	static final int COLOR_OVERLAY_SCROLLBAR_TRACK = 0x60FFFFFF;
	static final int COLOR_OVERLAY_SCROLLBAR_THUMB = 0xC0C8D4E0;

	// ----- map mode palettes -----
	/** Flat fill for a chunk whose data hasn't been read yet, in any data mode. */
	private static final int COLOR_MODE_UNKNOWN = 0xFF2A2A2A;
	/** Touched mode: deliberately two flat colours and nothing else. */
	private static final int COLOR_MODE_TOUCHED = 0xFF3E8E3E;
	private static final int COLOR_MODE_UNTOUCHED = 0xFF394453;
	/**
	 * Ore density ramp, coldest to hottest, interpolated between stops. Five
	 * stops rather than a smooth hue sweep because a sweep through green
	 * reads as "more" in one half and "different" in the other.
	 */
	private static final int[] ORE_RAMP = {
		0xFF223355, 0xFF2E6E8E, 0xFF49A05A, 0xFFD8B24C, 0xFFD85A3C
	};

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

	/**
	 * PAN_OR_CLICK is a bare left press, which won't know which it was until
	 * the button comes back up: it pans while it's held, and if it never
	 * travelled it turns out to have been a click on one chunk.
	 */
	private enum DragMode { NONE, PAN, PAN_OR_CLICK, SELECT_ADD, SELECT_REMOVE }

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

	// ----- responsive layout, all resolved in init() -----

	/** Actual width of the info and action panels this frame. */
	private int panelW = PANEL_WIDTH;
	/** Actual width of whichever secondary panel is open. */
	private int sidePanelW = FILTER_PANEL_WIDTH;
	/** Top-left of the secondary panel slot, wherever it ended up fitting. */
	private int sidePanelX, sidePanelY;
	/** Visible rows of the info panel's scrollable ore list: 2 normally, fewer when short. */
	private int oreRows = ORE_ROWS;
	/** True when the secondary panel had to be laid over the left column. */
	private boolean sidePanelOverlaps;

	// ----- map modes -----

	/**
	 * What the chunk squares are coloured by.
	 *
	 * TERRAIN is the map; the rest are the map answering one question each,
	 * and they replace the terrain rather than tinting over it. Tinting was
	 * the obvious first idea and it's wrong: a translucent wash over a
	 * screenshot of the world is unreadable at a glance precisely because
	 * the terrain underneath is doing its job. A biome map is only useful if
	 * two chunks of the same biome look identical, which means the terrain
	 * has to go.
	 */
	private enum MapMode {
		// Where an item says it better than a letter, use the item. A grass
		// block *is* what the terrain mode draws, and an ore block *is* what
		// the ore mode counts, so those two get read without the tooltip; the
		// remaining two are abstractions with no block to point at, and a
		// letter is honest about that rather than picking a vaguely
		// nature-coloured item and hoping.
		TERRAIN(Items.GRASS_BLOCK),
		BIOME("B"),
		ORES(Items.DIAMOND_ORE),
		TOUCHED("T");

		/** Shown on the button when there's no {@link #item}; null otherwise. */
		final String letter;
		/** Rendered over the button in place of a label; null when {@link #letter} is used. */
		final ItemStack item;

		MapMode(String letter) {
			this.letter = letter;
			this.item = null;
		}

		MapMode(net.minecraft.world.item.Item item) {
			this.letter = null;
			this.item = new ItemStack(item);
		}
	}

	private MapMode mapMode = MapMode.TERRAIN;
	private Button mapModeButton;
	/**
	 * Highest ore count among the chunks on screen this frame, which is what
	 * the density ramp normalises against. Per-frame and adaptive on purpose:
	 * a fixed ceiling makes every chunk in a diamond-poor region the same
	 * shade of cold, and the question the mode answers is "which of these" far
	 * more often than "how many".
	 */
	private int oreScaleMax = 1;

	// ----- chunk filter -----

	/** Which chunks the filter is allowed to consider. */
	private enum Scope { ON_SCREEN, RADIUS_4, RADIUS_8, RADIUS_16, MAPPED }

	/** Whether the filter wants chunks you've been in, ones you haven't, or both. */
	private enum Touched { ANY, YES, NO }

	/** Whether the picked ore has to be present or absent. */
	private enum OreRule { HAS, WITHOUT }

	/** Whether the filter wants slime chunks, non-slime chunks, or both. */
	private enum Slime { ANY, YES, NO }

	private boolean filterOpen;
	private int filterX, filterY, filterH;
	/** Row metrics shrink on a short window; see {@link #initFilterPanel}. */
	private int filterRowH = FILTER_ROW_H;
	private int filterRowGap = FILTER_ROW_GAP;
	private Scope filterScope = Scope.ON_SCREEN;
	private Touched filterTouched = Touched.ANY;
	private OreRule filterOreRule = OreRule.HAS;
	private Slime filterSlime = Slime.ANY;
	/** Both are -1 for "any", otherwise an index into the list below it. */
	private int filterBiomeIndex = -1;
	private int filterOreIndex = -1;
	private List<ResourceLocation> biomeChoices = List.of();
	private List<Block> oreChoices = List.of();
	/** What currently matches. Highlighted on the map, and what "Select" acts on. */
	private Set<ChunkPos> matches = Set.of();
	private long filterRefreshedAt;

	private Button manipulateButton;
	private Button clearButton;
	private Button findButton;
	private Button settingsButton;

	/** The two panels that float over the map rather than swapping the screen out. */
	private final ManipulationOverlay manipulationOverlay = new ManipulationOverlay(this);
	private final SettingsOverlay settingsOverlay = new SettingsOverlay(this);

	/** Scroll position of the info panel's ore list, in pixels; see {@link #drawOreSection}. */
	private int oreScrollPx;
	/** Which chunk {@link #oreScrollPx} belongs to, so hovering a new one resets it to the top. */
	private ChunkPos oreScrollChunk;
	private int oreListViewX, oreListViewY, oreListViewW, oreListViewH;
	private Button filterScopeButton;
	private Button filterTouchedButton;
	private Button filterBiomeButton;
	private Button filterOreButton;
	private Button filterOreRuleButton;
	private Button filterSlimeButton;
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

		clusterW = ICON_SIZE * 6 + ICON_GAP * 5;
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
		// Cycles rather than opening a picker: there are four modes, switching
		// between them is something you do while comparing, and a menu between
		// each comparison is a menu you stop using.
		mapModeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleMapMode())
			.bounds(x, clusterY, ICON_SIZE, ICON_SIZE).build());
		// Sets the real label and tooltip; the builder's is a placeholder
		// because what goes there depends on which mode is current.
		syncMapModeButton();
		x += ICON_SIZE + ICON_GAP;
		// Empty label: the gear itself is drawn in drawIconOverlays(), a real
		// shape built from Painter primitives rather than a font glyph that
		// goes fuzzy or lopsided depending on what font pack is loaded.
		settingsButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleSettings())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.settings")))
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

		// Panels take their preferred width only while that leaves the map the
		// bulk of the window; below that they shrink, and they never go under
		// MIN_PANEL_WIDTH because a narrower one can't hold a biome name.
		panelW = Math.max(MIN_PANEL_WIDTH, Math.min(PANEL_WIDTH, Math.round(width * 0.30f)));
		panelW = Math.min(panelW, Math.max(1, width - CHIP_MARGIN * 2));
		panelX = CHIP_MARGIN;
		infoPanelY = CHIP_MARGIN;

		actionPanelH = PANEL_PAD * 2 + LINE_H * 2 + 4
			+ ACTION_ROWS * ACTION_BUTTON_H + (ACTION_ROWS - 1) * ACTION_BUTTON_GAP;

		// Everything from the top margin down to the controls hint is the
		// column's budget. When it doesn't fit, the ore list viewport is what
		// shrinks first - it scrolls now, so a shorter viewport still reaches
		// every ore, whereas a clipped action panel means unreachable buttons.
		int columnBudget = hintY - infoPanelY - 4;
		oreRows = ORE_ROWS;
		while (oreRows > 0 && infoPanelHeight(oreRows) + 6 + actionPanelH > columnBudget) {
			oreRows--;
		}
		infoPanelH = infoPanelHeight(oreRows);
		actionPanelY = infoPanelY + infoPanelH + 6;

		layOutSidePanelSlot();

		int buttonX = panelX + PANEL_PAD;
		int buttonW = panelW - PANEL_PAD * 2;
		int buttonY = actionPanelY + PANEL_PAD + LINE_H * 2 + 4;
		// One entry point for everything that changes a chunk, rather than a
		// stack of verbs on the map: it keeps this panel two rows tall, and
		// the operations that need explaining get a screen with room to.
		manipulateButton = addActionButton(buttonX, buttonY, buttonW,
			"gui.retrograde.chunk_map.action.manipulate",
			"gui.retrograde.chunk_map.action.manipulate.tip", b -> openManipulation());
		buttonY += ACTION_BUTTON_H + ACTION_BUTTON_GAP;
		int halfW = (buttonW - ACTION_BUTTON_GAP) / 2;
		findButton = addActionButton(buttonX, buttonY, halfW, "gui.retrograde.chunk_map.action.find",
			"gui.retrograde.chunk_map.action.find.tip", b -> toggleFilter());
		clearButton = addActionButton(buttonX + halfW + ACTION_BUTTON_GAP, buttonY,
			buttonW - halfW - ACTION_BUTTON_GAP, "gui.retrograde.chunk_map.action.clear",
			"gui.retrograde.chunk_map.action.clear.tip", b -> clearSelection());

		initFilterPanel();
		manipulationOverlay.layout(width, height);
		settingsOverlay.layout(width, height);
		refreshSelectionStats();
	}

	/** Coordinates, status, biome, divider, ore heading, then {@code rows} of visible ore-list rows. */
	private int infoPanelHeight(int rows) {
		return PANEL_PAD * 2 + LINE_H * 4 + 5 + ORE_ROW_H * rows;
	}

	/**
	 * Decides where the chunk filter gets to live, in order of preference:
	 *
	 * <ol>
	 *   <li>Beside the info panel, while the two together stay inside
	 *       {@link #MAX_CHROME_FRACTION} of the window.</li>
	 *   <li>Pinned to the right edge under the icon cluster. Costs the same
	 *       pixels but takes them off the edge instead of out of the middle,
	 *       so what's left of the map is one usable area rather than a
	 *       gutter.</li>
	 *   <li>Over the left column, when the window is too narrow for both at
	 *       any placement. The secondary panel wins because it's only there
	 *       while you asked for it.</li>
	 * </ol>
	 */
	private void layOutSidePanelSlot() {
		sidePanelW = Math.min(FILTER_PANEL_WIDTH,
			Math.max(MIN_SIDE_PANEL_WIDTH, Math.round(width * 0.34f)));
		sidePanelW = Math.min(sidePanelW, Math.max(1, width - CHIP_MARGIN * 2));

		int beside = panelX + panelW + ORE_PANEL_GAP;
		int rightEdge = width - CHIP_MARGIN - sidePanelW;
		sidePanelOverlaps = false;

		if (beside + sidePanelW <= width * MAX_CHROME_FRACTION) {
			sidePanelX = beside;
			sidePanelY = infoPanelY;
		} else if (rightEdge >= beside) {
			sidePanelX = rightEdge;
			sidePanelY = clusterY + clusterH + ORE_PANEL_GAP;
		} else {
			sidePanelX = panelX;
			sidePanelY = infoPanelY;
			sidePanelOverlaps = true;
		}
	}

	/**
	 * The filter's controls are real widgets rather than hand-drawn hit
	 * boxes, so they get vanilla's hover and click feedback for free - but
	 * that means they exist whether or not the panel is open, and are simply
	 * hidden when it isn't.
	 */
	private void initFilterPanel() {
		filterX = sidePanelX;
		filterY = sidePanelY;
		int rowW = sidePanelW - PANEL_PAD * 2;
		int rowX = filterX + PANEL_PAD;

		// Eight rows of controls, and no row here is droppable the way the ore
		// icon grid was - each one is a criterion, and a filter missing a
		// criterion is a filter that quietly can't answer a question. So the
		// rows get shorter instead, down to a floor that still leaves a
		// vanilla button legible, and only then does the panel start from the
		// top margin regardless of where the slot wanted it.
		int rowsTall = 8;
		int chrome = PANEL_PAD * 2 + (LINE_H + 2) * 2;
		filterRowH = FILTER_ROW_H;
		filterRowGap = FILTER_ROW_GAP;
		while (filterRowH > MIN_FILTER_ROW_H
			&& filterY + chrome + rowsTall * (filterRowH + filterRowGap) > hintY) {
			filterRowH--;
			if (filterRowGap > 1) filterRowGap--;
		}
		if (filterY > CHIP_MARGIN
			&& filterY + chrome + rowsTall * (filterRowH + filterRowGap) > hintY) {
			// The slot put it under the icon cluster to keep the map's middle
			// clear; that's a nicety, and fitting on screen isn't.
			filterY = CHIP_MARGIN;
		}

		int y = filterY + PANEL_PAD + LINE_H + 2;

		filterScopeButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.scope.tip", b -> cycleScope());
		y += filterRowH + filterRowGap;
		filterTouchedButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.touched.tip", b -> cycleTouched());
		y += filterRowH + filterRowGap;
		filterSlimeButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.slime.tip", b -> cycleSlime());
		y += filterRowH + filterRowGap;
		filterBiomeButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.biome.tip", b -> cycleBiome());
		y += filterRowH + filterRowGap;
		filterOreButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.ore.tip", b -> cycleOre());
		y += filterRowH + filterRowGap;
		filterOreRuleButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.ore_rule.tip", b -> cycleOreRule());
		y += filterRowH + filterRowGap + LINE_H + 2;

		filterSelectButton = addFilterButton(rowX, y, rowW, "gui.retrograde.chunk_map.filter.select.tip", b -> selectMatches());
		y += filterRowH + filterRowGap;
		int half = (rowW - filterRowGap) / 2;
		filterRestoreButton = addFilterButton(rowX, y, half, "gui.retrograde.chunk_map.filter.restore.tip", b -> restoreLastSelection());
		filterCloseButton = addFilterButton(rowX + half + filterRowGap, y, rowW - half - filterRowGap,
			"gui.retrograde.chunk_map.filter.close.tip", b -> toggleFilter());
		y += filterRowH;

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
			.bounds(x, y, w, filterRowH)
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
		if (manipulationOverlay.isOpen()) {
			manipulationOverlay.tick();
		}
	}

	private void syncActionButtons() {
		int count = selection.size();
		boolean any = count > 0;
		manipulateButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.manipulate", count));
		clearButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.clear"));
		findButton.setMessage(Component.translatable(filterOpen
			? "gui.retrograde.chunk_map.action.find.close"
			: "gui.retrograde.chunk_map.action.find"));
		manipulateButton.active = any;
		clearButton.active = any;

		// When the window was too narrow to give the secondary panel its own
		// column it sits on top of this one, so the buttons underneath have to
		// stop taking clicks meant for the panel covering them.
		boolean buried = sidePanelOverlaps && filterOpen;
		manipulateButton.visible = !buried;
		clearButton.visible = !buried;
		findButton.visible = !buried;
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

	/** Package-private: the overlay classes hit-test their own bounds against the cursor with this too. */
	boolean isOverChip(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private boolean isOverMap(double mouseX, double mouseY) {
		if (isOverChip(mouseX, mouseY, titleX, titleY, titleW, titleH)) return false;
		if (isOverChip(mouseX, mouseY, clusterX, clusterY, clusterW, clusterH)) return false;
		if (isOverChip(mouseX, mouseY, hintX, hintY, hintW, hintH)) return false;
		int columnH = actionPanelY + actionPanelH - infoPanelY;
		if (isOverChip(mouseX, mouseY, panelX, infoPanelY, panelW, columnH)) return false;
		if (filterOpen && isOverChip(mouseX, mouseY, filterX, filterY, sidePanelW, filterH)) return false;
		// The manipulation and settings overlays float over the map and take
		// their own input - see handlePress/handleDrag/handleScroll, which
		// check these same two bounds before falling through to panning.
		if (manipulationOverlay.isOpen() && manipulationOverlay.contains(mouseX, mouseY)) return false;
		if (settingsOverlay.isOpen() && settingsOverlay.contains(mouseX, mouseY)) return false;
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
		Painter painter = new Painter(guiGraphics);
		draw(painter, mouseX, mouseY);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		drawIconOverlays(painter);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics) {
	}
	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		Painter painter = new Painter(guiGraphics);
		draw(painter, mouseX, mouseY);
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
		drawIconOverlays(painter);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}
	*///?}

	/**
	 * Drawn after super.render()/extractRenderState() rather than before, so
	 * it lands on top of the button's own bezel and hover tint the same way
	 * vanilla's own text label would - this replaces where a label would sit,
	 * it doesn't need to duck under one.
	 */
	private void drawIconOverlays(Painter painter) {
		if (mapModeButton != null && mapMode.item != null) {
			// Items always render 16x16 regardless of the button, so centre
			// rather than assuming the two sizes agree.
			painter.item(mapMode.item,
				mapModeButton.getX() + (mapModeButton.getWidth() - ITEM_ICON_SIZE) / 2,
				mapModeButton.getY() + (mapModeButton.getHeight() - ITEM_ICON_SIZE) / 2);
		}
		if (settingsButton == null) return;
		int cx = settingsButton.getX() + settingsButton.getWidth() / 2;
		int cy = settingsButton.getY() + settingsButton.getHeight() / 2;
		int radius = Math.max(4, Math.min(settingsButton.getWidth(), settingsButton.getHeight()) / 2 - 3);
		painter.gearIcon(cx, cy, radius, COLOR_GEAR_ICON, COLOR_GEAR_HOLE);
	}

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
		// Read once per frame rather than per chunk: both are constant for
		// the whole draw, and there can be a few hundred chunks on screen.
		boolean slimeChunks = RetrogradeConfig.showSlimeChunks() && isOverworld(dimension);
		long worldSeed = serverLevel.getSeed();

		List<VisibleChunk> visible = computeVisibleChunks();
		if (mapMode == MapMode.ORES) {
			oreScaleMax = highestOreCount(visible);
		}

		for (VisibleChunk chunk : visible) {
			dataCache.request(minecraft, server, serverLevel, chunk.pos());
			ChunkMapDataCache.Status status = dataCache.statusOf(chunk.pos());
			int x1 = chunk.screenX();
			int y1 = chunk.screenY();
			int x2 = x1 + chunk.size();
			int y2 = y1 + chunk.size();

			if (status == ChunkMapDataCache.Status.UNGENERATED) {
				// Same grey in every mode. Nothing generated there, so there's
				// no biome to colour and no ore to count - saying so once,
				// consistently, beats four different ways to say "no data".
				painter.fill(x1, y1, x2, y2, COLOR_UNGENERATED);
			} else if (status != ChunkMapDataCache.Status.READY) {
				painter.fill(x1, y1, x2, y2, COLOR_PENDING);
			} else if (mapMode == MapMode.TERRAIN) {
				painter.chunkTexture(dataCache.textureOf(chunk.pos()), x1, y1, chunk.size());
			} else {
				painter.fill(x1, y1, x2, y2, modeColor(chunk.pos(), dimension, tracker));
			}

			// Player tint answers "where am I", not "what's in this chunk" - an
			// orientation marker rather than a data overlay - so it draws
			// regardless of status. In practice the chunk you're standing in
			// is never UNGENERATED, but it is briefly PENDING (map just
			// opened, or you stepped into a chunk the cache hasn't sampled
			// yet), and losing your own position marker for that one frame
			// would be worse than a flat-grey square wearing a yellow tint.
			if (chunk.pos().equals(playerChunk)) {
				painter.fill(x1, y1, x2, y2, COLOR_PLAYER_TINT);
			} else if (status == ChunkMapDataCache.Status.READY && mapMode != MapMode.TOUCHED
				&& tracker.statusOf(dimension, chunk.pos()) == ChunkTracker.Status.TOUCHED) {
				// Skipped in TOUCHED mode: the fill underneath already says it,
				// and a wash over half the squares in a two-colour map is noise.
				// Gated on READY otherwise: touched-or-not describes what's in
				// the chunk, same as slime below, and a green wash over a grey
				// "nothing here yet" or "still loading" square reads as broken
				// rather than as information.
				painter.fill(x1, y1, x2, y2, COLOR_TOUCHED_TINT);
			}

			// Under the grid lines and everything interactive, because this is
			// a property of the coordinates rather than anything you did or
			// selected - it should read as part of the terrain, not as state.
			// Gated on READY: the slime pattern is knowable from the seed
			// alone without reading the chunk, but painting it over an
			// UNGENERATED or PENDING square dresses up a placeholder as real
			// information, which is the exact bug this whole fix is for.
			if (status == ChunkMapDataCache.Status.READY && slimeChunks && isSlimeChunk(worldSeed, chunk.pos())) {
				painter.fill(x1, y1, x2, y2, COLOR_SLIME_TINT);
				if (chunk.size() >= CHUNK_BLOCKS) {
					painter.outline(x1, y1, chunk.size(), chunk.size(), COLOR_SLIME_EDGE);
				}
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
		// Drawn last, over everything else on the map: both are the "on top of
		// the map" overlays the rest of this screen defers to when they're open.
		if (manipulationOverlay.isOpen()) {
			manipulationOverlay.draw(painter, mouseX, mouseY);
		}
		if (settingsOverlay.isOpen()) {
			settingsOverlay.draw(painter, mouseX, mouseY);
		}
	}

	private void cycleMapMode() {
		mapMode = next(MapMode.values(), mapMode.ordinal());
		syncMapModeButton();
	}

	private void syncMapModeButton() {
		if (mapModeButton == null) return;
		// An item-icon mode gets an empty label and is drawn in
		// drawIconOverlays(); a letter mode keeps the button's own text so it
		// picks up the hover and disabled tints for free.
		mapModeButton.setMessage(mapMode.letter == null ? Component.empty() : Component.literal(mapMode.letter));
		// The tooltip names the mode you're in rather than the one you'd get by
		// clicking. A cycling button with four stops can't usefully promise
		// where it lands, and "what am I looking at" is the question someone
		// hovering a one-letter icon is actually asking.
		mapModeButton.setTooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.mode",
			Component.translatable("gui.retrograde.chunk_map.mode." + mapMode.name().toLowerCase(java.util.Locale.ROOT)))));
	}

	/**
	 * The flat colour a chunk gets in whichever non-terrain mode is active.
	 * Only called for chunks the cache has read, so "no data" here means the
	 * read produced no inspection rather than that the chunk isn't generated.
	 */
	private int modeColor(ChunkPos pos, ResourceKey<Level> dimension, ChunkTracker tracker) {
		switch (mapMode) {
			case TOUCHED:
				return tracker.statusOf(dimension, pos) == ChunkTracker.Status.TOUCHED
					? COLOR_MODE_TOUCHED : COLOR_MODE_UNTOUCHED;
			case BIOME: {
				ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
				if (inspection == null || inspection.biome() == null) return COLOR_MODE_UNKNOWN;
				return biomeColor(inspection.biome());
			}
			case ORES: {
				ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
				if (inspection == null) return COLOR_MODE_UNKNOWN;
				return rampColor(totalOres(inspection) / (float) Math.max(1, oreScaleMax));
			}
			default:
				return COLOR_MODE_UNKNOWN;
		}
	}

	private int highestOreCount(List<VisibleChunk> visible) {
		int max = 1;
		for (VisibleChunk chunk : visible) {
			if (dataCache.statusOf(chunk.pos()) != ChunkMapDataCache.Status.READY) continue;
			ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(chunk.pos());
			if (inspection != null) max = Math.max(max, totalOres(inspection));
		}
		return max;
	}

	private static int totalOres(ChunkResourceInfo.Inspection inspection) {
		int total = 0;
		for (ChunkResourceInfo.Entry entry : inspection.ores()) {
			total += entry.count();
		}
		return total;
	}

	/** Position along {@link #ORE_RAMP}, linearly interpolated between stops. */
	private static int rampColor(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float scaled = t * (ORE_RAMP.length - 1);
		int lo = (int) scaled;
		int hi = Math.min(ORE_RAMP.length - 1, lo + 1);
		return lerpColor(ORE_RAMP[lo], ORE_RAMP[hi], scaled - lo);
	}

	private static int lerpColor(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/**
	 * A stable colour per biome id, derived from the id itself rather than
	 * from a table.
	 *
	 * A hand-picked palette would look better and would be wrong the moment a
	 * mod adds a biome, which - for a mod whose whole purpose is adding mods
	 * to an existing world - is the normal case rather than the edge one.
	 * Hashing gives every biome a colour, including ones that didn't exist
	 * when this was written, and the same one every time you open the map.
	 *
	 * Hue is where the entropy goes; saturation and lightness get narrow
	 * bands so that no biome comes out near-black or near-white and every
	 * pair of adjacent biomes is told apart by hue, which is the channel
	 * that survives being a 12-pixel square.
	 */
	private static int biomeColor(ResourceLocation biome) {
		int hash = biome.toString().hashCode();
		float hue = ((hash >>> 8) % 360) / 360f;
		float saturation = 0.45f + ((hash >>> 20) & 0x0F) / 60f;
		float lightness = 0.38f + ((hash >>> 26) & 0x0F) / 90f;
		return 0xFF000000 | (hslToRgb(hue, saturation, lightness) & 0xFFFFFF);
	}

	private static int hslToRgb(float h, float s, float l) {
		float c = (1 - Math.abs(2 * l - 1)) * s;
		float hp = h * 6f;
		float x = c * (1 - Math.abs(hp % 2 - 1));
		float r = 0, g = 0, b = 0;
		if (hp < 1) { r = c; g = x; }
		else if (hp < 2) { r = x; g = c; }
		else if (hp < 3) { g = c; b = x; }
		else if (hp < 4) { g = x; b = c; }
		else if (hp < 5) { r = x; b = c; }
		else { r = c; b = x; }
		float m = l - c / 2;
		return (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
	}

	private static int channel(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
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
		drawChip(painter, panelX, infoPanelY, panelW, infoPanelH);
		// Recomputed below if the list actually gets drawn this frame; zeroed
		// here so a chunk with no ore data leaves no phantom hit-box behind.
		oreListViewW = 0;
		oreListViewH = 0;

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
			painter.text(font, tag, panelX + panelW - PANEL_PAD - font.width(tag), y, COLOR_SELECTED_EDGE);
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
		// Right-aligned on the status row rather than a row of its own: the
		// panel's height is already what decides whether the ore grid fits.
		if (RetrogradeConfig.showSlimeChunks() && isOverworld(dimension)) {
			ServerLevel slimeLevel = server.getLevel(dimension);
			if (slimeLevel != null && isSlimeChunk(slimeLevel.getSeed(), pos)) {
				Component tag = Component.translatable("gui.retrograde.chunk_map.panel.slime");
				painter.text(font, tag, panelX + panelW - PANEL_PAD - font.width(tag), y, COLOR_SLIME_EDGE | 0xFF000000);
			}
		}
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

		painter.fill(textX, y + 1, panelX + panelW - PANEL_PAD, y + 2, COLOR_DIVIDER);
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

		// Too short a window for even a one-row viewport - the heading above
		// still carries the count, and there's nowhere left to scroll a list
		// into, so the section simply isn't drawn.
		if (oreRows <= 0) return;

		// A new chunk under the cursor starts its list at the top rather than
		// wherever the last one happened to leave it.
		if (!pos.equals(oreScrollChunk)) {
			oreScrollChunk = pos;
			oreScrollPx = 0;
		}

		int w = panelW - PANEL_PAD * 2;
		int h = oreRows * ORE_ROW_H;
		oreScrollPx = drawOreList(painter, ores, textX, y, w, h, oreScrollPx);
		oreListViewX = textX;
		oreListViewY = y;
		oreListViewW = w;
		oreListViewH = h;
	}

	/**
	 * Every ore for whatever this list is describing (a single chunk here,
	 * the whole selection in ManipulationOverlay), one row each with its
	 * icon, name and count, scrolled in place rather than capped with a
	 * "show more" panel. Package-private so ManipulationOverlay's selection
	 * breakdown draws through the exact same rows-plus-scrollbar code - each
	 * caller keeps its own scroll offset and view bounds, since the info
	 * panel and the overlay are never showing the same list.
	 *
	 * Clipped with the engine's own scissor rather than just trusting the
	 * caller's row math: a scroll offset one row short of the end would
	 * otherwise draw half a row past the viewport and into whatever panel
	 * sits below it.
	 *
	 * @return the scroll offset actually used, clamped to the list's real
	 *         length - the caller stores this back so a chunk that just lost
	 *         a few ores from a regen can't leave the list scrolled past its
	 *         own end.
	 */
	int drawOreList(Painter painter, List<ChunkResourceInfo.Entry> ores, int x, int y, int w, int h, int scrollPx) {
		int contentH = ores.size() * ORE_ROW_H;
		int maxScroll = Math.max(0, contentH - h);
		int scroll = Math.max(0, Math.min(scrollPx, maxScroll));

		boolean scrollable = contentH > h;
		int listW = scrollable ? w - ORE_SCROLLBAR_W - 3 : w;
		int countX = x + listW;

		painter.scissor(x, y, x + w, y + h);
		int firstRow = scroll / ORE_ROW_H;
		int rowY = y - (scroll % ORE_ROW_H);
		for (int i = firstRow; i < ores.size() && rowY < y + h; i++) {
			ChunkResourceInfo.Entry entry = ores.get(i);
			ItemStack stack = new ItemStack(entry.block());
			painter.item(stack, x, rowY);
			String count = String.valueOf(entry.count());
			int thisCountX = countX - font.width(count);
			painter.text(font, count, thisCountX, rowY + 4, 0xFFFFFFFF);
			int nameX = x + ORE_ICON + 4;
			painter.text(font, trimTo(stack.getHoverName().getString(), thisCountX - nameX - 4),
				nameX, rowY + 4, COLOR_BIOME_TEXT);
			rowY += ORE_ROW_H;
		}
		painter.resetScissor();

		if (scrollable) {
			painter.scrollbar(x + w - ORE_SCROLLBAR_W, y, ORE_SCROLLBAR_W, h, contentH, h, scroll,
				COLOR_OVERLAY_SCROLLBAR_TRACK, COLOR_OVERLAY_SCROLLBAR_THUMB);
		}
		return scroll;
	}

	/** True while the cursor sits over the info panel's ore list, for scroll routing. */
	private boolean isOverOreList(double mouseX, double mouseY) {
		return oreListViewW > 0 && oreListViewH > 0
			&& isOverChip(mouseX, mouseY, oreListViewX, oreListViewY, oreListViewW, oreListViewH);
	}

	// ----- chunk filter -----

	private void toggleFilter() {
		if (filterOpen) {
			closeFilter();
			return;
		}
		filterOpen = true;
		// Only one floating extra panel at a time - opening Find puts away
		// whichever overlay was up, the same way opening an overlay closes
		// Find (see openManipulation/toggleSettings).
		manipulationOverlay.close();
		settingsOverlay.close();
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

	private void cycleSlime() {
		filterSlime = next(Slime.values(), filterSlime.ordinal());
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
		// Same rule the map draws by: the slime maths produces a perfectly
		// convincing pattern in any dimension and means nothing outside the
		// overworld, so off there the criterion matches everything rather than
		// quietly matching a lie.
		ServerLevel slimeLevel = isOverworld(dimension) ? server.getLevel(dimension) : null;
		long slimeSeed = slimeLevel == null ? 0L : slimeLevel.getSeed();

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

			if (filterSlime != Slime.ANY && slimeLevel != null) {
				boolean slime = isSlimeChunk(slimeSeed, pos);
				if (filterSlime == Slime.YES && !slime) continue;
				if (filterSlime == Slime.NO && slime) continue;
			}

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
		for (Button button : List.of(filterScopeButton, filterTouchedButton, filterSlimeButton, filterBiomeButton,
				filterOreButton, filterOreRuleButton, filterSelectButton, filterRestoreButton, filterCloseButton)) {
			button.visible = filterOpen;
		}
		if (!filterOpen) return;

		filterScopeButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.scope",
			Component.translatable("gui.retrograde.chunk_map.filter.scope." + filterScope.name().toLowerCase(java.util.Locale.ROOT))));
		filterTouchedButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.touched",
			Component.translatable("gui.retrograde.chunk_map.filter.touched." + filterTouched.name().toLowerCase(java.util.Locale.ROOT))));
		filterSlimeButton.setMessage(Component.translatable("gui.retrograde.chunk_map.filter.slime",
			Component.translatable("gui.retrograde.chunk_map.filter.slime." + filterSlime.name().toLowerCase(java.util.Locale.ROOT))));
		// Greyed out rather than hidden outside the overworld: a row that comes
		// and goes as you walk through a portal is a row you can't find again.
		filterSlimeButton.active = minecraft != null && minecraft.player != null
			&& isOverworld(minecraft.player.level().dimension());

		ResourceLocation biome = choice(biomeChoices, filterBiomeIndex);
		filterBiomeButton.setMessage(biome == null
			? Component.translatable("gui.retrograde.chunk_map.filter.biome.any")
			: Component.literal(trimTo(biomeDisplayName(biome), sidePanelW - PANEL_PAD * 2 - 8)));
		filterBiomeButton.active = !biomeChoices.isEmpty();

		Block ore = choice(oreChoices, filterOreIndex);
		filterOreButton.setMessage(ore == null
			? Component.translatable("gui.retrograde.chunk_map.filter.ore.any")
			: Component.literal(trimTo(new ItemStack(ore).getHoverName().getString(), sidePanelW - PANEL_PAD * 2 - 8)));
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
		drawChip(painter, filterX, filterY, sidePanelW, filterH);
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
		drawChip(painter, panelX, actionPanelY, panelW, actionPanelH);

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
		return trimTo(text, panelW - PANEL_PAD * 2);
	}

	/** Package-private: the overlay classes trim their own rows against this same font. */
	String trimTo(String text, int budget) {
		if (font.width(text) <= budget) return text;
		return font.plainSubstrByWidth(text, Math.max(0, budget - font.width("..."))) + "...";
	}

	/** Package-private accessor: Screen#font is protected and declared in a different package, so overlay classes (not Screen subclasses) can't reach it by field access - only through this. */
	net.minecraft.client.gui.Font font() {
		return font;
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

	/** The current selection, for a sub-screen deciding what it can offer. */
	List<ChunkPos> selectionSnapshot() {
		return List.copyOf(selection);
	}

	/** How many of the selection have an undo snapshot behind them. */
	int undoableCount() {
		return selectedUndoCount;
	}

	/** How many of the selection you've set foot in. */
	int touchedCount() {
		return selectedTouchedCount;
	}

	/** Package-private passthrough so the overlay classes can register vanilla widgets on this screen. */
	Button addWidget(Button button) {
		return addRenderableWidget(button);
	}

	/**
	 * Package-private passthrough so an overlay can tear down its own
	 * widgets before rebuilding them - SettingsOverlay does this every time
	 * a toggle changes how many rows it has. Without it, every click would
	 * leave its old (merely hidden) buttons in this screen's widget list for
	 * the rest of the session instead of actually being gone.
	 */
	void removeWidget(Button button) {
		super.removeWidget(button);
	}

	/** Package-private: the overlays read cached biome/ore data for the whole selection through this. */
	ChunkMapDataCache dataCache() {
		return dataCache;
	}

	/**
	 * Every operation that changes chunks lives behind this one button, so
	 * the map keeps a two-row action panel however many operations there
	 * end up being. It opens as an overlay over the map rather than a screen
	 * swap - see ManipulationOverlay - so the map stays visible underneath
	 * while the selection is being inspected.
	 */
	private void openManipulation() {
		if (selection.isEmpty()) return;
		if (filterOpen) closeFilter();
		settingsOverlay.close();
		manipulationOverlay.open();
	}

	private void toggleSettings() {
		if (settingsOverlay.isOpen()) {
			settingsOverlay.close();
			return;
		}
		if (filterOpen) closeFilter();
		manipulationOverlay.close();
		settingsOverlay.open();
	}

	/**
	 * Whether slimes can spawn underground in this chunk.
	 *
	 * Same call vanilla's own slime spawn rule makes - WorldgenRandom
	 * #seedSlimeChunk with the 987234911 salt, then nextInt(10) == 0 - rather
	 * than a reimplementation of the scramble, so it can't drift away from
	 * what the game actually does. The method's signature is identical on
	 * 26.1 and 26.3 (checked against the shipped classes), and 26.3 moving
	 * the Slime entity itself into a cubemob package didn't touch it.
	 *
	 * Slime chunks only mean anything in the Overworld, hence the dimension
	 * check at the call site: the same maths produces a perfectly convincing
	 * and entirely meaningless pattern in the Nether.
	 */
	private static boolean isSlimeChunk(long worldSeed, ChunkPos pos) {
		return net.minecraft.world.level.levelgen.WorldgenRandom
			.seedSlimeChunk(chunkX(pos), chunkZ(pos), worldSeed, 987234911L)
			.nextInt(10) == 0;
	}

	private static boolean isOverworld(ResourceKey<Level> dimension) {
		return dimension == Level.OVERWORLD;
	}

	void beginBiomeEdit() {
		if (selection.isEmpty()) return;
		List<ResourceLocation> choices = registeredBiomes();
		if (choices.isEmpty()) {
			showNotice(Component.translatable("gui.retrograde.biome_edit.none").getString());
			return;
		}
		openScreen(new BiomeEditScreen(this, List.copyOf(selection), choices));
	}

	/**
	 * Every biome this world registered, not just the ones the map has seen,
	 * so the picker can offer a biome you've never been to. Sorted by id, so
	 * cycling through it is predictable rather than registry-order.
	 */
	private List<ResourceLocation> registeredBiomes() {
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		if (server == null) return List.of();
		List<ResourceLocation> ids = new ArrayList<>();
		//? if >=26 {
		/*server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
			.keySet().forEach(ids::add);
		*///?} else {
		server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
			.keySet().forEach(ids::add);
		//?}
		ids.sort(Comparator.comparing(ResourceLocation::toString));
		return List.copyOf(ids);
	}

	/**
	 * Runs vanilla's fillbiome over each selected chunk, one command per
	 * chunk, against a permission-4 source built from the server - so this
	 * works in a world that doesn't have cheats on, gated by Retrograde's own
	 * setting rather than the world's.
	 *
	 * Synchronous rather than a progress-screen job: fillbiome rewrites the
	 * biome container of chunks already in memory, with no teleporting, no
	 * waiting for an unload and no regeneration, so the whole selection is a
	 * frame's work. The jobs that need the progress screen are the ones that
	 * need you moved out of the way first.
	 */
	void applyBiomeEdit(List<ChunkPos> targets, ResourceLocation biome) {
		MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
		var player = minecraft == null ? null : minecraft.player;
		if (server == null || player == null || targets.isEmpty()) return;

		ServerLevel level = server.getLevel(player.level().dimension());
		if (level == null) return;

		//? if >=26 {
		/*int minY = level.getMinY();
		int maxY = level.getMaxY();
		*///?} else {
		int minY = level.getMinBuildHeight();
		// Exclusive on 1.20.1, inclusive on 26.x, and fillbiome wants inclusive.
		int maxY = level.getMaxBuildHeight() - 1;
		//?}

		// Onto the server thread: this is a client screen, and the command
		// dispatcher is about to edit chunks the server owns.
		server.execute(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			for (ChunkPos pos : targets) {
				int x = chunkX(pos) * CHUNK_BLOCKS;
				int z = chunkZ(pos) * CHUNK_BLOCKS;
				server.getCommands().performPrefixedCommand(source, String.format(
					"fillbiome %d %d %d %d %d %d %s",
					x, minY, z, x + CHUNK_BLOCKS - 1, maxY, z + CHUNK_BLOCKS - 1, biome));
			}
		});

		// Each cached chunk carries the biome it was read with, so everything
		// just rewritten is now stale on the map and in the filter.
		for (ChunkPos pos : targets) {
			dataCache.invalidate(minecraft, pos);
		}
		showNotice(Component.translatable("gui.retrograde.biome_edit.done", targets.size()).getString());
		openScreen(this);
	}

	/**
	 * Regen goes through a preview rather than a yes/no dialog. A confirm
	 * box can say "42 chunks" and nothing more; this is the one action in
	 * the mod that destroys work, and the numbers that decide whether you
	 * want it - how many you've built in, how many can be undone afterwards,
	 * what ore is down there, how long you'll be sat watching - are all
	 * knowable before it starts.
	 */
	void beginRegen() {
		if (selection.isEmpty()) return;
		List<ChunkPos> targets = List.copyOf(selection);
		openScreen(new RegenPreviewScreen(this, targets, selectedTouchedCount, selectedUndoCount,
			aggregateOres(targets), unscannedCount(targets)));
	}

	/** Called by RegenPreviewScreen once the numbers have been looked at and accepted. */
	public void startRegenJob(List<ChunkPos> targets) {
		startJob(ChunkRegenJob.Mode.REGENERATE, targets, null);
	}

	/**
	 * Every ore across the selection, biggest total first. Package-private:
	 * ManipulationOverlay shows this same aggregate for the whole selection
	 * rather than re-deriving it, since dataCache already has every chunk's
	 * ore tally cached from the read that drew it on the map - there is
	 * nothing here worth reading a region file a second time for, even at
	 * the 256-chunk selection cap.
	 */
	List<ChunkResourceInfo.Entry> aggregateOres(List<ChunkPos> targets) {
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
	 * How many chunks of the selection fall in each biome, biggest first.
	 * Same cached data as {@link #aggregateOres}, so this costs nothing new
	 * to compute per frame - it's a pass over a list already capped at 256.
	 */
	Map<ResourceLocation, Integer> aggregateBiomes(List<ChunkPos> targets) {
		Map<ResourceLocation, Integer> tally = new LinkedHashMap<>();
		for (ChunkPos pos : targets) {
			ChunkResourceInfo.Inspection inspection = dataCache.inspectionOf(pos);
			if (inspection == null || inspection.biome() == null) continue;
			tally.merge(inspection.biome(), 1, Integer::sum);
		}
		List<Map.Entry<ResourceLocation, Integer>> sorted = new ArrayList<>(tally.entrySet());
		sorted.sort((a, b) -> b.getValue() - a.getValue());
		Map<ResourceLocation, Integer> ordered = new LinkedHashMap<>();
		sorted.forEach(e -> ordered.put(e.getKey(), e.getValue()));
		return ordered;
	}

	/**
	 * How many of the targets the map never got a look inside. The ore
	 * figures on the preview are a floor, not a total, whenever this isn't
	 * zero - and saying so is better than quietly under-reporting.
	 */
	int unscannedCount(List<ChunkPos> targets) {
		int unscanned = 0;
		for (ChunkPos pos : targets) {
			if (dataCache.inspectionOf(pos) == null) unscanned++;
		}
		return unscanned;
	}

	void beginUndo() {
		if (selection.isEmpty()) return;
		List<ChunkPos> targets = List.copyOf(selection);
		confirmThen(
			Component.translatable("gui.retrograde.confirm_undo", selectedUndoCount),
			Component.translatable("gui.retrograde.confirm_undo.detail"),
			() -> startJob(ChunkRegenJob.Mode.UNDO, targets, null));
	}

	void beginRetrogen() {
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
		// Timeouts are read here rather than inside the job: settings hang off
		// Minecraft.getInstance(), and the job runs on the server thread.
		ChunkRegenJob job = ChunkRegenJob.start(server, serverLevel, player.getUUID(), mode, targets, integration,
			RetrogradeConfig.unloadTimeoutSeconds(), RetrogradeConfig.watchdogTimeoutSeconds());
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
		return handlePress(event.x(), event.y(), event.button(), event.hasControlDown(), event.hasShiftDown());
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
		return handlePress(mouseX, mouseY, button, Screen.hasControlDown(), Screen.hasShiftDown());
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
	 * A bare left-drag pans, because that's what dragging a map does
	 * everywhere else and a map that fights you on it feels broken. Box-select
	 * is the same drag with shift held, and ctrl makes it deselect instead.
	 * Right and middle still pan, for anyone already used to it.
	 *
	 * A left-click that doesn't travel far enough to count as a drag still
	 * toggles the chunk under it, modifier or not: it isn't a drag, so there's
	 * nothing for it to be ambiguous with, and needing a modifier to pick one
	 * chunk would be a strange thing to have to learn.
	 */
	private boolean handlePress(double mouseX, double mouseY, int button, boolean ctrlDown, boolean shiftDown) {
		if (!isOverMap(mouseX, mouseY)) return false;
		pressScreenX = dragScreenX = mouseX;
		pressScreenY = dragScreenY = mouseY;
		dragTotalDistance = 0;
		if (button == BUTTON_RIGHT || button == BUTTON_MIDDLE) {
			dragMode = DragMode.PAN;
		} else if (button == BUTTON_LEFT) {
			if (ctrlDown) {
				dragMode = DragMode.SELECT_REMOVE;
			} else if (shiftDown) {
				dragMode = DragMode.SELECT_ADD;
			} else {
				dragMode = DragMode.PAN_OR_CLICK;
			}
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Keyboard panning and zoom, so the map is usable without a mouse at all.
	 * WASD alongside the arrows because that's the hand position you arrived
	 * in - and there's no text field on this screen for them to be stolen from.
	 */
	private boolean handleKey(int keyCode) {
		double step = KEY_PAN_PIXELS / pixelsPerBlock();
		switch (keyCode) {
			case KEY_LEFT, KEY_A -> cameraBlockX -= step;
			case KEY_RIGHT, KEY_D -> cameraBlockX += step;
			case KEY_UP, KEY_W -> cameraBlockZ -= step;
			case KEY_DOWN, KEY_S -> cameraBlockZ += step;
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
		if (dragMode == DragMode.PAN || dragMode == DragMode.PAN_OR_CLICK) {
			cameraBlockX -= dragX / pixelsPerBlock();
			cameraBlockZ -= dragY / pixelsPerBlock();
		}
		return true;
	}

	/**
	 * Scroll is the one input every floating panel on this screen has to
	 * fight the map for: the map itself treats it as zoom, so anything drawn
	 * over the map that also wants to scroll - the overlays' ore lists, the
	 * info panel's ore list - has to claim it first or every attempt to
	 * scroll a list would also zoom the map out from under it.
	 */
	private boolean handleScroll(double mouseX, double mouseY, double scrollDelta) {
		if (scrollDelta == 0) return false;
		if (manipulationOverlay.isOpen() && manipulationOverlay.contains(mouseX, mouseY)) {
			manipulationOverlay.scroll(mouseX, mouseY, scrollDelta);
			return true;
		}
		if (settingsOverlay.isOpen() && settingsOverlay.contains(mouseX, mouseY)) {
			// Nothing in settings scrolls yet, but the overlay still owns
			// every pixel inside its own bounds.
			return true;
		}
		if (isOverOreList(mouseX, mouseY)) {
			oreScrollPx -= (int) Math.signum(scrollDelta) * ORE_SCROLL_STEP;
			return true;
		}
		if (!isOverMap(mouseX, mouseY)) return false;
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

		// It travelled, so the bare press was a pan after all and there's no
		// box to apply.
		if (mode == DragMode.PAN_OR_CLICK) return true;

		dragScreenX = mouseX;
		dragScreenY = mouseY;
		applyDragBox(mode == DragMode.SELECT_ADD);
		return true;
	}

	/**
	 * Escape climbs a ladder here too, just a one-rung one: an open overlay
	 * eats the first press and closes itself rather than the whole map, the
	 * same way a job's cancel ladder eats Escape presses before they reach
	 * "close the screen" (see RegenProgressScreen, which is untouched by any
	 * of this - it's a separate screen with its own ladder, and this method
	 * never runs while one of its jobs is on screen). Only once nothing is
	 * open does Escape fall through to actually closing the map.
	 */
	@Override
	public void onClose() {
		if (manipulationOverlay.isOpen()) {
			manipulationOverlay.close();
			return;
		}
		if (settingsOverlay.isOpen()) {
			settingsOverlay.close();
			return;
		}
		if (filterOpen) {
			closeFilter();
			return;
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
