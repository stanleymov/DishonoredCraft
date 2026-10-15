package com.dishonoredmc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dishonored slows time while the weapon wheel is open. In single-player the whole game (server and client
 * clocks, see TimeScale) runs at 1/{@link #FACTOR} speed. On a shared server, where slowing everyone would be
 * unfair, every entity except players ticks only on one tick in {@link #FACTOR} instead.
 */
public final class WheelSlowdown {
	private static final int FACTOR = 5;
	private static final Set<UUID> open = new HashSet<>();

	private WheelSlowdown() {}

	/** Single-player (one player on an integrated server): the whole game runs at quarter speed instead. */
	private static boolean wholeGame;

	public static void set(ServerPlayerEntity player, boolean opened) {
		if (opened) open.add(player.getUuid());
		else open.remove(player.getUuid());
		MinecraftServer server = player.getServer();
		boolean solo = server != null && !server.isDedicated() && server.getCurrentPlayerCount() <= 1;
		wholeGame = solo && !open.isEmpty();
		TimeScale.wheel = wholeGame ? 1F / FACTOR : 1F;
	}

	/** Called from the world tick hook: true means skip this entity's tick. */
	public static boolean shouldSkip(Entity entity) {
		if (open.isEmpty() || wholeGame || entity instanceof PlayerEntity) return false;
		if (entity.hasPassengers() && entity.getFirstPassenger() instanceof PlayerEntity) return false;
		return entity.getWorld().getTime() % FACTOR != 0;
	}

	/** A player left with the wheel open. */
	public static void forget(ServerPlayerEntity player) {
		set(player, false);
	}

	public static void reset(MinecraftServer server) {
		open.clear();
		wholeGame = false;
		TimeScale.reset();
	}
}
