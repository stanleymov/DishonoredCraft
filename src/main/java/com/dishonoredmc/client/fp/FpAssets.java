package com.dishonoredmc.client.fp;

import com.dishonoredmc.DishonoredMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Corvo's real first-person rig, weapons and Dishonored's original animations. The mod ships none of this:
 * it is read from the private "Dishonored Original Assets" pack built by tools/assets/bake_anims.py.
 */
public final class FpAssets implements SynchronousResourceReloader {
	public static final String[] WEAPONS = {"sword", "pistol", "crossbow", "bolt", "bolt_sleep", "bolt_flare"};
	public static final String[] WORLD = {"rat", "spring_razor", "grenade", "elixir_mana", "elixir_health"};

	private static final Map<String, FpModel> world = new HashMap<>();

	private static FpModel arms;
	private static final Map<String, FpModel> weapons = new HashMap<>();
	private static final Map<String, FpClip> clips = new HashMap<>();

	@Override
	public void reload(ResourceManager manager) {
		arms = null;
		weapons.clear();
		clips.clear();
		world.clear();
		// World objects (rats, gadgets, elixirs) load even without the first-person rig.
		for (String w : WORLD) {
			JsonObject wj = read(manager, "fp/world_" + w + ".json");
			if (wj != null) {
				try {
					world.put(w, FpModel.parse(wj));
				} catch (Exception e) {
					DishonoredMod.LOGGER.warn("Bad world mesh {}: {}", w, e.toString());
				}
			}
		}
		JsonObject worldAnims = read(manager, "fp/world_anims.json");
		if (worldAnims != null) for (var e : worldAnims.entrySet()) clips.put(e.getKey(), new FpClip(e.getKey(), e.getValue().getAsJsonObject()));
		JsonObject rig = read(manager, "fp/rig.json");
		JsonObject anims = read(manager, "fp/anims.json");
		if (rig == null || anims == null) return;
		try {
			arms = FpModel.parse(rig);
			for (String w : WEAPONS) {
				JsonObject wj = read(manager, "fp/weapon_" + w + ".json");
				if (wj != null) weapons.put(w, FpModel.parse(wj));
			}
			for (var e : anims.entrySet()) clips.put(e.getKey(), new FpClip(e.getKey(), e.getValue().getAsJsonObject()));
			DishonoredMod.LOGGER.info("Dishonored first-person rig loaded: {} bones, {} weapons, {} animations",
				arms.boneCount(), weapons.size(), clips.size());
		} catch (Exception e) {
			DishonoredMod.LOGGER.warn("Could not load the Dishonored first-person rig: {}", e.toString());
			arms = null;
		}
	}

	@Nullable
	private static JsonObject read(ResourceManager manager, String path) {
		Optional<Resource> res = manager.getResource(DishonoredMod.id(path));
		if (res.isEmpty()) return null;
		try (Reader r = res.get().getReader()) {
			return JsonParser.parseReader(r).getAsJsonObject();
		} catch (Exception e) {
			DishonoredMod.LOGGER.warn("Bad {}: {}", path, e.toString());
			return null;
		}
	}

	public static boolean available() {
		return arms != null && clips.containsKey("Empty_Idle");
	}

	public static FpModel arms() {
		return arms;
	}

	@Nullable
	public static FpModel weapon(String name) {
		return weapons.get(name);
	}

	@Nullable
	public static FpModel world(String name) {
		return world.get(name);
	}

	@Nullable
	public static FpClip clip(String name) {
		return clips.get(name);
	}
}
