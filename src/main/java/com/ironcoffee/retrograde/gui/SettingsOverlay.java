package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.config.RetrogradeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.ironcoffee.retrograde.gui.ChunkMapScreen.*;

/**
 * The gear's panel: what the map is allowed to do to your world, and how
 * the jobs that do it behave while they run - floating over the map rather
 * than replacing it with RetrogradeSettingsScreen used to.
 *
 * Same shape as before: the cheat group collapses to a single master switch
 * when it's off, so a world played straight never sees the toggles that
 * would hand it diamonds. What changed is presentation - grouped under
 * section headings with dividers between them instead of one flat stack of
 * identical rows - and that it no longer costs a screen swap to check a
 * setting mid-session.
 */
final class SettingsOverlay {
	private static final int WIDTH = 220;
	private static final int PAD = 10;
	private static final int LINE_H = 11;
	private static final int ROW_H = 15;
	private static final int ROW_GAP = 3;
	private static final int SECTION_GAP = 8;
	private static final int CLOSE_SIZE = 14;

	private final ChunkMapScreen screen;

	private boolean open;
	private int x, y, h;
	private int lastScreenW, lastScreenH;

	private Button closeButton;
	private final List<Button> rows = new ArrayList<>();

	SettingsOverlay(ChunkMapScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return open;
	}

	void open() {
		open = true;
		layout(lastScreenW, lastScreenH);
	}

	void close() {
		open = false;
		syncVisibility();
	}

	boolean contains(double mouseX, double mouseY) {
		return open && screen.isOverChip(mouseX, mouseY, x, y, WIDTH, h);
	}

	/**
	 * Whether this overlay covers the given rectangle, shadow included, so
	 * the map can take its own widgets out of the render pass rather than let
	 * them draw over the panel. See ChunkMapScreen#syncOverlayOcclusion.
	 */
	boolean occludes(int wx, int wy, int ww, int wh) {
		if (!open) return false;
		int spread = OVERLAY_SHADOW_SPREAD;
		return rectsOverlap(x - spread, y - spread, WIDTH + spread * 2, h + spread * 2, wx, wy, ww, wh);
	}

	void layout(int screenWidth, int screenHeight) {
		lastScreenW = screenWidth;
		lastScreenH = screenHeight;

		// Widgets are torn down and rebuilt every layout - not merely hidden,
		// actually removed from the map screen's widget list via
		// ChunkMapScreen#removeWidget - because the row count changes with
		// the master switch, this can run many times in one overlay session,
		// and there is no vanilla rebuildWidgets() to lean on outside a real
		// Screen. Leaving the old ones merely invisible would pile up a
		// growing set of dead buttons in the map screen for as long as the
		// map stays open.
		for (Button b : rows) screen.removeWidget(b);
		rows.clear();
		if (closeButton != null) {
			screen.removeWidget(closeButton);
			closeButton = null;
		}

		boolean cheats = RetrogradeConfig.cheatsEnabled();
		int sectionRows = 3 + 2 + (cheats ? 1 + 2 : 1);
		h = PAD * 2 + LINE_H + 6 // title
			+ sectionRows * (ROW_H + ROW_GAP)
			+ SECTION_GAP * 2;
		x = (screenWidth - WIDTH) / 2;
		y = Math.max(12, (screenHeight - h) / 2);

		int rowX = x + PAD;
		int rowW = WIDTH - PAD * 2;
		int rowY = y + PAD + LINE_H + 6;

		rowY = addToggle(rowX, rowY, rowW, "gui.retrograde.settings.slime",
			RetrogradeConfig.showSlimeChunks(), () -> {
				RetrogradeConfig.setShowSlimeChunks(!RetrogradeConfig.showSlimeChunks());
				layout(lastScreenW, lastScreenH);
			});
		rowY = addToggle(rowX, rowY, rowW, "gui.retrograde.settings.confirm",
			RetrogradeConfig.confirmDestructive(), () -> {
				RetrogradeConfig.setConfirmDestructive(!RetrogradeConfig.confirmDestructive());
				layout(lastScreenW, lastScreenH);
			});
		rowY = addToggle(rowX, rowY, rowW, "gui.retrograde.settings.opaque_progress",
			RetrogradeConfig.opaqueProgressScreen(), () -> {
				RetrogradeConfig.setOpaqueProgressScreen(!RetrogradeConfig.opaqueProgressScreen());
				layout(lastScreenW, lastScreenH);
			});
		rowY += SECTION_GAP;

		rowY = addCycle(rowX, rowY, rowW, "gui.retrograde.settings.unload_timeout",
			RetrogradeConfig.unloadTimeoutSeconds(), () -> {
				RetrogradeConfig.setUnloadTimeoutSeconds(nextTimeout(RetrogradeConfig.unloadTimeoutSeconds()));
				layout(lastScreenW, lastScreenH);
			});
		rowY = addCycle(rowX, rowY, rowW, "gui.retrograde.settings.watchdog_timeout",
			RetrogradeConfig.watchdogTimeoutSeconds(), () -> {
				RetrogradeConfig.setWatchdogTimeoutSeconds(nextWatchdog(RetrogradeConfig.watchdogTimeoutSeconds()));
				layout(lastScreenW, lastScreenH);
			});
		rowY += SECTION_GAP;

		rowY = addToggle(rowX, rowY, rowW, "gui.retrograde.settings.cheats", cheats, () -> {
			RetrogradeConfig.setCheatsEnabled(!RetrogradeConfig.cheatsEnabled());
			layout(lastScreenW, lastScreenH);
		});
		if (cheats) {
			rowY = addToggle(rowX, rowY, rowW, "gui.retrograde.settings.biome_edit",
				RetrogradeConfig.allowBiomeEdit(), () -> {
					RetrogradeConfig.setAllowBiomeEdit(!RetrogradeConfig.allowBiomeEdit());
					layout(lastScreenW, lastScreenH);
				});
			addToggle(rowX, rowY, rowW, "gui.retrograde.settings.ore_edit",
				RetrogradeConfig.allowOreEdit(), () -> {
					RetrogradeConfig.setAllowOreEdit(!RetrogradeConfig.allowOreEdit());
					layout(lastScreenW, lastScreenH);
				});
		}

		closeButton = screen.addWidget(Button.builder(Component.literal("X"), b -> close())
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.chunk_map.filter.close.tip")))
			.bounds(x + WIDTH - PAD - CLOSE_SIZE, y + PAD - 2, CLOSE_SIZE, CLOSE_SIZE).build());

		syncVisibility();
	}

	private int addToggle(int x, int y, int w, String key, boolean value, Runnable onPress) {
		Component label = Component.translatable(key)
			.append(": ")
			.append(Component.translatable(value ? "options.on" : "options.off"));
		Button button = screen.addWidget(Button.builder(label, b -> onPress.run())
			.tooltip(Tooltip.create(Component.translatable(key + ".tip")))
			.bounds(x, y, w, ROW_H).build());
		rows.add(button);
		return y + ROW_H + ROW_GAP;
	}

	private int addCycle(int x, int y, int w, String key, int seconds, Runnable onPress) {
		Component label = Component.translatable(key).append(": " + seconds + "s");
		Button button = screen.addWidget(Button.builder(label, b -> onPress.run())
			.tooltip(Tooltip.create(Component.translatable(key + ".tip")))
			.bounds(x, y, w, ROW_H).build());
		rows.add(button);
		return y + ROW_H + ROW_GAP;
	}

	private static int nextTimeout(int current) {
		if (current < 15) return 15;
		if (current < 30) return 30;
		if (current < 60) return 60;
		if (current < 120) return 120;
		return 10;
	}

	private static int nextWatchdog(int current) {
		if (current < 30) return 30;
		if (current < 60) return 60;
		if (current < 120) return 120;
		if (current < 300) return 300;
		return 20;
	}

	private void syncVisibility() {
		for (Button b : rows) b.visible = open;
		if (closeButton != null) closeButton.visible = open;
	}

	void draw(Painter painter, int mouseX, int mouseY) {
		painter.dropShadow(x, y, WIDTH, h, COLOR_OVERLAY_SHADOW, OVERLAY_SHADOW_SPREAD);
		painter.roundedPanel(x, y, WIDTH, h, COLOR_OVERLAY_BG, COLOR_OVERLAY_EDGE_LIGHT, COLOR_OVERLAY_EDGE_DARK);

		var font = screen.font();
		int textX = x + PAD;
		painter.text(font, Component.translatable("gui.retrograde.settings.title"), textX, y + PAD, COLOR_OVERLAY_VALUE);
	}
}
