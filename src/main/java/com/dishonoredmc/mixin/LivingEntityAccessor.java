package com.dishonoredmc.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean dishonored$isJumping();

	@org.spongepowered.asm.mixin.gen.Invoker("jump")
	void dishonored$jump();
}
