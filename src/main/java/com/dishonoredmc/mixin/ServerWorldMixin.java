package com.dishonoredmc.mixin;

import com.dishonoredmc.power.BendTime;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
abstract class ServerWorldMixin {
	@Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
	private void dishonored$bendTime(Entity entity, CallbackInfo ci) {
		if (BendTime.shouldFreeze(BendTime.ticksLeft((World) (Object) this), entity) || com.dishonoredmc.WheelSlowdown.shouldSkip(entity)
			|| (com.dishonoredmc.power.RedirectiveBlink.worldFrozen((World) (Object) this) && com.dishonoredmc.power.RedirectiveBlink.freezes(entity))) ci.cancel();
	}
}
