package com.dishonoredmc.client.fp;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws a Dishonored mesh (from the private pack) in the world: rats, spring razors, grenades and elixirs.
 * Rigged meshes are exported with height along -Z, so they get a +90 degree turn about X; static meshes are Y-up.
 */
public final class WorldMesh {
	private WorldMesh() {}

	public static boolean available(String name) {
		return FpAssets.world(name) != null;
	}

	public static boolean weaponAvailable(String name) {
		return FpAssets.weapon(name) != null || FpAssets.world(name) != null;
	}

	/** Placement of each weapon mesh in a third-person hand: {scale, rotX, rotY, rotZ, x, y, z} (live: HandTuning). */
	public static void renderHeldWeapon(String name, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers,
										int light, int overlay) {
		FpModel model = FpAssets.weapon(name) != null ? FpAssets.weapon(name) : FpAssets.world(name); // gadgets: world meshes
		if (model == null) return;
		com.dishonoredmc.client.HandTuning.maybeReload();
		float[] t = com.dishonoredmc.client.HandTuning.thirdPerson(name);
		matrices.push();
		matrices.translate(leftHanded ? -t[4] : t[4], t[5], t[6]);
		matrices.multiply(new Quaternionf().rotateXYZ((float) Math.toRadians(t[1]), (float) Math.toRadians(leftHanded ? -t[2] : t[2]),
			(float) Math.toRadians(leftHanded ? -t[3] : t[3])));
		Matrix4f[] world = model.world(null);
		Matrix4f space = new Matrix4f().scale(t[0]).mul(new Matrix4f(world[0]).invert());
		float[] pos = new float[model.vertexCount() * 3];
		float[] nrm = new float[model.vertexCount() * 3];
		model.skin(world, space, pos, nrm);
		FpRenderer.draw(model, pos, nrm, matrices, consumers, light, overlay);
		matrices.pop();
	}

	/** Render posed by a blend of two clips (`w` = weight of clip B), for cross-fades between animations. */
	public static void renderBlend(String name, String clipA, float timeA, boolean loopA, @Nullable String clipB, float timeB, boolean loopB,
								   float w, float scale, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		FpModel model = FpAssets.world(name);
		FpClip a = FpAssets.clip(clipA), b = clipB == null ? null : FpAssets.clip(clipB);
		if (model == null) return;
		if (a == null || b == null || w <= 0.001f || w >= 0.999f) {
			FpClip only = (b != null && w >= 0.999f) ? b : a;
			render(name, only == null ? null : only.name, only == b ? timeB : timeA, only == b ? loopB : loopA, scale, matrices, consumers, light, overlay);
			return;
		}
		Quaternionf[] pa = new Quaternionf[model.boneCount()], pb = new Quaternionf[model.boneCount()];
		a.sample(model, timeA, loopA, pa, null);
		b.sample(model, timeB, loopB, pb, null);
		for (int i = 0; i < pa.length; i++) {
			if (pa[i] == null && pb[i] == null) continue;
			// A bone only one clip drives blends from / to the bind pose instead of snapping.
			Quaternionf from = pa[i] != null ? pa[i] : model.bindRotation(i);
			Quaternionf to = pb[i] != null ? pb[i] : model.bindRotation(i);
			pa[i] = new Quaternionf(from).slerp(to, w);
		}
		draw(model, pa, scale, matrices, consumers, light, overlay);
	}

	private static void draw(FpModel model, Quaternionf[] pose, float scale, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		Matrix4f[] world = model.world(pose);
		Matrix4f space = new Matrix4f().scale(scale);
		if (model.boneCount() > 1) space.rotateX((float) Math.toRadians(90));
		float[] pos = new float[model.vertexCount() * 3];
		float[] nrm = new float[model.vertexCount() * 3];
		model.skin(world, space, pos, nrm);
		FpRenderer.draw(model, pos, nrm, matrices, consumers, light, overlay);
	}

	/** Render `name` posed by `clip` at `time` (or bind pose), scaled, at the current matrix origin. */
	public static void render(String name, @Nullable String clip, float time, boolean loop, float scale,
							  MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		FpModel model = FpAssets.world(name);
		if (model == null) return;
		Quaternionf[] pose = null;
		FpClip c = clip == null ? null : FpAssets.clip(clip);
		if (c != null) {
			pose = new Quaternionf[model.boneCount()];
			c.sample(model, time, loop, pose, null);
		}
		Matrix4f[] world = model.world(pose);
		Matrix4f space = new Matrix4f().scale(scale);
		if (model.boneCount() > 1) space.rotateX((float) Math.toRadians(90));
		float[] pos = new float[model.vertexCount() * 3];
		float[] nrm = new float[model.vertexCount() * 3];
		model.skin(world, space, pos, nrm);
		FpRenderer.draw(model, pos, nrm, matrices, consumers, light, overlay);
	}
}
