package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.fp.WorldMesh;
import com.dishonoredmc.item.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Elixirs render as Dishonored's real bottle everywhere except inventory slots, which keep the game's icon. */
@Mixin(ItemRenderer.class)
abstract class ElixirItemRendererMixin {
	@Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
		at = @At("HEAD"), cancellable = true)
	private void dishonored$elixirBottle(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
										VertexConsumerProvider consumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
		String mesh = stack.isOf(ModItems.SOKOLOVS_ELIXIR) ? "elixir_health" : stack.isOf(ModItems.PIEROS_REMEDY) ? "elixir_mana" : null;
		if (mesh == null || mode == ModelTransformationMode.GUI || !WorldMesh.available(mesh)) return;
		matrices.push();
		switch (mode) {
			case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
				matrices.translate(leftHanded ? -0.05 : 0.05, -0.05, 0.0);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(leftHanded ? -12 : 12));
				matrices.scale(0.75f, 0.75f, 0.75f);
			}
			case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
				matrices.translate(0.0, -0.05, -0.02);
				matrices.scale(0.5f, 0.5f, 0.5f);
			}
			default -> {
				matrices.translate(0.0, -0.08, 0.0);
				matrices.scale(0.6f, 0.6f, 0.6f);
			}
		}
		// The bottle is exported neck-down; stand it upright.
		matrices.translate(0.0, 0.575, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
		WorldMesh.render(mesh, null, 0, false, 1.0f, matrices, consumers, light, overlay);
		matrices.pop();
		ci.cancel();
	}
}
