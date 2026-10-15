package com.dishonoredmc.client.fp;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * One animation layer (an arm, the base body, or a weapon's own bones). Plays a clip, optionally a chain of
 * one-shots followed by a loop, and crossfades from the previous clip for a short time.
 */
final class FpSlot {
	private static final float FADE = 0.12f;
	/** Switching between looping cycles (idle / walk / sprint) blends longer, so the weapon doesn't jump. */
	private static final float LOOP_FADE = 0.3f;
	/**
	 * Clips with their own blend-in time. Blink's wind-up ends with the hand still closing fast (~1000 degrees a
	 * second) and the twitching loop starts ~50 degrees further on: a quick blend finishes that closing motion.
	 */
	private static final java.util.Map<String, Float> FADE_INTO = java.util.Map.of("Powers_Cast_Blink_Loop", 0.06f);
	/**
	 * Clips that hand over to the next loop slowly: Dishonored chains its right-hand slashes into a follow-up, so they
	 * end ~30 degrees away from the ready pose; a quick blend back to idle looked like the blade bouncing.
	 */
	private static final float SLASH_SETTLE = 0.35f;
	private float fadeLen = FADE;
	/** Loop-to-loop switches (idle / walk / sprint) also blend bones only one of the two clips drives. */
	private boolean smoothOneSided;
	/** Every transition on this slot blends smoothly (the gadget hand: draw, idle and throw all glide). */
	boolean alwaysSmooth;

	private FpClip clip;
	private float start;
	private boolean loop;
	/** Playback speed of the current clip (and of the previous one, while fading out of it). */
	private float speed = 1f, prevSpeed = 1f;
	/** Speed for the one-shots queued by the latest play(); loops always play at 1. */
	private float queuedSpeed = 1f;
	private final java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
	private String loopAfter;

	private FpClip prevClip;
	private float prevStart;
	private boolean prevLoop;
	private float fadeStart = -1;

	/** Stop and clear the slot (used by the full-body overlay). */
	void stop() {
		clip = null;
		prevClip = null;
		queue.clear();
		loopAfter = null;
	}

	/** Still showing something (a one-shot in progress, a queued clip or a loop). */
	boolean active(float now) {
		// A one-shot that has just ended with a loop queued after it still counts (the loop starts on the next tick).
		return clip != null && (loop || playingOneShot(now) || !queue.isEmpty() || loopAfter != null);
	}

	@Nullable
	String current() {
		return clip == null ? null : clip.name;
	}

	/** Current frame of the clip (for the dev frame counter), or -1. */
	int frame(float now) {
		if (clip == null) return -1;
		int f = (int) Math.floor(elapsed(now) * clip.fps);
		return loop ? Math.floorMod(f, Math.max(1, clip.frames)) : Math.min(f, clip.frames - 1);
	}

	int frameCount() {
		return clip == null ? 0 : clip.frames;
	}

	/** Clip time of the current clip (seconds into it, at its playback speed). */
	float elapsed(float now) {
		return (now - start) * speed;
	}

	private float prevTime(float now) {
		return (now - prevStart) * prevSpeed;
	}

	/** Progress 0..1 of a one-shot, or 1 for loops/nothing. */
	float progress(float now) {
		if (clip == null || loop || clip.duration() <= 0) return 1f;
		return Math.min(1f, elapsed(now) / clip.duration());
	}

	boolean playingOneShot(float now) {
		return clip != null && !loop && elapsed(now) < clip.duration();
	}

	/** Play clips in sequence, then hold `thenLoop` looping. */
	void play(float now, String thenLoop, String... oneShots) {
		playAt(now, 1f, thenLoop, oneShots);
	}

	/** play(), with the one-shots running `oneShotSpeed` times as fast. */
	void playAt(float now, float oneShotSpeed, String thenLoop, String... oneShots) {
		queuedSpeed = oneShotSpeed;
		queue.clear();
		for (String s : oneShots) if (FpAssets.clip(s) != null) queue.add(s);
		loopAfter = thenLoop;
		advance(now, true);
	}

	/** the clip being faded out of, while the fade lasts (else null). */
	@Nullable
	String fadingFrom(float now) {
		if (prevClip == null || fadeStart < 0 || now - fadeStart >= fadeLen) return null;
		return prevClip.name;
	}

	/** 0..1 through the current crossfade. */
	float fadeProgress(float now) {
		return fadeStart < 0 ? 1f : Math.max(0f, Math.min(1f, (now - fadeStart) / fadeLen));
	}

	/** the clock time `frac` (0..1) of the way through the current crossfade. */
	float fadeClock(float frac) {
		return fadeStart + fadeLen * frac;
	}

	/** frame position (fractional) in the current clip. */
	float clipFrame(float now) {
		return clip == null ? 0f : elapsed(now) * clip.fps;
	}

	/** the clock time that shows `frame` of the current draw clip (*_Ready_Equip), or NaN. */
	float freezeClock(int frame) {
		return clip != null && clip.name.endsWith("_Ready_Equip") ? freezeClock(clip.name, frame) : Float.NaN;
	}

	/** the clock time that shows `frame` of `name` while it is the current one-shot, or NaN. */
	float freezeClock(String name, int frame) {
		if (clip == null || loop || start == Float.MAX_VALUE || !clip.name.equals(name)) return Float.NaN;
		int f = Math.max(0, Math.min(frame, clip.frames - 1));
		return start + (f + 0.5f) / clip.fps / speed;
	}

	/** no crossfade into the current clip (Dishonored cuts straight into a gun / gadget draw). */
	void cut() {
		fadeStart = -1;
	}

	/** The current transition also blends bones only one of the two clips drives (no snap). */
	void blendSmoothly() {
		smoothOneSided = true;
	}

	/**
	 * Hold the first frame of `name` until the next play() (a pose to wait in). Counts as a one-shot in progress,
	 * so setLoop() leaves it alone.
	 */
	void holdFirstFrame(float now, String name) {
		queue.clear();
		loopAfter = null;
		start(now, name, false);
		start = Float.MAX_VALUE;
	}

	/** Switch the looping clip; does nothing if already looping it or busy with a one-shot (it will be used after). */
	void setLoop(float now, String name) {
		loopAfter = name;
		if (playingOneShot(now) || !queue.isEmpty()) return;
		if (clip != null && loop && clip.name.equals(name)) return;
		start(now, name, true);
		if (prevClip != null && prevLoop) {
			fadeLen = LOOP_FADE;
			smoothOneSided = true;
		}
	}

	void tick(float now) {
		if (clip != null && !loop && elapsed(now) >= clip.duration()) advance(now, false);
	}

	private void advance(float now, boolean force) {
		String next = queue.poll();
		if (next != null) start(now, next, false);
		else if (loopAfter != null && (force || clip == null || !loop)) start(now, loopAfter, true);
	}

	private void start(float now, String name, boolean asLoop) {
		FpClip c = FpAssets.clip(name);
		if (c == null) return;
		if (clip != null) {
			prevClip = clip;
			prevStart = start;
			prevLoop = loop;
			prevSpeed = speed;
			fadeStart = now;
			fadeLen = FADE;
			smoothOneSided = alwaysSmooth;
			if (asLoop && !prevLoop && prevClip.name.contains("_Attack")) {
				fadeLen = SLASH_SETTLE;
				smoothOneSided = true;
			}
			// the hand's return to idle after placing a spring razor has its own (tunable) length.
			if (asLoop && prevClip.name.equals("Gadgets_SpringRazor_Deploy")) {
				fadeLen = com.dishonoredmc.client.HandTuning.razorReturnFade;
			}
			Float into = FADE_INTO.get(name);
			if (into != null) {
				fadeLen = into;
				smoothOneSided = true;
			}
		}
		clip = c;
		start = now;
		loop = asLoop;
		speed = asLoop ? 1f : queuedSpeed;
	}

	void samplePos(FpModel model, float now, org.joml.Vector3f[] out) {
		if (clip == null) return;
		float fade = fadeStart < 0 ? 1f : Math.min(1f, (now - fadeStart) / fadeLen);
		if (fade < 1f && prevClip != null) {
			org.joml.Vector3f[] a = new org.joml.Vector3f[out.length];
			org.joml.Vector3f[] b = new org.joml.Vector3f[out.length];
			prevClip.samplePos(model, prevTime(now), prevLoop, a);
			clip.samplePos(model, elapsed(now), loop, b);
			for (int i = 0; i < out.length; i++) {
				if (b[i] != null && a[i] != null) out[i] = new org.joml.Vector3f(a[i]).lerp(b[i], fade);
				else if (b[i] != null) out[i] = b[i];
			}
		} else {
			clip.samplePos(model, elapsed(now), loop, out);
		}
	}

	void sample(FpModel model, float now, Quaternionf[] out, FpClip.BoneMask mask) {
		if (clip == null) return;
		float fade = fadeStart < 0 ? 1f : Math.min(1f, (now - fadeStart) / fadeLen);
		if (fade < 1f && prevClip != null) {
			Quaternionf[] a = new Quaternionf[out.length];
			Quaternionf[] b = new Quaternionf[out.length];
			prevClip.sample(model, prevTime(now), prevLoop, a, mask);
			clip.sample(model, elapsed(now), loop, b, mask);
			for (int i = 0; i < out.length; i++) {
				if (a[i] == null && b[i] == null) continue;
				// Draws, attacks and reloads keep the original behaviour: a bone only the new clip drives snaps to
				// it (that quick snap is what puts the guns into their grip).
				if (!smoothOneSided) {
					if (b[i] != null && a[i] != null) out[i] = new Quaternionf(a[i]).slerp(b[i], fade);
					else if (b[i] != null) out[i] = b[i];
					continue;
				}
				// A bone only one of the two clips drives blends from / to what lies underneath (the lower layer's
				// value, or the bind pose) instead of snapping. Sword_Ready_Sprint drives the shoulder but the
				// walk does not, and the snap flung the blade across the screen when a run started or stopped.
				Quaternionf under = out[i] != null ? out[i] : model.bindRotation(i);
				Quaternionf from = a[i] != null ? a[i] : under;
				Quaternionf to = b[i] != null ? b[i] : under;
				out[i] = new Quaternionf(from).slerp(to, fade);
			}
		} else {
			clip.sample(model, elapsed(now), loop, out, mask);
		}
	}
}
