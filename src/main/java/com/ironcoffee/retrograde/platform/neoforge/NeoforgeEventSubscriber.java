package com.ironcoffee.retrograde.platform.neoforge;

//? neoforge {

/*import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.event.ExampleEventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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

	// Both break and place fire *before* the change is applied and can be
	// cancelled by other mods — LOWEST priority runs last, after any other
	// listener has had a chance to cancel, so checking isCanceled() here
	// actually reflects whether the change is really going to happen.
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

	//? if <= 1.20.5 {
	@SubscribeEvent
	public static void onPlayerDamage(LivingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
			ExampleEventHandler.onPlayerHurt(player);
		}
	}
	//?} else {
	/^@SubscribeEvent
	public static void onPlayerDamage(LivingDamageEvent.Post event) {
		//? if < 26.1 {
		var damage = event.getNewDamage();
		//?} else {
		/^¹var damage = event.getInflictedDamage();
		¹^///?}
		if (event.getEntity() instanceof ServerPlayer player && damage > 0) {
			ExampleEventHandler.onPlayerHurt(player);
		}
	}
	^///?}
}
*///?}
