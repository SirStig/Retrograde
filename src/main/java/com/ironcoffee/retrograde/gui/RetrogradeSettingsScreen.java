package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.config.RetrogradeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The gear on the map screen. Two groups: what the map is allowed to do to
 * your world, and how the jobs that do it behave while they run.
 *
 * The cheat group is off by default and collapses to a single row when the
 * master switch is off - the individual toggles don't even appear, so a
 * world you meant to play straight never shows you a button that would
 * hand you diamonds. That's the shape the request asked for: not a warning
 * on a button that still works, but a switch you have to find first.
 */
public class RetrogradeSettingsScreen extends Screen {
	private static final int ROW_H = 20;
	private static final int ROW_GAP = 4;
	private static final int ROW_W = 240;

	private final ChunkMapScreen parent;

	public RetrogradeSettingsScreen(ChunkMapScreen parent) {
		super(Component.translatable("gui.retrograde.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();

		boolean cheats = RetrogradeConfig.cheatsEnabled();
		// Rows are counted before any are placed so the block stays centred
		// as the cheat group appears and disappears under the master switch.
		int rows = 6 + (cheats ? 2 : 0);
		int x = width / 2 - ROW_W / 2;
		int y = Math.max(28, height / 2 - (rows * (ROW_H + ROW_GAP)) / 2);

		y = toggle(x, y, "gui.retrograde.settings.slime",
			RetrogradeConfig.showSlimeChunks(),
			b -> {
				RetrogradeConfig.setShowSlimeChunks(!RetrogradeConfig.showSlimeChunks());
				rebuild();
			});

		y = toggle(x, y, "gui.retrograde.settings.confirm",
			RetrogradeConfig.confirmDestructive(),
			b -> {
				RetrogradeConfig.setConfirmDestructive(!RetrogradeConfig.confirmDestructive());
				rebuild();
			});

		y = toggle(x, y, "gui.retrograde.settings.opaque_progress",
			RetrogradeConfig.opaqueProgressScreen(),
			b -> {
				RetrogradeConfig.setOpaqueProgressScreen(!RetrogradeConfig.opaqueProgressScreen());
				rebuild();
			});

		// Cycles rather than a slider: there are four sensible answers and a
		// slider would invite picking 7 seconds, which only produces skips.
		y = cycle(x, y, "gui.retrograde.settings.unload_timeout",
			RetrogradeConfig.unloadTimeoutSeconds(),
			b -> {
				RetrogradeConfig.setUnloadTimeoutSeconds(nextTimeout(RetrogradeConfig.unloadTimeoutSeconds()));
				rebuild();
			});

		y = cycle(x, y, "gui.retrograde.settings.watchdog_timeout",
			RetrogradeConfig.watchdogTimeoutSeconds(),
			b -> {
				RetrogradeConfig.setWatchdogTimeoutSeconds(nextWatchdog(RetrogradeConfig.watchdogTimeoutSeconds()));
				rebuild();
			});

		y += ROW_GAP;
		y = toggle(x, y, "gui.retrograde.settings.cheats", cheats,
			b -> {
				RetrogradeConfig.setCheatsEnabled(!RetrogradeConfig.cheatsEnabled());
				rebuild();
			});

		if (cheats) {
			y = toggle(x, y, "gui.retrograde.settings.biome_edit",
				RetrogradeConfig.allowBiomeEdit(),
				b -> {
					RetrogradeConfig.setAllowBiomeEdit(!RetrogradeConfig.allowBiomeEdit());
					rebuild();
				});
			y = toggle(x, y, "gui.retrograde.settings.ore_edit",
				RetrogradeConfig.allowOreEdit(),
				b -> {
					RetrogradeConfig.setAllowOreEdit(!RetrogradeConfig.allowOreEdit());
					rebuild();
				});
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(x, y + ROW_GAP * 2, ROW_W, ROW_H)
			.build());
	}

	private int toggle(int x, int y, String key, boolean value, Button.OnPress onPress) {
		Component label = Component.translatable(key)
			.append(": ")
			.append(Component.translatable(value ? "options.on" : "options.off"));
		addRenderableWidget(Button.builder(label, onPress)
			.tooltip(Tooltip.create(Component.translatable(key + ".tip")))
			.bounds(x, y, ROW_W, ROW_H)
			.build());
		return y + ROW_H + ROW_GAP;
	}

	private int cycle(int x, int y, String key, int seconds, Button.OnPress onPress) {
		Component label = Component.translatable(key).append(": " + seconds + "s");
		addRenderableWidget(Button.builder(label, onPress)
			.tooltip(Tooltip.create(Component.translatable(key + ".tip")))
			.bounds(x, y, ROW_W, ROW_H)
			.build());
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

	/**
	 * Toggling the master switch adds or removes rows, so the screen is
	 * rebuilt rather than relabelled. Screen#rebuildWidgets is protected and
	 * unchanged on 1.20.1, 26.1 and 26.3, so this needs no version branch.
	 */
	private void rebuild() {
		rebuildWidgets();
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
