package com.ironcoffee.retrograde.platform.fabric;

//? fabric {

import com.ironcoffee.retrograde.Main;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;
//? if <26 {
import com.ironcoffee.retrograde.gui.ChunkMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
//?}

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	//? if <26 {
	private static KeyMapping openChunkMapKey;
	//?}

	@Override
	public void onInitializeClient() {
		Main.onInitializeClient();

		// Keybinding + map screen only wired up for <26 right now — the
		// screen itself doesn't render anything on >=26 yet (see
		// ChunkMapScreen), and 26.x's KeyMapping.Category is a proper type
		// rather than a bare category string, needing its own registration
		// research rather than a guess. Follow-up work, not this slice.
		//? if <26 {
		openChunkMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.retrograde.open_chunk_map",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"key.category.retrograde"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openChunkMapKey.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new ChunkMapScreen());
				}
			}
		});
		//?}
	}

}
//?}
