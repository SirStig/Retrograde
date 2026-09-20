package com.ironcoffee.retrograde.platform.fabric;

//? fabric {

import com.ironcoffee.retrograde.Main;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;
//? if <26.3 {
import com.ironcoffee.retrograde.gui.ChunkMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
//?}
//? if <26 {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else if <26.3 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.resources.ResourceLocation;
*///?}

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	//? if <26.3 {
	private static KeyMapping openChunkMapKey;
	//?}

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
		// >=26.3 has no keybinding (or the map screen it would open) yet:
		// Minecraft swapped GLFW for SDL for windowing/input in that version.
		// InputConstants.Type.KEYSYM is gone, replaced by KEYBOARD backed by
		// SDL scancodes, which don't share GLFW's numbering, so the old key
		// constant can't just be reused as-is.
		//? if <26 {
		openChunkMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.retrograde.open_chunk_map",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"key.category.retrograde"
		));
		//?} else if <26.3 {
		/*KeyMapping.Category category = KeyMapping.Category.register(
			ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "main"));
		openChunkMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.retrograde.open_chunk_map",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			category
		));
		*///?}

		//? if <26.3 {
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
