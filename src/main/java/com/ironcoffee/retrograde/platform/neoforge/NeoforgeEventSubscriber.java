package com.ironcoffee.retrograde.platform.neoforge;

//? neoforge {

/*import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//? if <= 1.20.3 {
import net.neoforged.fml.common.Mod;
//?} else {
/^import net.neoforged.fml.common.EventBusSubscriber;
 ^///?}

//? if <= 1.20.3 {
@Mod.EventBusSubscriber
//?} else {
/^@EventBusSubscriber
 ^///?}
public class NeoforgeEventSubscriber {

	// Both events fire before the change is applied and can be cancelled by
	// other mods. LOWEST priority runs last, so isCanceled() here reflects
	// the final outcome, not just this listener's view.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockBreak(BreakBlockEvent event) {
		if (event.isCanceled()) return;
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
		ChunkTracker.forServer(serverLevel.getServer())
			.markTouched(serverLevel, ChunkPos.containing(event.getPos()));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
		if (event.isCanceled()) return;
		if (!(event.getEntity() instanceof ServerPlayer)) return;
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
		ChunkTracker.forServer(serverLevel.getServer())
			.markTouched(serverLevel, ChunkPos.containing(event.getPos()));
	}

}
*///?}
