package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.config.RetrogradeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;

import static com.ironcoffee.retrograde.gui.ChunkMapScreen.*;

/**
 * The inspector panel for the current selection: what's in it and what you
 * can do to it, floating over the map instead of swapping it out for a
 * screen.
 *
 * This replaces ChunkManipulationScreen, which was a real Screen - closing
 * the map, showing its own background, and handing back control on Cancel.
 * That worked but hid the very thing the numbers on it describe: opening
 * "Chunk Manipulation" made the selection you were about to act on disappear
 * behind the menu asking what to do with it. As an overlay the map, and the
 * selection tinted on it, stay on screen the whole time.
 *
 * Not a Screen, so it owns no Stonecutter branches of its own - every draw
 * call here goes through the Painter the map screen hands it, and every
 * button is a vanilla {@link Button} registered on the map screen itself via
 * {@link ChunkMapScreen#addWidget}, which is what gives them correct 26.3
 * SDL click handling for free (see the comment on that method's caller in
 * InputCodes). The one thing this class does read a raw input value for -
 * routing a scroll wheel to the ore list instead of the map - goes through
 * {@link ChunkMapScreen#drawOreList}, the same scroll-safe path the info
 * panel's own ore list uses, so there is exactly one place that logic lives.
 */
final class ManipulationOverlay {
	private static final int WIDTH = 260;
	private static final int PAD = 10;
	private static final int LINE_H = 11;
	private static final int ORE_VIEW_ROWS = 4;
	private static final int ORE_ROW_H = 18;
	private static final int BIOME_ROWS = 3;
	private static final int BUTTON_H = 15;
	private static final int BUTTON_GAP = 4;
	private static final int CLOSE_SIZE = 14;

	private final ChunkMapScreen screen;

	private boolean open;
	private int x, y, h;
	private int oreViewX, oreViewY, oreViewW, oreViewH;
	private int oreScrollPx;

	private Button closeButton;
	private Button regenButton;
	private Button undoButton;
	private Button retrogenButton;
	private Button biomeButton;

	/** Snapshot taken when the overlay opens, so the numbers on it don't shift while it's up. */
	private List<ChunkPos> targets = List.of();
	private int touched;
	private int undoable;
	private List<ChunkResourceInfo.Entry> ores = List.of();
	private Map<ResourceLocation, Integer> biomes = Map.of();
	private int unscanned;

	ManipulationOverlay(ChunkMapScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return open;
	}

	void open() {
		open = true;
		targets = screen.selectionSnapshot();
		touched = screen.touchedCount();
		undoable = screen.undoableCount();
		ores = screen.aggregateOres(targets);
		biomes = screen.aggregateBiomes(targets);
		unscanned = screen.unscannedCount(targets);
		oreScrollPx = 0;
		syncWidgets();
	}

	void close() {
		open = false;
		syncWidgets();
	}

	void tick() {
		// Undo count in particular can change while the overlay is up - the
		// map keeps its background undo-count scan running - so the button
		// this overlay shows stays honest without needing to be reopened.
		undoable = screen.undoableCount();
		syncWidgets();
	}

	boolean contains(double mouseX, double mouseY) {
		return open && screen.isOverChip(mouseX, mouseY, x, y, WIDTH, h);
	}

	void scroll(double mouseX, double mouseY, double delta) {
		if (!screen.isOverChip(mouseX, mouseY, oreViewX, oreViewY, oreViewW, oreViewH)) return;
		oreScrollPx -= (int) Math.signum(delta) * ORE_ROW_H;
	}

	/** Recomputed on every map resize, same as the filter panel's row metrics. */
	void layout(int screenWidth, int screenHeight) {
		x = (screenWidth - WIDTH) / 2;

		int contentH = PAD // top
			+ LINE_H + 6 // title
			+ LINE_H + 4 // selection summary
			+ 6 // divider
			+ LINE_H + ORE_VIEW_ROWS * ORE_ROW_H + 4 // ore heading + list
			+ 6 // divider
			+ LINE_H + BIOME_ROWS * LINE_H + 4 // biome heading + rows
			+ 6 // divider
			+ BUTTON_H * 2 + BUTTON_GAP // two rows of actions
			+ PAD;
		h = Math.min(contentH, screenHeight - 24);
		y = Math.max(12, (screenHeight - h) / 2);

		int buttonW = (WIDTH - PAD * 2 - BUTTON_GAP) / 2;
		int buttonY = y + h - PAD - BUTTON_H * 2 - BUTTON_GAP;

		regenButton = screen.addWidget(Button.builder(Component.empty(), b -> act(screen::beginRegen))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.action.regen.tip")))
			.bounds(x + PAD, buttonY, buttonW, BUTTON_H).build());
		undoButton = screen.addWidget(Button.builder(Component.empty(), b -> act(screen::beginUndo))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.action.undo.tip")))
			.bounds(x + PAD + buttonW + BUTTON_GAP, buttonY, buttonW, BUTTON_H).build());
		buttonY += BUTTON_H + BUTTON_GAP;
		retrogenButton = screen.addWidget(Button.builder(Component.empty(), b -> act(screen::beginRetrogen))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.action.retrogen.tip")))
			.bounds(x + PAD, buttonY, buttonW, BUTTON_H).build());
		biomeButton = screen.addWidget(Button.builder(Component.empty(), b -> act(screen::beginBiomeEdit))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.manipulate.set_biome.tip")))
			.bounds(x + PAD + buttonW + BUTTON_GAP, buttonY, buttonW, BUTTON_H).build());

		closeButton = screen.addWidget(Button.builder(Component.literal("X"), b -> close())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.filter.close.tip")))
			.bounds(x + WIDTH - PAD - CLOSE_SIZE, y + PAD - 2, CLOSE_SIZE, CLOSE_SIZE).build());

		syncWidgets();
	}

	/** Every action closes the overlay first: each one hands off to its own preview or confirm screen. */
	private void act(Runnable operation) {
		close();
		operation.run();
	}

	private void syncWidgets() {
		for (Button b : List.of(regenButton, undoButton, retrogenButton, biomeButton, closeButton)) {
			if (b != null) b.visible = open;
		}
		if (!open) return;

		boolean any = !targets.isEmpty();
		regenButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.regen", targets.size()));
		regenButton.active = any;
		undoButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.undo", undoable));
		undoButton.active = undoable > 0;
		retrogenButton.setMessage(Component.translatable("gui.retrograde.chunk_map.action.retrogen", targets.size()));
		retrogenButton.active = any;
		boolean biomeAllowed = RetrogradeConfig.allowBiomeEdit();
		biomeButton.visible = biomeAllowed;
		biomeButton.setMessage(Component.translatable("gui.retrograde.manipulate.set_biome", targets.size()));
		biomeButton.active = any;
	}

	void draw(Painter painter, int mouseX, int mouseY) {
		painter.dropShadow(x, y, WIDTH, h, COLOR_OVERLAY_SHADOW, 5);
		painter.roundedPanel(x, y, WIDTH, h, COLOR_OVERLAY_BG, COLOR_OVERLAY_EDGE_LIGHT, COLOR_OVERLAY_EDGE_DARK);

		var font = screen.font();
		int textX = x + PAD;
		int rightX = x + WIDTH - PAD;
		int lineY = y + PAD;

		painter.text(font, Component.translatable("gui.retrograde.manipulate.title", targets.size()),
			textX, lineY, COLOR_OVERLAY_VALUE);
		lineY += LINE_H + 6;

		Component summary = Component.translatable("gui.retrograde.manipulate.summary", targets.size(), touched, undoable);
		painter.text(font, summary, textX, lineY, COLOR_OVERLAY_LABEL);
		lineY += LINE_H + 4;

		painter.divider(textX, lineY, WIDTH - PAD * 2, COLOR_OVERLAY_DIVIDER);
		lineY += 6;

		painter.text(font, Component.translatable("gui.retrograde.preview.ores"), textX, lineY, COLOR_OVERLAY_HEADING);
		if (unscanned > 0) {
			Component note = Component.translatable("gui.retrograde.preview.unscanned", unscanned);
			painter.text(font, note, rightX - font.width(note), lineY, COLOR_OVERLAY_CAUTION);
		}
		lineY += LINE_H;

		int oreViewHeight = ORE_VIEW_ROWS * ORE_ROW_H;
		if (ores.isEmpty()) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.no_ores"),
				textX, lineY + 4, COLOR_OVERLAY_LABEL);
		} else {
			oreScrollPx = screen.drawOreList(painter, ores, textX, lineY, WIDTH - PAD * 2, oreViewHeight, oreScrollPx);
		}
		oreViewX = textX;
		oreViewY = lineY;
		oreViewW = WIDTH - PAD * 2;
		oreViewH = oreViewHeight;
		lineY += oreViewHeight + 4;

		painter.divider(textX, lineY, WIDTH - PAD * 2, COLOR_OVERLAY_DIVIDER);
		lineY += 6;

		painter.text(font, Component.translatable("gui.retrograde.manipulate.biomes"), textX, lineY, COLOR_OVERLAY_HEADING);
		lineY += LINE_H;
		drawBiomeRows(painter, textX, rightX, lineY);
		lineY += BIOME_ROWS * LINE_H + 4;

		painter.divider(textX, lineY, WIDTH - PAD * 2, COLOR_OVERLAY_DIVIDER);
	}

	private void drawBiomeRows(Painter painter, int textX, int rightX, int startY) {
		var font = screen.font();
		if (biomes.isEmpty()) {
			painter.text(font, Component.translatable("gui.retrograde.manipulate.biomes.unknown"),
				textX, startY + 2, COLOR_OVERLAY_LABEL);
			return;
		}
		int shown = Math.min(BIOME_ROWS, biomes.size());
		int y = startY;
		int i = 0;
		int total = targets.size();
		for (Map.Entry<ResourceLocation, Integer> entry : biomes.entrySet()) {
			if (i >= shown) break;
			String name = biomeName(entry.getKey());
			String count = entry.getValue() + "/" + total;
			int countX = rightX - font.width(count);
			painter.text(font, screen.trimTo(name, countX - textX - 4), textX, y, COLOR_OVERLAY_LABEL);
			painter.text(font, count, countX, y, COLOR_OVERLAY_VALUE);
			y += LINE_H;
			i++;
		}
		if (biomes.size() > shown) {
			painter.text(font, Component.translatable("gui.retrograde.manipulate.biomes.more", biomes.size() - shown),
				textX, y, COLOR_OVERLAY_LABEL);
		}
	}

	private static String biomeName(ResourceLocation id) {
		return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
	}
}
