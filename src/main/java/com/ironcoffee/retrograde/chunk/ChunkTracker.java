package com.ironcoffee.retrograde.chunk;

import com.ironcoffee.retrograde.Main;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which chunks have been player-modified ("touched") since this mod
 * was installed, per dimension. A chunk this tracker has never recorded is
 * UNKNOWN, not "untouched" — there's no way to know a chunk's history from
 * before the mod was installed, so the tracker never claims otherwise.
 *
 * Storage is a plain append-only text file per dimension under the world
 * save directory ("<world>/retrograde/<dimension>.chunks", one "x,z" per
 * line), not Minecraft's SavedData/PersistentState system — that API has
 * shifted enough across the versions this mod targets that a small flat
 * file we own outright is simpler than chasing it per version. Append-only
 * because a touched chunk only ever needs writing once (idempotent) and an
 * interrupted write can't corrupt previously-recorded lines.
 */
public final class ChunkTracker {
	public enum Status {
		UNKNOWN,
		TOUCHED
	}

	private static final Map<MinecraftServer, ChunkTracker> INSTANCES = new ConcurrentHashMap<>();

	private final Map<ResourceKey<Level>, Set<Long>> touchedByDimension = new ConcurrentHashMap<>();
	private final Map<ResourceKey<Level>, BufferedWriter> writers = new ConcurrentHashMap<>();
	private final Path storageDir;

	private ChunkTracker(MinecraftServer server) {
		this.storageDir = server.getWorldPath(LevelResource.ROOT).resolve("retrograde");
	}

	public static ChunkTracker forServer(MinecraftServer server) {
		return INSTANCES.computeIfAbsent(server, ChunkTracker::new);
	}

	public static void close(MinecraftServer server) {
		ChunkTracker tracker = INSTANCES.remove(server);
		if (tracker != null) {
			tracker.closeAllWriters();
		}
	}

	// ChunkPos#toLong() was renamed to #pack() in 26.x (alongside ChunkPos
	// becoming a record) — bridge both names to one call site.
	private static long packed(ChunkPos pos) {
		//? if >=26 {
		/*return pos.pack();
		*///?} else {
		return pos.toLong();
		//?}
	}

	public Status statusOf(ResourceKey<Level> dimension, ChunkPos pos) {
		Set<Long> touched = touchedByDimension.get(dimension);
		if (touched == null || !touched.contains(packed(pos))) {
			return Status.UNKNOWN;
		}
		return Status.TOUCHED;
	}

	/** Marks a chunk touched. A no-op (no file write) if already recorded. */
	public void markTouched(ServerLevel level, ChunkPos pos) {
		ResourceKey<Level> dimension = level.dimension();
		Set<Long> touched = touchedByDimension.computeIfAbsent(dimension, key -> loadDimension(key));
		if (!touched.add(packed(pos))) {
			return;
		}
		appendToFile(dimension, pos);
	}

	private Set<Long> loadDimension(ResourceKey<Level> dimension) {
		Set<Long> result = new HashSet<>();
		Path file = fileFor(dimension);
		if (Files.exists(file)) {
			try {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String trimmed = line.trim();
					if (trimmed.isEmpty()) continue;
					int comma = trimmed.indexOf(',');
					if (comma < 0) continue;
					try {
						int x = Integer.parseInt(trimmed.substring(0, comma));
						int z = Integer.parseInt(trimmed.substring(comma + 1));
						//? if >=26 {
						/*result.add(ChunkPos.pack(x, z));
						*///?} else {
						result.add(ChunkPos.asLong(x, z));
						//?}
					} catch (NumberFormatException ignored) {
						// Skip a malformed line (e.g. truncated by a crash mid-write)
						// rather than fail loading the whole dimension over it.
					}
				}
			} catch (IOException e) {
				Main.LOGGER.error("[{}] Failed reading chunk tracker file {}", Main.MOD_ID, file, e);
			}
		}
		return result;
	}

	private void appendToFile(ResourceKey<Level> dimension, ChunkPos pos) {
		try {
			BufferedWriter writer = writers.computeIfAbsent(dimension, key -> openWriter(key));
			if (writer == null) return;
			//? if >=26 {
			/*writer.write(pos.x() + "," + pos.z());
			*///?} else {
			writer.write(pos.x + "," + pos.z);
			//?}
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed recording touched chunk {} in {}", Main.MOD_ID, pos, dimension.location(), e);
		}
	}

	private BufferedWriter openWriter(ResourceKey<Level> dimension) {
		Path file = fileFor(dimension);
		try {
			Files.createDirectories(file.getParent());
			return Files.newBufferedWriter(file, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed opening chunk tracker file {}", Main.MOD_ID, file, e);
			return null;
		}
	}

	private Path fileFor(ResourceKey<Level> dimension) {
		// Dimension location e.g. "minecraft:overworld" / "minecraft:the_nether"
		// -> sanitize to a safe filename.
		String safe = dimension.location().toString().replace(':', '_').replace('/', '_');
		return storageDir.resolve(safe + ".chunks");
	}

	private void closeAllWriters() {
		for (BufferedWriter writer : writers.values()) {
			try {
				writer.close();
			} catch (IOException e) {
				Main.LOGGER.error("[{}] Failed closing chunk tracker writer", Main.MOD_ID, e);
			}
		}
		writers.clear();
	}
}
