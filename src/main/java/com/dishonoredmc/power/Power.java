package com.dishonoredmc.power;

import net.minecraft.text.Text;

/** Corvo's active powers, in the order the power wheel cycles through them. */
public enum Power {
	BLINK("blink", 12f),
	// Dark Vision costs 10 to switch on but needs 25 in the pool, so it never starts with only a second of drain left.
	DARK_VISION("dark_vision", 10f, 25f),
	BEND_TIME("bend_time", 60f),
	POSSESSION("possession", 40f),
	DEVOURING_SWARM("devouring_swarm", 30f),
	WINDBLAST("windblast", 30f);

	public final String key;
	public final float cost;
	/** Mana needed to cast (at least the cost). */
	public final float minMana;

	Power(String key, float cost) {
		this(key, cost, cost);
	}

	Power(String key, float cost, float minMana) {
		this.key = key;
		this.cost = cost;
		this.minMana = Math.max(cost, minMana);
	}

	public Text displayName() {
		return Text.translatable("power.dishonored." + key);
	}

	public static Power byIndex(int i) {
		Power[] values = values();
		return values[Math.floorMod(i, values.length)];
	}
}
