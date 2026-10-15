package com.dishonoredmc.client;

import com.dishonoredmc.power.Power;
import net.minecraft.util.math.MathHelper;

/** What the client knows about its powers (the server stays authoritative), plus hand-animation state. */
public final class ClientPowerState {
	public static final int CAST_TICKS = 8;

	public static float mana = 100f;
	public static float maxMana = 100f;
	public static int selected = 0;
	public static int bendTimeTicks = 0;
	public static boolean darkVision = false;
	public static boolean aimingBlink = false;

	private static int castAnim;
	private static float aim, prevAim;

	private ClientPowerState() {}

	public static Power selectedPower() {
		return Power.byIndex(selected);
	}

	public static void startCastAnimation() {
		castAnim = CAST_TICKS;
	}

	public static void tick() {
		if (castAnim > 0) castAnim--;
		prevAim = aim;
		aim += ((aimingBlink ? 1f : 0f) - aim) * 0.4f;
	}

	/** 0..1 swing progress of the marked hand's cast gesture. */
	public static float castProgress(float tickDelta) {
		if (castAnim <= 0) return 0f;
		return MathHelper.clamp((CAST_TICKS - castAnim + tickDelta) / CAST_TICKS, 0f, 1f);
	}

	public static float aimAmount(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevAim, aim);
	}

	public static void reset() {
		mana = maxMana = 100f;
		bendTimeTicks = 0;
		darkVision = false;
		aimingBlink = false;
		castAnim = 0;
		aim = prevAim = 0;
	}
}
