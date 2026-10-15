package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The caster rides inside the target, invisible, and steers it (see MobEntityMixin / LivingEntityMixin).
 * Sneak to leave early.
 */
public final class Possession {
	public static final int DURATION = 20 * 20;
	public static final String TAG = "dishonored_possessed";

	private record State(MobEntity host, boolean hadNoAi, int endAge, int duration) {}
	/** Welcoming Host bone charm: possession lasts this long instead. */
	public static final int CHARM_DURATION = 20 * 30;

	private static final Map<UUID, State> states = new HashMap<>();

	private Possession() {}

	public static boolean isPossessing(PlayerEntity player) {
		return states.containsKey(player.getUuid());
	}

	public static boolean canPossess(Entity entity) {
		return entity instanceof MobEntity mob && mob.isAlive() && !mob.hasPassengers() && !mob.hasVehicle()
			&& !(mob instanceof EnderDragonEntity) && !(mob instanceof WitherEntity);
	}

	/** Entity id of the mob the local player possesses, on the client (-1 = none). Synced with PowerStatePayload. */
	public static volatile int clientHostId = -1;

	/** True when `player` is possessing `host` (works on both sides; the client steers its own vehicle). */
	public static boolean controls(Entity host, PlayerEntity player) {
		if (host.getFirstPassenger() != player) return false;
		if (host.getWorld().isClient) return clientHostId == host.getId();
		State s = states.get(player.getUuid());
		return s != null && s.host() == host;
	}

	/** The mob `player` is possessing on the server, or -1. */
	public static int hostId(PlayerEntity player) {
		State s = states.get(player.getUuid());
		return s == null ? -1 : s.host().getId();
	}

	public static boolean start(ServerPlayerEntity player, MobEntity host) {
		if (!canPossess(host)) return false;
		boolean hadNoAi = host.isAiDisabled();
		if (!player.startRiding(host, true)) return false;
		host.setAiDisabled(true);
		host.setTarget(null);
		host.addCommandTag(TAG);
		// Welcoming Host bone charm: 30 seconds in any host instead of 20.
		int duration = com.dishonoredmc.BoneCharms.has(player, com.dishonoredmc.item.ModItems.CHARM_WELCOMING_HOST) ? CHARM_DURATION : DURATION;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, duration + 40, 0, false, false, false));
		states.put(player.getUuid(), new State(host, hadNoAi, player.age + duration, duration));
		BendTime.sendState(player);
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.SQUID_INK, host.getX(), host.getBodyY(0.5), host.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		world.playSound(null, host.getBlockPos(), ModSounds.POSSESSION_IN, SoundCategory.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** Fraction (0..1) of the current possession still left. */
	public static float remainingFraction(ServerPlayerEntity player) {
		State s = states.get(player.getUuid());
		if (s == null) return 0f;
		return Math.max(0f, Math.min(1f, (s.endAge() - player.age) / (float) s.duration()));
	}

	public static void end(ServerPlayerEntity player) {
		State state = states.remove(player.getUuid());
		if (state == null) return;
		MobEntity host = state.host();
		if (player.getVehicle() == host) player.stopRiding();
		BendTime.sendState(player);
		player.removeStatusEffect(StatusEffects.INVISIBILITY);
		if (host.isAlive()) {
			host.setAiDisabled(state.hadNoAi());
			host.removeScoreboardTag(TAG);
			host.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
			host.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.SQUID_INK, host.getX(), host.getBodyY(0.5), host.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
			world.playSound(null, host.getBlockPos(), ModSounds.POSSESSION_OUT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		}
	}

	public static void tick(ServerPlayerEntity player) {
		State state = states.get(player.getUuid());
		if (state == null) return;
		boolean expired = player.age >= state.endAge();
		if (expired || !state.host().isAlive() || player.getVehicle() != state.host() || player.isDead()) {
			end(player);
		}
	}

	/** Restores a mob that was saved to disk mid-possession. */
	public static void onEntityLoad(Entity entity) {
		if (entity instanceof MobEntity mob && mob.getCommandTags().contains(TAG) && !mob.hasPassengers()) {
			mob.removeScoreboardTag(TAG);
			mob.setAiDisabled(false);
		}
	}
}
