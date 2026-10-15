package com.dishonoredmc.client.mixin;

import com.dishonoredmc.TimeScale;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The client's tick timer follows TimeScale, so the player and everything on screen slow with the server. */
@Mixin(RenderTickCounter.class)
abstract class RenderTickCounterMixin {
	@Shadow @Final @Mutable private float tickTime;

	@Inject(method = "beginRenderTick", at = @At("HEAD"))
	private void dishonored$timeScale(long timeMillis, CallbackInfoReturnable<Integer> cir) {
		this.tickTime = TimeScale.clientTickMillis();
	}
}
