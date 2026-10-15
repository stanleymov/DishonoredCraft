package com.dishonoredmc.client.mixin;

import com.dishonoredmc.power.Possession;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Possession rides the mob under the hood; hide vanilla's "Press [sneak] to dismount" prompt for it. */
@Mixin(InGameHud.class)
abstract class InGameHudMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void dishonored$noDismountHint(Text message, boolean tinted, CallbackInfo ci) {
		if (Possession.clientHostId >= 0 && message.getContent() instanceof TranslatableTextContent t && t.getKey().equals("mount.onboard")) {
			ci.cancel();
		}
	}
}
