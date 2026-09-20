package com.ironcoffee.retrograde.platform.forge;

//? if forge && >1.12 && <=1.14 {
/*import com.ironcoffee.retrograde.Main;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class ForgeServerEventSubscriber {

	@SubscribeEvent
	public static void onServerStarting(FMLServerStartingEvent event) {
		Main.serverInit(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStarted(FMLServerStartedEvent event) {
		Main.levelLoad();
	}
}
*///?}
