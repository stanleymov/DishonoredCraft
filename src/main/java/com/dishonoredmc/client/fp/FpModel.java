package com.dishonoredmc.client.fp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** A skinned mesh with its skeleton, as baked by tools/assets/bake_anims.py from the player's Dishonored install. */
public final class FpModel {
	public final String[] names;
	public final int[] parents;
	final float[] bindT, bindQ, bindS;
	final Matrix4f[] inverseBind;
	public final Map<String, Integer> index = new HashMap<>();

	final Identifier texture;
	final float[] positions, normals, uvs, weights;
	final int[] joints, indices;
	/** Per-material parts (texture + index range); a single part when the mesh has one material. */
	public final java.util.List<Part> parts = new java.util.ArrayList<>();

	/** A material section; `glow` parts (e.g. a dart's vial) are drawn tinted at full brightness. */
	public record Part(Identifier texture, boolean translucent, int[] indices, boolean glow) {
		public Part(Identifier texture, boolean translucent, int[] indices) {
			this(texture, translucent, indices, false);
		}
	}

	private FpModel(JsonObject json) {
		JsonObject sk = json.getAsJsonObject("skeleton");
		JsonArray n = sk.getAsJsonArray("names");
		names = new String[n.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = n.get(i).getAsString();
			index.put(names[i], i);
		}
		parents = ints(sk.getAsJsonArray("parents"));
		bindT = flat(sk.getAsJsonArray("bindT"));
		bindQ = flat(sk.getAsJsonArray("bindQ"));
		bindS = flat(sk.getAsJsonArray("bindS"));
		JsonArray ib = sk.getAsJsonArray("inverseBind");
		inverseBind = new Matrix4f[ib.size()];
		for (int i = 0; i < inverseBind.length; i++) {
			float[] m = floats(ib.get(i).getAsJsonArray());
			inverseBind[i] = new Matrix4f().setTransposed(m); // baked row-major
		}
		JsonObject mesh = json.getAsJsonObject("mesh");
		texture = new Identifier(mesh.get("texture").getAsString());
		positions = floats(mesh.getAsJsonArray("positions"));
		normals = floats(mesh.getAsJsonArray("normals"));
		uvs = floats(mesh.getAsJsonArray("uvs"));
		weights = floats(mesh.getAsJsonArray("weights"));
		joints = ints(mesh.getAsJsonArray("joints"));
		indices = ints(mesh.getAsJsonArray("indices"));
		if (mesh.has("parts")) {
			for (var e : mesh.getAsJsonArray("parts")) {
				JsonObject part = e.getAsJsonObject();
				parts.add(new Part(new Identifier(part.get("texture").getAsString()),
					part.has("translucent") && part.get("translucent").getAsBoolean(), ints(part.getAsJsonArray("indices")),
					part.has("glow") && part.get("glow").getAsBoolean()));
			}
		} else {
			parts.add(new Part(texture, false, indices));
		}
	}

	public static FpModel parse(JsonObject json) {
		return new FpModel(json);
	}

	/** The bone's bind-pose local rotation. */
	public Quaternionf bindRotation(int i) {
		return new Quaternionf(bindQ[i * 4], bindQ[i * 4 + 1], bindQ[i * 4 + 2], bindQ[i * 4 + 3]);
	}

	public int boneCount() {
		return names.length;
	}

	/** World matrices for a pose: rot[i] replaces bone i's bind rotation when non-null. */
	public Matrix4f[] world(Quaternionf[] rot) {
		return world(rot, null);
	}

	public Matrix4f[] world(Quaternionf[] rot, @org.jetbrains.annotations.Nullable org.joml.Vector3f[] pos) {
		Matrix4f[] world = new Matrix4f[names.length];
		Quaternionf q = new Quaternionf();
		for (int i = 0; i < names.length; i++) {
			if (rot != null && rot[i] != null) q.set(rot[i]);
			else q.set(bindQ[i * 4], bindQ[i * 4 + 1], bindQ[i * 4 + 2], bindQ[i * 4 + 3]);
			Matrix4f local = new Matrix4f();
			if (pos != null && pos[i] != null) local.translation(pos[i]);
			else local.translation(bindT[i * 3], bindT[i * 3 + 1], bindT[i * 3 + 2]);
			local
				.rotate(q)
				.scale(bindS[i * 3], bindS[i * 3 + 1], bindS[i * 3 + 2]);
			world[i] = parents[i] < 0 ? local : new Matrix4f(world[parents[i]]).mul(local);
		}
		return world;
	}

	/** Linear-blend skinning into the given output arrays (positions/normals in the frame of `space`). */
	public void skin(Matrix4f[] world, Matrix4f space, float[] outPos, float[] outNrm) {
		Matrix4f[] skinM = new Matrix4f[world.length];
		for (int i = 0; i < world.length; i++) skinM[i] = new Matrix4f(space).mul(world[i]).mul(inverseBind[i]);
		Vector3f p = new Vector3f(), nrm = new Vector3f(), tmp = new Vector3f();
		int verts = positions.length / 3;
		for (int v = 0; v < verts; v++) {
			float px = 0, py = 0, pz = 0, nx = 0, ny = 0, nz = 0;
			for (int k = 0; k < 4; k++) {
				float w = weights[v * 4 + k];
				if (w <= 0) continue;
				Matrix4f m = skinM[joints[v * 4 + k]];
				m.transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], tmp);
				px += tmp.x * w; py += tmp.y * w; pz += tmp.z * w;
				m.transformDirection(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], tmp);
				nx += tmp.x * w; ny += tmp.y * w; nz += tmp.z * w;
			}
			outPos[v * 3] = px; outPos[v * 3 + 1] = py; outPos[v * 3 + 2] = pz;
			p.set(nx, ny, nz);
			if (p.lengthSquared() > 1e-12f) p.normalize();
			outNrm[v * 3] = p.x; outNrm[v * 3 + 1] = p.y; outNrm[v * 3 + 2] = p.z;
		}
	}

	public int vertexCount() {
		return positions.length / 3;
	}

	static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	static float[] flat(JsonArray a) {
		int per = a.get(0).getAsJsonArray().size();
		float[] out = new float[a.size() * per];
		for (int i = 0; i < a.size(); i++) {
			JsonArray row = a.get(i).getAsJsonArray();
			for (int j = 0; j < per; j++) out[i * per + j] = row.get(j).getAsFloat();
		}
		return out;
	}

	static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
		return out;
	}
}
