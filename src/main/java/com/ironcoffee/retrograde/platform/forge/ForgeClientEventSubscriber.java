package com.ironcoffee.retrograde.platform.forge;

//? forge {
/*//? if <= 1.12 {
/^public final class ForgeClientEventSubscriber {
	private ForgeClientEventSubscriber() {} // <=1.12 client setup handled in ForgeEntrypoint
}
^///?} else {
import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.gui.ChunkMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//? if <= 1.21.5 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
/^import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
 ^///?}
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeClientEventSubscriber {

	private static KeyMapping openChunkMapKey;

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		openChunkMapKey = new KeyMapping(
			"key.retrograde.open_chunk_map",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"key.category.retrograde"
		);
		event.register(openChunkMapKey);

		// Client tick lives on Forge's general event bus, not the MOD bus
		// this class's other handler (client setup) is scoped to — register
		// this one manually rather than fighting the class-level bus
		// annotation for a single listener.
		MinecraftForge.EVENT_BUS.addListener(ForgeClientEventSubscriber::onClientTick);
	}

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft client = Minecraft.getInstance();
		while (openChunkMapKey.consumeClick()) {
			if (client.player != null && client.screen == null) {
				client.setScreen(new ChunkMapScreen());
			}
		}
	}

	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		Main.onInitializeClient();
	}
}
//?}
*///?}
