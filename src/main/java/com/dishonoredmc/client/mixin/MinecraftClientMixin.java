package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.ClientPowerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dark Vision: living things glow through walls. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@Shadow @Nullable public ClientPlayerEntity player;

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void dishonored$darkVision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ClientPowerState.darkVision && player != null && entity != player && entity instanceof LivingEntity
			&& entity.squaredDistanceTo(player) < 56 * 56) {
			cir.setReturnValue(true);
		}
	}
}
