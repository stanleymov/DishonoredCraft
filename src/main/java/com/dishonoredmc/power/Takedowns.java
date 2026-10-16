package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.item.FoldingBladeItem;
import com.dishonoredmc.net.AnimEventPayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Dishonored's lethal and non-lethal takedowns: drop and stealth assassinations, and the choke hold. */
public final class Takedowns {
	private static final int CHOKE_TICKS = 50;
	private static final int CHOKE_SLEEP_TICKS = 20 * 90;

	private record Choke(MobEntity victim, int endAge, boolean hadNoAi, Vec3d hold, float yaw) {}

	private static final Map<UUID, Choke> chokes = new HashMap<>();

	/** A victim held in the takedown animation until the blade lands. */
	private record Kill(MobEntity victim, int killAge, boolean drop, Vec3d hold, float yaw) {}

	private static final Map<UUID, Kill> kills = new HashMap<>();
	private static final int BACK_STAB_TICKS = 8, DROP_STAB_TICKS = 5;

	private Takedowns() {}

	public static boolean isBoss(Entity e) {
		// (also: the Devouring Swarm's rats are Corvo's own, never takedown targets)
		if (e instanceof com.dishonoredmc.entity.PlagueRatEntity) return true;
		return e instanceof EnderDragonEntity || e instanceof WitherEntity || e instanceof net.minecraft.entity.mob.WardenEntity;
	}

	/** Cosine of the angle between where `target` faces and the direction to `player` (1 = dead ahead). */
	static double facing(PlayerEntity player, LivingEntity target) {
		// The body, not the head: mobs glance around with their heads all the time.
		Vec3d facing = Vec3d.fromPolar(0, target.getBodyYaw()).normalize();
		Vec3d toPlayer = player.getPos().subtract(target.getPos()).multiply(1, 0, 1);
		if (toPlayer.lengthSquared() < 1.0E-4) return -1;
		return facing.dotProduct(toPlayer.normalize());
	}

	/** True when `player` stands behind `target`, close enough to its back for a takedown. */
	public static boolean isBehind(PlayerEntity player, LivingEntity target) {
		return facing(player, target) < -0.2;
	}

	/**
	 * Dishonored-style perception. Crouched: seen only inside the mob's forward cone (~120 degrees) and within
	 * 10 blocks. Standing: seen anywhere in front, or heard within 4 blocks (8 when sprinting). A mob the player
	 * just hit always knows where they are.
	 */
	public static boolean seenBy(PlayerEntity player, MobEntity mob) {
		if (mob.getAttacker() == player && mob.age - mob.getLastAttackedTime() < 100) return true;
		double dist = mob.distanceTo(player);
		double cone = facing(player, mob);
		// Crouched above a mob's eye line (over ~15 degrees), the player is out of sight. Standing, height doesn't help.
		double dy = player.getEyeY() - mob.getEyeY();
		double flat = Math.hypot(player.getX() - mob.getX(), player.getZ() - mob.getZ());
		boolean above = dy > 0.5 && dy > flat * 0.27;
		if (player.isSneaking()) return !above && cone > 0.5 && dist < 10;
		if (dist < (player.isSprinting() ? 8 : 4)) return true;
		return cone > 0.0;
	}

	public static boolean unseen(PlayerEntity player, MobEntity mob) {
		return !seenBy(player, mob);
	}

	/** How long (ticks) each hunting mob has gone without perceiving its target player. */
	private static final Map<UUID, Integer> unseenFor = new HashMap<>();
	private static final int LOSE_TRACK_TICKS = 200;

	/** Every 10 ticks: mobs hunting `player` lose track after LOSE_TRACK_TICKS without seeing or hearing them. */
	public static void tickStealth(ServerPlayerEntity player) {
		if (player.age % 10 != 0) return;
		var box = player.getBoundingBox().expand(40);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, box, m -> m.getTarget() == player)) {
			if (seenBy(player, mob) && mob.canSee(player)) {
				unseenFor.remove(mob.getUuid());
				continue;
			}
			int t = unseenFor.merge(mob.getUuid(), 10, Integer::sum);
			if (t >= LOSE_TRACK_TICKS) {
				mob.setTarget(null);
				Awareness.lostTrack(mob);
				mob.getNavigation().stop();
				unseenFor.remove(mob.getUuid());
			}
		}
	}

	/** True while `mob` is lined up for, or held in, a backstab, drop assassination or choke. */
	public static boolean inTakedown(Entity mob) {
		for (Kill k : kills.values()) if (k.victim() == mob) return true;
		for (Choke c : chokes.values()) if (c.victim() == mob) return true;
		for (Drop d : drops.values()) if (d.victim() == mob) return true;
		return false;
	}

	private static boolean unaware(MobEntity mob, PlayerEntity player) {
		return mob.getTarget() != player && !Awareness.isAware(mob, player);
	}

	/** How far below a falling player a drop assassination can be lined up, in blocks. */
	public static final double DROP_RANGE = 14;

	/** The mob a falling player would land on, if any (see the client's drop prompt). */
	public static MobEntity mobBelow(PlayerEntity player) {
		net.minecraft.util.math.Box below = player.getBoundingBox().expand(1.6, 0, 1.6).stretch(0, -DROP_RANGE, 0);
		MobEntity best = null;
		for (Entity e : player.getWorld().getOtherEntities(player, below, e -> e instanceof MobEntity m && m.isAlive() && !isBoss(m))) {
			if (e.getBoundingBox().maxY > player.getY() + 0.5) continue;
			if (best == null || e.squaredDistanceTo(player) < best.squaredDistanceTo(player)) best = (MobEntity) e;
		}
		return best;
	}

	/** A drop assassination lined up mid-fall; it lands when Corvo reaches the victim. */
	private record Drop(MobEntity victim, int expireAge) {}

	private static final Map<UUID, Drop> drops = new HashMap<>();

	/**
	 * Attack while falling onto a mob: Dishonored locks onto the victim below, and the kill plays out once
	 * Corvo comes down on it (see tick).
	 */
	public static void tryDropAssassinate(ServerPlayerEntity player) {
		if (!FoldingBladeItem.bladeOut(player) || player.isOnGround() || Possession.isPossessing(player)) return;
		MobEntity target = mobBelow(player);
		if (target != null) drops.put(player.getUuid(), new Drop(target, player.age + 80));
	}

	private static void tickDrop(ServerPlayerEntity player) {
		Drop d = drops.get(player.getUuid());
		if (d == null) return;
		MobEntity mob = d.victim();
		if (!mob.isAlive() || player.isDead() || player.age > d.expireAge()) {
			drops.remove(player.getUuid());
			return;
		}
		double above = player.getY() - mob.getBoundingBox().maxY;
		double dx = player.getX() - mob.getX(), dz = player.getZ() - mob.getZ();
		boolean close = above < 1.6 && dx * dx + dz * dz < 2.5 * 2.5;
		if (close) {
			drops.remove(player.getUuid());
			// Land on the ground facing the victim (not on its head), like Dishonored's drop takedown.
			Vec3d fwd = Vec3d.fromPolar(0, player.getYaw()).normalize();
			Vec3d spot = mob.getPos().subtract(fwd.multiply(0.9));
			if (player.getWorld().isSpaceEmpty(player, player.getDimensions(player.getPose()).getBoxAt(spot))) {
				player.requestTeleport(spot.x, mob.getY(), spot.z);
			}
			player.setVelocity(Vec3d.ZERO);
			player.velocityModified = true;
			start(player, mob, AnimEventPayload.DROP_ASSASSINATION);
		} else if (player.isOnGround()) {
			drops.remove(player.getUuid()); // landed somewhere else
		}
	}

	/** Called when a player melee-attacks an entity. Returns true when it became a one-hit takedown. */
	public static boolean tryAssassinate(ServerPlayerEntity player, Entity entity) {
		// No takedowns from inside a possessed creature.
		if (!FoldingBladeItem.bladeOut(player) || Possession.isPossessing(player)) return false;
		if (!(entity instanceof MobEntity target) || !target.isAlive() || isBoss(target)) return false;

		int event = -1;
		if (!player.isOnGround() && player.fallDistance > 1.0F && player.getVelocity().y < 0) {
			event = AnimEventPayload.DROP_ASSASSINATION;
		} else if ((SleepDarts.isAsleep(target) || isBehind(player, target)) && unaware(target, player)) {
			// From behind (crouching or not), or from any side on an unconscious mob.
			event = AnimEventPayload.BACK_ASSASSINATION;
		}
		if (event < 0) return false;
		return start(player, target, event);
	}

	private static boolean start(ServerPlayerEntity player, MobEntity target, int event) {
		if (kills.containsKey(player.getUuid())) return true;
		player.fallDistance = 0;
		// Freeze the victim for the animation; the blow lands a few ticks in (see tick).
		target.setAiDisabled(true);
		target.setVelocity(0, 0, 0);
		target.setTarget(null);
		boolean drop = event == AnimEventPayload.DROP_ASSASSINATION;
		// Falling Star bone charm: a drop assassination restores 20% mana.
		if (drop && com.dishonoredmc.BoneCharms.has(player, com.dishonoredmc.item.ModItems.CHARM_FALLING_STAR)) Mana.refund(player, Mana.MAX * 0.2f);
		kills.put(player.getUuid(), new Kill(target, player.age + (drop ? DROP_STAB_TICKS : BACK_STAB_TICKS), drop, holdSpot(player, 0.9), player.getYaw()));
		player.getWorld().playSound(null, target.getBlockPos(), ModSounds.SWORD_WHOOSH, SoundCategory.PLAYERS, 1.2F, 0.8F);
		com.dishonoredmc.net.Net.toPlayer(player, new AnimEventPayload(event));
		return true;
	}

	/** Use on an unaware mob from behind: choke it unconscious. One that is already unconscious can't be choked again. */
	public static boolean tryChoke(ServerPlayerEntity player, Entity entity) {
		if (chokes.containsKey(player.getUuid())) return true;
		if (Possession.isPossessing(player)) return false;
		if (!(entity instanceof MobEntity mob) || !mob.isAlive() || isBoss(mob) || SleepDarts.isAsleep(mob)) return false;
		if (!isBehind(player, mob) || !unaware(mob, player) || player.distanceTo(mob) > 3.0F) return false;
		boolean hadNoAi = mob.isAiDisabled();
		mob.setAiDisabled(true);
		mob.setTarget(null);
		// Strong Arms bone charm: chokes take ~2.9 s instead of ~3.5 s in Dishonored (the same 17% faster here).
		int chokeTicks = com.dishonoredmc.BoneCharms.has(player, com.dishonoredmc.item.ModItems.CHARM_STRONG_ARMS) ? Math.round(CHOKE_TICKS * 2.9f / 3.5f) : CHOKE_TICKS;
		chokes.put(player.getUuid(), new Choke(mob, player.age + chokeTicks, hadNoAi, holdSpot(player, 0.85), player.getYaw()));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, chokeTicks, 6, false, false, false));
		player.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.8F, 0.6F);
		com.dishonoredmc.net.Net.toPlayer(player, new AnimEventPayload(AnimEventPayload.CHOKE_START));
		return true;
	}

	private static void land(ServerPlayerEntity player, MobEntity target) {
		ServerWorld world = player.getServerWorld();
		target.setAiDisabled(false);
		target.damage(world.getDamageSources().playerAttack(player), Math.max(1000F, target.getMaxHealth() * 10));
		Awareness.forget(target); // the blow itself counts as an attack: don't flash the alert on a dying victim
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.7), target.getZ(), 14, 0.2, 0.3, 0.2, 0.2);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.7F);
	}

	/**
	 * Where a victim is held during a takedown: ahead and off to Corvo's right, so the view looks past its
	 * shoulder (as in Dishonored) instead of into its back.
	 */
	private static Vec3d holdSpot(ServerPlayerEntity player, double ahead) {
		Vec3d fwd = Vec3d.fromPolar(0, player.getYaw()).normalize();
		Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
		return player.getPos().add(fwd.multiply(ahead)).add(right.multiply(0.45));
	}

	public static void tick(ServerPlayerEntity player) {
		tickDrop(player);
		Kill k = kills.get(player.getUuid());
		if (k != null) {
			MobEntity mob = k.victim();
			if (!mob.isAlive() || player.isDead()) {
				kills.remove(player.getUuid());
			} else {
				{
					// Hold the victim where the takedown started (it doesn't follow the player's view).
					mob.refreshPositionAndAngles(k.hold().x, mob.getY(), k.hold().z, k.yaw(), 0);
					mob.setHeadYaw(k.yaw());
					mob.setBodyYaw(k.yaw());
				}
				if (player.age >= k.killAge()) {
					kills.remove(player.getUuid());
					land(player, mob);
				}
			}
		}
		Choke c = chokes.get(player.getUuid());
		if (c == null) return;
		MobEntity mob = c.victim();
		if (!mob.isAlive() || player.isDead() || player.distanceTo(mob) > 3.5F || mob.hurtTime > 0) {
			chokes.remove(player.getUuid());
			if (mob.isAlive()) mob.setAiDisabled(c.hadNoAi());
			com.dishonoredmc.net.Net.toPlayer(player, new AnimEventPayload(AnimEventPayload.CHOKE_CANCEL));
			return;
		}
		// Hold the victim where the choke started (it doesn't follow the player's view).
		mob.refreshPositionAndAngles(c.hold().x, mob.getY(), c.hold().z, c.yaw(), 0);
		if (player.age % 10 == 0) {
			player.getServerWorld().spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
		}
		if (player.age >= c.endAge()) {
			chokes.remove(player.getUuid());
			mob.setAiDisabled(c.hadNoAi());
			SleepDarts.sleep(mob, CHOKE_SLEEP_TICKS);
			player.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_DROWN, SoundCategory.PLAYERS, 0.6F, 0.5F);
			com.dishonoredmc.net.Net.toPlayer(player, new AnimEventPayload(AnimEventPayload.CHOKE_WIN));
		}
	}

	public static void forget(UUID id) {
		drops.remove(id);
		Kill k = kills.remove(id);
		if (k != null && k.victim().isAlive()) k.victim().setAiDisabled(false);
		Choke c = chokes.remove(id);
		if (c != null && c.victim().isAlive()) c.victim().setAiDisabled(c.hadNoAi());
	}

	public static void clear() {
		drops.clear();
		kills.clear();
		for (Iterator<Choke> it = chokes.values().iterator(); it.hasNext(); ) {
			Choke c = it.next();
			if (c.victim().isAlive()) c.victim().setAiDisabled(c.hadNoAi());
			it.remove();
		}
	}
}
