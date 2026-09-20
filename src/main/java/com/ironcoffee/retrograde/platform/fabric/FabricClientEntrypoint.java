package com.ironcoffee.retrograde.platform.fabric;

//? fabric {

import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.gui.ChunkMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
//? if <26 {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.resources.ResourceLocation;
*///?}
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	private static KeyMapping openChunkMapKey;

	@Override
	public void onInitializeClient() {
		Main.onInitializeClient();

		// Fabric API's keybinding module was renamed end-to-end in 26.x:
		// fabric-key-binding-api-v1 -> fabric-key-mapping-api-v1,
		// KeyBindingHelper -> KeyMappingHelper, registerKeyBinding ->
		// registerKeyMapping, matching the vanilla KeyBinding -> KeyMapping
		// rename. The category parameter also changed, from a bare String to
		// a KeyMapping.Category record that needs one-time registration.
		// KeyMapping.Category.register(ResourceLocation) is marked
		// @deprecated in favor of NeoForge's RegisterKeyMappingsEvent, but
		// that's NeoForge-specific; it's still the right call on plain Fabric.
		//
		// 26.3 also swapped GLFW for SDL for windowing/input, which retired
		// InputConstants.Type.KEYSYM in favor of KEYBOARD. The key constant
		// itself didn't need to change though: InputConstants.KEY_M is its
		// own abstraction over the GLFW/SDL keycode, not a raw GLFW value,
		// so the same constant works on both sides of that split.
		//? if <26 {
		openChunkMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.retrograde.open_chunk_map",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"key.category.retrograde"
		));
		//?} else {
		/*KeyMapping.Category category = KeyMapping.Category.register(
			ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "main"));
		openChunkMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.retrograde.open_chunk_map",
			//? if <26.3 {
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			//?} else {
			/^InputConstants.Type.KEYBOARD,
			InputConstants.KEY_M,
			^///?}
			category
		));
		*///?}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openChunkMapKey.consumeClick()) {
				//? if <26.3 {
				if (client.player != null && client.screen == null) {
					client.setScreen(new ChunkMapScreen());
				}
				//?} else {
				/*if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new ChunkMapScreen());
				}
				*///?}
			}
		});
	}

}
//?}
