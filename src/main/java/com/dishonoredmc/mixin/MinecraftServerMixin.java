package com.dishonoredmc.mixin;

import com.dishonoredmc.TimeScale;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The server's 50 ms tick length follows TimeScale (weapon wheel slow motion). */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L))
	private long dishonored$tickLength(long original) {
		return TimeScale.serverTickMillis();
	}
}
