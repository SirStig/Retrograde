package com.ironcoffee.retrograde.platform.neoforge;

//? neoforge {

/*import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.gui.ChunkMapScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//? if <26.3 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
//?}
//? if <= 1.20.3 {
import net.neoforged.fml.common.Mod;
//?} else {
/^import net.neoforged.fml.common.EventBusSubscriber;
^///?}

//? if <= 1.20.3 {
@Mod.EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
 //?} else if <= 1.21.2 {
/^@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
^///?} else {
/^@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
^///?}
public class NeoforgeClientEventSubscriber {

	//? if <26.3 {
	private static KeyMapping openChunkMapKey;

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		// NeoForge's own recommended path (RegisterKeyMappingsEvent) rather
		// than the vanilla KeyMapping.Category.register(ResourceLocation) helper
		// Fabric uses — that helper is explicitly marked @deprecated in
		// favor of this event for NeoForge mods specifically.
		var category = new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "main"));
		event.registerCategory(category);
		openChunkMapKey = new KeyMapping(
			"key.retrograde.open_chunk_map",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			category
		);
		event.register(openChunkMapKey);

		// Tick events live on NeoForge's general event bus, not the MOD bus
		// this class's other handler (client setup) is scoped to — register
		// this one manually rather than fighting the class-level bus
		// annotation for a single listener.
		NeoForge.EVENT_BUS.addListener(NeoforgeClientEventSubscriber::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		while (openChunkMapKey.consumeClick()) {
			if (client.player != null && client.screen == null) {
				client.setScreen(new ChunkMapScreen());
			}
		}
	}
	//?}

	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		Main.onInitializeClient();
	}
}
*///?}
