package com.dishonoredmc.mixin;

import com.dishonoredmc.BoneCharms;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.item.ItemStack;

/** The six bone charm slots, added after all of the player inventory's own slots (indices 46-51). */
@Mixin(PlayerScreenHandler.class)
abstract class PlayerScreenHandlerMixin extends AbstractRecipeScreenHandler<RecipeInputInventory> {
	PlayerScreenHandlerMixin() {
		super(null, 0);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void dishonored$charmSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
		var charms = BoneCharms.of(owner);
		for (int i = 0; i < BoneCharms.SLOTS; i++) this.addSlot(new BoneCharms.CharmSlot(charms, i, !onServer));
	}

	/** Shift-click on a bone charm in the inventory equips it in a free charm slot (if it isn't worn already). */
	@Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
	private void dishonored$equipCharm(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
		int first = this.slots.size() - BoneCharms.SLOTS;
		if (index < 9 || index >= first) return; // crafting / armor slots and the charm slots: vanilla rules
		var slot = this.slots.get(index);
		ItemStack stack = slot.getStack();
		if (!(stack.getItem() instanceof com.dishonoredmc.item.BoneCharmItem)) return;
		ItemStack moved = stack.copy();
		if (!this.insertItem(stack, first, first + BoneCharms.SLOTS, false)) return; // no free slot: vanilla shuffle
		if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
		else slot.markDirty();
		cir.setReturnValue(moved);
	}
}
