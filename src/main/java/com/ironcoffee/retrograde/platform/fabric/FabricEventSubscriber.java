package com.ironcoffee.retrograde.platform.fabric;

//? fabric {

import com.ironcoffee.retrograde.chunk.ChunkTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

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
	}
}
//?}
