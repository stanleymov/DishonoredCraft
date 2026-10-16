package com.dishonoredmc.mixin;

import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Squids (and glow squids) swim by their own swim vector and ignore a rider's input: possessed, they swim where the
 * player looks (forward / back / sideways), and sink slowly out of water.
 */
@Mixin(SquidEntity.class)
abstract class SquidEntityMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void dishonored$possessedSwim(Vec3d movementInput, CallbackInfo ci) {
		SquidEntity self = (SquidEntity) (Object) this;
		if (!(self.getFirstPassenger() instanceof PlayerEntity player) || !com.dishonoredmc.power.Possession.controls(self, player)) return;
		Vec3d look = player.getRotationVector();
		Vec3d side = new Vec3d(look.z, 0, -look.x).normalize();
		float forward = player.forwardSpeed, sideways = player.sidewaysSpeed;
		Vec3d push = look.multiply(forward * 0.06).add(side.multiply(sideways * 0.04));
		Vec3d v = self.getVelocity().multiply(0.8).add(push);
		if (!self.isTouchingWater()) v = v.add(0, -0.08, 0);
		self.setVelocity(v);
		self.setYaw(player.getYaw());
		self.bodyYaw = self.getYaw();
		self.move(MovementType.SELF, self.getVelocity());
		ci.cancel();
	}
}
