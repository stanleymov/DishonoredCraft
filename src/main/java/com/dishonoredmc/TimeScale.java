package com.dishonoredmc;

/**
 * Whole-game slow motion for single-player. The integrated server and the client run in the same JVM, so both
 * clocks read this: the server's tick length (MinecraftServerMixin) and the client's tick timer
 * (RenderTickCounterMixin). Everything slows together, the player included, as in Dishonored.
 */
public final class TimeScale {
	/** Weapon wheel (1 or 1/5) and Redirective Blink (eases from 1 down to MIN). The slower one wins. */
	public static volatile float wheel = 1F, charm = 1F;
	/** Slowest the clocks ever run; a fully stopped world is frozen per entity instead (RedirectiveBlink). */
	public static final float MIN = 0.05F;

	private TimeScale() {}

	public static float factor() {
		return Math.max(MIN, Math.min(wheel, charm));
	}

	public static long serverTickMillis() {
		return Math.round(50.0 / factor());
	}

	public static float clientTickMillis() {
		return 50F / factor();
	}

	public static void reset() {
		wheel = 1F;
		charm = 1F;
	}
}
