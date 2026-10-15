package com.dishonoredmc.client.mixin;

import com.dishonoredmc.client.hud.CharmPanel;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The bone charm panel sits outside the inventory's frame: clicks on it must not drop the held item. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
	@Inject(method = "isClickOutsideBounds", at = @At("HEAD"), cancellable = true)
	private void dishonored$charmPanel(double mouseX, double mouseY, int left, int top, int button, CallbackInfoReturnable<Boolean> cir) {
		if (CharmPanel.inPanel(left, top, mouseX, mouseY)) cir.setReturnValue(false);
	}
}
