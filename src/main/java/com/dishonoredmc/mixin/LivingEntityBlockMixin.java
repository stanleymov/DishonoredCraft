package com.dishonoredmc.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocking with the blade while a gun or gadget is in hand counts as a raised shield (see BladeBlock). */
@Mixin(LivingEntity.class)
abstract class LivingEntityBlockMixin {
	@Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
	private void dishonored$bladeBlock(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof PlayerEntity p && com.dishonoredmc.BladeBlock.isBlocking(p)) cir.setReturnValue(true);
	}
}
