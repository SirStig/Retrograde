package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Lists installed mods that have a known retrogen integration (see
 * RetrogenIntegrations) and hands the current chunk selection to one of
 * them. Reached from the Retrogen button on the map screen, so the targets
 * are whatever was selected there - one chunk or a hundred.
 */
public class RetrogenScreen extends Screen {
	private final ChunkMapScreen parent;
	private final List<ChunkPos> targets;
	private final List<RetrogenIntegration> integrations;

	public RetrogenScreen(ChunkMapScreen parent, List<ChunkPos> targets, List<RetrogenIntegration> integrations) {
		super(Component.translatable("gui.retrograde.retrogen.title", targets.size()));
		this.parent = parent;
		this.targets = targets;
		this.integrations = integrations;
	}

	@Override
	protected void init() {
		super.init();
		int y = height / 2 - (integrations.size() * 24 + 10) / 2;
		for (RetrogenIntegration integration : integrations) {
			addRenderableWidget(Button.builder(Component.literal(integration.displayName()), btn -> confirm(integration))
				.bounds(width / 2 - 100, y, 200, 20)
				.build());
			y += 24;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
			.bounds(width / 2 - 100, y + 6, 200, 20)
			.build());
	}

	private void confirm(RetrogenIntegration integration) {
		openScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					// Hands off to the map screen, which owns the job and the
					// progress screen that drives it.
					parent.startRetrogenJob(targets, integration);
				} else {
					openScreen(parent);
				}
			},
			Component.translatable("gui.retrograde.retrogen.confirm", integration.displayName(), targets.size()),
			Component.translatable("gui.retrograde.retrogen.confirm.detail")
		));
	}

	@Override
	public void onClose() {
		openScreen(parent);
	}

	// 26.3 moved screen management off Minecraft onto its new Gui wrapper:
	// Minecraft#setScreen is gone, replaced by minecraft.gui.setScreen(...).
	// 26.1 still has it directly, same as 1.20.1. (Duplicated from
	// ChunkMapScreen since it's a two-line Stonecutter branch either way.)
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
