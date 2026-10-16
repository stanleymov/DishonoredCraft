package com.dishonoredmc.power;

import com.dishonoredmc.net.AwarenessPayload;
import com.dishonoredmc.net.Net;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dishonored's detection gauge. Every hostile mob has an awareness of the player that fills while it can see or
 * hear them and drains when it can't:
 * <ul>
 *   <li>0.3+ suspicious: the mob turns its head toward the player;</li>
 *   <li>0.6+ investigating: it walks toward where it noticed them;</li>
 *   <li>1.0 alerted: it hunts the player (only then may it target them, see TargetPredicateMixin).</li>
 * </ul>
 * The gauge is shown above the mob's head (client: AwarenessRenderer).
 */
public final class Awareness {
	/** Detection gauge switched off: mobs use the instant sight / hearing rules in Takedowns.seenBy instead. */
	public static final boolean ENABLED = true;
	public static final float SUSPICIOUS = 0.3f, INVESTIGATE = 0.6f;
	/** How far away hostile mobs are tracked. */
	private static final double RANGE = 32;
	/** Seconds without perceiving the player before the gauge starts to drain, and how fast it drains (per second). */
	private static final int DRAIN_DELAY = 20;
	private static final float DRAIN_PER_SECOND = 0.25f;

	private static final class State {
		UUID player;
		float level;
		boolean alerted;
		long lastPerceived;
	}

	private static final Map<UUID, State> states = new HashMap<>();

	private Awareness() {}

	/** True when `mob` has fully detected `player` (and may hunt them). */
	public static boolean isAlerted(MobEntity mob, PlayerEntity player) {
		State s = states.get(mob.getUuid());
		return s != null && s.alerted && player.getUuid().equals(s.player);
	}

	/**
	 * True when `mob` knows `player` is around: fully alerted, or its gauge is at least suspicious (it has turned to
	 * look, or is still searching after losing track). Only a mob that isn't aware can be backstabbed or choked.
	 */
	public static boolean isAware(MobEntity mob, PlayerEntity player) {
		State s = states.get(mob.getUuid());
		return s != null && player.getUuid().equals(s.player) && (s.alerted || s.level >= SUSPICIOUS);
	}

	/** A player hurting a mob is detected at once. */
	public static void alert(MobEntity mob, ServerPlayerEntity player) {
		if (!ENABLED || !(mob instanceof Monster) || hidden(mob) || !mob.canTarget(player)) return;
		State s = states.computeIfAbsent(mob.getUuid(), k -> new State());
		s.player = player.getUuid();
		s.level = 1f;
		s.alerted = true;
		s.lastPerceived = player.getServerWorld().getTime();
		if (mob.getTarget() == null) mob.setTarget(player);
	}

	/** A mob that lost track of the player (see Takedowns.tickStealth) goes back to searching. */
	public static void lostTrack(MobEntity mob) {
		State s = states.get(mob.getUuid());
		if (s == null) return;
		s.alerted = false;
		s.level = Math.min(s.level, INVESTIGATE + 0.05f);
	}

	/**
	 * Vanilla's own rules for who goes after a player: neutral mobs (zombified piglins, wolves...) only once they are
	 * angry at them, piglins not while the player wears gold armour, spiders not in bright light. Endermen have
	 * their own stare rule (see tick).
	 */
	private static boolean wouldHunt(MobEntity mob, PlayerEntity player) {
		if (mob instanceof net.minecraft.entity.mob.EndermanEntity) return true;
		if (mob instanceof net.minecraft.entity.mob.Angerable angry && !player.getUuid().equals(angry.getAngryAt())) return false;
		if (mob instanceof net.minecraft.entity.mob.PiglinEntity && net.minecraft.entity.mob.PiglinBrain.wearsGoldArmor(player)) return false;
		if (mob instanceof net.minecraft.entity.mob.SpiderEntity && mob.getTarget() != player && mob.getBrightnessAtEyes() >= 0.5F) return false;
		return true;
	}

	/** No gauge for takedown victims, sleeping mobs or the dying. */
	private static boolean hidden(MobEntity mob) {
		return !mob.isAlive() || Takedowns.inTakedown(mob) || SleepDarts.isAsleep(mob);
	}

	public static void forget(MobEntity mob) {
		states.remove(mob.getUuid());
	}

	/** Vanilla's enderman check: the player looks at its head (no carved pumpkin worn) and can see it. */
	private static boolean staring(ServerPlayerEntity player, MobEntity enderman) {
		if (player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(net.minecraft.item.Items.CARVED_PUMPKIN)) return false;
		var look = player.getRotationVec(1.0F).normalize();
		var to = new net.minecraft.util.math.Vec3d(enderman.getX() - player.getX(), enderman.getEyeY() - player.getEyeY(), enderman.getZ() - player.getZ());
		double d = to.length();
		return look.dotProduct(to.normalize()) > 1.0 - 0.025 / d && player.canSee(enderman);
	}

	/** How quickly (fraction of the gauge per tick) `mob` is noticing `player` right now; 0 = not at all. */
	private static float gain(ServerPlayerEntity player, MobEntity mob) {
		if (player.isCreative() || player.isSpectator() || player.isInvisible() || Possession.isPossessing(player)) return 0f;
		double dist = mob.distanceTo(player);
		boolean crouched = player.isSneaking();
		float gain = 0f;
		// Sight.
		if (mob.canSee(player)) {
			double cone = Takedowns.facing(player, mob);
			double dy = player.getEyeY() - mob.getEyeY();
			double flat = Math.hypot(player.getX() - mob.getX(), player.getZ() - mob.getZ());
			boolean above = dy > 0.5 && dy > flat * 0.27;
			double minCone = crouched ? 0.5 : 0.0; // crouched: ~120 degree view, standing: ~180 degrees
			double range = crouched ? 10 : 24;
			if (!(crouched && above) && cone > minCone && dist < range) {
				// Seconds to fill the gauge: close and in plain sight is fast, far, peripheral or crouched is slow.
				double seconds = 0.4 + dist * 0.15;
				if (crouched) seconds *= 2.5;
				if (player.isSprinting()) seconds *= 0.6;
				if (cone < 0.7) seconds *= 2.0;
				gain = (float) (1.0 / (seconds * 20));
			}
		}
		// Hearing: footsteps close by, even from behind. Crouching is silent.
		if (!crouched) {
			double hear = player.isSprinting() ? 8 : 4;
			if (dist < hear) gain = Math.max(gain, (float) (1.0 / ((player.isSprinting() ? 0.8 : 1.5) * 20)));
		}
		return gain;
	}

	/** Every server tick for each player: update the gauges of the hostile mobs around them. */
	public static void tick(ServerPlayerEntity player) {
		if (!ENABLED) return;
		long now = player.getServerWorld().getTime();
		var box = player.getBoundingBox().expand(RANGE);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, box, m -> m instanceof Monster && m.isAlive())) {
			if (Takedowns.isBoss(mob) && mob.getTarget() == player) continue;
			// Mobs that never go for players (the Devouring Swarm's rats) have no gauge.
			if (hidden(mob) || !mob.canTarget(player) || !wouldHunt(mob, player)) {
				states.remove(mob.getUuid());
				continue;
			}
			// Bend Time / Redirective Blink: a frozen mob notices nothing, and its gauge holds where it was.
			if (BendTime.isFrozen(BendTime.ticksLeft(mob.getWorld()), mob)
				|| (RedirectiveBlink.worldFrozen(mob.getWorld()) && RedirectiveBlink.freezes(mob))) {
				continue;
			}
			// Endermen, as in vanilla: they don't watch or listen for the player; only meeting their gaze (or
			// hurting them, see alert) gives the player away, at once.
			if (mob instanceof net.minecraft.entity.mob.EndermanEntity) {
				if (!player.isCreative() && !player.isSpectator() && !isAlerted(mob, player) && staring(player, mob)) alert(mob, player);
				continue;
			}
			State s = states.get(mob.getUuid());
			// One player per mob: the gauge follows whoever it noticed first.
			if (s != null && s.player != null && !s.player.equals(player.getUuid()) && s.level > 0.01f) continue;
			float g = gain(player, mob);
			if (s == null) {
				if (g <= 0f) continue;
				s = new State();
				states.put(mob.getUuid(), s);
			}
			s.player = player.getUuid();
			if (s.alerted) {
				if (mob.getTarget() != player) lostTrack(mob);
				else {
					s.level = 1f;
					continue;
				}
			}
			if (g > 0f) {
				s.level = Math.min(1f, s.level + g);
				s.lastPerceived = now;
			} else if (now - s.lastPerceived > DRAIN_DELAY) {
				s.level = Math.max(0f, s.level - DRAIN_PER_SECOND / 20f);
			}
			if (s.level >= 1f) {
				s.alerted = true;
				mob.setTarget(player);
			} else if (s.level >= SUSPICIOUS && g > 0f) {
				mob.getLookControl().lookAt(player, 30f, 30f);
				if (s.level >= INVESTIGATE && mob.getNavigation().isIdle()) {
					mob.getNavigation().startMovingTo(player.getX(), player.getY(), player.getZ(), 0.8);
				}
			}
		}
		if (now % 3 == 0) sync(player);
	}

	private static void sync(ServerPlayerEntity player) {
		List<Integer> ids = new ArrayList<>();
		List<Integer> levels = new ArrayList<>();
		var box = player.getBoundingBox().expand(RANGE);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, box, m -> m instanceof Monster)) {
			State s = states.get(mob.getUuid());
			if (s == null || !player.getUuid().equals(s.player) || (s.level <= 0.01f && !s.alerted) || hidden(mob)) continue;
			ids.add(mob.getId());
			levels.add(s.alerted ? 255 : Math.round(s.level * 250));
		}
		Net.toPlayer(player, new AwarenessPayload(ids, levels));
	}

	/** Forget mobs that died or unloaded, now and then. */
	public static void cleanup(net.minecraft.server.MinecraftServer server) {
		if (server.getTicks() % 200 != 0) return;
		for (Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			boolean alive = false;
			for (var world : server.getWorlds()) {
				var ent = world.getEntity(e.getKey());
				if (ent != null && ent.isAlive()) {
					alive = true;
					break;
				}
			}
			if (!alive || (e.getValue().level <= 0f && !e.getValue().alerted)) it.remove();
		}
	}

	public static void clear() {
		states.clear();
	}
}
