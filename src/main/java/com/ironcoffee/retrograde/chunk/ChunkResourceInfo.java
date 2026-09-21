package com.ironcoffee.retrograde.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ore tally for a single chunk, for the map screen's hover tooltip.
 *
 * Only works on chunks that are already loaded: getChunkNow doesn't force
 * one, so most of the 17x17 grid comes back null since it's wider than
 * typical render/simulation distance. That's fine, we'd rather say nothing
 * than lie about a chunk we can't actually see.
 *
 * Counting uses PalettedContainer#count, which walks the palette rather
 * than all 4096 positions per section, so this is cheap enough to run on
 * hover without a persistent cache.
 */
public final class ChunkResourceInfo {
	// 26.3 collapsed the individual per-ore tags (COAL_ORES, DIAMOND_ORES,
	// EMERALD_ORES, REDSTONE_ORES, LAPIS_ORES) into one umbrella ORES tag;
	// 1.20.1 and 26.1 only have the per-type ones.
	//? if >=26.3 {
	/*private static final List<TagKey<Block>> ORE_TAGS = List.of(BlockTags.ORES);
	*///?} else {
	private static final List<TagKey<Block>> ORE_TAGS = List.of(
		BlockTags.COAL_ORES, BlockTags.COPPER_ORES, BlockTags.IRON_ORES,
		BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES,
		BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES
	);
	//?}

	// Cross-mod ore conventions: most modded ores tag into one of these even
	// when they don't bother with vanilla's per-type tags above. Referencing
	// a tag id that no loaded mod ever populates is harmless - state.is()
	// on an empty tag just returns false.
	private static final TagKey<Block> COMMON_ORES_TAG = blockTag("c", "ores");
	private static final TagKey<Block> FORGE_ORES_TAG = blockTag("forge", "ores");

	private static TagKey<Block> blockTag(String namespace, String path) {
		//? if >=26 {
		/*return TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
		*///?} else {
		return TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation(namespace, path));
		//?}
	}

	private ChunkResourceInfo() {}

	public record Entry(Block block, int count) {}

	/** Ore tally plus the biome at the chunk's center, or null if the chunk isn't currently loaded. */
	public record Inspection(List<Entry> ores, ResourceLocation biome) {}

	/** Null if the chunk isn't currently loaded. */
	public static Inspection scan(ServerLevel level, ChunkPos pos) {
		ChunkSource chunkSource = level.getChunkSource();
		//? if >=26 {
		/*int x = pos.x();
		int z = pos.z();
		*///?} else {
		int x = pos.x;
		int z = pos.z;
		//?}
		if (!chunkSource.hasChunk(x, z)) {
			return null;
		}
		LevelChunk chunk = chunkSource.getChunkNow(x, z);
		if (chunk == null) {
			return null;
		}

		Map<Block, Integer> tally = new HashMap<>();
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.hasOnlyAir()) continue;
			section.getStates().count((BlockState state, int count) -> {
				Block block = state.getBlock();
				if (isOre(state, block)) {
					tally.merge(block, count, Integer::sum);
				}
			});
		}

		List<Entry> entries = new ArrayList<>();
		tally.forEach((block, count) -> entries.add(new Entry(block, count)));
		entries.sort((a, b) -> b.count() - a.count());
		return new Inspection(entries, biomeAt(level, chunk, x, z));
	}

	private static ResourceLocation biomeAt(ServerLevel level, LevelChunk chunk, int chunkX, int chunkZ) {
		int worldX = chunkX * 16 + 8;
		int worldZ = chunkZ * 16 + 8;
		int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
		Holder<Biome> biome = level.getBiome(new BlockPos(worldX, surfaceY, worldZ));
		return biome.unwrapKey().map(key -> key.location()).orElse(null);
	}

	/** Shared with SavedChunkReader, which tallies the same ores off the region file. */
	static boolean isOre(BlockState state, Block block) {
		for (TagKey<Block> tag : ORE_TAGS) {
			if (state.is(tag)) return true;
		}
		if (state.is(COMMON_ORES_TAG) || state.is(FORGE_ORES_TAG)) return true;
		// Vanilla's ore tags miss a couple of vanilla blocks (ancient
		// debris, nether quartz has no tag either) and won't cover a
		// modded ore that skips both the vanilla and common tags, so fall
		// back to a name check.
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return path.contains("_ore") || path.equals("ancient_debris");
	}
}
