package com.dishonoredmc.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Possession: the possessing player controls the mob, including pigs, horses, striders and camels. */
@Mixin({MobEntity.class, net.minecraft.entity.passive.PigEntity.class, net.minecraft.entity.passive.StriderEntity.class,
	net.minecraft.entity.passive.AbstractHorseEntity.class, net.minecraft.entity.passive.CamelEntity.class})
abstract class MobEntityMixin {
	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true, require = 0)
	private void dishonored$possessed(CallbackInfoReturnable<LivingEntity> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getFirstPassenger() instanceof PlayerEntity player && com.dishonoredmc.power.Possession.controls(self, player)) {
			cir.setReturnValue(player);
		}
	}
}
