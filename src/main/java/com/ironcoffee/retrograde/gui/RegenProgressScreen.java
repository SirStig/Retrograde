package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.config.RetrogradeConfig;
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
 *
 * That same fact is why the button here is a ladder rather than one action.
 * A screen that is also the clock cannot afford a state where its only
 * control is greyed out, which is what the old "Cancelling…" did: if the
 * thing you were cancelling was itself what had stopped responding, there
 * was nothing left to press. So: Cancel stops at the next clean boundary,
 * Force cancel stops now and puts you back, and Leave anyway lets go of a
 * job the server thread has stopped answering for at all. Only the last one
 * can leave a mess, and it says so on screen instead of in a log file.
 */
public class RegenProgressScreen extends Screen {
	private static final int CARD_WIDTH = 280;
	private static final int CARD_HEIGHT = 154;
	private static final int CARD_PAD = 14;
	private static final int BAR_HEIGHT = 12;
	private static final int BUTTON_WIDTH = 120;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 4;
	/**
	 * How long a force cancel gets to work before the screen starts offering
	 * to leave without it. Long enough that a busy server thread finishes the
	 * chunk it's on first, short enough that nobody sits staring at a screen
	 * that isn't going to change.
	 */
	private static final int FORCE_CANCEL_GRACE_TICKS = 60;

	private static final int COLOR_BACKDROP = 0xE0080808;
	/**
	 * Fully opaque, and the default. A regen job parks you above the build
	 * height and brings you back, and watching that happen through a
	 * translucent screen reads as the game having broken rather than as the
	 * job doing exactly what it said it would.
	 */
	private static final int COLOR_BACKDROP_OPAQUE = 0xFF080808;
	private static final int COLOR_WARNING = 0xFFE0A040;
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
	private Button escapeButton;
	private boolean reportedToParent;
	/** Ticks since force cancel was asked for, or -1 if it hasn't been. */
	private int forceCancelTicks = -1;

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

		// Sits below the card rather than inside it: it is the way out of a
		// job that has gone wrong, not one of the job's own controls, and it
		// should not look like one until it's needed.
		escapeButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.retrograde.job.leave"), b -> onEscapeButton())
			.bounds((width - BUTTON_WIDTH) / 2, buttonY + BUTTON_HEIGHT + BUTTON_GAP + 6,
				BUTTON_WIDTH, BUTTON_HEIGHT)
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
			// After an abandoned job this button becomes the retry, since the
			// only thing still worth doing is getting the player back down.
			if (job.playerMayBeStranded()) {
				job.retryRestore();
				return;
			}
			openParent();
			return;
		}
		if (job.isCancelled()) {
			job.forceCancel();
			forceCancelTicks = 0;
		} else {
			job.requestCancel();
		}
		syncActionButton();
	}

	/**
	 * The last resort: stop waiting on a job the server thread isn't
	 * answering for, and let the screen close. The job keeps its restore
	 * queued, so a thread that comes back still puts the player down.
	 */
	private void onEscapeButton() {
		if (job.isFinished()) {
			openParent();
			return;
		}
		job.forceAbandon();
		syncActionButton();
	}

	private void syncActionButton() {
		if (actionButton == null || escapeButton == null) return;

		if (job.isFinished()) {
			boolean stranded = job.playerMayBeStranded();
			actionButton.setMessage(stranded
				? Component.translatable("gui.retrograde.job.retry_restore")
				: Component.translatable("gui.done"));
			actionButton.active = true;
			// Closing is always allowed once the job is over, even stranded -
			// trapping someone in a screen is not a safety feature.
			escapeButton.visible = stranded;
			escapeButton.setMessage(Component.translatable("gui.retrograde.job.close_anyway"));
			return;
		}

		if (job.isForceCancelling()) {
			actionButton.setMessage(Component.translatable("gui.retrograde.job.stopping"));
			actionButton.active = false;
		} else if (job.isCancelled()) {
			actionButton.setMessage(Component.translatable("gui.retrograde.job.force_cancel"));
			actionButton.active = true;
		} else {
			actionButton.setMessage(Component.translatable("gui.cancel"));
			actionButton.active = true;
		}

		// Only offered once a force cancel has had its grace period and still
		// hasn't taken, because it's the one button here that can leave the
		// world and the player in a state nothing tidies up afterwards.
		escapeButton.setMessage(Component.translatable("gui.retrograde.job.leave"));
		escapeButton.visible = forceCancelTicks >= FORCE_CANCEL_GRACE_TICKS;
	}

	@Override
	public void tick() {
		super.tick();
		job.pump();
		if (forceCancelTicks >= 0 && !job.isFinished()) {
			forceCancelTicks++;
		}
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
		painter.fill(0, 0, width, height,
			RetrogradeConfig.opaqueProgressScreen() ? COLOR_BACKDROP_OPAQUE : COLOR_BACKDROP);

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
		lineY += font.lineHeight + 4;

		// The one thing that must never only be in a log file: the player is
		// still above the build height with gravity off and doesn't know it.
		if (job.isFinished() && job.playerMayBeStranded()) {
			painter.text(font, Component.translatable("gui.retrograde.job.warn.stranded"),
				textX, lineY, COLOR_WARNING);
		} else if (job.stubbornChunks() > 0 && job.phase() == ChunkRegenJob.Phase.WORKING) {
			painter.text(font, Component.translatable("gui.retrograde.job.warn.stubborn", job.stubbornChunks()),
				textX, lineY, COLOR_SKIPPED);
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
		// still has to put the player back before anyone leaves. Pressing it
		// again escalates, the same ladder the button climbs, because the
		// reflex when a screen won't go away is to press Escape harder and
		// that should get you somewhere.
		onActionButton();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// True, despite this screen refusing to close while a job runs: it is
		// what makes vanilla route Escape into onClose() at all, and Escape
		// doing nothing whatsoever is the state this whole ladder exists to
		// avoid. onClose() decides whether leaving is allowed, not this.
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		// The job needs the world ticking to unload chunks and to move the
		// player, so this must never pause the integrated server.
		return false;
	}
}
