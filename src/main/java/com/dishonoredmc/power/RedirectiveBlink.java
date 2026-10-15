package com.dishonoredmc.power;

import com.dishonoredmc.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redirective Blink (bone charm, from Dishonored 2): with the charm equipped, time stops while the player
 * aims Blink without moving. Everything except players freezes, like Bend Time. The client decides when the
 * player is aiming and standing still (ActionPayload.REDIRECTIVE) and mirrors the freeze locally.
 */
public final class RedirectiveBlink {
	/** Server: worlds where some player is currently holding time still, by player. */
	private static final Map<UUID, RegistryKey<World>> freezing = new HashMap<>();
	/** Client: the local player is freezing time right now. */
	public static volatile boolean clientFrozen;

	private RedirectiveBlink() {}

	/** The charm is equipped in one of the bone charm slots. */
	public static boolean hasCharm(PlayerEntity player) {
		return com.dishonoredmc.BoneCharms.has(player, ModItems.BONE_CHARM);
	}

	public static void set(PlayerEntity player, boolean frozen) {
		if (frozen && hasCharm(player)) freezing.put(player.getUuid(), player.getWorld().getRegistryKey());
		else freezing.remove(player.getUuid());
	}

	public static boolean worldFrozen(World world) {
		return !freezing.isEmpty() && freezing.containsValue(world.getRegistryKey());
	}

	/** Players (and what they ride) keep moving; everything else stands still. */
	public static boolean freezes(Entity entity) {
		if (entity instanceof PlayerEntity) return false;
		return !(entity.hasPassengers() && entity.getFirstPassenger() instanceof PlayerEntity);
	}

	public static void forget(UUID id) {
		freezing.remove(id);
	}

	public static void clear() {
		freezing.clear();
	}
}
