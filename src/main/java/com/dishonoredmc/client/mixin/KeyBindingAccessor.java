package com.dishonoredmc.client.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For ControlSwap: a key's queued clicks and the button it is bound to. */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
	@Accessor("timesPressed")
	int dishonored$getTimesPressed();

	@Accessor("timesPressed")
	void dishonored$setTimesPressed(int times);

	@Accessor("boundKey")
	InputUtil.Key dishonored$getBoundKey();
}
