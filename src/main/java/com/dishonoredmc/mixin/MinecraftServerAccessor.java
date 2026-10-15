package com.dishonoredmc.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("timeReference")
	void dishonored$setTimeReference(long time);

	@Accessor("nextTickTimestamp")
	void dishonored$setNextTickTimestamp(long time);
}
