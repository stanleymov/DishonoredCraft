package com.dishonoredmc.client.fp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One decoded Dishonored animation: per-frame local rotations for the bones it drives. */
public final class FpClip {
	public final String name;
	final float fps;
	final int frames;
	final String[] bones;
	final float[] rot;
	/** Bones with animated local positions (the folding blade's telescoping segments, gun parts), or empty. */
	final String[] posBones;
	final float[] pos;
	private int[] boneIndex, posIndex;
	private FpModel resolvedFor;

	/** Frames cut from the end of a clip. */
	private static final java.util.Map<String, Integer> TRIM_END = java.util.Map.of("Guns_Ready_Equip", 2, "Gadgets_Ready_Equip", 1);

	FpClip(String name, JsonObject json) {
		this.name = name;
		this.fps = json.get("fps").getAsFloat();
		// The gun and gadget draws end with a jump in the hand; they stop a frame or two early instead.
		this.frames = json.get("frames").getAsInt() - TRIM_END.getOrDefault(name, 0);
		JsonArray b = json.getAsJsonArray("bones");
		bones = new String[b.size()];
		for (int i = 0; i < bones.length; i++) bones[i] = b.get(i).getAsString();
		rot = FpModel.floats(json.getAsJsonArray("rot"));
		if (json.has("posBones")) {
			JsonArray pb = json.getAsJsonArray("posBones");
			posBones = new String[pb.size()];
			for (int i = 0; i < posBones.length; i++) posBones[i] = pb.get(i).getAsString();
			pos = FpModel.floats(json.getAsJsonArray("pos"));
		} else {
			posBones = new String[0];
			pos = new float[0];
		}
	}

	public float duration() {
		return frames <= 1 ? 0f : (frames - 1) / fps;
	}

	private int[] indices(FpModel model) {
		if (resolvedFor != model) {
			boneIndex = new int[bones.length];
			for (int i = 0; i < bones.length; i++) boneIndex[i] = model.index.getOrDefault(bones[i], -1);
			posIndex = new int[posBones.length];
			for (int i = 0; i < posBones.length; i++) {
				// The root keeps its bind position so the tuned grips stay where they are.
				posIndex[i] = posBones[i].startsWith("Root") ? -1 : model.index.getOrDefault(posBones[i], -1);
			}
			resolvedFor = model;
		}
		return boneIndex;
	}

	/** Writes the sampled local positions into out[] (bones without animated positions are left untouched). */
	public void samplePos(FpModel model, float time, boolean loop, Vector3f[] out) {
		if (posBones.length == 0) return;
		float f = time * fps;
		if (loop && frames > 1) {
			f %= (frames - 1);
			if (f < 0) f += frames - 1;
		} else {
			f = Math.max(0, Math.min(f, frames - 1));
		}
		int f0 = (int) Math.floor(f);
		int f1 = Math.min(f0 + 1, frames - 1);
		float a = f - f0;
		indices(model);
		int nb = posBones.length;
		for (int i = 0; i < nb; i++) {
			int bone = posIndex[i];
			if (bone < 0) continue;
			int o0 = (f0 * nb + i) * 3, o1 = (f1 * nb + i) * 3;
			if (out[bone] == null) out[bone] = new Vector3f();
			out[bone].set(pos[o0] + (pos[o1] - pos[o0]) * a, pos[o0 + 1] + (pos[o1 + 1] - pos[o0 + 1]) * a, pos[o0 + 2] + (pos[o1 + 2] - pos[o0 + 2]) * a);
		}
	}

	/** Writes the sampled rotations into out[] for bones accepted by the mask (null mask = all). */
	public void sample(FpModel model, float time, boolean loop, Quaternionf[] out, BoneMask mask) {
		float f = time * fps;
		if (loop && frames > 1) {
			f %= (frames - 1);
			if (f < 0) f += frames - 1;
		} else {
			f = Math.max(0, Math.min(f, frames - 1));
		}
		int f0 = (int) Math.floor(f);
		int f1 = Math.min(f0 + 1, frames - 1);
		float a = f - f0;
		int nb = bones.length;
		int[] idx = indices(model);
		Quaternionf q0 = new Quaternionf(), q1 = new Quaternionf();
		for (int i = 0; i < nb; i++) {
			int bone = idx[i];
			if (bone < 0 || (mask != null && !mask.accepts(model.names[bone]))) continue;
			int o0 = (f0 * nb + i) * 4, o1 = (f1 * nb + i) * 4;
			q0.set(rot[o0], rot[o0 + 1], rot[o0 + 2], rot[o0 + 3]);
			q1.set(rot[o1], rot[o1 + 1], rot[o1 + 2], rot[o1 + 3]);
			if (out[bone] == null) out[bone] = new Quaternionf();
			q0.slerp(q1, a, out[bone]);
		}
	}

	public interface BoneMask {
		boolean accepts(String bone);
	}
}
