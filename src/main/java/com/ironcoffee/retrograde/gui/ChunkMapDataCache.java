package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkResourceInfo;
import com.ironcoffee.retrograde.chunk.ChunkTerrainSample;
import com.ironcoffee.retrograde.chunk.SavedChunkReader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything the map knows about a chunk: its terrain as a GPU texture, and
 * its biome and ore tally. Both come out of the same read, so they're cached
 * together rather than making the screen ask two different places and read
 * each chunk twice.
 *
 * The texture matters because it makes drawing the map one blit per chunk
 * instead of a fill() call per block - which only started mattering once
 * panning and zooming could put hundreds of chunks on screen at once.
 *
 * A chunk is read from memory when the server has it loaded and from the
 * region file (SavedChunkReader) when it doesn't, so the map covers
 * everywhere you've been rather than the loaded radius around you. Either
 * way it takes two thread hops: reading needs the server thread, and
 * creating a texture needs the client/render thread. MinecraftServer#execute
 * does the first, Minecraft#execute the second.
 */
final class ChunkMapDataCache {
	/**
	 * How long a chunk with nothing saved for it stays written off before we
	 * look again. Without this, a chunk that hadn't been generated the first
	 * time you panned over it would stay grey for the rest of the session
	 * even after you walked there - or after a regen rewrote it.
	 */
	private static final long UNLOADED_RETRY_MS = 2000;

	private final Map<ChunkPos, ResourceLocation> ready = new ConcurrentHashMap<>();
	private final Map<ResourceLocation, DynamicTexture> textures = new ConcurrentHashMap<>();
	private final Map<ChunkPos, Long> unloadedAt = new ConcurrentHashMap<>();
	private final Map<ChunkPos, ChunkResourceInfo.Inspection> inspections = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
	private final AtomicInteger nextId = new AtomicInteger();

	/** UNGENERATED means nothing is saved at those coordinates, not merely that the server isn't holding the chunk. */
	enum Status { READY, PENDING, UNGENERATED }

	Status statusOf(ChunkPos pos) {
		if (ready.containsKey(pos)) return Status.READY;
		if (unloadedAt.containsKey(pos)) return Status.UNGENERATED;
		return Status.PENDING;
	}

	ResourceLocation textureOf(ChunkPos pos) {
		return ready.get(pos);
	}

	/**
	 * Forgets a chunk's cached texture so the next frame re-samples it.
	 * Used after a regen job, where the whole point is that the terrain on
	 * screen is no longer what's in the world.
	 */
	void invalidate(Minecraft minecraft, ChunkPos pos) {
		unloadedAt.remove(pos);
		inspections.remove(pos);
		ResourceLocation location = ready.remove(pos);
		if (location == null) return;
		DynamicTexture texture = textures.remove(location);
		minecraft.execute(() -> {
			minecraft.getTextureManager().release(location);
			if (texture != null) {
				texture.close();
			}
		});
	}

	void request(Minecraft minecraft, MinecraftServer server, ServerLevel level, ChunkPos pos) {
		if (ready.containsKey(pos) || !readyToRetry(pos) || !pending.add(pos)) {
			return;
		}
		server.execute(() -> {
			int[] sample = ChunkTerrainSample.sample(level, pos);
			if (sample != null) {
				// Already on the server thread with the chunk in hand, and
				// counting ores off a live chunk walks the palette rather
				// than the blocks - cheap enough to just do now instead of
				// making a second trip over here when the cursor arrives.
				ChunkResourceInfo.Inspection inspection = ChunkResourceInfo.scan(level, pos);
				if (inspection != null) inspections.put(pos, inspection);
				upload(minecraft, pos, sample);
				return;
			}
			// Not in memory, so go to the region file instead of giving up.
			// The read is async and the decode runs off the server thread, so
			// panning over a few hundred unloaded chunks doesn't stall the
			// server the way a blocking read per chunk would.
			SavedChunkReader.read(level, pos).thenAccept(saved -> {
				if (saved == null) {
					unloadedAt.put(pos, System.currentTimeMillis());
					pending.remove(pos);
					return;
				}
				inspections.put(pos, saved.inspection());
				upload(minecraft, pos, saved.colors());
			});
		});
	}

	/**
	 * Biome and ore tally for a chunk, or null if it hasn't been read yet.
	 * Filled in by the same pass that builds the texture - reading the chunk
	 * is the expensive part and the tally comes almost free with it, so every
	 * chunk the map has drawn can also be described and filtered on without
	 * a second read.
	 */
	ChunkResourceInfo.Inspection inspectionOf(ChunkPos pos) {
		return inspections.get(pos);
	}

	/**
	 * Every chunk read successfully so far - the search space for the map's
	 * chunk filter. Deliberately not every chunk in the world: a filter can
	 * only honestly answer "which chunks have no diamond" for chunks it has
	 * actually looked inside, and quietly searching the whole save from a
	 * GUI would mean reading every region file on disk.
	 */
	Set<ChunkPos> mappedChunks() {
		return Set.copyOf(ready.keySet());
	}

	private void upload(Minecraft minecraft, ChunkPos pos, int[] sample) {
		minecraft.execute(() -> {
			int size = ChunkTerrainSample.SIZE;
			NativeImage image = new NativeImage(size, size, false);
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					int color = sample[z * size + x];
					//? if >=26 {
					/*// calculateARGBColor (26.x) returns real ARGB, unlike the
					// pre-26 method below, so it needs converting to the ABGR
					// setPixelABGR expects: swap red and blue, keep alpha/green.
					int abgr = (color & 0xFF00FF00) | ((color & 0xFF0000) >> 16) | ((color & 0xFF) << 16);
					image.setPixelABGR(x, z, abgr);
					*///?} else {
					// calculateRGBColor already returns its channels pre-swapped
					// for direct NativeImage use (Mojang's "RGB" name here is
					// misleading), so this needs no conversion - swapping it
					// again was the bug that made water/lava render red.
					image.setPixelRGBA(x, z, color);
					//?}
				}
			}
			//? if >=26 {
			/*DynamicTexture texture = new DynamicTexture(() -> "retrograde chunk terrain", image);
			*///?} else {
			DynamicTexture texture = new DynamicTexture(image);
			//?}
			String path = "chunk_terrain_" + nextId.getAndIncrement();
			//? if >=26 {
			/*ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, path);
			*///?} else {
			ResourceLocation location = new ResourceLocation(Main.MOD_ID, path);
			//?}
			minecraft.getTextureManager().register(location, texture);
			textures.put(location, texture);
			ready.put(pos, location);
			unloadedAt.remove(pos);
			pending.remove(pos);
		});
	}

	private boolean readyToRetry(ChunkPos pos) {
		Long lastMiss = unloadedAt.get(pos);
		return lastMiss == null || System.currentTimeMillis() - lastMiss >= UNLOADED_RETRY_MS;
	}

	/**
	 * Releases every texture. Each one has to come out of the texture
	 * manager as well as being closed - closing alone leaves the manager
	 * holding a registration for a texture that no longer exists, and the
	 * map can easily register a few hundred of them in one session.
	 */
	void close(Minecraft minecraft) {
		for (Map.Entry<ResourceLocation, DynamicTexture> entry : textures.entrySet()) {
			minecraft.getTextureManager().release(entry.getKey());
			entry.getValue().close();
		}
		textures.clear();
		ready.clear();
		unloadedAt.clear();
		inspections.clear();
	}
}
