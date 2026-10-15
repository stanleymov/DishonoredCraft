package com.dishonoredmc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * No hunger and no natural regeneration: ordinary food heals instead, slowly. Each food restores as much health
 * as it would have restored hunger (steak 8 = 4 hearts), one point every HEAL_INTERVAL ticks.
 */
public final class FoodHealing {
	private static final int HEAL_INTERVAL = 30; // 1.5 s per half heart
	private static final Map<UUID, Integer> pending = new HashMap<>();

	private FoodHealing() {}

	public static void ate(PlayerEntity player, FoodComponent food) {
		if (food == null || food.getHunger() <= 0) return;
		pending.merge(player.getUuid(), food.getHunger(), (a, b) -> Math.min(40, a + b));
	}

	public static void tick(ServerPlayerEntity player) {
		Integer left = pending.get(player.getUuid());
		if (left == null) return;
		if (player.isDead() || left <= 0) {
			pending.remove(player.getUuid());
			return;
		}
		if (player.age % HEAL_INTERVAL != 0) return;
		if (player.getHealth() < player.getMaxHealth()) player.heal(1.0F);
		pending.put(player.getUuid(), left - 1);
	}

	public static void forget(UUID id) {
		pending.remove(id);
	}
}
