package com.ironcoffee.retrograde.platform.fabric;

//? fabric {

import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.event.ExampleEventHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
//? if <= 1.16 {
/*import com.ironcoffee.retrograde.Main;
import net.fabricmc.fabric.api.event.world.WorldTickCallback;
*///?} else {
import net.fabricmc.fabric.api.entity.event.v1.*;
//?}

public class FabricEventSubscriber {

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel serverLevel) {
				//? if >=26 {
				/*ChunkPos chunkPos = ChunkPos.containing(pos);
				*///?} else {
				ChunkPos chunkPos = new ChunkPos(pos);
				//?}
				ChunkTracker.forServer(serverLevel.getServer()).markTouched(serverLevel, chunkPos);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(ChunkTracker::close);

		//? if <= 1.16 {
		/*WorldTickCallback.EVENT.register((level) -> {
			Main.LOGGER.info("TICK");
		});
		*///?} else if <= 1.19.4 {
		/*ServerPlayerEvents.ALLOW_DEATH.register((entity, source, damageTaken) -> {
			if (entity instanceof ServerPlayer && damageTaken > 0) {
				ExampleEventHandler.onPlayerHurt((ServerPlayer) entity);
			}
			return true;
		});
		*///?} else if <= 1.21 {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damageTaken) -> {
			if (entity instanceof ServerPlayer && damageTaken > 0) {
				ExampleEventHandler.onPlayerHurt((ServerPlayer) entity);
			}
			return true;
		});
		//?} else {
		/*ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer && damageTaken > 0) {
				ExampleEventHandler.onPlayerHurt((ServerPlayer) entity);
			}
		});
		*///?}
	}
}
//?}
