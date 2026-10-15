package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.power.BendTime;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Entities frozen by Bend Time stop ticking, but the renderer would keep blending between their last two ticks
 * every frame, so they rocked back and forth. Draw them exactly at their last pose instead.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float dishonored$frozenPose(float tickDelta, Entity entity, double x, double y, double z, float td, MatrixStack matrices, VertexConsumerProvider consumers) {
		boolean frozen = BendTime.isFrozen(ClientPowerState.bendTimeTicks, entity)
			|| (com.dishonoredmc.power.RedirectiveBlink.clientFrozen && com.dishonoredmc.power.RedirectiveBlink.freezes(entity));
		return frozen ? 1.0F : tickDelta;
	}
}
