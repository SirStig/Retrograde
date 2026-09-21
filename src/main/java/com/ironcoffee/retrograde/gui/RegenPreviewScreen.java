package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.regen.ChunkRegenJob;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}

import java.util.List;

/**
 * The dry run for a regen: everything that's about to happen, in numbers,
 * before anything is destroyed.
 *
 * This replaced a plain ConfirmScreen, which could only ask "regenerate 42
 * chunks?" and leave you to remember what was in them. Regen is the one
 * action here that can't be shrugged off - undo only reaches back as far as
 * the snapshots on disk, and only for chunks that have one - so the things
 * that decide whether you actually want it are worth showing: how much of
 * the selection you've built in, how much of it undo could rescue, what ore
 * is down there, and roughly how long you'll be watching a progress bar.
 *
 * Everything on it is already known by the time you get here - the map read
 * these chunks to draw them - so none of it costs a second pass over the
 * world.
 */
public class RegenPreviewScreen extends Screen {
	private static final int CARD_WIDTH = 300;
	private static final int CARD_PAD = 14;
	private static final int LINE_H = 12;
	private static final int ORE_ROW_H = 18;
	private static final int ORE_ICON = 16;
	/**
	 * Four, not more: the card is sized around this, and at maximum GUI
	 * scale the whole window is only 240px tall. The rest are counted on a
	 * final line, and the full per-chunk breakdown is on the map anyway.
	 */
	private static final int MAX_ORE_ROWS = 4;
	private static final int BUTTON_WIDTH = 132;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;

	private static final int COLOR_BACKDROP = 0xE0080808;
	private static final int COLOR_CARD_BG = 0xF0161616;
	private static final int COLOR_CARD_EDGE_LIGHT = 0x40FFFFFF;
	private static final int COLOR_CARD_EDGE_DARK = 0x80000000;
	private static final int COLOR_LABEL = 0xFFB0B0B0;
	private static final int COLOR_VALUE = 0xFFFFFFFF;
	private static final int COLOR_HEADING = 0xFF8AA0B4;
	private static final int COLOR_DIVIDER = 0x30FFFFFF;
	private static final int COLOR_WARN = 0xFFE06060;
	private static final int COLOR_CAUTION = 0xFFD8B24C;
	private static final int COLOR_OK = 0xFF6FCF6F;

	private final ChunkMapScreen parent;
	private final List<ChunkPos> targets;
	private final int touched;
	private final int undoable;
	private final List<ChunkResourceInfo.Entry> ores;
	private final int unscanned;
	private final int oreRows;

	/** Set in init(), not the constructor: it depends on font, which isn't there yet. */
	private int cardHeight;

	public RegenPreviewScreen(ChunkMapScreen parent, List<ChunkPos> targets, int touched, int undoable,
			List<ChunkResourceInfo.Entry> ores, int unscanned) {
		super(Component.translatable("gui.retrograde.preview.title", targets.size()));
		this.parent = parent;
		this.targets = targets;
		this.touched = touched;
		this.undoable = undoable;
		this.ores = ores;
		this.unscanned = unscanned;
		this.oreRows = Math.min(ores.size(), MAX_ORE_ROWS);
	}

	@Override
	protected void init() {
		super.init();
		// Sized to its contents rather than padded out to a fixed height: a
		// selection with no ore in it shouldn't leave a hole where the list
		// would have been.
		cardHeight = CARD_PAD * 2
			+ font.lineHeight + 8
			+ LINE_H * 4
			+ (touched > 0 ? font.lineHeight + 4 : 0)
			+ 4 + font.lineHeight + 2
			+ Math.max(ORE_ROW_H, oreRows * ORE_ROW_H + (ores.size() > oreRows ? LINE_H : 0))
			+ 10 + BUTTON_HEIGHT;

		int buttonY = cardY() + cardHeight - CARD_PAD - BUTTON_HEIGHT;
		int totalW = BUTTON_WIDTH * 2 + BUTTON_GAP;
		int buttonX = (width - totalW) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.retrograde.preview.confirm"), b -> confirm())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.preview.confirm.tip")))
			.bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
			.bounds(buttonX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
	}

	private int cardX() {
		return (width - CARD_WIDTH) / 2;
	}

	private int cardY() {
		return (height - cardHeight) / 2;
	}

	private void confirm() {
		// The map screen owns the job and the progress screen that drives it.
		parent.startRegenJob(targets);
	}

	//? if <26 {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics));
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics) {
	}
	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics));
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}
	*///?}

	private void draw(Painter painter) {
		painter.fill(0, 0, width, height, COLOR_BACKDROP);

		int x = cardX();
		int y = cardY();
		painter.dropShadow(x, y, CARD_WIDTH, cardHeight, 0xA0000000, 5);
		painter.roundedPanel(x, y, CARD_WIDTH, cardHeight, COLOR_CARD_BG, COLOR_CARD_EDGE_LIGHT, COLOR_CARD_EDGE_DARK);

		int textX = x + CARD_PAD;
		int rightX = x + CARD_WIDTH - CARD_PAD;
		int lineY = y + CARD_PAD;

		painter.centeredText(font, title, x + CARD_WIDTH / 2, lineY, COLOR_VALUE);
		lineY += font.lineHeight + 8;

		row(painter, textX, rightX, lineY, "gui.retrograde.preview.chunks",
			String.valueOf(targets.size()), COLOR_VALUE);
		lineY += LINE_H;
		// Red only when there's something to lose: a selection you've never
		// set foot in is the safe case, and colouring it like a warning
		// teaches people to ignore the colour.
		row(painter, textX, rightX, lineY, "gui.retrograde.preview.touched",
			String.valueOf(touched), touched > 0 ? COLOR_WARN : COLOR_OK);
		lineY += LINE_H;
		row(painter, textX, rightX, lineY, "gui.retrograde.preview.undoable",
			undoable + " / " + targets.size(), undoable == targets.size() ? COLOR_OK : COLOR_CAUTION);
		lineY += LINE_H;
		row(painter, textX, rightX, lineY, "gui.retrograde.preview.time",
			"~" + ChunkRegenJob.estimateSeconds(ChunkRegenJob.Mode.REGENERATE, targets.size()) + "s", COLOR_VALUE);
		lineY += LINE_H;

		// The one thing the numbers above don't say on their own: undo is a
		// finite number of snapshots on disk, not a guarantee.
		if (touched > 0) {
			painter.text(font, Component.translatable("gui.retrograde.preview.warning"), textX, lineY, COLOR_WARN);
			lineY += font.lineHeight + 4;
		}

		painter.fill(textX, lineY, rightX, lineY + 1, COLOR_DIVIDER);
		lineY += 5;

		painter.text(font, Component.translatable("gui.retrograde.preview.ores"), textX, lineY, COLOR_HEADING);
		if (unscanned > 0) {
			Component note = Component.translatable("gui.retrograde.preview.unscanned", unscanned);
			painter.text(font, note, rightX - font.width(note), lineY, COLOR_CAUTION);
		}
		lineY += font.lineHeight + 2;

		if (ores.isEmpty()) {
			painter.text(font, Component.translatable("gui.retrograde.chunk_map.tooltip.no_ores"),
				textX, lineY + 4, COLOR_LABEL);
			return;
		}

		for (int i = 0; i < oreRows; i++) {
			ChunkResourceInfo.Entry entry = ores.get(i);
			ItemStack stack = new ItemStack(entry.block());
			painter.item(stack, textX, lineY);
			String count = String.valueOf(entry.count());
			int countX = rightX - font.width(count);
			painter.text(font, count, countX, lineY + 4, COLOR_VALUE);
			int nameX = textX + ORE_ICON + 4;
			painter.text(font, trimTo(stack.getHoverName().getString(), countX - nameX - 4),
				nameX, lineY + 4, COLOR_LABEL);
			lineY += ORE_ROW_H;
		}
		if (ores.size() > oreRows) {
			painter.text(font, Component.translatable("gui.retrograde.preview.more_ores", ores.size() - oreRows),
				textX, lineY, COLOR_LABEL);
		}
	}

	/** Label left, value right - so the numbers line up in a column you can read down. */
	private void row(Painter painter, int labelX, int rightX, int y, String labelKey, String value, int valueColor) {
		painter.text(font, Component.translatable(labelKey), labelX, y, COLOR_LABEL);
		painter.text(font, value, rightX - font.width(value), y, valueColor);
	}

	private String trimTo(String text, int budget) {
		if (font.width(text) <= budget) return text;
		return font.plainSubstrByWidth(text, Math.max(0, budget - font.width("..."))) + "...";
	}

	@Override
	public void onClose() {
		//? if >=26.3 {
		/*minecraft.gui.setScreen(parent);
		*///?} else {
		minecraft.setScreen(parent);
		//?}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
