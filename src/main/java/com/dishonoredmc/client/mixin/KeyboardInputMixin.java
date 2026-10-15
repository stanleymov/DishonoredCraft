package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.CorvoMovement;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While leaning, A/D tilt the camera and Corvo doesn't walk. Swift Shadow speeds up crouch-walking. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends Input {
	/** Swift Shadow bone charm: a smaller crouch slow-down (like Swift Sneak), so the view doesn't widen. */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "tick", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float dishonored$swiftShadow(float slowDownFactor) {
		var p = net.minecraft.client.MinecraftClient.getInstance().player;
		if (p != null && com.dishonoredmc.BoneCharms.has(p, com.dishonoredmc.item.ModItems.CHARM_SWIFT_SHADOW)) {
			return Math.min(1.0F, slowDownFactor * com.dishonoredmc.BoneCharms.SWIFT_SHADOW);
		}
		return slowDownFactor;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void dishonored$leanNoStrafe(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		// Leaning keeps Corvo in place: A/D tilt the camera, and W/S don't walk.
		if (CorvoMovement.isLeaning()) {
			this.movementSideways = 0.0F;
			this.movementForward = 0.0F;
			this.pressingLeft = false;
			this.pressingRight = false;
			this.pressingForward = false;
			this.pressingBack = false;
		}
	}
}
