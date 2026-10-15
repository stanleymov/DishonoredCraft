package com.dishonoredmc.client;

import com.dishonoredmc.TimeScale;
import com.dishonoredmc.net.ActionPayload;
import com.dishonoredmc.net.Net;
import com.dishonoredmc.power.Power;
import com.dishonoredmc.power.RedirectiveBlink;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

/**
 * Redirective Blink, checked every frame: while Blink is aimed and the player stands still with the charm in the
 * offhand, the whole game (player included) eases down to TimeScale.MIN over ~0.3 s, then every non-player
 * entity freezes outright. Releasing Blink, cancelling or moving restores normal time instantly.
 */
public final class RedirectiveClient {
	/** Seconds for the slow-down to settle. */
	private static final float EASE = 0.12f;
	private static long lastNanos;
	private static boolean engaged;

	private RedirectiveClient() {}

	/** Real time of the last extra particle step while time is slowed. */
	private static long lastParticleStep;

	public static void frame(MinecraftClient client) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		// While time is slowed (frozen by Redirective Blink, or the wheel), game ticks come seconds apart: keep the
		// particles moving and the Blink preview following the aim at the normal 20 steps per real second.
		if (TimeScale.factor() < 0.99f && client.world != null && !client.isPaused()) {
			if (now - lastParticleStep >= 50_000_000L) {
				lastParticleStep = now;
				client.particleManager.tick();
				if (ClientPowerState.aimingBlink) DishonoredClient.blinkPreview(client);
			}
		} else {
			lastParticleStep = now;
		}
		var p = client.player;
		var o = client.options;
		boolean moving = o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed()
			|| o.jumpKey.isPressed();
		boolean active = p != null && client.getServer() != null && client.currentScreen == null
			&& RedirectiveBlink.hasCharm(p) && ClientPowerState.selectedPower() == Power.BLINK && ClientPowerState.aimingBlink
			&& ControlSwap.powerHeld(client) && !DishonoredClient.cancelPower.isPressed() && !moving;
		if (active) {
			TimeScale.charm += (TimeScale.MIN - TimeScale.charm) * (1f - (float) Math.exp(-dt / EASE));
			if (!engaged && TimeScale.charm < TimeScale.MIN * 1.3f) {
				engaged = true;
				RedirectiveBlink.clientFrozen = true;
				Net.toServer(new ActionPayload(ActionPayload.REDIRECTIVE, "", 1));
			}
		} else if (TimeScale.charm < 1f || engaged) {
			TimeScale.charm = 1f;
			// The server scheduled its next tick at the slow rate: bring it forward so time resumes at once.
			var server = client.getServer();
			if (server != null) {
				long t = Util.getMeasuringTimeMs();
				((com.dishonoredmc.mixin.MinecraftServerAccessor) server).dishonored$setTimeReference(t);
				((com.dishonoredmc.mixin.MinecraftServerAccessor) server).dishonored$setNextTickTimestamp(t);
			}
			if (engaged) {
				engaged = false;
				RedirectiveBlink.clientFrozen = false;
				Net.toServer(new ActionPayload(ActionPayload.REDIRECTIVE, "", 0));
			}
		}
	}
}
