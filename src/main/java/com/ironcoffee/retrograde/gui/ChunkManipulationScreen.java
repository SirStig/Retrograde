package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.config.RetrogradeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Everything that changes the selected chunks, in one place.
 *
 * This used to be three buttons on the map itself (Regenerate, Undo,
 * Retrogen), which was fine at three and stopped being fine the moment
 * biome and ore edits were on the list - the action panel was already the
 * reason the map didn't fit a small window. Folding them behind one
 * "Chunk Manipulation" button keeps that panel two rows tall no matter how
 * many operations end up here, and gives each operation room to say what
 * it does to a chunk before it does it.
 *
 * Operations that can hand you resources you didn't earn only appear when
 * they've been switched on in settings (see RetrogradeConfig) - they're
 * absent rather than greyed, so a world played straight never shows them.
 *
 * The operations themselves still run on the map screen: it owns the job
 * and the progress screen that drives the job's clock.
 */
public class ChunkManipulationScreen extends Screen {
	private static final int ROW_H = 20;
	private static final int ROW_GAP = 4;
	private static final int ROW_W = 240;

	private final ChunkMapScreen parent;
	private final List<ChunkPos> targets;
	private final int undoable;

	public ChunkManipulationScreen(ChunkMapScreen parent) {
		super(Component.translatable("gui.retrograde.manipulate.title", parent.selectionSnapshot().size()));
		this.parent = parent;
		this.targets = parent.selectionSnapshot();
		this.undoable = parent.undoableCount();
	}

	@Override
	protected void init() {
		super.init();

		boolean any = !targets.isEmpty();
		boolean biome = RetrogradeConfig.allowBiomeEdit();
		int rows = 3 + (biome ? 1 : 0);

		int x = width / 2 - ROW_W / 2;
		int y = Math.max(40, height / 2 - (rows * (ROW_H + ROW_GAP)) / 2);

		y = row(x, y, Component.translatable("gui.retrograde.chunk_map.action.regen", targets.size()),
			"gui.retrograde.chunk_map.action.regen.tip", any, b -> handOff(parent::beginRegen));

		y = row(x, y, Component.translatable("gui.retrograde.chunk_map.action.undo", undoable),
			"gui.retrograde.chunk_map.action.undo.tip", undoable > 0, b -> handOff(parent::beginUndo));

		y = row(x, y, Component.translatable("gui.retrograde.chunk_map.action.retrogen", targets.size()),
			"gui.retrograde.chunk_map.action.retrogen.tip", any, b -> handOff(parent::beginRetrogen));

		if (biome) {
			y = row(x, y, Component.translatable("gui.retrograde.manipulate.set_biome", targets.size()),
				"gui.retrograde.manipulate.set_biome.tip", any, b -> handOff(parent::beginBiomeEdit));
		}
		// Ore editing is switched on in settings but has no row yet: scattering
		// ore into an already-generated chunk doesn't look like worldgen and
		// can't be undone the way regen can, so it wants its own design rather
		// than a button wired to the nearest available primitive.

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
			.bounds(x, y + ROW_GAP * 2, ROW_W, ROW_H)
			.build());
	}

	private int row(int x, int y, Component label, String tooltipKey, boolean enabled, Button.OnPress onPress) {
		Button button = addRenderableWidget(Button.builder(label, onPress)
			.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
			.bounds(x, y, ROW_W, ROW_H)
			.build());
		button.active = enabled;
		return y + ROW_H + ROW_GAP;
	}

	/**
	 * Each operation opens its own preview or confirmation from the map
	 * screen, so this menu steps out of the way first rather than sitting
	 * underneath as a screen nobody can get back to.
	 */
	private void handOff(Runnable operation) {
		openScreen(parent);
		operation.run();
	}

	@Override
	public void onClose() {
		openScreen(parent);
	}

	// Same two-line Stonecutter branch as the other screens here: 26.3 moved
	// screen management off Minecraft onto its Gui wrapper.
	private void openScreen(Screen screen) {
		//? if >=26.3 {
		/*minecraft.gui.setScreen(screen);
		*///?} else {
		minecraft.setScreen(screen);
		//?}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
