package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.net.PowerStatePayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Bend Time stops every non-player entity in the caster's dimension. */
public final class BendTime {
	public static final int DURATION = 20 * 8;
	private static final int PROJECTILE_DRIFT_TICKS = 3;
	private static final Map<RegistryKey<World>, Integer> remaining = new HashMap<>();

	private BendTime() {}

	public static void start(ServerWorld world) {
		remaining.put(world.getRegistryKey(), DURATION);
		broadcast(world);
	}

	public static int ticksLeft(World world) {
		return remaining.getOrDefault(world.getRegistryKey(), 0);
	}

	public static void tick(MinecraftServer server) {
		if (remaining.isEmpty()) return;
		for (ServerWorld world : server.getWorlds()) {
			Integer left = remaining.get(world.getRegistryKey());
			if (left == null) continue;
			if (left <= 1) {
				remaining.remove(world.getRegistryKey());
				for (ServerPlayerEntity p : world.getPlayers()) {
					p.playSound(ModSounds.BEND_TIME_END, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
				}
				broadcast(world);
			} else {
				remaining.put(world.getRegistryKey(), left - 1);
			}
		}
	}

	/** Same as shouldFreeze without its side effects (for rendering). */
	public static boolean isFrozen(int ticksLeft, Entity entity) {
		if (ticksLeft <= 0 || entity instanceof PlayerEntity) return false;
		if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) {
			int drift = entity.getVelocity().lengthSquared() > 4.0 ? 1 : PROJECTILE_DRIFT_TICKS;
			if (entity.age < drift) return false;
		}
		return !(entity.hasPassengers() && entity.getFirstPassenger() instanceof PlayerEntity);
	}

	/** Called from the world tick mixins: true means skip this entity's tick. */
	public static boolean shouldFreeze(int ticksLeft, Entity entity) {
		if (ticksLeft <= 0) return false;
		if (entity instanceof PlayerEntity) return false;
		// Shots fired into bent time travel a short way, then hang in the air until time resumes.
		if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) {
			int drift = entity.getVelocity().lengthSquared() > 4.0 ? 1 : PROJECTILE_DRIFT_TICKS;
			if (entity.age < drift) return false;
		}
		if (entity.hasPassengers() && entity.getFirstPassenger() instanceof PlayerEntity) return false;
		if (entity instanceof LivingEntity living) {
			// Let hits keep landing on a frozen target instead of being eaten by the invulnerability window.
			if (entity.timeUntilRegen > 0) entity.timeUntilRegen--;
			if (living.hurtTime > 0) living.hurtTime--;
		}
		return true;
	}

	public static void broadcast(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) sendState(player);
	}

	public static void sendState(ServerPlayerEntity player) {
		com.dishonoredmc.net.Net.toPlayer(player, new PowerStatePayload(ticksLeft(player.getWorld()), DarkVision.isActive(player), Possession.hostId(player)));
	}

	public static void stop(ServerWorld world) {
		if (remaining.remove(world.getRegistryKey()) != null) {
			for (ServerPlayerEntity p : world.getPlayers()) {
				p.playSound(ModSounds.BEND_TIME_END, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
			broadcast(world);
		}
	}

	public static void clear() {
		remaining.clear();
	}
}
