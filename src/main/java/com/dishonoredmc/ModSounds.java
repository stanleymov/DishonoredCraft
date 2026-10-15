package com.dishonoredmc;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Every sound the mod plays. The shipped sounds.json points each one at a vanilla sound; the private
 * "Dishonored Original Assets" pack built by tools/assets/build_pack.py replaces them with the real
 * audio from the player's own Dishonored install.
 */
public final class ModSounds {
	public static final SoundEvent BLINK = register("power.blink");
	public static final SoundEvent BLINK_ARRIVE = register("power.blink_arrive");
	public static final SoundEvent DARK_VISION_ON = register("power.dark_vision_on");
	public static final SoundEvent DARK_VISION_OFF = register("power.dark_vision_off");
	public static final SoundEvent BEND_TIME = register("power.bend_time");
	public static final SoundEvent BEND_TIME_END = register("power.bend_time_end");
	public static final SoundEvent POSSESSION_IN = register("power.possession_in");
	public static final SoundEvent POSSESSION_OUT = register("power.possession_out");
	public static final SoundEvent SWARM = register("power.swarm");
	public static final SoundEvent SWARM_RATS = register("power.swarm_rats");
	public static final SoundEvent WINDBLAST = register("power.windblast");
	public static final SoundEvent SHADOW_KILL = register("power.shadow_kill");
	public static final SoundEvent POWER_SWITCH = register("power.switch");
	public static final SoundEvent POWER_EMPTY = register("power.empty");
	public static final SoundEvent SUPER_JUMP = register("power.super_jump");
	public static final SoundEvent MANTLE = register("player.mantle");
	public static final SoundEvent SLIDE = register("player.slide");
	public static final SoundEvent PISTOL_FIRE = register("weapon.pistol_fire");
	public static final SoundEvent PISTOL_EMPTY = register("weapon.pistol_empty");
	public static final SoundEvent CROSSBOW_FIRE = register("weapon.crossbow_fire");
	public static final SoundEvent CROSSBOW_RELOAD = register("weapon.crossbow_reload");
	public static final SoundEvent GRENADE_EXPLODE = register("weapon.grenade_explode");
	public static final SoundEvent GRENADE_PIN = register("weapon.grenade_pin");
	public static final SoundEvent RAZOR_INSTALL = register("weapon.razor_install");
	public static final SoundEvent RAZOR_ARMING = register("weapon.razor_arming");
	public static final SoundEvent RAZOR_SHOT = register("weapon.razor_shot");
	public static final SoundEvent SWORD_WHOOSH = register("weapon.sword_whoosh");
	public static final SoundEvent SWORD_PARRY = register("weapon.sword_parry");
	public static final SoundEvent SWORD_UNFOLD = register("weapon.sword_unfold");
	public static final SoundEvent ELIXIR_MANA = register("item.elixir_mana");
	public static final SoundEvent ELIXIR_HEALTH = register("item.elixir_health");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		return Registry.register(Registries.SOUND_EVENT, DishonoredMod.id(name), SoundEvent.of(DishonoredMod.id(name)));
	}

	public static void init() {}
}
