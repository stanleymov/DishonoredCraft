package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.ClientPowerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dark Vision outline colours: hostiles burn orange, everything else glows pale gold. */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void dishonored$darkVisionColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (ClientPowerState.darkVision && self.getWorld().isClient && !self.isGlowing()) {
			cir.setReturnValue(self instanceof Monster ? 0xFF6A1A : 0xF2E0A0);
		}
	}
}
