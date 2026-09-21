package com.ironcoffee.retrograde.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Rewrites the biome of the selected chunks.
 *
 * Delegates to vanilla's own {@code fillbiome} rather than editing the
 * biome palette by hand: the command exists on 1.20.1, 26.1 and 26.3
 * alike (checked against the shipped classes, not assumed), it already
 * handles the biome container's 4x4x4 granularity, and it sends the chunk
 * back to the client so the change shows up without a reload. Writing the
 * palette directly would mean reimplementing all three of those, on a
 * format that has already changed once under this mod (see
 * SavedChunkReader).
 *
 * It runs one command per chunk against a permission-4 command source
 * built from the server, so this works in a world that doesn't have
 * cheats enabled - the gate on it is Retrograde's own setting, not the
 * world's.
 *
 * What it does not do is regenerate anything. The blocks already there
 * stay exactly as they are; only the biome changes, so a desert turned
 * into a swamp is a desert that now has swamp fog, mob spawns and grass
 * colour. Actually reshaping the terrain to match is a regen, and that's
 * a different button in the same menu.
 */
public class BiomeEditScreen extends Screen {
	private static final int ROW_H = 20;
	private static final int ROW_GAP = 4;
	private static final int ROW_W = 240;

	private final ChunkMapScreen parent;
	private final List<ChunkPos> targets;
	private final List<ResourceLocation> choices;
	private int index;

	public BiomeEditScreen(ChunkMapScreen parent, List<ChunkPos> targets, List<ResourceLocation> choices) {
		super(Component.translatable("gui.retrograde.biome_edit.title", targets.size()));
		this.parent = parent;
		this.targets = targets;
		this.choices = choices;
	}

	@Override
	protected void init() {
		super.init();

		int x = width / 2 - ROW_W / 2;
		int y = Math.max(50, height / 2 - 40);

		// Cycled from the biomes the world actually registered rather than
		// typed, for the same reason the filter does it: there is nothing to
		// spell, and no way to land on a biome this world has never heard of.
		addRenderableWidget(Button.builder(biomeLabel(), b -> {
				if (!choices.isEmpty()) {
					index = (index + 1) % choices.size();
				}
				rebuildWidgets();
			})
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.biome_edit.pick.tip")))
			.bounds(x, y, ROW_W, ROW_H)
			.build());
		y += ROW_H + ROW_GAP * 2;

		Button apply = addRenderableWidget(Button.builder(
				Component.translatable("gui.retrograde.biome_edit.apply", targets.size()),
				b -> parent.applyBiomeEdit(targets, choices.get(index)))
			.tooltip(Tooltip.create(Component.translatable("gui.retrograde.biome_edit.apply.tip")))
			.bounds(x, y, ROW_W, ROW_H)
			.build());
		apply.active = !choices.isEmpty() && !targets.isEmpty();
		y += ROW_H + ROW_GAP;

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
			.bounds(x, y, ROW_W, ROW_H)
			.build());
	}

	private Component biomeLabel() {
		if (choices.isEmpty()) {
			return Component.translatable("gui.retrograde.biome_edit.none");
		}
		ResourceLocation id = choices.get(index);
		return Component.translatable("gui.retrograde.biome_edit.pick",
			Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString());
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
