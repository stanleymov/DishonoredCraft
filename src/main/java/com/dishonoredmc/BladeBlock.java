package com.dishonoredmc;

import com.dishonoredmc.item.FoldingBladeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blocking with the blade while a gun or gadget is in hand (the power button, see the client's ControlSwap). The
 * blade isn't the item in use then, so LivingEntityMixin reports the player as blocking and vanilla's shield
 * rules apply, exactly as when the blade itself is raised (after the same 5-tick raise).
 */
public final class BladeBlock {
	/** Player -> age when the block started. */
	private static final Map<UUID, Integer> blocking = new HashMap<>();

	private BladeBlock() {}

	public static void set(ServerPlayerEntity player, boolean on) {
		if (on) blocking.putIfAbsent(player.getUuid(), player.age);
		else blocking.remove(player.getUuid());
	}

	/** Holding the block button with the blade out and a gun or gadget in hand. */
	public static boolean isBlocking(PlayerEntity player) {
		Integer since = blocking.get(player.getUuid());
		return since != null && player.age - since >= 5 && FoldingBladeItem.isLeftHandTool(player.getMainHandStack())
			&& FoldingBladeItem.bladeOut(player);
	}

	public static void forget(UUID id) {
		blocking.remove(id);
	}

	public static void clear() {
		blocking.clear();
	}
}
