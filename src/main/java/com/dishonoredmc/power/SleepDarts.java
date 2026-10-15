package com.dishonoredmc.power;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Mobs hit by a sleep dart lose their AI until they wake up or get hurt. */
public final class SleepDarts {
	public static final int DURATION = 20 * 30;
	public static final String TAG = "dishonored_asleep";

	private static final Map<MobEntity, Integer> sleepers = new HashMap<>();

	private SleepDarts() {}

	public static void sleep(MobEntity mob) {
		sleep(mob, DURATION);
	}

	public static void sleep(MobEntity mob, int ticks) {
		// Leave mobs that were already AI-less (possessed, or set up by a map maker) alone.
		if (mob.isAiDisabled() && !sleepers.containsKey(mob)) return;
		mob.setAiDisabled(true);
		mob.setTarget(null);
		mob.addCommandTag(TAG);
		sleepers.put(mob, ticks);
	}

	public static void wake(MobEntity mob) {
		if (sleepers.remove(mob) != null || mob.getCommandTags().contains(TAG)) {
			mob.removeScoreboardTag(TAG);
			mob.setAiDisabled(false);
		}
	}

	public static boolean isAsleep(Entity entity) {
		return entity instanceof MobEntity mob && sleepers.containsKey(mob);
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<MobEntity, Integer>> it = sleepers.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<MobEntity, Integer> entry = it.next();
			MobEntity mob = entry.getKey();
			int left = entry.getValue() - 1;
			if (left <= 0 || mob.isRemoved()) {
				it.remove();
				if (!mob.isRemoved()) {
					mob.removeScoreboardTag(TAG);
					mob.setAiDisabled(false);
				}
				continue;
			}
			entry.setValue(left);
			if (left % 30 == 0 && mob.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.CLOUD, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
			}
		}
	}

	/** A mob saved to disk while asleep wakes up on load instead of staying frozen forever. */
	public static void onEntityLoad(Entity entity) {
		if (entity instanceof MobEntity mob && mob.getCommandTags().contains(TAG) && !sleepers.containsKey(mob)) {
			mob.removeScoreboardTag(TAG);
			mob.setAiDisabled(false);
		}
	}
}
