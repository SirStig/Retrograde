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
import net.minecraft.server.level.ServerPlayer;
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
 * How it works: writes a minimal "Status: empty" NBT tag for the target
 * chunk directly into the level's on-disk chunk storage (ChunkMap's base
 * class exposes read/write publicly — no reflection needed). That alone
 * doesn't touch whatever's currently loaded in memory; it only changes what
 * a *future* load will see. Once the chunk naturally unloads (no player
 * nearby) and gets requested again, Minecraft's own chunk-loading pipeline
 * treats it exactly like unexplored terrain and regenerates it from scratch
 * — the same machinery ordinary world exploration already relies on,
 * including correctly blending with already-generated neighbor chunks. This
 * deliberately avoids hand-driving the multi-stage ChunkGenerator pipeline
 * (createBiomes, fillFromNoise, buildSurface, applyCarvers,
 * applyBiomeDecoration, createStructures, ...) ourselves, which would mean
 * correctly replicating Mojang's neighbor-chunk bookkeeping — fragile, and
 * not verifiable here since there's no way to launch a graphical client in
 * this environment.
 *
 * Practical implication: a chunk won't visibly regenerate while a player is
 * standing on or next to it, since its in-memory chunk holder has to
 * actually unload first — callers should require the player to step away
 * (see isPlayerNear) rather than promise an instant result.
 *
 * The storage class backing ChunkMap's read/write changed name between
 * 1.20.1 (ChunkStorage) and 26.x (SimpleRegionStorage), and write() went
 * from synchronous to returning a CompletableFuture — neither matters here
 * since the public read(ChunkPos)/write(ChunkPos, CompoundTag) signatures
 * we actually call are unchanged, and we don't need write's return value.
 */
public final class ChunkRegenService {
	private static final int MAX_SNAPSHOTS_PER_CHUNK = 5;

	private ChunkRegenService() {}

	public enum Result {
		OK,
		PLAYER_TOO_CLOSE,
		NOTHING_TO_UNDO,
		IO_ERROR
	}

	public static Result regenerate(ServerLevel level, ChunkPos pos) {
		if (isPlayerNear(level, pos)) {
			return Result.PLAYER_TOO_CLOSE;
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
		if (isPlayerNear(level, pos)) {
			return Result.PLAYER_TOO_CLOSE;
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

	private static boolean isPlayerNear(ServerLevel level, ChunkPos pos) {
		for (ServerPlayer player : level.players()) {
			//? if >=26 {
			/*ChunkPos playerChunk = ChunkPos.containing(player.blockPosition());
			*///?} else {
			ChunkPos playerChunk = new ChunkPos(player.blockPosition());
			//?}
			if (playerChunk.getChessboardDistance(pos) <= 1) {
				return true;
			}
		}
		return false;
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
