package com.ironcoffee.retrograde.gui;

import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTerrainSample;
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
 * Turns a sampled chunk (ChunkTerrainSample) into an actual GPU texture, so
 * drawing the map is one blit per chunk instead of one fill() call per
 * block. That only matters once panning/zooming can put dozens of chunks
 * on screen at once, which a fixed small grid never needed.
 *
 * Two thread hops per chunk: sampling needs the server thread (same reason
 * as ChunkTerrainSample itself), texture creation needs the client/render
 * thread. MinecraftServer#execute does the first hop, Minecraft#execute
 * (same BlockableEventLoop family, just the client-side instance) does the
 * second once the sample is back.
 */
final class ChunkTerrainTextureCache {
	/**
	 * How long an unloaded chunk stays written off before we look again.
	 * Without this, a chunk that was out of range the first time you panned
	 * over it would stay grey for the rest of the session even after you
	 * walked to it - or after a regen loaded it back in with new terrain.
	 */
	private static final long UNLOADED_RETRY_MS = 2000;

	private final Map<ChunkPos, ResourceLocation> ready = new ConcurrentHashMap<>();
	private final Map<ResourceLocation, DynamicTexture> textures = new ConcurrentHashMap<>();
	private final Map<ChunkPos, Long> unloadedAt = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
	private final AtomicInteger nextId = new AtomicInteger();

	enum Status { READY, PENDING, UNLOADED }

	Status statusOf(ChunkPos pos) {
		if (ready.containsKey(pos)) return Status.READY;
		if (unloadedAt.containsKey(pos)) return Status.UNLOADED;
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
			if (sample == null) {
				unloadedAt.put(pos, System.currentTimeMillis());
				pending.remove(pos);
				return;
			}
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
	}
}
