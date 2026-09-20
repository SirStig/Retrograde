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

import java.util.ArrayList;
import java.util.List;
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
final class ChunkTerrainTextureCache implements AutoCloseable {
	private final Map<ChunkPos, ResourceLocation> ready = new ConcurrentHashMap<>();
	private final Set<ChunkPos> confirmedUnloaded = ConcurrentHashMap.newKeySet();
	private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
	private final List<DynamicTexture> owned = new ArrayList<>();
	private final AtomicInteger nextId = new AtomicInteger();

	enum Status { READY, PENDING, UNLOADED }

	Status statusOf(ChunkPos pos) {
		if (ready.containsKey(pos)) return Status.READY;
		if (confirmedUnloaded.contains(pos)) return Status.UNLOADED;
		return Status.PENDING;
	}

	ResourceLocation textureOf(ChunkPos pos) {
		return ready.get(pos);
	}

	void request(Minecraft minecraft, MinecraftServer server, ServerLevel level, ChunkPos pos) {
		if (ready.containsKey(pos) || confirmedUnloaded.contains(pos) || !pending.add(pos)) {
			return;
		}
		server.execute(() -> {
			int[] sample = ChunkTerrainSample.sample(level, pos);
			if (sample == null) {
				confirmedUnloaded.add(pos);
				pending.remove(pos);
				return;
			}
			minecraft.execute(() -> {
				int size = ChunkTerrainSample.SIZE;
				NativeImage image = new NativeImage(size, size, false);
				for (int z = 0; z < size; z++) {
					for (int x = 0; x < size; x++) {
						int argb = sample[z * size + x];
						// NativeImage stores ABGR, not ARGB - swap red and blue.
						int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF0000) >> 16) | ((argb & 0xFF) << 16);
						//? if >=26 {
						/*image.setPixelABGR(x, z, abgr);
						*///?} else {
						image.setPixelRGBA(x, z, abgr);
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
				ready.put(pos, location);
				owned.add(texture);
				pending.remove(pos);
			});
		});
	}

	@Override
	public void close() {
		for (DynamicTexture texture : owned) {
			texture.close();
		}
		owned.clear();
		ready.clear();
	}
}
