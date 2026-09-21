package com.ironcoffee.retrograde.regen;

import com.ironcoffee.retrograde.Main;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
//? if >=26 {
/*import net.minecraft.nbt.NbtAccounter;
*///?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Chunk regeneration with undo.
 *
 * Writes a minimal "Status: empty" NBT tag directly into the level's
 * on-disk chunk storage (ChunkMap's base class exposes read/write
 * publicly). That only changes what a future load sees, not what's
 * currently in memory: once the chunk naturally unloads and gets requested
 * again, vanilla's own chunk pipeline treats it as unexplored terrain and
 * regenerates it, blending correctly with neighbors. Hand-driving the
 * ChunkGenerator pipeline ourselves would mean replicating Mojang's
 * neighbor-chunk bookkeeping, so we let vanilla do that part.
 *
 * The one hard requirement is that the chunk must not be loaded when we
 * write: a loaded chunk holder saves itself back out when it eventually
 * unloads, quietly clobbering the tag we just wrote. So both entry points
 * refuse while the chunk is in memory (see isLoaded), and it's the
 * caller's job to get it unloaded first - ChunkRegenJob does that by
 * moving the player out of range and waiting.
 *
 * ChunkMap's storage class was renamed ChunkStorage -> SimpleRegionStorage
 * between 1.20.1 and 26.x, and write() became async. Neither matters here
 * since the read(ChunkPos)/write(ChunkPos, CompoundTag) signatures we call
 * are unchanged.
 */
public final class ChunkRegenService {
	private static final int MAX_SNAPSHOTS_PER_CHUNK = 5;

	private ChunkRegenService() {}

	public enum Result {
		OK,
		CHUNK_LOADED,
		NOTHING_TO_UNDO,
		IO_ERROR
	}

	public static Result regenerate(ServerLevel level, ChunkPos pos) {
		if (isLoaded(level, pos)) {
			return Result.CHUNK_LOADED;
		}
		ServerChunkCache chunkSource = level.getChunkSource();
		try {
			CompoundTag current = chunkSource.chunkMap.read(pos).join().orElse(null);
			if (current != null) {
				saveSnapshot(level, pos, current);
			}
			chunkSource.chunkMap.write(pos, emptyChunkTag(pos));
			return Result.OK;
		} catch (Exception e) {
			Main.LOGGER.error("[{}] Failed regenerating chunk {}", Main.MOD_ID, pos, e);
			return Result.IO_ERROR;
		}
	}

	public static Result undo(ServerLevel level, ChunkPos pos) {
		if (isLoaded(level, pos)) {
			return Result.CHUNK_LOADED;
		}
		Path snapshot = latestSnapshot(level, pos);
		if (snapshot == null) {
			return Result.NOTHING_TO_UNDO;
		}
		try {
			CompoundTag tag = readSnapshot(snapshot);
			level.getChunkSource().chunkMap.write(pos, tag);
			Files.deleteIfExists(snapshot);
			return Result.OK;
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed restoring chunk {} from {}", Main.MOD_ID, pos, snapshot, e);
			return Result.IO_ERROR;
		}
	}

	public static boolean hasUndo(ServerLevel level, ChunkPos pos) {
		return latestSnapshot(level, pos) != null;
	}

	/**
	 * Whether the chunk is currently in memory. This is the real gate on
	 * regenerating - a loaded chunk will save itself over our on-disk edit -
	 * and it's also strictly more accurate than asking how close a player
	 * is, since chunks stay loaded well past a player's immediate
	 * neighbours (view distance, forced chunks, spawn chunks).
	 */
	public static boolean isLoaded(ServerLevel level, ChunkPos pos) {
		//? if >=26 {
		/*return level.getChunkSource().hasChunk(pos.x(), pos.z());
		*///?} else {
		return level.getChunkSource().hasChunk(pos.x, pos.z);
		//?}
	}

	private static CompoundTag emptyChunkTag(ChunkPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("DataVersion", SharedConstants.WORLD_VERSION);
		//? if >=26 {
		/*tag.putInt("xPos", pos.x());
		tag.putInt("zPos", pos.z());
		*///?} else {
		tag.putInt("xPos", pos.x);
		tag.putInt("zPos", pos.z);
		//?}
		tag.putString("Status", "minecraft:empty");
		return tag;
	}

	private static void saveSnapshot(ServerLevel level, ChunkPos pos, CompoundTag tag) throws IOException {
		Path dir = undoDir(level, pos);
		Files.createDirectories(dir);
		Path file = dir.resolve(System.currentTimeMillis() + ".dat");
		//? if >=26 {
		/*NbtIo.writeCompressed(tag, file);
		*///?} else {
		NbtIo.writeCompressed(tag, file.toFile());
		//?}
		pruneOldSnapshots(dir);
	}

	private static CompoundTag readSnapshot(Path file) throws IOException {
		//? if >=26 {
		/*return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		*///?} else {
		return NbtIo.readCompressed(file.toFile());
		//?}
	}

	private static Path latestSnapshot(ServerLevel level, ChunkPos pos) {
		Path dir = undoDir(level, pos);
		if (!Files.isDirectory(dir)) return null;
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".dat"))
				.max(Comparator.naturalOrder())
				.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static void pruneOldSnapshots(Path dir) throws IOException {
		List<Path> files;
		try (Stream<Path> stream = Files.list(dir)) {
			files = stream.filter(p -> p.toString().endsWith(".dat"))
				.sorted(Comparator.naturalOrder())
				.toList();
		}
		int excess = files.size() - MAX_SNAPSHOTS_PER_CHUNK;
		for (int i = 0; i < excess; i++) {
			Files.deleteIfExists(files.get(i));
		}
	}

	private static Path undoDir(ServerLevel level, ChunkPos pos) {
		MinecraftServer server = level.getServer();
		String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
		//? if >=26 {
		/*String chunkKey = pos.x() + "_" + pos.z();
		*///?} else {
		String chunkKey = pos.x + "_" + pos.z;
		//?}
		return server.getWorldPath(LevelResource.ROOT)
			.resolve("retrograde").resolve("undo").resolve(dimension)
			.resolve(chunkKey);
	}
}
