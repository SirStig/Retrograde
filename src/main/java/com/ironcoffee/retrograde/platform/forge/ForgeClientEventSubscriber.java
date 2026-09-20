package com.ironcoffee.retrograde.platform.forge;

//? forge {
/*//? if <= 1.12 {
/^public final class ForgeClientEventSubscriber {
	private ForgeClientEventSubscriber() {} // <=1.12 client setup handled in ForgeEntrypoint
}
^///?} else {
import com.ironcoffee.retrograde.Main;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//? if <= 1.21.5 {
/^import net.minecraftforge.eventbus.api.SubscribeEvent;
^///?} else {
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
 //?}

@Mod.EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeClientEventSubscriber {

	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		Main.onInitializeClient();
	}
}
//?}
*///?}
