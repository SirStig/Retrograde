package com.ironcoffee.retrograde.platform.forge;

//? forge {

/*import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerPlayer;
//? if <= 1.7 {
/^import cpw.mods.fml.common.eventhandler.SubscribeEvent;
^///?} else if <= 1.12 {
/^import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
^///?} else if <= 1.21.5 {
/^import net.minecraftforge.eventbus.api.SubscribeEvent;
^///?} else {
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
//?}
//? if > 1.9
import net.minecraftforge.fml.common.Mod;

//? if <= 1.9 {
//?} else if <= 1.11 {
/^@Mod.EventBusSubscriber
^///?} else {
@Mod.EventBusSubscriber(modid = Main.MOD_ID)
//?}
public class ForgeEventSubscriber {

	// Both events fire before the change is applied and can be cancelled,
	// so check isCanceled() before marking the chunk touched.
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (event.isCanceled()) return;
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
		ChunkTracker.forServer(serverLevel.getServer())
			.markTouched(serverLevel, new ChunkPos(event.getPos()));
	}

	@SubscribeEvent
	public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
		if (event.isCanceled()) return;
		if (!(event.getEntity() instanceof ServerPlayer)) return;
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
		ChunkTracker.forServer(serverLevel.getServer())
			.markTouched(serverLevel, new ChunkPos(event.getPos()));
	}
}
*///?}
