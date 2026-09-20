package com.ironcoffee.retrograde.mixin;

import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a chunk TOUCHED when a player places a block in it. Fabric API has
 * no "block placed" event (only break/attack/use callbacks, see
 * net.fabricmc.fabric.api.event.player), so this hooks BlockItem#place
 * directly instead, the one method every block item's placement funnels
 * through regardless of game mode or loader.
 *
 * Breaking is handled separately via Fabric's PlayerBlockBreakEvents.AFTER
 * (see FabricEventSubscriber), which needs no mixin.
 */
@Mixin(BlockItem.class)
@MixinEnvironment(type = MixinEnvironment.Env.MAIN)
public class BlockPlaceMixin {

	@Inject(method = "place", at = @At("RETURN"))
	private void retrograde$onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()) {
			return;
		}
		Player player = context.getPlayer();
		if (player == null) {
			// Not placed by a player (e.g. a dispenser), so it doesn't count as touched.
			return;
		}
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockPos pos = context.getClickedPos();
		//? if >=26 {
		/*ChunkPos chunkPos = ChunkPos.containing(pos);
		*///?} else {
		ChunkPos chunkPos = new ChunkPos(pos);
		//?}
		ChunkTracker.forServer(serverLevel.getServer())
			.markTouched(serverLevel, chunkPos);
	}
}
