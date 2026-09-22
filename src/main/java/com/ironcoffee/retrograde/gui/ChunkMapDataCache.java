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
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything the map knows about a chunk: its terrain color and its biome
 * and ore tally. Both come out of the same read, so they're cached together
 * rather than making the screen ask two different places and read each
 * chunk twice.
 *
 * Terrain colors go into a shared region atlas rather than one GPU texture
 * per chunk (see {@link Region}): a chunk is a 16x16 pixel square, and this
 * screen can have anywhere from a few dozen to several hundred thousand of
 * them on screen depending on zoom, and a unique texture object per chunk is
 * a unique GL bind-and-draw per chunk that no amount of zooming out makes
 * smaller. Batching {@value #REGION_CHUNK_SPAN}x{@value #REGION_CHUNK_SPAN}
 * chunks into one texture turns "one draw call per chunk" into "one draw
 * call per region", the same trick every other chunk-grid map mod (Xaero's,
 * JourneyMap) uses - draw call count then scales with screen area divided by
 * region size, not with how small the zoom has made an individual chunk.
 *
 * A chunk is read from memory when the server has it loaded and from the
 * region file (SavedChunkReader) when it doesn't, so the map covers
 * everywhere you've been rather than the loaded radius around you. Either
 * way it takes two thread hops: reading needs the server thread, and
 * touching a region's image/texture needs the client/render thread.
 * MinecraftServer#execute does the first, Minecraft#execute the second.
 *
 * <h2>Disk cache</h2>
 *
 * Every other chunk-grid map mod feels instant to open because it isn't
 * actually rendering anything when you open it - it's showing you a picture
 * it already had lying around from last time. Without that, this screen has
 * to re-decode NBT for every chunk in view from scratch on every single
 * open, and there is no version of "on demand" that makes several hundred
 * thousand region-file reads feel instant.
 *
 * So each region's painted pixels are mirrored to a small file under the
 * world's save folder ({@link #cacheDirFor}) and read back in on next open:
 * see {@link #paintRegion} for the write side and {@link #loadCachedRegion}
 * for the read side. That mirror is deliberately kept out of everything this
 * class uses to decide whether a chunk is actually generated - {@link
 * #textured}, {@link #inspected}, {@link #statusOf} - because the regen tool
 * targets chunks off exactly that decision, and a stale on-disk picture is
 * the last thing that should ever influence which chunks a destructive
 * action is allowed to touch. Loading a cached region only ever affects what
 * {@link #hasCachedVisual} reports, which the map screen uses purely to
 * decide whether to paint a "still loading" square over a chunk it already
 * has *some* picture for - the real read this session still runs in the
 * background regardless, via the ordinary {@link #request} path, and
 * corrects that picture the moment it lands.
 */
final class ChunkMapDataCache {
	/**
	 * How long a chunk with nothing saved for it stays written off before we
	 * look again. Without this, a chunk that hadn't been generated the first
	 * time you panned over it would stay grey for the rest of the session
	 * even after you walked there - or after a regen rewrote it.
	 */
	private static final long UNLOADED_RETRY_MS = 2000;

	/**
	 * Chunks per side of one region atlas texture - {@value} x {@value},
	 * i.e. a {@code 16 * ChunkTerrainSample.SIZE} pixel square. Big enough
	 * that even a fully zoomed-out view of a huge explored area only needs a
	 * few dozen region textures on screen; small enough that reading one
	 * never-before-seen chunk in an unexplored area doesn't allocate a
	 * wildly oversized texture around it.
	 */
	static final int REGION_CHUNK_SPAN = 16;
	/** A region atlas's own pixel width/height - it's square. */
	static final int REGION_TEXTURE_PIXELS = REGION_CHUNK_SPAN * ChunkTerrainSample.SIZE;
	/** Total pixels in one region atlas. */
	private static final int REGION_PIXEL_COUNT = REGION_TEXTURE_PIXELS * REGION_TEXTURE_PIXELS;
	/** How often {@link #flushDirtyRegions} gets a chance to matter - see ChunkMapScreen's tick(). */
	static final long CACHE_FLUSH_INTERVAL_MS = 10_000;

	/**
	 * How many chunk reads are allowed in flight at once - a backstop against
	 * unbounded growth, not a throughput limiter. Each request's actual
	 * server-thread work is a single {@code hasChunk} check; the real cost
	 * (a region-file read and NBT decode, or a height-map walk) runs async
	 * off that thread already, so the game's own IO/decode pool - not this
	 * number - is what paces how fast chunks actually come in. Setting this
	 * too low is exactly what made a zoomed-out view visibly crawl in one
	 * thin slice at a time: with only a couple hundred requests admitted per
	 * frame, a few hundred thousand newly-visible chunks took many seconds
	 * to finish admitting at all, regardless of how fast they were read.
	 */
	private static final int MAX_CONCURRENT_REQUESTS = 8192;

	/**
	 * One shared texture backing a grid of chunks; see the class doc.
	 *
	 * The texture's registered id is deliberately named {@code textureId}
	 * rather than something shaped like a Mojang accessor name: this field's
	 * type gets a different accessor-style name on different versions, and
	 * Stonecutter's chiseling rewrites call sites to match whichever one is
	 * current on the active version without checking whose symbol it's
	 * touching - it happily rewrote this record's own accessor calls right
	 * alongside real Mojang ones. Staying clear of that naming pattern avoids
	 * the collision instead of fighting it on every version switch.
	 *
	 * {@code rawPixels} mirrors {@code image} in the same pre-conversion
	 * representation {@link ChunkTerrainSample} and {@link SavedChunkReader}
	 * produce, so it can be written straight to disk (and back) without
	 * depending on any version-specific NativeImage encoding. {@code
	 * cachedVisual} tracks which of this region's chunk slots actually have
	 * real painted data, from either this session or a loaded cache file -
	 * see {@link #hasCachedVisual}. {@code dirty} marks a region as having
	 * pixels newer than whatever's on disk for it.
	 */
	private record Region(ResourceLocation textureId, NativeImage image, DynamicTexture texture,
			int[] rawPixels, boolean[] cachedVisual, AtomicBoolean dirty) {}

	/** A region's coordinates, in units of {@link #REGION_CHUNK_SPAN} chunks. */
	record RegionPos(int rx, int rz) {
		static RegionPos of(int chunkX, int chunkZ) {
			return new RegionPos(Math.floorDiv(chunkX, REGION_CHUNK_SPAN), Math.floorDiv(chunkZ, REGION_CHUNK_SPAN));
		}
	}

	private final Map<RegionPos, Region> regions = new ConcurrentHashMap<>();
	/** Chunks whose terrain colour has actually been verified this session - see the class doc's note on why this never comes from the disk cache. */
	private final Set<ChunkPos> textured = ConcurrentHashMap.newKeySet();
	/** Chunks whose biome/ore data has been read, whether or not their colour has (see the two-argument {@link #request}). */
	private final Set<ChunkPos> inspected = ConcurrentHashMap.newKeySet();
	private final Map<ChunkPos, Long> unloadedAt = new ConcurrentHashMap<>();
	private final Map<ChunkPos, ChunkResourceInfo.Inspection> inspections = new ConcurrentHashMap<>();
	private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
	private final AtomicInteger nextId = new AtomicInteger();

	/** Every region-cache file read/write runs here - never the render or server thread. */
	private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "retrograde-map-cache-io");
		thread.setDaemon(true);
		return thread;
	});

	/** UNGENERATED means nothing is saved at those coordinates, not merely that the server isn't holding the chunk. */
	enum Status { READY, PENDING, UNGENERATED }

	Status statusOf(ChunkPos pos) {
		if (textured.contains(pos) || inspected.contains(pos)) return Status.READY;
		if (unloadedAt.containsKey(pos)) return Status.UNGENERATED;
		return Status.PENDING;
	}

	/** The region backing a chunk's terrain colour, or null if that chunk hasn't been painted into one yet. */
	ResourceLocation regionTextureOf(ChunkPos pos) {
		Region region = regions.get(RegionPos.of(chunkXOf(pos), chunkZOf(pos)));
		return region == null ? null : region.textureId();
	}

	/** The region backing a given region position, or null if nothing in it has been read yet. */
	ResourceLocation regionTexture(RegionPos regionPos) {
		Region region = regions.get(regionPos);
		return region == null ? null : region.textureId();
	}

	/**
	 * Whether this chunk already has a real picture to show - either
	 * verified this session ({@link #hasTexture}) or loaded from a prior
	 * session's disk cache. The map screen uses this, not {@link
	 * #hasTexture}, to decide whether to paint a "still loading" square over
	 * a chunk in Terrain mode: a cached picture that hasn't been reverified
	 * yet is still a far better answer than a flat grey square, and the live
	 * read this session is already running in the background regardless.
	 */
	boolean hasCachedVisual(ChunkPos pos) {
		if (textured.contains(pos)) return true;
		Region region = regions.get(RegionPos.of(chunkXOf(pos), chunkZOf(pos)));
		return region != null && region.cachedVisual()[localSlot(pos)];
	}

	/** Whether this chunk's colour is actually verified this session (as opposed to only cached - see {@link #hasCachedVisual}). */
	boolean hasTexture(ChunkPos pos) {
		return textured.contains(pos);
	}

	/**
	 * Reads a chunk's biome/ore tally, and - only when {@code wantTexture} is
	 * true - its terrain colour into the shared region atlas.
	 *
	 * {@code wantTexture} should be false whenever the caller isn't going to
	 * draw the terrain texture anyway: zoomed out past the point a chunk
	 * shows more than a couple of screen pixels, or in a map mode (biome,
	 * ore density, touched) that paints a flat colour instead. Skipping the
	 * terrain sample in that case skips a real chunk of work - walking the
	 * height map for all 256 columns - not just the GPU upload, since the
	 * ore/biome scan is a separate, independent read of the same chunk.
	 */
	void request(Minecraft minecraft, MinecraftServer server, ServerLevel level, ChunkPos pos, boolean wantTexture) {
		if (textured.contains(pos)) return;
		if (!wantTexture && inspected.contains(pos)) return;
		if (!readyToRetry(pos) || pending.contains(pos)) return;
		if (pending.size() >= MAX_CONCURRENT_REQUESTS || !pending.add(pos)) return;
		server.execute(() -> {
			if (wantTexture) {
				int[] sample = ChunkTerrainSample.sample(level, pos);
				if (sample != null) {
					// Already on the server thread with the chunk in hand, and
					// counting ores off a live chunk walks the palette rather
					// than the blocks - cheap enough to just do now instead of
					// making a second trip over here when the cursor arrives.
					ChunkResourceInfo.Inspection inspection = ChunkResourceInfo.scan(level, pos);
					if (inspection != null) {
						inspections.put(pos, inspection);
						inspected.add(pos);
					}
					paintRegion(minecraft, pos, sample, cacheDirFor(server, level));
					return;
				}
			} else {
				ChunkResourceInfo.Inspection inspection = ChunkResourceInfo.scan(level, pos);
				if (inspection != null) {
					inspections.put(pos, inspection);
					inspected.add(pos);
					pending.remove(pos);
					return;
				}
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
				inspected.add(pos);
				if (wantTexture) {
					paintRegion(minecraft, pos, saved.colors(), cacheDirFor(server, level));
				} else {
					pending.remove(pos);
				}
			});
		});
	}

	/**
	 * Biome and ore tally for a chunk, or null if it hasn't been read yet.
	 * Filled in by the same pass that reads the terrain colour - reading the
	 * chunk is the expensive part and the tally comes almost free with it, so
	 * every chunk the map has drawn can also be described and filtered on
	 * without a second read.
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
		Set<ChunkPos> all = new HashSet<>(textured);
		all.addAll(inspected);
		return Set.copyOf(all);
	}

	/**
	 * Paints one chunk's 16x16 terrain sample into its region's shared image
	 * and re-uploads that region's texture. Runs on the render thread, same
	 * as the GPU texture creation this replaced - a region's NativeImage and
	 * DynamicTexture are both render-thread-only objects.
	 */
	private void paintRegion(Minecraft minecraft, ChunkPos pos, int[] sample, Path cacheDir) {
		minecraft.execute(() -> {
			RegionPos regionPos = RegionPos.of(chunkXOf(pos), chunkZOf(pos));
			Region region = regions.computeIfAbsent(regionPos, rp -> createRegion(minecraft, rp, cacheDir));
			int size = ChunkTerrainSample.SIZE;
			int baseX = Math.floorMod(chunkXOf(pos), REGION_CHUNK_SPAN) * size;
			int baseZ = Math.floorMod(chunkZOf(pos), REGION_CHUNK_SPAN) * size;
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					int color = sample[z * size + x];
					region.rawPixels()[(baseZ + z) * REGION_TEXTURE_PIXELS + (baseX + x)] = color;
					writePixel(region.image(), baseX + x, baseZ + z, color);
				}
			}
			region.cachedVisual()[localSlot(pos)] = true;
			region.dirty().set(true);
			region.texture().upload();
			textured.add(pos);
			unloadedAt.remove(pos);
			pending.remove(pos);
		});
	}

	/** Must run on the render thread - allocates the region's NativeImage, registers its GPU texture, and kicks off a background load of anything already cached for it. */
	private Region createRegion(Minecraft minecraft, RegionPos regionPos, Path cacheDir) {
		NativeImage image = new NativeImage(REGION_TEXTURE_PIXELS, REGION_TEXTURE_PIXELS, false);
		//? if >=26 {
		/*DynamicTexture texture = new DynamicTexture(() -> "retrograde chunk region", image);
		*///?} else {
		DynamicTexture texture = new DynamicTexture(image);
		//?}
		String path = "chunk_region_" + regionPos.rx() + "_" + regionPos.rz() + "_" + nextId.getAndIncrement();
		//? if >=26 {
		/*ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, path);
		*///?} else {
		ResourceLocation location = new ResourceLocation(Main.MOD_ID, path);
		//?}
		minecraft.getTextureManager().register(location, texture);
		Region region = new Region(location, image, texture,
			new int[REGION_PIXEL_COUNT], new boolean[REGION_CHUNK_SPAN * REGION_CHUNK_SPAN], new AtomicBoolean(false));
		loadCachedRegion(minecraft, regionPos, region, cacheDir);
		return region;
	}

	/** Kicks off a background read of this region's cache file, if any, and applies it back on the render thread once it lands. */
	private void loadCachedRegion(Minecraft minecraft, RegionPos regionPos, Region region, Path cacheDir) {
		Path file = regionFile(cacheDir, regionPos);
		ioExecutor.execute(() -> {
			int[] loaded = readRegionFile(file);
			if (loaded == null) return;
			minecraft.execute(() -> applyCachedPixels(region, loaded));
		});
	}

	/**
	 * Paints whatever a loaded cache file has into a region's live image,
	 * skipping any slot the game has already verified this session (a fresh
	 * read always wins over a stale cache) and any slot the cache never
	 * actually painted (a chunk that was never read when the cache was
	 * written comes back as all-zero, i.e. fully transparent - real chunk
	 * paints always write alpha 0xFF, so a zero alpha unambiguously means
	 * "no data here" rather than "a real, very dark chunk").
	 */
	private void applyCachedPixels(Region region, int[] loaded) {
		int size = ChunkTerrainSample.SIZE;
		boolean any = false;
		for (int cz = 0; cz < REGION_CHUNK_SPAN; cz++) {
			for (int cx = 0; cx < REGION_CHUNK_SPAN; cx++) {
				int slot = cz * REGION_CHUNK_SPAN + cx;
				if (region.cachedVisual()[slot]) continue;
				int baseX = cx * size;
				int baseZ = cz * size;
				if ((loaded[baseZ * REGION_TEXTURE_PIXELS + baseX] >>> 24) == 0) continue;
				for (int z = 0; z < size; z++) {
					for (int x = 0; x < size; x++) {
						int idx = (baseZ + z) * REGION_TEXTURE_PIXELS + (baseX + x);
						int color = loaded[idx];
						region.rawPixels()[idx] = color;
						writePixel(region.image(), baseX + x, baseZ + z, color);
					}
				}
				region.cachedVisual()[slot] = true;
				any = true;
			}
		}
		if (any) region.texture().upload();
	}

	/** Writes one pixel into a region's image, handling the same per-version colour-channel layout {@link #paintRegion} always used. */
	private static void writePixel(NativeImage image, int x, int y, int color) {
		//? if >=26 {
		/*// calculateARGBColor (26.x) returns real ARGB, unlike the pre-26
		// method below, so it needs converting to the ABGR setPixelABGR
		// expects: swap red and blue, keep alpha/green.
		int abgr = (color & 0xFF00FF00) | ((color & 0xFF0000) >> 16) | ((color & 0xFF) << 16);
		image.setPixelABGR(x, y, abgr);
		*///?} else {
		// calculateRGBColor already returns its channels pre-swapped for
		// direct NativeImage use (Mojang's "RGB" name here is misleading),
		// so this needs no conversion - swapping it again was the bug that
		// made water/lava render red.
		image.setPixelRGBA(x, y, color);
		//?}
	}

	private static int localSlot(ChunkPos pos) {
		return Math.floorMod(chunkZOf(pos), REGION_CHUNK_SPAN) * REGION_CHUNK_SPAN + Math.floorMod(chunkXOf(pos), REGION_CHUNK_SPAN);
	}

	private boolean readyToRetry(ChunkPos pos) {
		Long lastMiss = unloadedAt.get(pos);
		return lastMiss == null || System.currentTimeMillis() - lastMiss >= UNLOADED_RETRY_MS;
	}

	/**
	 * Forgets a chunk's cached data so the next frame re-samples it. Used
	 * after a regen job, where the whole point is that the terrain on screen
	 * is no longer what's in the world.
	 *
	 * Zeroes that chunk's slot in its region - both the live image and the
	 * on-disk mirror the next flush writes - rather than leaving the stale
	 * pre-regen pixels sitting there under a "pending" square: {@link
	 * #hasCachedVisual} would otherwise keep reporting a picture for it, and
	 * showing last session's terrain for a chunk regen just rewrote is
	 * exactly the kind of stale answer the disk cache is supposed to stay
	 * out of the way of.
	 */
	void invalidate(Minecraft minecraft, ChunkPos pos) {
		unloadedAt.remove(pos);
		inspections.remove(pos);
		inspected.remove(pos);
		textured.remove(pos);

		Region region = regions.get(RegionPos.of(chunkXOf(pos), chunkZOf(pos)));
		if (region == null) return;
		region.cachedVisual()[localSlot(pos)] = false;
		int size = ChunkTerrainSample.SIZE;
		int baseX = Math.floorMod(chunkXOf(pos), REGION_CHUNK_SPAN) * size;
		int baseZ = Math.floorMod(chunkZOf(pos), REGION_CHUNK_SPAN) * size;
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				region.rawPixels()[(baseZ + z) * REGION_TEXTURE_PIXELS + (baseX + x)] = 0;
			}
		}
		region.dirty().set(true);
	}

	/**
	 * Writes every region touched since the last flush to disk, off the
	 * render thread. Called on a timer while the map is open (see
	 * ChunkMapScreen's tick()) and always on {@link #close}, so a crash mid
	 * session loses at most the last {@link #CACHE_FLUSH_INTERVAL_MS} of
	 * newly-read chunks rather than the whole session.
	 */
	void flushDirtyRegions() {
		Path dir = cacheDir;
		if (dir == null) return;
		for (Map.Entry<RegionPos, Region> entry : regions.entrySet()) {
			Region region = entry.getValue();
			if (!region.dirty().compareAndSet(true, false)) continue;
			RegionPos regionPos = entry.getKey();
			// Snapshot rather than handing the live array to the IO thread:
			// paints keep landing on the render thread while this write is
			// in flight, and an array being read and written on two threads
			// at once with no ordering between them is a real data race.
			int[] snapshot = region.rawPixels().clone();
			ioExecutor.execute(() -> writeRegionFile(dir, regionPos, snapshot));
		}
	}

	private volatile Path cacheDir;

	/** Resolves (and remembers) the on-disk cache folder for this world+dimension. Safe to call from the server thread. */
	private Path cacheDirFor(MinecraftServer server, ServerLevel level) {
		Path dir = cacheDir;
		if (dir != null) return dir;
		String safeDimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
		dir = server.getWorldPath(LevelResource.ROOT).resolve("retrograde").resolve("mapcache").resolve(safeDimension);
		cacheDir = dir;
		return dir;
	}

	private static Path regionFile(Path dir, RegionPos regionPos) {
		return dir.resolve("r." + regionPos.rx() + "." + regionPos.rz() + ".bin");
	}

	/** Null on anything short of a clean, right-sized file - a missing, truncated or corrupt cache is just a cache miss, never an error worth surfacing. */
	private static int[] readRegionFile(Path file) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			return null;
		}
		if (bytes.length != REGION_PIXEL_COUNT * Integer.BYTES) return null;
		int[] pixels = new int[REGION_PIXEL_COUNT];
		ByteBuffer.wrap(bytes).asIntBuffer().get(pixels);
		return pixels;
	}

	/** Write-to-temp-then-rename, so a crash or kill mid-write leaves the previous good file in place rather than a truncated one. */
	private static void writeRegionFile(Path dir, RegionPos regionPos, int[] pixels) {
		try {
			Files.createDirectories(dir);
			Path file = regionFile(dir, regionPos);
			Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
			ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
			buffer.asIntBuffer().put(pixels);
			Files.write(tmp, buffer.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException notSupported) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Main.LOGGER.debug("Retrograde: couldn't write map cache region {}", regionPos, e);
		}
	}

	/**
	 * Releases every region texture and stops the cache-file IO thread.
	 * Each texture has to come out of the texture manager as well as being
	 * closed - closing alone leaves the manager holding a registration for a
	 * texture that no longer exists.
	 */
	void close(Minecraft minecraft) {
		flushDirtyRegions();
		ioExecutor.shutdown();
		try {
			ioExecutor.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		for (Region region : regions.values()) {
			minecraft.getTextureManager().release(region.textureId());
			region.texture().close();
		}
		regions.clear();
		textured.clear();
		inspected.clear();
		unloadedAt.clear();
		inspections.clear();
	}

	private static int chunkXOf(ChunkPos pos) {
		//? if >=26 {
		/*return pos.x();
		*///?} else {
		return pos.x;
		//?}
	}

	private static int chunkZOf(ChunkPos pos) {
		//? if >=26 {
		/*return pos.z();
		*///?} else {
		return pos.z;
		//?}
	}
}
