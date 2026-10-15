package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.fp.WorldMesh;
import com.dishonoredmc.item.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held in third person (your own F5 view, and how other players see you), the blade, pistol, crossbow, grenade and spring razor are
 * Dishonored's own meshes (the ones the first-person view uses). Inventories, the ground and item frames keep
 * Dishonored's item icons.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
	@Inject(
		method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
		at = @At("HEAD"), cancellable = true)
	private void dishonored$heldWeapon(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
									   VertexConsumerProvider consumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
		if (mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND && mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND) return;
		String mesh = stack.isOf(ModItems.FOLDING_BLADE) ? "sword" : stack.isOf(ModItems.PISTOL) ? "pistol"
			: stack.isOf(ModItems.WRIST_BOW) ? "crossbow" : stack.isOf(ModItems.GRENADE) ? "grenade"
			: stack.isOf(ModItems.SPRING_RAZOR) ? "spring_razor" : null;
		if (mesh == null || !WorldMesh.weaponAvailable(mesh)) return;
		WorldMesh.renderHeldWeapon(mesh, leftHanded, matrices, consumers, light, overlay);
		ci.cancel();
	}
}
