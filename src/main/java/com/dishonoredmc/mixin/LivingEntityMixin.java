package com.dishonoredmc.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Possession steering: the possessed mob walks where the player looks. Pigs, striders, horses and camels have
 * their own riding code, so the hooks are applied to them as well.
 */
@Mixin({LivingEntity.class, net.minecraft.entity.passive.PigEntity.class, net.minecraft.entity.passive.StriderEntity.class,
	net.minecraft.entity.passive.AbstractHorseEntity.class, net.minecraft.entity.passive.CamelEntity.class})
abstract class LivingEntityMixin {
	private boolean dishonored$possessedBy(PlayerEntity player) {
		Object self = this;
		return self instanceof MobEntity mob && com.dishonoredmc.power.Possession.controls(mob, player);
	}

	@Inject(method = "getControlledMovementInput", at = @At("HEAD"), cancellable = true, require = 0)
	private void dishonored$input(PlayerEntity player, Vec3d input, CallbackInfoReturnable<Vec3d> cir) {
		if (!dishonored$possessedBy(player)) return;
		float forward = player.forwardSpeed;
		if (forward < 0) forward *= 0.5F;
		cir.setReturnValue(new Vec3d(player.sidewaysSpeed * 0.6F, 0.0, forward));
	}

	@Inject(method = "tickControlled", at = @At("HEAD"), require = 0)
	private void dishonored$steer(PlayerEntity player, Vec3d input, CallbackInfo ci) {
		if (!dishonored$possessedBy(player)) return;
		LivingEntity self = (LivingEntity) (Object) this;
		self.setYaw(player.getYaw());
		self.prevYaw = self.getYaw();
		self.setPitch(player.getPitch() * 0.5F);
		self.bodyYaw = self.getYaw();
		self.headYaw = self.getYaw();
		if (((LivingEntityAccessor) player).dishonored$isJumping() && self.isOnGround()) {
			((LivingEntityAccessor) self).dishonored$jump();
		}
	}

	@Inject(method = "getSaddledSpeed", at = @At("HEAD"), cancellable = true, require = 0)
	private void dishonored$speed(PlayerEntity player, CallbackInfoReturnable<Float> cir) {
		if (!dishonored$possessedBy(player)) return;
		LivingEntity self = (LivingEntity) (Object) this;
		// Mobs' speed attribute is used squared by their own AI (forward input x speed); a rider's input is ~1, so
		// half the attribute gives roughly the mob's own brisk walking pace (pigs were ~4x too fast).
		cir.setReturnValue((float) self.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 0.5F);
	}
}
