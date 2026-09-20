package com.ironcoffee.retrograde.platform;

public interface Platform {
	boolean isModLoaded(String modId);

	ModLoader loader();

	String mcVersion();

	boolean isDevelopmentEnvironment();

	boolean isClient();

	default boolean isDebug() {
		return isDevelopmentEnvironment();
	}

	enum ModLoader {
		FABRIC, NEOFORGE, FORGE, QUILT
	}
}
