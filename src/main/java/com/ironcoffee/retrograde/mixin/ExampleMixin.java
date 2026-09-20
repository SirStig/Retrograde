package com.ironcoffee.retrograde.mixin;

import com.ironcoffee.retrograde.Main;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
@MixinEnvironment(type = MixinEnvironment.Env.MAIN)
public class ExampleMixin {
// Mixins were not available yet in forge <= 1.14.
//? if forge && <= 1.14 {
//?} else {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void serverInit(CallbackInfo ci) {
		Main.serverInit((MinecraftServer) (Object) this);
	}

	//? if forge && <= 1.15 {
	/*@Inject(method = "loadInitialChunks", at = @At("HEAD"))
	*///?} else {
	@Inject(method = "loadLevel", at = @At("RETURN"))
	//?}
	private void afterLoadLevel(CallbackInfo ci) {
		Main.levelLoad();
	}
//?}
}
