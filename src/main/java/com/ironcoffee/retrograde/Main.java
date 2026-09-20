package com.ironcoffee.retrograde;

import com.ironcoffee.retrograde.platform.Platform;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

//? fabric {
import com.ironcoffee.retrograde.platform.fabric.FabricPlatform;
//?} neoforge {
/*import com.ironcoffee.retrograde.platform.neoforge.NeoforgePlatform;
 *///?} forge {
/*import com.ironcoffee.retrograde.platform.forge.ForgePlatform;
*///?}

public class Main {

	public static final String MOD_ID = "retrograde";
	public static final String MOD_VERSION = "1.0.0";
	public static final String MOD_FRIENDLY_NAME = "Retrograde";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, platform().loader());
		LOGGER.info("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);

	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, platform().loader());
		LOGGER.info("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	static Platform platform() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		return new FabricPlatform();
		//?} neoforge {
		/*return new NeoforgePlatform();
		 *///?} forge {
		/*return new ForgePlatform();
		*///?}
	}

	public static void serverInit(MinecraftServer server) {
		Main.LOGGER.info("[{}] Server Init!", Main.MOD_ID);
		List<String> list = List.of("Test", "Downgrader");
		Map<String, String> map = Map.of("Key", "Value");

		Main.LOGGER.info("[DowngraderTest] Success! List: " + list);
		Main.LOGGER.info("[DowngraderTest] Success! Map: " + map);

		try {
			//? if forge && <=1.15 {
			/*int ticks = server.tickCounter;
			 *///?} else {
			int ticks = server.tickCount;
			//?}
			Main.LOGGER.info("[AccessTest] OK - the private MinecraftServer tick counter is accessible (value: {})", ticks);
		} catch (Throwable t) {
			Main.LOGGER.error("[AccessTest] FAILED - the access widener / access transformer was not applied", t);
		}
	}

	public static void levelLoad() {
		Main.LOGGER.info("[{}] Level Loaded!", Main.MOD_ID);
	}
}
