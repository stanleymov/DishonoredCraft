package com.dishonoredmc.power;

import com.dishonoredmc.net.ManaSyncPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative mana pool, persisted on the player and mirrored to the client. */
public final class Mana {
	public static final float MAX = 100f;
	private static final float REGEN_PER_TICK = 0.2f; // 4 mana per second
	private static final int REGEN_DELAY = 40;

	/** Saved in the player's Forge persistent data (written with the player, survives relogging). */
	private static final String KEY = "dishonored_mana";

	private static final Map<UUID, Integer> lastSpend = new HashMap<>();
	private static final Map<UUID, Float> lastSent = new HashMap<>();

	private Mana() {}

	public static float get(ServerPlayerEntity player) {
		var data = player.getPersistentData();
		return data.contains(KEY) ? data.getFloat(KEY) : MAX;
	}

	private static void store(ServerPlayerEntity player, float value) {
		player.getPersistentData().putFloat(KEY, value);
	}

	public static void set(ServerPlayerEntity player, float value) {
		store(player, Math.max(0f, Math.min(MAX, value)));
		sync(player, false);
	}

	/** Give back mana at once (Falling Star), up to the normal maximum. */
	public static void refund(ServerPlayerEntity player, float amount) {
		if (player.isCreative() || amount <= 0f) return;
		float mana = get(player);
		if (mana < MAX) set(player, mana + amount);
	}

	/** Mana still to flow back in (a power ended early), by player. */
	private static final Map<UUID, Float> regenBack = new HashMap<>();
	/** How fast it flows back, per tick (10 mana a second). */
	private static final float REGEN_BACK_PER_TICK = 0.5f;

	/** Give back mana gradually (a power ended early): it regenerates over the next seconds, past the Blink cap. */
	public static void regenerate(ServerPlayerEntity player, float amount) {
		if (player.isCreative() || amount <= 0f) return;
		regenBack.merge(player.getUuid(), amount, Float::sum);
	}

	/** Keep mana when the player entity is recreated (death, leaving the End). */
	public static void copy(net.minecraft.entity.player.PlayerEntity from, net.minecraft.entity.player.PlayerEntity to) {
		if (from.getPersistentData().contains(KEY)) to.getPersistentData().putFloat(KEY, from.getPersistentData().getFloat(KEY));
	}

	/** Spends mana if the player has enough. Creative players cast for free. */
	public static boolean trySpend(ServerPlayerEntity player, float cost) {
		lastSpend.put(player.getUuid(), player.age);
		if (player.isCreative()) return true;
		float mana = get(player);
		if (mana < cost) return false;
		set(player, mana - cost);
		return true;
	}

	/** Drains mana without the full-cost check (sustained powers). Returns false when empty. */
	public static boolean drain(ServerPlayerEntity player, float amount) {
		lastSpend.put(player.getUuid(), player.age);
		if (player.isCreative()) return true;
		float mana = get(player);
		set(player, mana - amount);
		return mana - amount > 0f;
	}

	public static void tick(ServerPlayerEntity player) {
		Float back = regenBack.get(player.getUuid());
		if (back != null) {
			float step = Math.min(back, REGEN_BACK_PER_TICK);
			float cur = get(player);
			if (cur >= MAX || back - step <= 0f) regenBack.remove(player.getUuid());
			else regenBack.put(player.getUuid(), back - step);
			if (cur < MAX) store(player, Math.min(MAX, cur + step));
		}
		float mana = get(player);
		int since = player.age - lastSpend.getOrDefault(player.getUuid(), -1000);
		// As in Dishonored, mana only refills by itself up to the cost of one Blink; elixirs fill the rest.
		float cap = Power.BLINK.cost;
		if (mana < cap && (since > REGEN_DELAY || since < 0)) {
			// Spiritual Pool bone charm: a little faster.
			float rate = REGEN_PER_TICK * (com.dishonoredmc.BoneCharms.has(player, com.dishonoredmc.item.ModItems.CHARM_SPIRITUAL_POOL) ? 1.3f : 1f);
			store(player, Math.min(cap, mana + rate));
		}
		if (player.age % 5 == 0) sync(player, false);
	}

	public static void sync(ServerPlayerEntity player, boolean force) {
		float mana = get(player);
		Float prev = lastSent.get(player.getUuid());
		if (!force && prev != null && Math.abs(prev - mana) < 0.01f) return;
		lastSent.put(player.getUuid(), mana);
		com.dishonoredmc.net.Net.toPlayer(player, new ManaSyncPayload(mana, MAX));
	}

	public static void forget(UUID id) {
		regenBack.remove(id);
		lastSpend.remove(id);
		lastSent.remove(id);
	}
}
