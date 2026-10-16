package com.dishonoredmc.mixin;

import com.dishonoredmc.BoneCharms;
import com.dishonoredmc.EdgeGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sneak edge protection can be turned off (see EdgeGuard); every player carries six bone charm slots (BoneCharms). */
@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin implements BoneCharms.Holder {
	@Unique
	private SimpleInventory dishonored$charmSlots;

	@Override
	public SimpleInventory dishonored$charms() {
		// Created on first use: the inventory screen asks for it from inside PlayerEntity's constructor.
		if (dishonored$charmSlots == null) dishonored$charmSlots = new SimpleInventory(BoneCharms.SLOTS);
		return dishonored$charmSlots;
	}

	/** Crouching (toggle crouch too) doesn't throw the player out of a possessed creature: casting again does. */
	@Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true, require = 0)
	private void dishonored$stayPossessing(CallbackInfoReturnable<Boolean> cir) {
		if (com.dishonoredmc.power.Possession.isPossessing((PlayerEntity) (Object) this)) cir.setReturnValue(false);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void dishonored$saveCharms(NbtCompound nbt, CallbackInfo ci) {
		nbt.put("DishonoredBoneCharms", BoneCharms.write(dishonored$charms()));
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void dishonored$loadCharms(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains("DishonoredBoneCharms", NbtElement.LIST_TYPE)) {
			BoneCharms.read(dishonored$charms(), nbt.getList("DishonoredBoneCharms", NbtElement.COMPOUND_TYPE));
		}
	}

	/** There is no hunger, so food can always be eaten (it heals slowly instead, see FoodHealing). */
	@Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
	private void dishonored$alwaysEat(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	/** Crouching with the blade, a gun or a gadget in hand still opens doors and uses blocks instead of firing. */
	@Inject(method = "shouldCancelInteraction", at = @At("HEAD"), cancellable = true)
	private void dishonored$useBlocksCrouched(CallbackInfoReturnable<Boolean> cir) {
		var main = ((PlayerEntity) (Object) this).getMainHandStack();
		if (main.getItem() instanceof com.dishonoredmc.item.FoldingBladeItem || com.dishonoredmc.item.FoldingBladeItem.isLeftHandTool(main)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
	private void dishonored$edgeGuard(CallbackInfoReturnable<Boolean> cir) {
		if (!EdgeGuard.enabled) cir.setReturnValue(false);
	}
}
