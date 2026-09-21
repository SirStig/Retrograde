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
 * was installed, per dimension. A chunk the tracker has never recorded is
 * UNKNOWN, not "untouched": there's no way to know a chunk's history from
 * before the mod was installed.
 *
 * Storage is a plain text file per dimension under the world save dir
 * ("<world>/retrograde/<dimension>.chunks", one "x,z" per line), instead of
 * Minecraft's SavedData/PersistentState system, since that API has shifted
 * too much across supported versions to chase. Marking a chunk appends one
 * line, so the common case can't corrupt lines already recorded; clearing
 * one (which only happens on regen) rewrites the file from memory.
 */
public final class ChunkTracker {
	public enum Status {
		UNKNOWN,
		TOUCHED
	}

	private static final Map<MinecraftServer, ChunkTracker> INSTANCES = new ConcurrentHashMap<>();

	private final Map<ResourceKey<Level>, Set<ChunkPos>> touchedByDimension = new ConcurrentHashMap<>();
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

	public Status statusOf(ResourceKey<Level> dimension, ChunkPos pos) {
		Set<ChunkPos> touched = touchedByDimension.get(dimension);
		if (touched == null || !touched.contains(pos)) {
			return Status.UNKNOWN;
		}
		return Status.TOUCHED;
	}

	/** Marks a chunk touched. A no-op (no file write) if already recorded. */
	public void markTouched(ServerLevel level, ChunkPos pos) {
		ResourceKey<Level> dimension = level.dimension();
		Set<ChunkPos> touched = touchedByDimension.computeIfAbsent(dimension, key -> loadDimension(key));
		if (!touched.add(pos)) {
			return;
		}
		appendToFile(dimension, pos);
	}

	/**
	 * Drops a chunk's touched mark. Regenerating a chunk throws away exactly
	 * the player changes that mark was recording, so leaving it set would
	 * make the map lie about freshly regenerated terrain. Undo re-marks it
	 * (see ChunkRegenJob), since restoring the snapshot brings those changes
	 * back.
	 */
	public void clearTouched(ServerLevel level, ChunkPos pos) {
		ResourceKey<Level> dimension = level.dimension();
		Set<ChunkPos> touched = touchedByDimension.computeIfAbsent(dimension, key -> loadDimension(key));
		if (!touched.remove(pos)) {
			return;
		}
		rewriteFile(dimension, touched);
	}

	private Set<ChunkPos> loadDimension(ResourceKey<Level> dimension) {
		Set<ChunkPos> result = new HashSet<>();
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
						result.add(new ChunkPos(x, z));
					} catch (NumberFormatException ignored) {
						// Skip malformed lines (e.g. truncated by a crash mid-write)
						// instead of failing the whole dimension load.
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
			writer.write(line(pos));
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed recording touched chunk {} in {}", Main.MOD_ID, pos, dimension.location(), e);
		}
	}

	private void rewriteFile(ResourceKey<Level> dimension, Set<ChunkPos> touched) {
		// The append writer is holding the file open in append mode, so it
		// has to go before we can truncate. The next markTouched() reopens it.
		BufferedWriter appendWriter = writers.remove(dimension);
		if (appendWriter != null) {
			try {
				appendWriter.close();
			} catch (IOException e) {
				Main.LOGGER.error("[{}] Failed closing chunk tracker writer for {}", Main.MOD_ID, dimension.location(), e);
			}
		}
		Path file = fileFor(dimension);
		try {
			Files.createDirectories(file.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				for (ChunkPos pos : touched) {
					writer.write(line(pos));
					writer.newLine();
				}
			}
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed rewriting chunk tracker file {}", Main.MOD_ID, file, e);
		}
	}

	private static String line(ChunkPos pos) {
		//? if >=26 {
		/*return pos.x() + "," + pos.z();
		*///?} else {
		return pos.x + "," + pos.z;
		//?}
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
