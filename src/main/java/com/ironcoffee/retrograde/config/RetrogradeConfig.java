package com.ironcoffee.retrograde.config;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-side settings, reached from the gear on the map screen.
 *
 * Deliberately java.util.Properties rather than Gson or a loader config
 * library: Properties is in the JDK, behaves identically on all six
 * targets, and survives a half-written file by falling back to defaults
 * per key instead of throwing the whole file away. There are a dozen
 * booleans here - nothing that earns a dependency or a Stonecutter branch.
 *
 * Stored under the game directory rather than a loader-specific config
 * path for the same reason: Minecraft#gameDirectory is a public File on
 * 1.20.1, 26.1 and 26.3 alike, where FabricLoader#getConfigDir and
 * NeoForge's equivalent would need three branches to say the same thing.
 *
 * Everything that can hand you resources you did not earn defaults to
 * off, and says so in its tooltip. The mod's own reason to exist - regen,
 * undo, retrogen - is not gated, because that is rebuilding a chunk the
 * way the game would have, not inventing ore.
 */
public final class RetrogradeConfig {
	private static final String FILE_NAME = "retrograde.properties";

	/**
	 * Master switch for everything below it. Off means the cheat-flagged
	 * operations are not merely disabled but absent from the manipulation
	 * menu, so a world you meant to play straight never shows the buttons.
	 */
	private static boolean cheatsEnabled = false;

	/** Rewrite a chunk's biome in place, via vanilla's own fillbiome. */
	private static boolean allowBiomeEdit = false;

	/** Add or remove ore in an already-generated chunk. */
	private static boolean allowOreEdit = false;

	/** Show slime chunks as an overlay and let the filter match on them. */
	private static boolean showSlimeChunks = true;

	/** Second confirmation on anything that destroys player work. */
	private static boolean confirmDestructive = true;

	/**
	 * Draw the progress screen over an opaque backdrop. On by default
	 * because a regen job teleports you above the build height and back,
	 * and watching that happen through a translucent screen reads as the
	 * game having broken rather than as the job doing its work.
	 */
	private static boolean opaqueProgressScreen = true;

	/** Seconds a job waits for a chunk to unload before reporting it skipped. */
	private static int unloadTimeoutSeconds = 30;

	/** Seconds without progress before the job's watchdog aborts and restores you. */
	private static int watchdogTimeoutSeconds = 60;

	private static boolean loaded;

	private RetrogradeConfig() {
	}

	// ----- accessors -----

	public static boolean cheatsEnabled() {
		ensureLoaded();
		return cheatsEnabled;
	}

	/** Cheat-gated: false whenever the master switch is off, whatever the key says. */
	public static boolean allowBiomeEdit() {
		ensureLoaded();
		return cheatsEnabled && allowBiomeEdit;
	}

	/** Cheat-gated: false whenever the master switch is off, whatever the key says. */
	public static boolean allowOreEdit() {
		ensureLoaded();
		return cheatsEnabled && allowOreEdit;
	}

	public static boolean showSlimeChunks() {
		ensureLoaded();
		return showSlimeChunks;
	}

	public static boolean confirmDestructive() {
		ensureLoaded();
		return confirmDestructive;
	}

	public static boolean opaqueProgressScreen() {
		ensureLoaded();
		return opaqueProgressScreen;
	}

	public static int unloadTimeoutSeconds() {
		ensureLoaded();
		return unloadTimeoutSeconds;
	}

	public static int watchdogTimeoutSeconds() {
		ensureLoaded();
		return watchdogTimeoutSeconds;
	}

	// ----- mutators, each saving immediately -----

	public static void setCheatsEnabled(boolean value) {
		ensureLoaded();
		cheatsEnabled = value;
		save();
	}

	public static void setAllowBiomeEdit(boolean value) {
		ensureLoaded();
		allowBiomeEdit = value;
		save();
	}

	public static void setAllowOreEdit(boolean value) {
		ensureLoaded();
		allowOreEdit = value;
		save();
	}

	public static void setShowSlimeChunks(boolean value) {
		ensureLoaded();
		showSlimeChunks = value;
		save();
	}

	public static void setConfirmDestructive(boolean value) {
		ensureLoaded();
		confirmDestructive = value;
		save();
	}

	public static void setOpaqueProgressScreen(boolean value) {
		ensureLoaded();
		opaqueProgressScreen = value;
		save();
	}

	public static void setUnloadTimeoutSeconds(int value) {
		ensureLoaded();
		unloadTimeoutSeconds = clamp(value, 5, 300);
		save();
	}

	public static void setWatchdogTimeoutSeconds(int value) {
		ensureLoaded();
		watchdogTimeoutSeconds = clamp(value, 15, 600);
		save();
	}

	// ----- persistence -----

	private static Path file() {
		Minecraft minecraft = Minecraft.getInstance();
		// Null only in a headless test harness; the caller still gets
		// defaults rather than an NPE on a settings read.
		if (minecraft == null || minecraft.gameDirectory == null) return null;
		return minecraft.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
	}

	private static synchronized void ensureLoaded() {
		if (loaded) return;
		// Set first: a failure below leaves the in-memory defaults in place
		// and must not make every accessor retry the same broken read.
		loaded = true;

		Path path = file();
		if (path == null || !Files.isRegularFile(path)) return;

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
		} catch (IOException e) {
			// Unreadable settings are not worth interrupting anyone over -
			// the defaults are all safe-side, and save() will rewrite it.
			return;
		}

		cheatsEnabled = bool(props, "cheatsEnabled", cheatsEnabled);
		allowBiomeEdit = bool(props, "allowBiomeEdit", allowBiomeEdit);
		allowOreEdit = bool(props, "allowOreEdit", allowOreEdit);
		showSlimeChunks = bool(props, "showSlimeChunks", showSlimeChunks);
		confirmDestructive = bool(props, "confirmDestructive", confirmDestructive);
		opaqueProgressScreen = bool(props, "opaqueProgressScreen", opaqueProgressScreen);
		unloadTimeoutSeconds = clamp(integer(props, "unloadTimeoutSeconds", unloadTimeoutSeconds), 5, 300);
		watchdogTimeoutSeconds = clamp(integer(props, "watchdogTimeoutSeconds", watchdogTimeoutSeconds), 15, 600);
	}

	private static synchronized void save() {
		Path path = file();
		if (path == null) return;

		Properties props = new Properties();
		props.setProperty("cheatsEnabled", Boolean.toString(cheatsEnabled));
		props.setProperty("allowBiomeEdit", Boolean.toString(allowBiomeEdit));
		props.setProperty("allowOreEdit", Boolean.toString(allowOreEdit));
		props.setProperty("showSlimeChunks", Boolean.toString(showSlimeChunks));
		props.setProperty("confirmDestructive", Boolean.toString(confirmDestructive));
		props.setProperty("opaqueProgressScreen", Boolean.toString(opaqueProgressScreen));
		props.setProperty("unloadTimeoutSeconds", Integer.toString(unloadTimeoutSeconds));
		props.setProperty("watchdogTimeoutSeconds", Integer.toString(watchdogTimeoutSeconds));

		try {
			Files.createDirectories(path.getParent());
			try (OutputStream out = Files.newOutputStream(path)) {
				props.store(out, "Retrograde settings");
			}
		} catch (IOException e) {
			// Same reasoning as the read: the setting still applies this
			// session, it just won't survive a restart.
		}
	}

	/**
	 * Unknown or misspelled values fall back to the default rather than to
	 * false, which is what Boolean#parseBoolean would do - and "false"
	 * happens to be the unsafe answer for the settings that default true.
	 */
	private static boolean bool(Properties props, String key, boolean fallback) {
		String raw = props.getProperty(key);
		if (raw == null) return fallback;
		raw = raw.trim();
		if (raw.equalsIgnoreCase("true")) return true;
		if (raw.equalsIgnoreCase("false")) return false;
		return fallback;
	}

	private static int integer(Properties props, String key, int fallback) {
		String raw = props.getProperty(key);
		if (raw == null) return fallback;
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
