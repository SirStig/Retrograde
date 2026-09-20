package com.ironcoffee.retrograde.platform.forge;

//? forge {

/*//? if <= 1.12 {
/^import com.ironcoffee.retrograde.platform.Platform;
import net.minecraft.launchwrapper.Launch;
//? if <= 1.7 {
/^¹import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
¹^///?} else {
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
//?}

public class ForgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		return Loader.isModLoaded(modId);
	}

	@Override
	public ModLoader loader() {
		return ModLoader.FORGE;
	}

	@Override
	public String mcVersion() {
		return "";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
	}

	@Override
	public boolean isClient() {
		return FMLCommonHandler.instance().getSide().isClient();
	}
}
^///?} else {
import com.ironcoffee.retrograde.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;

public class ForgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		try {
			//? if <= 1.21.11 {
			if (LoadingModList.get() != null) {
				return LoadingModList.get().getModFileById(modId) != null;
			}
			//?} else {
			/^return LoadingModList.getModFileById(modId) != null;
			^///?}
		} catch (Throwable ignored) {}

		try {
			//? if <= 1.21.11 {
			if (ModList.get() != null) {
				return ModList.get().isLoaded(modId);
			}
			//?} else {
			/^return ModList.isLoaded(modId);
			^///?}
		} catch (Throwable ignored) {}
		return false;
	}

	@Override
	public ModLoader loader() {
		return ModLoader.FORGE;
	}

	@Override
	public String mcVersion() {
		return "";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		//? if <= 1.14.4 {
		/^return !"srg".equals(FMLLoader.getNaming());
		^///?} else {
		return !FMLLoader.isProduction();
		//?}
	}

	@Override
	public boolean isClient() {
		return FMLEnvironment.dist.isClient();
	}
}
//?}

*///?}
