package com.dishonoredmc.client;


import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where Corvo's gripping arm sits relative to a held weapon. In a dev run the values hot-reload from
 * config/dishonored_hands.properties once a second, so the pose can be tuned against live screenshots.
 */
public final class HandTuning {
	public static float x = 0.08F, y = -0.08F, z = 0.0F, rx = 0.0F, ry = 0.0F, rz = 0.0F;
	public static float offX = 0.0F, offY = 0.0F, offZ = 0.0F;
	/** Weapons held in third person: {scale, rotX, rotY, rotZ, x, y, z} per mesh (keys tp_<mesh>_s / rx / ry / rz / x / y / z). */
	private static final java.util.Map<String, float[]> THIRD_PERSON = new java.util.HashMap<>(java.util.Map.of(
		"sword", new float[] {1.5F, -170F, 90F, 0F, 0F, 0F, 0.08F},
		"pistol", new float[] {1.5F, 170F, 90F, 0F, 0F, 0.06F, 0.14F},
		"crossbow", new float[] {1.3F, 160F, 90F, 0F, 0F, 0.09F, 0.12F},
		"grenade", new float[] {1.3F, 20F, 0F, 90F, 0F, 0.01F, 0.02F},
		"spring_razor", new float[] {1.2F, 10F, 0F, 90F, 0F, 0.01F, 0.03F}));

	public static float[] thirdPerson(String mesh) {
		return THIRD_PERSON.getOrDefault(mesh, new float[] {1F, 0F, 0F, 0F, 0F, 0F, 0F});
	}
	/** Bolt seated in the crossbow: offset (m) and rotation (deg) from the crossbow's bolt_jnt. */
	public static float[] boltOffset = {0F, 0.18F, 0F}, boltRot = {0F, 0F, 0F};
	/**
	 * Pistol / crossbow draw: rotation (deg) and position (m, socket space) at the START of the draw. Between draw
	 * frames blendFrom and blendTo (Guns_Ready_Equip) it turns into the END placement (gunRot / bowRot and
	 * gunHeldPos / bowHeldPos), which is also how the gun is held. Tuned in the dev client against Dishonored.
	 */
	public static float[] gunEquipRot = {0F, 0F, 0F}, bowEquipRot = {0F, -10F, -15F};
	public static float[] gunStartPos = {0F, 0F, 0F}, gunHeldPos = {0F, 0F, 0F}, bowStartPos = {0F, 0F, 0F}, bowHeldPos = {0F, 0F, 0F};
	public static float blendFrom = 11F, blendTo = 11F;
	/** Grenade / spring razor in the left palm: START of the draw and held (END) placement, position (m) and rotation (deg). */
	public static float[] grenadeStartPos = {0.03F, 0.04F, -0.007F}, grenadeStartRot = {-50F, -6F, -99F};
	public static float[] grenadeHeldPos = {-0.04F, -0.03F, -0.01F}, grenadeHeldRot = {220F, -50F, 0F};
	public static float[] razorStartPos = {0.05F, 0.006F, -0.04F}, razorStartRot = {4F, 47F, -33F};
	public static float[] razorHeldPos = {-0.007F, -0.063F, 0.01F}, razorHeldRot = {162F, 3F, 60F};
	/** Draw frames (Gadgets_Ready_Equip) over which a gadget turns from START into its held placement. */
	public static float gadgetBlendFrom = 11F, gadgetBlendTo = 11F;
	/**
	 * Spring razor pressed onto a surface (Gadgets_SpringRazor_Deploy): START -> END placement between deploy frames
	 * razorUseFrom and razorUseTo, then back to held between razorBackFrom and razorBackTo.
	 */
	public static float[] razorUseStartPos = {-0.008F, -0.01F, -0.02F}, razorUseStartRot = {20F, 80F, -80F};
	public static float[] razorUsePos = {-0.008F, -0.01F, -0.02F}, razorUseRot = {20F, 3F, 60F};
	public static float razorUseFrom = 1F, razorUseTo = 5F, razorBackFrom = 10F, razorBackTo = 10F;
	/**
	 * After a razor is placed the hand returns to idle over razorReturnFade s, and the razor turns from its RETURN
	 * placement into the held one: the long way round (outside the hand) when razorReturnLong is set.
	 */
	public static float[] razorReturnPos = {-0.008F, -0.01F, -0.02F}, razorReturnRot = {20F, 3F, 60F};
	public static float razorReturnFade = 0.12F;
	public static boolean razorReturnLong = true;
	/** First-person animation speed (dev tuning): 1 = normal, 0.1 = ten times slower, 0 = frozen. */
	public static float animSpeed = 1F;
	/**
	 * Dev tuning: hold a gun / gadget draw on this frame (freezeFrame), the spring razor's use movement on this frame
	 * (freezeUse), or its return to idle at this percentage (freezeReturn). -1 = play normally.
	 */
	public static int freezeFrame = -1, freezeUse = -1, freezeReturn = -1;
	/** Folding blade while blocking: extra rotation (deg) and offset (m) in the hand, blended in with the block pose. */
	public static float[] blockRot = {0F, 0F, 0F}, blockOffset = {0F, 0F, 0F};
	/** Folding blade grip in the right hand (always applied): rotation (deg) and offset (m). */
	public static float[] swordRot = {0F, 0F, 0F}, swordOffset = {0F, -0.008F, 0.01F};
	/** Tuning aid: hold the blocking pose without holding right-click. */
	public static boolean blockPreview = false;
	/** Sprinting marked hand: upper-arm pull-back x/y/z, pump x/y/z, forearm pump (degrees), and pump speed. */
	public static float[] runArm = {15F, 0F, 0F, 0F, 0F, 0F, 0F};
	public static float runFreq = 1F;
	/** Horizontal field of view (deg) the first-person arms are drawn with. 110 matches the player's Dishonored footage (sword grip position measured against the rig). */
	public static float fpFov = 110F;
	/** Drop arm triangles closer than this to the eye (m); 0 = off. */
	public static float nearClip = 0F;
	public static float[] gunRot = {-70F, -90F, 80F}, bowRot = {-120F, -100F, 15F};

	private static long nextCheck;
	private static long lastModified = -1;

	private HandTuning() {}

	public static void maybeReload() {
		long now = System.currentTimeMillis();
		if (now < nextCheck) return;
		nextCheck = now + 1000;
		Path file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("dishonored_hands.properties");
		try {
			if (!Files.exists(file)) return;
			long modified = Files.getLastModifiedTime(file).toMillis();
			if (modified == lastModified) return;
			lastModified = modified;
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(file)) {
				p.load(r);
			}
			x = f(p, "x", x); y = f(p, "y", y); z = f(p, "z", z);
			rx = f(p, "rx", rx); ry = f(p, "ry", ry); rz = f(p, "rz", rz);
			offX = f(p, "offX", offX); offY = f(p, "offY", offY); offZ = f(p, "offZ", offZ);
			for (int i = 0; i < 3; i++) {
				String a = "xyz".substring(i, i + 1);
				gunRot[i] = f(p, "gunr" + a, gunRot[i]);
				bowRot[i] = f(p, "bowr" + a, bowRot[i]);
				gunEquipRot[i] = f(p, "guner" + a, gunEquipRot[i]);
				gunStartPos[i] = f(p, "gunstart" + a, gunStartPos[i]);
				gunHeldPos[i] = f(p, "gunheld" + a, gunHeldPos[i]);
				bowStartPos[i] = f(p, "bowstart" + a, bowStartPos[i]);
				bowHeldPos[i] = f(p, "bowheld" + a, bowHeldPos[i]);
				boltOffset[i] = f(p, "bolt" + a, boltOffset[i]);
				boltRot[i] = f(p, "boltr" + a, boltRot[i]);
				bowEquipRot[i] = f(p, "bower" + a, bowEquipRot[i]);
			}
			animSpeed = Math.max(0F, f(p, "animspeed", animSpeed));
			freezeFrame = (int) f(p, "freezeframe", -1F);
			blendFrom = f(p, "blendfrom", blendFrom);
			blendTo = f(p, "blendto", blendTo);
			gadgetBlendFrom = f(p, "gadgetblendfrom", gadgetBlendFrom);
			gadgetBlendTo = f(p, "gadgetblendto", gadgetBlendTo);
			razorUseFrom = f(p, "razorusefrom", razorUseFrom);
			razorUseTo = f(p, "razoruseto", razorUseTo);
			razorBackFrom = f(p, "razorbackfrom", razorBackFrom);
			razorBackTo = f(p, "razorbackto", razorBackTo);
			freezeUse = (int) f(p, "freezeuse", -1F);
			freezeReturn = (int) f(p, "freezereturn", -1F);
			razorReturnLong = f(p, "razorreturnlong", razorReturnLong ? 1F : 0F) != 0F;
			razorReturnFade = Math.max(0.01F, f(p, "razorreturnfade", razorReturnFade));
			for (int i = 0; i < 3; i++) {
				String a = "xyz".substring(i, i + 1);
				razorReturnPos[i] = f(p, "razorreturn" + a, razorReturnPos[i]);
				razorReturnRot[i] = f(p, "razorreturnr" + a, razorReturnRot[i]);
			}
			for (int i = 0; i < 3; i++) {
				String a = "xyz".substring(i, i + 1);
				razorUsePos[i] = f(p, "razoruseend" + a, razorUsePos[i]);
				razorUseRot[i] = f(p, "razoruseendr" + a, razorUseRot[i]);
				razorUseStartPos[i] = f(p, "razorusestart" + a, razorUseStartPos[i]);
				razorUseStartRot[i] = f(p, "razorusestartr" + a, razorUseStartRot[i]);
			}
			for (int i = 0; i < 3; i++) {
				String a = "xyz".substring(i, i + 1);
				grenadeStartPos[i] = f(p, "grenadestart" + a, grenadeStartPos[i]);
				grenadeStartRot[i] = f(p, "grenadestartr" + a, grenadeStartRot[i]);
				grenadeHeldPos[i] = f(p, "grenadeheld" + a, grenadeHeldPos[i]);
				grenadeHeldRot[i] = f(p, "grenadeheldr" + a, grenadeHeldRot[i]);
				razorStartPos[i] = f(p, "razorstart" + a, razorStartPos[i]);
				razorStartRot[i] = f(p, "razorstartr" + a, razorStartRot[i]);
				razorHeldPos[i] = f(p, "razorheld" + a, razorHeldPos[i]);
				razorHeldRot[i] = f(p, "razorheldr" + a, razorHeldRot[i]);
			}
			for (int i = 0; i < 3; i++) {
				String a = "xyz".substring(i, i + 1);
				blockRot[i] = f(p, "blockr" + a, blockRot[i]);
				blockOffset[i] = f(p, "block" + a, blockOffset[i]);
				swordRot[i] = f(p, "swordr" + a, swordRot[i]);
				swordOffset[i] = f(p, "sword" + a, swordOffset[i]);
			}
			com.dishonoredmc.client.hud.HudArt.hudScale = Math.max(0.5F, f(p, "hudscale", com.dishonoredmc.client.hud.HudArt.hudScale));
			com.dishonoredmc.client.hud.HudArt.hudPadLeft = f(p, "hudpadleft", f(p, "hudpad", com.dishonoredmc.client.hud.HudArt.hudPadLeft));
			com.dishonoredmc.client.hud.HudArt.hudPadTop = f(p, "hudpadtop", f(p, "hudpad", com.dishonoredmc.client.hud.HudArt.hudPadTop));
			com.dishonoredmc.client.hud.HudArt.vignetteZoom = Math.max(1F, f(p, "vignettezoom", com.dishonoredmc.client.hud.HudArt.vignetteZoom));
			String[] tk = {"s", "rx", "ry", "rz", "x", "y", "z"};
			for (var e : THIRD_PERSON.entrySet()) {
				for (int i = 0; i < tk.length; i++) e.getValue()[i] = f(p, "tp_" + e.getKey() + "_" + tk[i], e.getValue()[i]);
			}
			blockPreview = f(p, "blockpreview", 0F) != 0F;
			String[] rk = {"runbackx", "runbacky", "runbackz", "runpumpx", "runpumpy", "runpumpz", "runfore"};
			for (int i = 0; i < rk.length; i++) runArm[i] = f(p, rk[i], runArm[i]);
			runFreq = f(p, "runfreq", runFreq);
			fpFov = Math.max(40F, Math.min(120F, f(p, "fpfov", fpFov)));
			nearClip = Math.max(0F, f(p, "nearclip", nearClip));
		} catch (Exception ignored) {
		}
	}

	private static float f(Properties p, String key, float fallback) {
		try {
			return Float.parseFloat(p.getProperty(key, Float.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
