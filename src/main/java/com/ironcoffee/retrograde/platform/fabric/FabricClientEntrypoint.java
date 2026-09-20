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
		// KeyBindingHelper -> KeyMappingHelper,
		// registerKeyBinding -> registerKeyMapping — matching the vanilla
		// KeyBinding -> KeyMapping rename. The bigger change is the category
		// parameter: a bare String before, a proper KeyMapping.Category
		// record needing its own one-time registration now (verified
		// against the real 26.1 decompiled source, including that
		// KeyMapping.Category.register(ResourceLocation) — while marked
		// @deprecated in favor of NeoForge's RegisterKeyMappingsEvent for
		// NeoForge mods specifically — is still the correct, functional
		// call for a plain Fabric client with no such event to hook).
		//
		// >=26.3 doesn't get a keybinding (or the map screen it would open)
		// at all yet: tracing InputConstants.Type into the real 26.3
		// decompiled source turned up something much bigger than another
		// rename — Minecraft replaced GLFW with SDL for windowing/input in
		// 26.3 specifically (InputConstants.Type.KEYSYM is gone, replaced
		// by KEYBOARD, backed by SDLKeyboard.SDL_GetKeyFromScancode instead
		// of GLFW key constants; Minecraft.screen moved behind a new `gui`
		// wrapper too). That's a real, dedicated research problem — SDL
		// scancodes aren't the same numbering as the GLFW keycode I'd
		// otherwise hardcode — not something to graft a guess onto.
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
