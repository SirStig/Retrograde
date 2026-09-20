package com.ironcoffee.retrograde.platform.forge;

//? forge {
/*//? if <= 1.12 {
/^import com.ironcoffee.retrograde.Main;
//? if <= 1.7 {
/^¹import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
¹^///?} else {
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
//?}
//? if <= 1.9
//import net.minecraftforge.common.MinecraftForge;

@Mod(modid = Main.MOD_ID, name = Main.MOD_ID, version = "${version}", acceptableRemoteVersions = "*")
public class ForgeEntrypoint {
	public ForgeEntrypoint() {
		Main.onInitialize();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		if (FMLCommonHandler.instance().getSide().isClient()) {
			Main.onInitializeClient();
		}
		//? if <= 1.9
		//MinecraftForge.EVENT_BUS.register(new ForgeEventSubscriber());
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		Main.serverInit(event.getServer());
	}

	@Mod.EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		Main.levelLoad();
	}
}
^///?} else {
import com.ironcoffee.retrograde.Main;
import net.minecraftforge.fml.common.Mod;

@Mod(Main.MOD_ID)
public class ForgeEntrypoint {

	public ForgeEntrypoint() {
		Main.onInitialize();
	}
}
//?}

*///?}
