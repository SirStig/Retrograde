package com.ironcoffee.retrograde.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Samples a chunk's top-down terrain color, one pixel per block, the same
 * way vanilla's own held map item does it (MapItem#update): walk the
 * height map down to the first block with a real map color, shade it a bit
 * lighter or darker than its west neighbor depending on whether the
 * terrain rises or drops, matching vanilla's own hillshade look. Reused
 * rather than reinvented so the map actually resembles the terrain instead
 * of a made-up color scheme.
 *
 * Must run on the server thread, same reason as ChunkResourceInfo.
 */
public final class ChunkTerrainSample {
	public static final int SIZE = 16;

	private ChunkTerrainSample() {}

	/** 256 packed ARGB ints, row-major (index = localZ * 16 + localX), or null if the chunk isn't loaded. */
	public static int[] sample(ServerLevel level, ChunkPos pos) {
		ChunkSource chunkSource = level.getChunkSource();
		//? if >=26 {
		/*int chunkX = pos.x();
		int chunkZ = pos.z();
		*///?} else {
		int chunkX = pos.x;
		int chunkZ = pos.z;
		//?}
		if (!chunkSource.hasChunk(chunkX, chunkZ)) {
			return null;
		}
		LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
		if (chunk == null) {
			return null;
		}

		int minBlockX = chunkX * SIZE;
		int minBlockZ = chunkZ * SIZE;
		//? if >=26 {
		/*int minBuildHeight = level.getMinY();
		*///?} else {
		int minBuildHeight = level.getMinBuildHeight();
		//?}
		int[] colors = new int[SIZE * SIZE];
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

		for (int lz = 0; lz < SIZE; lz++) {
			int previousHeight = Integer.MIN_VALUE;
			for (int lx = 0; lx < SIZE; lx++) {
				int worldX = minBlockX + lx;
				int worldZ = minBlockZ + lz;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);

				BlockState state;
				MapColor mapColor;
				if (y <= minBuildHeight) {
					state = Blocks.BEDROCK.defaultBlockState();
					mutablePos.set(worldX, minBuildHeight, worldZ);
					mapColor = state.getMapColor(level, mutablePos);
				} else {
					mutablePos.set(worldX, y, worldZ);
					do {
						mutablePos.setY(--y);
						state = chunk.getBlockState(mutablePos);
						mapColor = state.getMapColor(level, mutablePos);
					} while (mapColor == MapColor.NONE && y > minBuildHeight);
				}

				MapColor.Brightness brightness;
				if (previousHeight == Integer.MIN_VALUE || y == previousHeight) {
					brightness = MapColor.Brightness.NORMAL;
				} else if (y > previousHeight) {
					brightness = MapColor.Brightness.HIGH;
				} else {
					brightness = MapColor.Brightness.LOW;
				}
				previousHeight = y;

				//? if >=26 {
				/*colors[lz * SIZE + lx] = mapColor.calculateARGBColor(brightness) | 0xFF000000;
				*///?} else {
				colors[lz * SIZE + lx] = mapColor.calculateRGBColor(brightness) | 0xFF000000;
				//?}
			}
		}
		return colors;
	}
}
