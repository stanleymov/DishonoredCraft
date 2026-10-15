package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.CorvoMovement;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lean: slide the first-person camera sideways (stopping short of walls) and roll it a little. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow private Vec3d pos;
	@Shadow @Final private Quaternionf rotation;

	@Shadow protected abstract void moveBy(double forward, double up, double right);

	@Shadow protected abstract void setPos(Vec3d pos);

	@Inject(method = "update", at = @At("TAIL"))
	private void dishonored$lean(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (thirdPerson) return;
		float lean = CorvoMovement.lean(tickDelta);
		if (Math.abs(lean) < 0.01F) return;

		Vec3d start = this.pos;
		// 1.20.1's third camera axis points left (1.21 flipped it to the right).
		this.moveBy(0.0F, -0.1F * Math.abs(lean), -0.7F * lean);
		Vec3d wanted = this.pos;
		Vec3d dir = wanted.subtract(start);
		double length = dir.length();
		if (length > 1.0E-4) {
			Vec3d unit = dir.multiply(1.0 / length);
			BlockHitResult hit = area.raycast(new RaycastContext(start, wanted.add(unit.multiply(0.25)),
				RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focusedEntity));
			if (hit.getType() != HitResult.Type.MISS) {
				double allowed = Math.max(0.0, Math.min(length, hit.getPos().distanceTo(start) - 0.25));
				this.setPos(start.add(unit.multiply(allowed)));
			}
		}
		this.rotation.rotateZ(-lean * 0.17F);
	}
}
