package com.dishonoredmc.client;

import com.dishonoredmc.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

/** Bend Time's sound, heard by everyone in the world while time is bent; it fades out as soon as time resumes. */
public final class BendTimeSound extends MovingSoundInstance {
	private static BendTimeSound current;
	private static final float VOLUME = 1.2f, FADE_PER_TICK = 0.12f;

	private BendTimeSound() {
		super(ModSounds.BEND_TIME, SoundCategory.PLAYERS, SoundInstance.createRandom());
		this.volume = VOLUME;
		this.relative = true;
		this.attenuationType = SoundInstance.AttenuationType.NONE;
	}

	public static void start() {
		if (current != null) current.setDone();
		current = new BendTimeSound();
		MinecraftClient.getInstance().getSoundManager().play(current);
	}

	@Override
	public void tick() {
		if (ClientPowerState.bendTimeTicks > 0) return;
		this.volume -= FADE_PER_TICK;
		if (this.volume <= 0f) {
			this.volume = 0f;
			this.setDone();
		}
	}
}
