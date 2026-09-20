package com.ironcoffee.retrograde.event;

import com.ironcoffee.retrograde.Main;
//? if forge && <= 1.13 {
/*import net.minecraft.entity.player.EntityPlayerMP;
*///?} else if forge && <= 1.16 {
/*import net.minecraft.entity.player.ServerPlayerEntity;
*///?} else {
import net.minecraft.server.level.ServerPlayer;
 //?}

public class ExampleEventHandler {

	//? if forge && <= 1.13 {
	/*public static void onPlayerHurt(EntityPlayerMP player) {
	*///?} else if forge && <= 1.16 {
	/*public static void onPlayerHurt(ServerPlayerEntity player) {
	*///?} else {
	public static void onPlayerHurt(ServerPlayer player) {
	 //?}
		Main.LOGGER.info("{} took damage.", player.getDisplayName());
	}
}
