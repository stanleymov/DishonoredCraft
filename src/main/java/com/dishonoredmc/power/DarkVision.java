package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Toggled sight through walls. The client draws outlines; the server drains mana and grants night vision. */
public final class DarkVision {
	private static final float DRAIN_PER_SECOND = 1.5f;
	private static final Set<UUID> active = new HashSet<>();

	private DarkVision() {}

	public static boolean isActive(ServerPlayerEntity player) {
		return active.contains(player.getUuid());
	}

	public static void toggle(ServerPlayerEntity player, boolean on) {
		if (on) {
			active.add(player.getUuid());
		} else {
			if (active.remove(player.getUuid())) {
				player.getWorld().playSound(null, player.getBlockPos(), ModSounds.DARK_VISION_OFF, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.0F);
			}
			player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		BendTime.sendState(player);
	}

	public static void tick(ServerPlayerEntity player) {
		if (!isActive(player)) return;
		if (player.age % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
			if (!Mana.drain(player, DRAIN_PER_SECOND)) toggle(player, false);
		}
	}

	public static void forget(UUID id) {
		active.remove(id);
	}
}
