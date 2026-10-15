package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.power.BendTime;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirror the server's Bend Time freeze so arrows and mobs hang still instead of being predicted forward. */
@Mixin(ClientWorld.class)
abstract class ClientWorldMixin {
	@Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
	private void dishonored$bendTime(Entity entity, CallbackInfo ci) {
		if (BendTime.shouldFreeze(ClientPowerState.bendTimeTicks, entity)
			|| (com.dishonoredmc.power.RedirectiveBlink.clientFrozen && com.dishonoredmc.power.RedirectiveBlink.freezes(entity))) ci.cancel();
	}
}
