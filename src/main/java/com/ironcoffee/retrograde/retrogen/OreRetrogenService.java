package com.ironcoffee.retrograde.retrogen;

import com.ironcoffee.retrograde.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
//? if >=26.3 {
/*import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
*///?}
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ore retrogen: re-runs the world's own underground-ore features against a
 * chunk that already exists.
 *
 * This is the "ore editing" the settings screen has had a toggle for, built
 * the way ROADMAP.md argued it had to be. The obvious implementation -
 * scatter ore blocks into a generated chunk until some number is hit -
 * produces something that doesn't look like worldgen: wrong vein shapes,
 * wrong depth distribution, no respect for the biome or the surrounding
 * stone type. So this doesn't place ore at all. It asks the biome for the
 * placed features it would have run in the UNDERGROUND_ORES decoration
 * step and runs exactly those, through vanilla's own placement code, which
 * is the same thing the Mekanism integration does via that mod's command
 * (see RetrogenService). Real veins, real depths, real stone-type matching,
 * for free.
 *
 * Deterministic, but deliberately not claimed to be a bit-exact replay of
 * the original decoration pass. The random is seeded from the world seed and
 * the chunk's coordinates the same way ChunkGenerator seeds its own, but the
 * per-feature seed also takes an index, and vanilla's index is the feature's
 * position in the generator's globally sorted feature list (FeatureSorter),
 * which ChunkGenerator keeps in a private field with no accessor. The index
 * used here is the feature's position in the list this class builds instead.
 *
 * What that costs and what it buys is worth being precise about, because it
 * decides what the button does:
 *
 * - Ore that was never registered when the chunk was written - the mod or
 *   datapack case this whole mod exists for - generates properly. Nothing
 *   about the index affects whether a feature runs, only where it lands.
 * - Ore that *did* already generate gets a second, independent set of veins
 *   rather than the same ones again. So on a plain vanilla world this is a
 *   genuine "more ore", not a no-op.
 * - Running it twice on the same chunk does the same thing twice: same seed,
 *   same feature list, same order, same veins. It can't be stacked by
 *   holding down the button, which is the safety property that actually
 *   matters.
 *
 * There is no "less ore" and there probably shouldn't be. Removing ore from
 * a chunk someone may have already mined is a diff problem, not a generation
 * one, and there's no honest feature-rerun shape for it.
 *
 * Unlike regen, this needs the chunk *loaded* rather than unloaded - it
 * writes blocks through the live level rather than editing region files -
 * so the job running it skips the move-player-and-wait dance entirely.
 */
public final class OreRetrogenService {
	private OreRetrogenService() {}

	public enum Result {
		OK,
		/** This world has no underground-ore features at all to re-run. */
		NO_FEATURES,
		FAILED
	}

	public static Result run(ServerLevel level, ChunkPos pos) {
		try {
			// Forces the chunk in if it isn't already: the placement code
			// below writes through the live level, so unlike regen there is
			// nothing to do to a chunk that's only on disk.
			level.getChunk(chunkX(pos), chunkZ(pos));

			BlockPos origin = new BlockPos(pos.getMinBlockX(), minY(level), pos.getMinBlockZ());
			List<Holder<PlacedFeature>> features = oreFeatures(level, origin);
			if (features.isEmpty()) {
				return Result.NO_FEATURES;
			}

			ChunkGenerator generator = level.getChunkSource().getGenerator();
			// The same decoration seed ChunkGenerator#applyBiomeDecoration
			// derives for this chunk, so veins are placed by worldgen's own
			// distribution for this position rather than by an arbitrary
			// random. See the class comment for the one part of the seeding
			// that isn't reproducible from outside.
			WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed()));
			long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
			int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();

			// 26.3 moved placement off PlacedFeature onto a FeaturePlacer that
			// reuses its scratch position lists across features, so it's built
			// once out here rather than per feature. Everything it does is what
			// PlacedFeature did itself in 1.20.1 and 26.1.
			//? if >=26.3 {
			/*FeaturePlacer placer = new FeaturePlacer(level, generator);
			*///?}

			int index = 0;
			for (Holder<PlacedFeature> feature : features) {
				random.setFeatureSeed(decorationSeed, index++, step);
				// The biome-checking variant rather than the plain one: it
				// re-checks that the biome at each candidate position actually
				// wants this feature, which is what stops a vein spilling over
				// a biome border into somewhere it was never meant to generate.
				//? if >=26.3 {
				/*placer.placeWithBiomeCheck(feature.value(), random, origin);
				*///?} else {
				feature.value().placeWithBiomeCheck(level, generator, random, origin);
				//?}
			}
			return Result.OK;
		} catch (Exception e) {
			Main.LOGGER.error("[{}] Failed re-running ore features for chunk {}", Main.MOD_ID, pos, e);
			return Result.FAILED;
		}
	}

	/**
	 * Whether this world has any underground-ore features at all, so the map
	 * screen can say so up front instead of making someone watch a progress
	 * bar to find out nothing was going to happen.
	 *
	 * Asks the generator's biome source what biomes it can produce rather
	 * than asking a chunk what biome it is. That's the whole point of doing
	 * it this way: this is called from the client thread, and reading chunk
	 * data from there is the bug that made every chunk on this map read as
	 * "not loaded" once already. possibleBiomes() and the generation settings
	 * behind it are registry state, fixed once the world loads, so there is
	 * nothing here for the server thread to be halfway through changing.
	 */
	public static boolean hasOreFeatures(ServerLevel level) {
		try {
			int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
			for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
				List<HolderSet<PlacedFeature>> byStep = biome.value().getGenerationSettings().features();
				if (step < byStep.size() && byStep.get(step).size() > 0) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			// Better to offer the action and have it report nothing to do
			// than to hide it because an unexpected generator shape threw.
			return true;
		}
	}

	/** Horizontal offsets into the chunk that {@link #oreFeatures} samples biomes at. */
	private static final int[] SAMPLE_OFFSETS = {4, 12};
	/** How far apart, vertically, those samples are taken. */
	private static final int SAMPLE_Y_STEP = 32;

	/**
	 * Every placed feature any biome in this chunk runs in the
	 * UNDERGROUND_ORES step, in a stable order.
	 *
	 * Sampled across the chunk rather than read once at a corner, because a
	 * chunk is not one biome: the cave biomes carry ore features the surface
	 * biome above them doesn't, and vice versa, and taking only the biome at
	 * the origin would silently skip whichever of the two wasn't there. A
	 * superset is safe where a subset isn't - placeWithBiomeCheck filters
	 * each candidate position by the biome actually at it, so a feature
	 * gathered from a biome that only occupies part of the chunk still can't
	 * generate outside it.
	 */
	private static List<Holder<PlacedFeature>> oreFeatures(ServerLevel level, BlockPos origin) {
		int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
		// LinkedHashSet for a stable order across runs, which is what keeps
		// the per-feature seed - and so the result - reproducible. Registry
		// holders are singletons, so identity comparison is the right one.
		Set<Holder<PlacedFeature>> gathered = new LinkedHashSet<>();
		int minY = origin.getY();
		int maxY = minY + levelHeight(level);
		for (int y = minY; y < maxY; y += SAMPLE_Y_STEP) {
			for (int dx : SAMPLE_OFFSETS) {
				for (int dz : SAMPLE_OFFSETS) {
					Holder<Biome> biome = level.getBiome(new BlockPos(origin.getX() + dx, y, origin.getZ() + dz));
					List<HolderSet<PlacedFeature>> byStep = biome.value().getGenerationSettings().features();
					// A biome is free to declare fewer steps than the enum
					// has, so this is bounds-checked rather than assumed.
					if (step >= byStep.size()) continue;
					for (Holder<PlacedFeature> feature : byStep.get(step)) {
						gathered.add(feature);
					}
				}
			}
		}
		return List.copyOf(gathered);
	}

	private static int levelHeight(ServerLevel level) {
		return level.getHeight();
	}

	private static int minY(ServerLevel level) {
		//? if >=26 {
		/*return level.getMinY();
		*///?} else {
		return level.getMinBuildHeight();
		//?}
	}

	private static int chunkX(ChunkPos pos) {
		//? if >=26 {
		/*return pos.x();
		*///?} else {
		return pos.x;
		//?}
	}

	private static int chunkZ(ChunkPos pos) {
		//? if >=26 {
		/*return pos.z();
		*///?} else {
		return pos.z;
		//?}
	}
}
