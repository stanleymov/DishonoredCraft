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
		// A creeper taken mid-hiss doesn't blow up with Corvo inside.
		if (host instanceof net.minecraft.entity.mob.CreeperEntity creeper) creeper.setFuseSpeed(-1);
		host.addCommandTag(TAG);
		// Welcoming Host bone charm: 30 seconds in any host instead of 20.
		int duration = com.dishonoredmc.BoneCharms.has(player, com.dishonoredmc.item.ModItems.CHARM_WELCOMING_HOST) ? CHARM_DURATION : DURATION;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, duration + 40, 0, false, false, false));
		states.put(player.getUuid(), new State(host, hadNoAi, player.age + duration, duration));
		// Corvo vanishes into the host: every mob after him forgets him (a creeper's fuse goes out too).
		for (MobEntity m : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(48),
				m -> m != host && (m.getTarget() == player || Awareness.isAware(m, player)))) {
			m.setTarget(null);
			m.getNavigation().stop();
			if (m instanceof net.minecraft.entity.mob.CreeperEntity creeper) creeper.setFuseSpeed(-1);
			if (m instanceof net.minecraft.entity.mob.Angerable angry) angry.stopAnger();
			Awareness.forget(m);
		}
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
		// The host's water breathing / fire immunity ends with the possession (a potion the player drank stays).
		for (var effect : new net.minecraft.entity.effect.StatusEffect[] {StatusEffects.WATER_BREATHING, StatusEffects.FIRE_RESISTANCE}) {
			StatusEffectInstance e = player.getStatusEffect(effect);
			if (e != null && e.isAmbient() && e.getDuration() <= 40) player.removeStatusEffect(effect);
		}
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
		// While Corvo is inside a host, no creeper near him keeps its fuse lit (the host included): their ignite goal
		// relights it from a target or a revenge memory between our checks otherwise.
		for (net.minecraft.entity.mob.CreeperEntity c : player.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.CreeperEntity.class,
				player.getBoundingBox().expand(16), c -> c.getFuseSpeed() > 0 || c == state.host())) {
			if (c.isIgnited()) continue; // lit with flint and steel: that one is the player's own doing
			c.setFuseSpeed(-1);
			if (c != state.host() && (c.getTarget() == player || c.getTarget() == state.host())) c.setTarget(null);
		}
		// Corvo takes on the host's nature: inside a fish or another water creature he can breathe under water (his
		// breath refills as on land), inside a strider or another fire-immune creature fire and lava don't hurt him.
		MobEntity host = state.host();
		if (player.age % 10 == 0) {
			if (host.canBreatheInWater()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, true, false, false));
			}
			if (host.isFireImmune()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
			}
		}
		if (host.isFireImmune()) player.setFireTicks(0);
		// Water breathing only stops the breath running out under water; inside a fish it also refills, as on land.
		if (host.canBreatheInWater() && player.getAir() < player.getMaxAir()) player.setAir(Math.min(player.getMaxAir(), player.getAir() + 4));
		// A squid's own swim vector would keep pushing it along: the player steers it (SquidEntityMixin).
		if (host instanceof net.minecraft.entity.passive.SquidEntity squid) squid.setSwimmingVector(0f, 0f, 0f);
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
