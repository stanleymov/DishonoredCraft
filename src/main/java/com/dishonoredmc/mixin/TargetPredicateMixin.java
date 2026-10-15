package com.dishonoredmc.mixin;

import com.dishonoredmc.power.Takedowns;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mobs can't notice a crouching player behind them: covers targeting (ActiveTargetGoal) and looking at
 * players (LookAtEntityGoal), which both go through TargetPredicate.
 */
@Mixin(TargetPredicate.class)
abstract class TargetPredicateMixin {
	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void dishonored$stealth(@Nullable LivingEntity base, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (!(base instanceof MobEntity mob) || !(target instanceof PlayerEntity player)) return;
		// Hostile mobs build up a detection gauge first (power.Awareness); they may only hunt once alerted.
		if (com.dishonoredmc.power.Awareness.ENABLED && mob instanceof net.minecraft.entity.mob.Monster) {
			if (!player.isCreative() && !player.isSpectator() && !com.dishonoredmc.power.Awareness.isAlerted(mob, player)) {
				cir.setReturnValue(false);
			}
		} else if (Takedowns.unseen(player, mob)) {
			cir.setReturnValue(false);
		}
	}
}
