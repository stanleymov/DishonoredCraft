package com.dishonoredmc.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererAccessor {
	@Invoker("renderArmHoldingItem")
	void dishonored$renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm);

	@Invoker("applyEquipOffset")
	void dishonored$applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);

	@Invoker("applySwingOffset")
	void dishonored$applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);
}
