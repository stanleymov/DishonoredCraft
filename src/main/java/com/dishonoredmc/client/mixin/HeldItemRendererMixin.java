package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.client.fp.FpAssets;
import com.dishonoredmc.client.fp.FpController;
import com.dishonoredmc.client.fp.FpRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import com.dishonoredmc.client.HandTuning;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dishonored-style first person: Corvo's right hand grips the weapon, and his marked left hand is always
 * up, raised while aiming Blink and thrust forward when a power is cast.
 */
@Mixin(HeldItemRenderer.class)
abstract class HeldItemRendererMixin {
	@Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
	private void dishonored$realRig(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (FpAssets.available() && !player.isInvisible() && !player.isUsingSpyglass()) {
			FpRenderer.render(tickDelta, matrices, vertexConsumers, light);
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void dishonored$corvoHands(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
									   ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
									   int light, CallbackInfo ci) {
		// The bone charm is carried, not held up.
		if (item.isOf(com.dishonoredmc.item.ModItems.BONE_CHARM)) {
			ci.cancel();
			return;
		}
		// Possessing a mob: Corvo has no hands or weapons to show.
		if (com.dishonoredmc.power.Possession.clientHostId >= 0) {
			ci.cancel();
			return;
		}
		if (player.isUsingSpyglass() || player.isInvisible()) return;
		boolean main = hand == Hand.MAIN_HAND;
		Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		HeldItemRendererAccessor self = (HeldItemRendererAccessor) this;
		HandTuning.maybeReload();

		// With the private Dishonored asset pack, Corvo's real rig (drawn in renderItem below) replaces
		// vanilla's hands and our own items; other items still render the vanilla way.
		if (FpAssets.available() && (item.isEmpty() || FpController.isFpItem(item))) {
			ci.cancel();
			return;
		}

		if (!main && item.isEmpty()) {
			// The marked hand.
			float cast = ClientPowerState.castProgress(tickDelta);
			float aim = ClientPowerState.aimAmount(tickDelta);
			matrices.push();
			matrices.translate(0.0F, aim * 0.16F, -aim * 0.12F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(aim * 12.0F));
			self.dishonored$renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, cast, arm);
			matrices.pop();
			ci.cancel();
			return;
		}

	}
}
