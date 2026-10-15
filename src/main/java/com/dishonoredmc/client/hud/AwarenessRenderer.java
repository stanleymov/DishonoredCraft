package com.dishonoredmc.client.hud;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.net.AwarenessPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Dishonored's guard awareness marker: six lightning bolts in a ring around the head, three on each side. They
 * light up in three stages from the bottom pair up; each newly lit pair flashes red, then turns white. At the
 * third stage the mob is alerted and the marker fades away. Uses the game's own bolt sprites.
 */
public final class AwarenessRenderer {
	private static final Identifier BOLT = DishonoredMod.id("textures/gui/hud/aware_bolt.png");
	private static final Identifier BOLT_DIM = DishonoredMod.id("textures/gui/hud/aware_bolt_dim.png");
	private static final Identifier BOLT_GLOW = DishonoredMod.id("textures/gui/hud/aware_bolt_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Bolt positions, degrees clockwise from straight up: top, middle, bottom on each side. */
	private static final float[] ANGLES = {30f, 75f, 120f};
	private static final float RADIUS = 0.62f, BOLT_W = 0.1f, BOLT_H = 0.17f;

	/** Seconds a newly lit pair stays red before turning white, and how long the marker fades once alerted. */
	private static final float FLASH = 0.3f, FADE_OUT = 0.8f;
	/** Seconds for the marker to fade in when a mob starts noticing the player, and out when it stops. */
	private static final float SHOW = 0.2f;

	private static final class Anim {
		int stage;
		long since;
		/** 0..1 how far the marker has faded in; the last level it was synced at, kept while it fades out. */
		float vis;
		int lastLevel;
		long lastFrame;
	}

	private static final Map<Integer, Anim> ANIM = new HashMap<>();

	/** Entity id -> level 0..250 (255 = alerted), replaced by every sync. */
	private static Map<Integer, Integer> levels = new HashMap<>();
	/** Smoothed fill shown per entity, so the bolts light continuously between syncs. */
	private static final Map<Integer, Float> shown = new HashMap<>();

	private AwarenessRenderer() {}

	public static void receive(AwarenessPayload p) {
		Map<Integer, Integer> next = new HashMap<>();
		for (int i = 0; i < p.ids().size(); i++) next.put(p.ids().get(i), p.levels().get(i));
		levels = next;
		// Markers no longer synced fade out (see render); forget them once gone or no longer drawn.
		long now = System.currentTimeMillis();
		ANIM.entrySet().removeIf(e -> !next.containsKey(e.getKey()) && (e.getValue().vis <= 0f || now - e.getValue().lastFrame > 1000));
		shown.keySet().retainAll(ANIM.keySet());
	}

	/** The mob has noticed the player (gauge at least suspicious, or alerted): no backstab or choke prompt. */
	public static boolean aware(int entityId) {
		Integer l = levels.get(entityId);
		return l != null && l >= Math.round(com.dishonoredmc.power.Awareness.SUSPICIOUS * 250);
	}

	public static void reset() {
		levels = new HashMap<>();
		shown.clear();
		ANIM.clear();
	}

	/** Called after a living entity renders (Forge RenderLivingEvent.Post). */
	public static void render(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers) {
		Integer synced = levels.get(entity.getId());
		Anim an = ANIM.get(entity.getId());
		if ((synced == null && an == null) || !HudArt.has("aware_bolt")) return;
		if (an == null) ANIM.put(entity.getId(), an = new Anim());
		long now = System.currentTimeMillis();
		float dt = an.lastFrame == 0 ? 0f : Math.min(0.1f, (now - an.lastFrame) / 1000f);
		an.lastFrame = now;
		// Fade in while the mob is noticing the player, out once it isn't (or a takedown has started).
		an.vis = clamp(an.vis + (synced != null ? dt : -dt) / SHOW);
		if (synced != null) an.lastLevel = synced;
		if (synced == null && an.vis <= 0f) return;
		int level = an.lastLevel;
		boolean alerted = level >= 255;
		float target = alerted ? 1f : level / 250f;
		float fill = shown.merge(entity.getId(), target, (old, t) -> old + (t - old) * 0.25f);

		MinecraftClient mc = MinecraftClient.getInstance();
		matrices.push();
		matrices.translate(0, entity.getStandingEyeHeight() + 0.05, 0);
		matrices.multiply(mc.getEntityRenderDispatcher().getRotation());
		float s = Math.max(1f, entity.getWidth() / 0.6f);
		matrices.scale(-s, -s, s);
		Matrix4f m = matrices.peek().getPositionMatrix();
		Matrix3f n = matrices.peek().getNormalMatrix();

		// As in Dishonored: stage 1 lights the bottom pair white (the others grey); stage 2 flashes the middle pair
		// red, then white; stage 3 (alerted) lights the top pair and all six flash red, then the whole marker fades
		// away while the mob is hostile.
		int stage = alerted ? 3 : fill >= 0.5f ? 2 : 1;
		if (stage != an.stage) {
			an.stage = stage;
			an.since = now;
		}
		float t = (now - an.since) / 1000f;
		float fade = (stage == 3 ? clamp(1f - (t - FLASH - 0.25f) / FADE_OUT) : 1f) * an.vis;
		if (fade <= 0f) {
			matrices.pop();
			return;
		}
		for (int i = 0; i < 3; i++) { // i = 0 top, 1 middle, 2 bottom
			int pairStage = 3 - i; // bottom = stage 1, middle = 2, top = 3
			boolean on = stage >= pairStage;
			// The pair that just lit flashes red, then settles to white. Once alerted, every pair turns red and fades.
			float red = stage == 3 ? 1f : on && pairStage == stage && pairStage > 1 ? clamp(1f - (t - FLASH) / 0.2f) : 0f;
			for (int side = -1; side <= 1; side += 2) {
				float a = (float) Math.toRadians(ANGLES[i] * side);
				float cx = (float) Math.sin(a) * RADIUS, cy = -(float) Math.cos(a) * RADIUS;
				if (!on) {
					// Solid grey, as in Dishonored: a see-through bolt picks up a bright sky behind it and looks lit.
					bolt(consumers, BOLT_DIM, m, n, cx, cy, a, BOLT_W, BOLT_H, 255, 255, 255, (int) (255 * fade));
					continue;
				}
				if (red > 0.01f) {
					bolt(consumers, BOLT_GLOW, m, n, cx, cy, a, BOLT_W * 1.6f, BOLT_H * 1.4f, 255, 40, 30, (int) (150 * red * fade));
					bolt(consumers, BOLT, m, n, cx, cy, a, BOLT_W, BOLT_H, 230, 30, 25, (int) (255 * red * fade));
				}
				if (red < 0.99f) bolt(consumers, BOLT, m, n, cx, cy, a, BOLT_W, BOLT_H, 255, 255, 255, (int) (235 * (1f - red) * fade));
			}
		}
		matrices.pop();
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/** One bolt: a quad centred at (cx, cy), turned by `rot` so it points away from the head. */
	private static void bolt(VertexConsumerProvider consumers, Identifier tex, Matrix4f m, Matrix3f n,
							 float cx, float cy, float rot, float w, float h, int r, int g, int b, int a) {
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(tex));
		float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
		float[][] corners = {{-w, -h, 0, 0}, {-w, h, 0, 1}, {w, h, 1, 1}, {w, -h, 1, 0}};
		for (float[] c : corners) {
			float x = cx + c[0] * cos - c[1] * sin;
			float y = cy + c[0] * sin + c[1] * cos;
			vc.vertex(m, x, y, 0).color(r, g, b, a).texture(c[2], c[3]).overlay(OverlayTexture.DEFAULT_UV)
				.light(FULL_BRIGHT).normal(n, 0, 1, 0).next();
		}
	}
}
