package com.dishonoredmc.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** View bobbing at a fifth of vanilla strength (the stride amount scales every part of vanilla's bob). */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	private static final float BOB_SCALE = 0.2F;

	@Redirect(method = "bobView", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
	private float dishonored$softerBob(float delta, float start, float end) {
		return MathHelper.lerp(delta, start, end) * BOB_SCALE;
	}
}
