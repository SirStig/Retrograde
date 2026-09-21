package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.regen.ChunkRegenJob;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}

/**
 * The loading screen for a {@link ChunkRegenJob}: what phase it's in, how
 * far through it is, and what happened to each chunk.
 *
 * It also drives the job. Nothing else ticks it - there's no server tick
 * hook in this mod - so this screen calling pump() once per client tick is
 * what makes the job move at all. That's why it refuses to close while a
 * job is running: closing it mid-run would strand the player at the staging
 * position with the work half done.
 */
public class RegenProgressScreen extends Screen {
	private static final int CARD_WIDTH = 280;
	private static final int CARD_HEIGHT = 136;
	private static final int CARD_PAD = 14;
	private static final int BAR_HEIGHT = 12;
	private static final int BUTTON_WIDTH = 120;
	private static final int BUTTON_HEIGHT = 20;

	private static final int COLOR_BACKDROP = 0xE0080808;
	private static final int COLOR_CARD_BG = 0xF0161616;
	private static final int COLOR_CARD_EDGE_LIGHT = 0x40FFFFFF;
	private static final int COLOR_CARD_EDGE_DARK = 0x80000000;
	private static final int COLOR_BAR_TRACK = 0xFF2A2A2A;
	private static final int COLOR_BAR_FILL = 0xFF4C9BE8;
	private static final int COLOR_BAR_FILL_DONE = 0xFF5FBF5F;
	private static final int COLOR_LABEL = 0xFFB0B0B0;
	private static final int COLOR_VALUE = 0xFFFFFFFF;
	private static final int COLOR_OK = 0xFF6FCF6F;
	private static final int COLOR_SKIPPED = 0xFFD8B24C;
	private static final int COLOR_FAILED = 0xFFE06060;

	private final ChunkMapScreen parent;
	private final ChunkRegenJob job;

	private Button actionButton;
	private boolean reportedToParent;

	public RegenProgressScreen(ChunkMapScreen parent, ChunkRegenJob job) {
		super(job.titleLine());
		this.parent = parent;
		this.job = job;
	}

	@Override
	protected void init() {
		super.init();
		int buttonY = cardY() + CARD_HEIGHT - CARD_PAD - BUTTON_HEIGHT;
		actionButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onActionButton())
			.bounds((width - BUTTON_WIDTH) / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
		syncActionButton();
	}

	private int cardX() {
		return (width - CARD_WIDTH) / 2;
	}

	private int cardY() {
		return (height - CARD_HEIGHT) / 2;
	}

	private void onActionButton() {
		if (job.isFinished()) {
			openParent();
		} else {
			job.requestCancel();
			syncActionButton();
		}
	}

	private void syncActionButton() {
		if (actionButton == null) return;
		if (job.isFinished()) {
			actionButton.setMessage(Component.translatable("gui.done"));
			actionButton.active = true;
		} else if (job.isCancelled()) {
			actionButton.setMessage(Component.translatable("gui.retrograde.job.cancelling"));
			actionButton.active = false;
		} else {
			actionButton.setMessage(Component.translatable("gui.cancel"));
			actionButton.active = true;
		}
	}

	@Override
	public void tick() {
		super.tick();
		job.pump();
		if (job.isFinished() && !reportedToParent) {
			reportedToParent = true;
			parent.onJobFinished(job);
		}
		syncActionButton();
	}

	private void openParent() {
		//? if >=26.3 {
		/*minecraft.gui.setScreen(parent);
		*///?} else {
		minecraft.setScreen(parent);
		//?}
	}

	//? if <26 {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics));
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		draw(new Painter(guiGraphics));
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void draw(Painter painter) {
		painter.fill(0, 0, width, height, COLOR_BACKDROP);

		int x = cardX();
		int y = cardY();
		painter.panel(x, y, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_BG, COLOR_CARD_EDGE_LIGHT, COLOR_CARD_EDGE_DARK);

		int textX = x + CARD_PAD;
		int innerWidth = CARD_WIDTH - CARD_PAD * 2;
		int lineY = y + CARD_PAD;

		painter.centeredText(font, title, x + CARD_WIDTH / 2, lineY, COLOR_VALUE);
		lineY += font.lineHeight + 8;

		painter.text(font, job.statusLine(), textX, lineY, COLOR_LABEL);
		lineY += font.lineHeight + 5;

		boolean done = job.isFinished();
		painter.progressBar(textX, lineY, innerWidth, BAR_HEIGHT, job.progress(),
			COLOR_BAR_TRACK, done ? COLOR_BAR_FILL_DONE : COLOR_BAR_FILL);
		String fraction = job.processed() + " / " + job.total();
		painter.text(font, fraction, x + CARD_WIDTH / 2 - font.width(fraction) / 2, lineY + 2, COLOR_VALUE);
		lineY += BAR_HEIGHT + 8;

		// Counters only earn their space once something has landed in them.
		int counterX = textX;
		counterX = drawCounter(painter, counterX, lineY, "gui.retrograde.job.count.done", job.succeeded(), COLOR_OK, true);
		if (job.skipped() > 0) {
			counterX = drawCounter(painter, counterX, lineY, "gui.retrograde.job.count.skipped", job.skipped(), COLOR_SKIPPED, false);
		}
		if (job.failed() > 0) {
			drawCounter(painter, counterX, lineY, "gui.retrograde.job.count.failed", job.failed(), COLOR_FAILED, false);
		}
	}

	private int drawCounter(Painter painter, int x, int y, String key, int value, int color, boolean alwaysShow) {
		if (value == 0 && !alwaysShow) return x;
		Component label = Component.translatable(key, value);
		painter.text(font, label, x, y, color);
		return x + font.width(label) + 10;
	}

	@Override
	public void onClose() {
		if (job.isFinished()) {
			openParent();
			return;
		}
		// Esc during a run means "stop", not "walk away from it" - the job
		// still has to put the player back before anyone leaves.
		job.requestCancel();
		syncActionButton();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		// The job needs the world ticking to unload chunks and to move the
		// player, so this must never pause the integrated server.
		return false;
	}
}
