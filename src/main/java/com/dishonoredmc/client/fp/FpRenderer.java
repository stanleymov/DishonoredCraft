package com.dishonoredmc.client.fp;

import com.dishonoredmc.client.HandTuning;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders Corvo's real arms and weapons, animated, from the rig's own camera bone. */
public final class FpRenderer {

	private static float[] armPos, armNrm;
	private static final Identifier WHITE = new Identifier("textures/misc/white.png");
	private static final java.util.Map<String, float[][]> weaponBuffers = new java.util.HashMap<>();

	private FpRenderer() {}

	public static void render(float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		FpModel arms = FpAssets.arms();
		if (arms == null) return;
		float now = FpController.now(tickDelta);
		Quaternionf[] pose = FpController.pose(arms, now);
		Matrix4f[] world = arms.world(pose);
		// Like Dishonored, the view stays on the rig's camera bone during takedowns (no steering toward the hands).
		Matrix4f view = cameraView(world[arms.index.get("camera_jnt")]);
		view = new Matrix4f().scale(lensScale(), lensScale(), 1f).mul(view);
		FpController.trackLeftHand(arms, world, view, now);

		if (armPos == null || armPos.length != arms.vertexCount() * 3) {
			armPos = new float[arms.vertexCount() * 3];
			armNrm = new float[arms.vertexCount() * 3];
		}
		arms.skin(world, view, armPos, armNrm);
		drawNearClipped(arms, armPos, armNrm, matrices, consumers, light);

		for (boolean rightHand : new boolean[] {true, false}) {
			String w = FpController.weaponFor(rightHand);
			FpModel weapon = w == null ? null : FpAssets.weapon(w);
			if (weapon == null) continue;
			FpSlot bones = w.equals("sword") ? FpController.swordBones : FpController.gunBones;
			Quaternionf[] wpose = new Quaternionf[weapon.boneCount()];
			bones.sample(weapon, now, wpose, null);
			org.joml.Vector3f[] wpos = new org.joml.Vector3f[weapon.boneCount()];
			// Only the folding blade uses animated positions (its telescoping segments); the guns stay rotation-only.
			if (w.equals("sword")) bones.samplePos(weapon, now, wpos);
			Matrix4f[] wworld = weapon.world(wpose, wpos);
			Matrix4f rootBindInv = new Matrix4f(weapon.world(null)[0]).invert();
			// The weapon socket cancels the attachment bone's own bind rotation. Verified against the pistol
			// aim pose (barrel within 5 degrees of the view axis) and Dishonored's screenshots (blade to the right).
			int socketBone = arms.index.get(rightHand ? "handAttachment_R_jnt" : "handAttachment_L_jnt");
			Matrix4f socket = new Matrix4f().rotation(arms.bindRotation(socketBone)).invert();
			if (rightHand) {
				float[] r = HandTuning.swordRot, o = HandTuning.swordOffset;
				socket = new Matrix4f().translate(o[0], o[1], o[2]).rotateXYZ(rad(r[0]), rad(r[1]), rad(r[2])).mul(socket);
			}
			float bw = rightHand ? FpController.blockWeight : 0f;
			if (bw > 0.001f) {
				float[] r = HandTuning.blockRot, o = HandTuning.blockOffset;
				socket = new Matrix4f()
					.translate(o[0] * bw, o[1] * bw, o[2] * bw)
					.rotateXYZ(rad(r[0] * bw), rad(r[1] * bw), rad(r[2] * bw))
					.mul(socket);
			}
			if (!rightHand) {
				boolean bow = w.equals("crossbow");
				float[] r = bow ? HandTuning.bowRot : HandTuning.gunRot;
				float[] e = bow ? HandTuning.bowEquipRot : HandTuning.gunEquipRot;
				Quaternionf held = new Quaternionf().rotateXYZ(rad(r[0]), rad(r[1]), rad(r[2]));
				// 0 0 0 = Dishonored's own pull-out (the gun turns in the hand as it is drawn).
				Quaternionf pullOut = new Quaternionf().rotateXYZ(rad(e[0]), rad(e[1]), rad(e[2]));
				// START placement during the draw, turning into the END (held) placement over the blend frames.
				float gw = FpController.gunGripWeight(now);
				Quaternionf grip = pullOut.slerp(held, gw);
				float[] ps = bow ? HandTuning.bowStartPos : HandTuning.gunStartPos, ph = bow ? HandTuning.bowHeldPos : HandTuning.gunHeldPos;
				socket = new Matrix4f()
					.translate(ps[0] + (ph[0] - ps[0]) * gw, ps[1] + (ph[1] - ps[1]) * gw, ps[2] + (ph[2] - ps[2]) * gw)
					.rotate(grip).mul(socket);
			}
			Matrix4f attach = new Matrix4f(view).mul(world[socketBone]).mul(socket).mul(rootBindInv);
			float[][] buf = weaponBuffers.computeIfAbsent(w, k -> new float[][] {new float[weapon.vertexCount() * 3], new float[weapon.vertexCount() * 3]});
			weapon.skin(wworld, attach, buf[0], buf[1]);
			draw(weapon, buf[0], buf[1], matrices, consumers, light);
			if (w.equals("crossbow")) drawLoadedBolt(weapon, wworld, attach, now, matrices, consumers, light);
		}

		// Grenade / spring razor sit in the left palm.
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		String gadget = player == null ? null : FpController.gadgetFor(player);
		FpModel g = gadget == null ? null : FpAssets.world(gadget);
		if (g != null) {
			int socketBone = arms.index.get("handAttachment_L_jnt");
			Matrix4f socket = new Matrix4f().rotation(arms.bindRotation(socketBone)).invert();
			Matrix4f attach = new Matrix4f(view).mul(world[socketBone]).mul(socket)
				.mul(gadgetPlacement(FpController.gadgetHeldWeight(now), gadget));
			float[][] buf = weaponBuffers.computeIfAbsent("gadget:" + gadget, k -> new float[][] {new float[g.vertexCount() * 3], new float[g.vertexCount() * 3]});
			g.skin(g.world(null), attach, buf[0], buf[1]);
			draw(g, buf[0], buf[1], matrices, consumers, light);
		}
	}


	/** Minecraft draws first-person hands with a fixed 70 degree vertical field of view. */
	private static final double MC_HAND_VFOV = Math.toRadians(70);

	/**
	 * Dishonored's arms were animated for its own, much narrower camera (HandTuning.fpFov, horizontal, like the
	 * game's FOV slider). Spreading camera-space x/y by this factor shows them as Dishonored's lens would: hands at
	 * the screen edges as in the game, and the shoulders and near-camera parts of the rig pushed out of frame.
	 */
	private static float lensScale() {
		// Dishonored's FOV is horizontal at 16:9 (wider screens add width, not zoom), so the vertical view is fixed.
		double aspect = 16.0 / 9.0;
		double dishonoredVfov = 2 * Math.atan(Math.tan(Math.toRadians(HandTuning.fpFov) / 2) / aspect);
		return (float) (Math.tan(MC_HAND_VFOV / 2) / Math.tan(dishonoredVfov / 2));
	}

	/** The bolt seated in the crossbow (on its bolt_jnt), matching the loaded bolt type; hidden while it reloads. */
	private static void drawLoadedBolt(FpModel crossbow, Matrix4f[] wworld, Matrix4f attach, float now,
									   MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		Integer jnt = crossbow.index.get("bolt_jnt");
		if (player == null || jnt == null || !FpController.crossbowLoaded(now)) return;
		var stack = player.getMainHandStack().isOf(com.dishonoredmc.item.ModItems.WRIST_BOW) ? player.getMainHandStack() : player.getOffHandStack();
		int mode = com.dishonoredmc.item.WristBowItem.getModeIndex(stack);
		var ammo = new net.minecraft.item.Item[] {com.dishonoredmc.item.ModItems.CROSSBOW_BOLT, com.dishonoredmc.item.ModItems.SLEEP_DART, com.dishonoredmc.item.ModItems.INCENDIARY_BOLT}[mode];
		if (!player.isCreative() && player.getInventory().count(ammo) == 0) return;
		String key = new String[] {"bolt", "bolt_sleep", "bolt_flare"}[mode];
		FpModel bolt = FpAssets.weapon(key);
		if (bolt == null) return;
		float[] o = HandTuning.boltOffset, r = HandTuning.boltRot;
		Matrix4f m = new Matrix4f(attach).mul(wworld[jnt])
			.translate(o[0], o[1], o[2])
			.rotateXYZ(rad(r[0]), rad(r[1]), rad(r[2]))
			.mul(new Matrix4f(bolt.world(null)[0]).invert());
		float[][] buf = weaponBuffers.computeIfAbsent(key, k -> new float[][] {new float[bolt.vertexCount() * 3], new float[bolt.vertexCount() * 3]});
		bolt.skin(bolt.world(null), m, buf[0], buf[1]);
		// Sleep darts glow green and incendiary bolts blue, as in Dishonored (tinted, full brightness).
		int fullBright = 0xF000F0;
		int glow = mode == 1 ? 0x9BFF3C : 0x8CC8FF;
		for (FpModel.Part part : bolt.parts) {
			if (part.glow()) drawTinted(bolt, part, buf[0], buf[1], matrices, consumers, fullBright, glow);
			else drawTinted(bolt, part, buf[0], buf[1], matrices, consumers, light, 0xFFFFFF);
		}
	}

	/**
	 * Where the grenade / spring razor sits relative to the hand's attachment bone. Dishonored animates that bone
	 * during the draw (it starts away from the palm), so the draw has its own placement that blends into the held
	 * one (weight 1 = held).
	 */
	private static Matrix4f gadgetPlacement(float weight, String gadget) {
		boolean razor = gadget.equals("spring_razor");
		float[] po = razor ? HandTuning.razorStartPos : HandTuning.grenadeStartPos, pr = razor ? HandTuning.razorStartRot : HandTuning.grenadeStartRot;
		float[] ho = razor ? HandTuning.razorHeldPos : HandTuning.grenadeHeldPos, hr = razor ? HandTuning.razorHeldRot : HandTuning.grenadeHeldRot;
		// The spring razor's use movement and the hand's return to idle afterwards have their own placements.
		float rw = razor ? FpController.razorReturnWeight(FpController.now(0f)) : -1f;
		if (rw >= 0f) {
			// The hand's return to idle after placing a razor: RETURN -> held.
			float[] ro = HandTuning.razorReturnPos, rr = HandTuning.razorReturnRot;
			Quaternionf from = new Quaternionf().rotateXYZ(rad(rr[0]), rad(rr[1]), rad(rr[2]));
			Quaternionf to = new Quaternionf().rotateXYZ(rad(hr[0]), rad(hr[1]), rad(hr[2]));
			Quaternionf q = HandTuning.razorReturnLong ? slerpLong(from, to, rw) : from.slerp(to, rw);
			return new Matrix4f().translate(ro[0] + (ho[0] - ro[0]) * rw, ro[1] + (ho[1] - ro[1]) * rw, ro[2] + (ho[2] - ro[2]) * rw).rotate(q);
		}
		float[] uw = razor ? FpController.razorUseWeights(FpController.now(0f)) : null;
		if (uw != null) {
			// START -> END over the use movement, then END -> held as the hand comes back.
			float[] so = HandTuning.razorUseStartPos, sr = HandTuning.razorUseStartRot, eo = HandTuning.razorUsePos, er = HandTuning.razorUseRot;
			float a = uw[0], b = uw[1];
			float[] o = new float[3];
			for (int i = 0; i < 3; i++) {
				float use = so[i] + (eo[i] - so[i]) * a;
				o[i] = use + (ho[i] - use) * b;
			}
			Quaternionf q = new Quaternionf().rotateXYZ(rad(sr[0]), rad(sr[1]), rad(sr[2]))
				.slerp(new Quaternionf().rotateXYZ(rad(er[0]), rad(er[1]), rad(er[2])), a)
				.slerp(new Quaternionf().rotateXYZ(rad(hr[0]), rad(hr[1]), rad(hr[2])), b);
			return new Matrix4f().translate(o[0], o[1], o[2]).rotate(q);
		}
		Quaternionf pull = new Quaternionf().rotateXYZ(rad(pr[0]), rad(pr[1]), rad(pr[2]));
		Quaternionf held = new Quaternionf().rotateXYZ(rad(hr[0]), rad(hr[1]), rad(hr[2]));
		Quaternionf q = pull.slerp(held, weight);
		return new Matrix4f()
			.translate(po[0] + (ho[0] - po[0]) * weight, po[1] + (ho[1] - po[1]) * weight, po[2] + (ho[2] - po[2]) * weight)
			.rotate(q);
	}

	/** Turn from `from` to `to` the long way round (the opposite direction to a normal slerp). */
	private static Quaternionf slerpLong(Quaternionf from, Quaternionf to, float t) {
		Quaternionf d = new Quaternionf(from).conjugate().mul(to);
		if (d.w < 0) d.set(-d.x, -d.y, -d.z, -d.w); // the short way: angle 0..180 degrees
		org.joml.AxisAngle4f aa = new org.joml.AxisAngle4f().set(d);
		if (aa.angle < 1e-4f) return new Quaternionf(from);
		float angle = (aa.angle - (float) (2 * Math.PI)) * t; // the same end, the other way round
		return new Quaternionf(from).mul(new Quaternionf().fromAxisAngleRad(aa.x, aa.y, aa.z, angle));
	}

	private static float rad(float deg) {
		return (float) Math.toRadians(deg);
	}

	/** Camera bone axes in the export: forward = -local Y, up = +local X. */
	private static Matrix4f cameraView(Matrix4f cam) {
		Vector3f eye = cam.getTranslation(new Vector3f());
		Vector3f f = new Vector3f(-cam.m10(), -cam.m11(), -cam.m12()).normalize();
		Vector3f up = new Vector3f(cam.m00(), cam.m01(), cam.m02()).normalize();
		Vector3f r = new Vector3f(f).cross(up).normalize();
		up = new Vector3f(r).cross(f).normalize();
		Matrix4f v = new Matrix4f(
			r.x, up.x, -f.x, 0,
			r.y, up.y, -f.y, 0,
			r.z, up.z, -f.z, 0,
			0, 0, 0, 1);
		v.translate(-eye.x, -eye.y, -eye.z);
		return v;
	}

	/**
	 * Dishonored's camera clips geometry closer than its near plane, which hides the cut edge of the arms mesh at
	 * the shoulders when a takedown leans the body. Minecraft's near plane is much closer, so triangles of the
	 * arms that come within HandTuning.nearClip of the eye can be dropped here (0 = off, the default).
	 */
	private static float nearClip() {
		return HandTuning.nearClip;
	}

	private static void drawNearClipped(FpModel model, float[] pos, float[] nrm, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		int overlay = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
		for (FpModel.Part part : model.parts) {
			VertexConsumer vc = consumers.getBuffer(part.translucent()
				? FpRenderLayers.TRANSLUCENT.apply(part.texture()) : FpRenderLayers.CUTOUT.apply(part.texture()));
			int[] idx = part.indices();
			for (int t = 0; t < idx.length; t += 3) {
				if (tooNear(pos, idx[t]) || tooNear(pos, idx[t + 1]) || tooNear(pos, idx[t + 2])) continue;
				vertex(vc, m, entry, model, pos, nrm, idx[t], light, overlay, 255);
				vertex(vc, m, entry, model, pos, nrm, idx[t + 1], light, overlay, 255);
				vertex(vc, m, entry, model, pos, nrm, idx[t + 2], light, overlay, 255);
			}
		}
	}

	private static boolean tooNear(float[] pos, int i) {
		float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
		float n = nearClip();
		return n > 0f && x * x + y * y + z * z < n * n;
	}

	public static void draw(FpModel model, float[] pos, float[] nrm, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		draw(model, pos, nrm, matrices, consumers, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
	}

	private static void drawTinted(FpModel model, FpModel.Part only, float[] pos, float[] nrm, MatrixStack matrices, VertexConsumerProvider consumers, int light, int rgb) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		for (FpModel.Part part : java.util.List.of(only)) {
			// Glowing vials are a solid colour (the bolt textures are too dark to tint), drawn at full brightness.
			VertexConsumer vc = consumers.getBuffer(FpRenderLayers.CUTOUT.apply(part.glow() ? WHITE : part.texture()));
			int[] idx = part.indices();
			for (int t = 0; t < idx.length; t += 3) {
				for (int k : new int[] {idx[t], idx[t + 1], idx[t + 2]}) {
					vc.vertex(m, pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2])
						.color(r, g, b, 255)
						.texture(model.uvs[k * 2], model.uvs[k * 2 + 1])
						.overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
						.light(light)
						.normal(entry.getNormalMatrix(), nrm[k * 3], nrm[k * 3 + 1], nrm[k * 3 + 2]).next();
				}
			}
		}
	}

	public static void draw(FpModel model, float[] pos, float[] nrm, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		for (FpModel.Part part : model.parts) {
			VertexConsumer vc = consumers.getBuffer(part.translucent()
				? FpRenderLayers.TRANSLUCENT.apply(part.texture()) : FpRenderLayers.CUTOUT.apply(part.texture()));
			int[] idx = part.indices();
			int alpha = part.translucent() ? 110 : 255;
			for (int t = 0; t < idx.length; t += 3) {
				vertex(vc, m, entry, model, pos, nrm, idx[t], light, overlay, alpha);
				vertex(vc, m, entry, model, pos, nrm, idx[t + 1], light, overlay, alpha);
				vertex(vc, m, entry, model, pos, nrm, idx[t + 2], light, overlay, alpha);
			}
		}
		// Glowing / Dark Vision outline: Minecraft's outline buffer takes quads, so each triangle goes in as one
		// (its last corner twice).
		if (consumers instanceof net.minecraft.client.render.OutlineVertexConsumerProvider) {
			for (FpModel.Part part : model.parts) {
				if (part.translucent()) continue;
				VertexConsumer oc = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getOutline(part.texture()));
				int[] idx = part.indices();
				for (int t = 0; t < idx.length; t += 3) {
					for (int k : new int[] {idx[t], idx[t + 1], idx[t + 2], idx[t + 2]}) {
						oc.vertex(m, pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2]).color(255, 255, 255, 255)
							.texture(model.uvs[k * 2], model.uvs[k * 2 + 1]).next();
					}
				}
			}
		}
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, MatrixStack.Entry entry, FpModel model, float[] pos, float[] nrm, int i, int light, int overlay, int alpha) {
		vc.vertex(m, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
			.color(255, 255, 255, alpha)
			.texture(model.uvs[i * 2], model.uvs[i * 2 + 1])
			.overlay(overlay)
			.light(light)
			.normal(entry.getNormalMatrix(), nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2]).next();
	}

	static Identifier texture(FpModel m) {
		return m.texture;
	}
}
