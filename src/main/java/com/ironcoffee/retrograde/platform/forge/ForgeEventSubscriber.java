package com.ironcoffee.retrograde.platform.forge;

//? forge {

/*import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.event.ExampleEventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.BlockEvent;
//? if <= 1.13 {
/^import net.minecraft.entity.player.EntityPlayerMP;
^///?} else if <= 1.16 {
/^import net.minecraft.entity.player.ServerPlayerEntity;
^///?} else {
import net.minecraft.server.level.ServerPlayer;
//?}
//? if <= 1.11 {
/^import net.minecraftforge.event.entity.living.LivingHurtEvent;
^///?} else {
import net.minecraftforge.event.entity.living.LivingDamageEvent;
//?}
//? if <= 1.7 {
/^import cpw.mods.fml.common.eventhandler.SubscribeEvent;
^///?} else if <= 1.12 {
/^import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
^///?} else if <= 1.21.5 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
/^import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
^///?}
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

	@SubscribeEvent
	//? if <= 1.9 {
	/^public void onPlayerDamage(LivingHurtEvent event) {
	^///?} else if <= 1.11 {
	/^public static void onPlayerDamage(LivingHurtEvent event) {
	^///?} else {
	public static void onPlayerDamage(LivingDamageEvent event) {
	//?}
		//? if <= 1.8 {
		/^if (event.entity instanceof EntityPlayerMP player && event.ammount > 0) {
		^///?} else if <= 1.11 {
		/^if (event.getEntity() instanceof EntityPlayerMP player && event.getAmount() > 0) {
		^///?} else if <= 1.13 {
		/^if (event.getEntity() instanceof EntityPlayerMP player && event.getAmount() > 0) {
		^///?} else if <= 1.16 {
		/^if (event.getEntity() instanceof ServerPlayerEntity player && event.getAmount() > 0) {
		^///?} else {
		if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
		//?}
			ExampleEventHandler.onPlayerHurt(player);
		}
	}
}
*///?}
