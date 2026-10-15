package com.dishonoredmc.client.fp;

import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.item.FoldingBladeItem;
import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.power.Power;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Drives Dishonored's original first-person animations from Minecraft state, like the game's own anim tree:
 * the right arm carries the folding blade, the left arm holds powers, the pistol/crossbow or a gadget.
 */
public final class FpController {
	public enum Right { NONE, SWORD }

	public enum Left { POWERS, PISTOL, CROSSBOW, GADGET }

	static final FpSlot base = new FpSlot();
	static final FpSlot right = new FpSlot();
	static final FpSlot left = new FpSlot();
	static final FpSlot swordBones = new FpSlot();
	static final FpSlot gunBones = new FpSlot();
	/** Takedown clips (assassinations, the choke hold) that override both arms. */
	static final FpSlot overlay = new FpSlot();
	/** When the current takedown clip is cut off (it fades out over FADE_OUT before this), in seconds. */
	private static float overlayStart, overlayEnd = -1;
	private static final float FADE_IN = 0.2f, FADE_OUT = 0.4f;
	/** Seconds into the frontal stab when its reverse grip turns back to normal (blade off screen then). */
	private static final float FRONT_GRIP_SWAP = 1.2f;

	static Right rightMode = Right.NONE;
	static Left leftMode = Left.POWERS;
	private static boolean initialized;
	private static ClientPlayerEntity lastPlayer;
	private static int sprintHold, moveHold;
	/** Player age when the attack button last triggered (see DishonoredClient). */
	public static int lastAttackInput = -1000;
	/** 0..1: the blade's blocking adjustment, eased in pose(). */
	static float blockWeight;
	private static float blockTarget;
	/** 0..1: how far the powers hand has pulled back for sprinting (eased in pose()). */
	private static float sprintPull, lastPoseNow = -1;
	private static final float SPRINT_PULL_MAX = 0.55f, SPRINT_PULL_RATE = 6f;
	private static boolean wasSwinging;
	private static int lastSwingTicks;
	private static boolean wasBlocking;
	private static int attackIndex;
	private static boolean pistolCooling, bowCooling, grenadeCooling, razorCooling;
	private static boolean wasSneaking;
	/** The crouched hand-over after a choke has started (see tick). */
	private static boolean chokeSettled;
	/** The gun / gadget is put away for a takedown (both hands are busy) and drawn again once it ends. */
	private static boolean leftPutAway;
	/**
	 * During a takedown: the takedown's left hand has been on screen, and has since left it. From that moment
	 * (leftRelease, animation time) the left arm leaves the takedown and draws the gun / gadget again while the
	 * blade arm finishes.
	 */
	private static boolean leftSeen;
	private static float leftRelease = -1;
	private static final float LEFT_RELEASE_FADE = 0.15f;

	private static final String[] READY_ATTACKS = {
		"Sword_Ready_AttackRight_A_Small", "Sword_Ready_AttackLeft_A_Small", "Sword_Ready_AttackRight_B_Small", "Sword_Ready_AttackLeft_B_Small"};

	private FpController() {}

	private static float clockRaw = -1, clock;

	/**
	 * Animation clock in seconds. It follows the game's ticks, scaled by HandTuning.animSpeed (dev only), so the
	 * hands can be slowed down or frozen while tuning weapon poses.
	 */
	public static float now(float tickDelta) {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		if (p == null) return 0f;
		float raw = (p.age + tickDelta) / 20f;
		// Dev tuning: hold one frame of a gun / gadget draw (freezeframe in the tuning file).
		if (com.dishonoredmc.client.HandTuning.freezeFrame >= 0 && clockRaw >= 0) {
			float t = left.freezeClock(com.dishonoredmc.client.HandTuning.freezeFrame);
			if (!Float.isNaN(t)) {
				clock = t;
				clockRaw = raw;
				return clock;
			}
		}
		if (com.dishonoredmc.client.HandTuning.freezeReturn >= 0 && clockRaw >= 0
			&& "Gadgets_SpringRazor_Deploy".equals(left.fadingFrom(clock))) {
			clock = left.fadeClock(Math.min(99, com.dishonoredmc.client.HandTuning.freezeReturn) / 100f);
			clockRaw = raw;
			return clock;
		}
		if (com.dishonoredmc.client.HandTuning.freezeUse >= 0 && clockRaw >= 0) {
			float t = left.freezeClock("Gadgets_SpringRazor_Deploy", com.dishonoredmc.client.HandTuning.freezeUse);
			if (!Float.isNaN(t)) {
				clock = t;
				clockRaw = raw;
				return clock;
			}
		}
		float d = raw - clockRaw;
		if (clockRaw < 0 || d < -1f || d > 5f) {
			clock = raw;
			clockRaw = raw;
		} else if (d > 0f) {
			float speed = com.dishonoredmc.client.HandTuning.animSpeed;
			// Game time runs slow while the weapon wheel is open or Redirective Blink holds time still; the hands
			// keep real-time speed so a cast or draw still finishes.
			clock += d * speed / com.dishonoredmc.TimeScale.factor();
			clockRaw = raw;
		}
		return clock;
	}



	static boolean isLeftBone(String b) {
		return b.contains("_L_") || b.endsWith("_L_jnt") || b.endsWith("_L_Jnt");
	}

	static boolean isRightBone(String b) {
		return b.contains("_R_") || b.endsWith("_R_jnt") || b.endsWith("_R_Jnt");
	}

	public static boolean isFpItem(ItemStack stack) {
		Item i = stack.getItem();
		return i == ModItems.FOLDING_BLADE || i == ModItems.PISTOL || i == ModItems.WRIST_BOW || i == ModItems.GRENADE || i == ModItems.SPRING_RAZOR;
	}

	private static Left leftFor(ClientPlayerEntity p) {
		for (ItemStack s : new ItemStack[] {p.getMainHandStack(), p.getOffHandStack()}) {
			if (s.isOf(ModItems.PISTOL)) return Left.PISTOL;
			if (s.isOf(ModItems.WRIST_BOW)) return Left.CROSSBOW;
		}
		ItemStack main = p.getMainHandStack();
		if (main.isOf(ModItems.GRENADE) || main.isOf(ModItems.SPRING_RAZOR)) return Left.GADGET;
		return Left.POWERS;
	}

	private static String leftPrefix() {
		return switch (leftMode) {
			case POWERS -> "Powers";
			case PISTOL, CROSSBOW -> "Guns";
			case GADGET -> "Gadgets";
		};
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p == null || !FpAssets.available()) {
			initialized = false;
			return;
		}
		// A respawn (or dimension change) creates a new player whose age restarts at 0: start the animations over.
		if (p != lastPlayer) {
			lastPlayer = p;
			initialized = false;
			for (FpSlot s : new FpSlot[] {base, right, left, swordBones, gunBones, overlay}) s.stop();
			rightMode = Right.NONE;
			leftMode = Left.POWERS;
			clockRaw = -1;
		}
		float now = now(0f);
		// Horizontal speed only (not "on ground"): sprint-jumping keeps the run cycle going instead of
		// restarting it on every jump.
		// Bumping into a wall stops you for a tick or two; hold the walk/run cycle through that.
		if (p.getVelocity().horizontalLengthSquared() > 0.0025) moveHold = 5;
		else if (moveHold > 0) moveHold--;
		boolean moving = moveHold > 0;
		// Sprint flickers off for a tick at a time (bumping into things, landing); hold it briefly so the run
		// cycle isn't restarted, which made the blade bounce.
		if (p.isSprinting() && moving) sprintHold = 6;
		else if (sprintHold > 0) sprintHold--;
		boolean sprinting = sprintHold > 0;
		boolean sneaking = p.isInSneakingPose();

		if (!initialized) {
			base.play(now, "Empty_Idle");
			right.play(now, "Empty_Idle");
			left.play(now, "Powers_Idle");
			swordBones.play(now, "sword:Sword_Ready_Idle");
			initialized = true;
		}

		// Right arm: draw / sheathe the folding blade. It stays out with a gun or gadget in the left hand.
		Right r = FoldingBladeItem.bladeOut(p) ? Right.SWORD : Right.NONE;
		if (r != rightMode) {
			if (r == Right.SWORD) {
				right.play(now, swordLoop(moving, sprinting, sneaking), "Sword_Ready_Equip");
				swordBones.play(now, "sword:Sword_Ready_Idle", "sword:Sword_Ready_Equip");
			} else {
				right.play(now, "Empty_Idle", "Sword_Ready_UnEquip");
				swordBones.play(now, "sword:Sword_Ready_Idle", "sword:Sword_Ready_UnEquip");
			}
			rightMode = r;
		}
		if (rightMode == Right.SWORD) {
			// Crouching raises the blade (Sneak stance), as in Dishonored.
			if (sneaking != wasSneaking && !right.playingOneShot(now)) {
				right.play(now, swordLoop(moving, sprinting, sneaking), sneaking ? "Sword_Sneak_In" : "Sword_Sneak_Out");
			}
			boolean blocking = (p.isUsingItem() && p.getActiveItem().getItem() instanceof FoldingBladeItem)
				|| com.dishonoredmc.client.ControlSwap.toolBlocking() || com.dishonoredmc.client.HandTuning.blockPreview;
			blockTarget = blocking ? 1f : 0f;
			if (blocking && !wasBlocking) right.play(now, "Sword_Ready_ParryIdle", "Sword_Ready_ParryIdle_In");
			else if (!blocking && wasBlocking) right.play(now, swordLoop(moving, sprinting, sneaking), "Sword_Ready_ParryIdle_Out");
			else if (!blocking) right.setLoop(now, swordLoop(moving, sprinting, sneaking));
			wasBlocking = blocking;

			// Only swings from the attack button are attacks: right-clicking doors, levers or buttons swings the
			// hand too, and must not play a slash.
			boolean swingStart = p.handSwinging && (!wasSwinging || p.handSwingTicks < lastSwingTicks)
				&& p.age - lastAttackInput >= 0 && p.age - lastAttackInput <= 2;
			// A slash waits for the previous slash to be half done, but cuts straight through anything else (lowering
			// the guard, crouching in or out), crossfading from wherever the arm is.
			String cur = right.current();
			boolean midSlash = cur != null && cur.contains("_Attack") && right.progress(now) <= 0.5f;
			if (swingStart && !midSlash && !blocking) {
				String attack = sneaking ? "Sword_Sneak_Attack_Small" : READY_ATTACKS[attackIndex++ % READY_ATTACKS.length];
				right.playAt(now, swingSpeed(p), swordLoop(moving, sprinting, sneaking), attack);
				// The marked hand reacts to the slash.
				String side = attack.contains("Left") ? "Left" : "Right";
				// (Dishonored has no gadget version: a grenade or spring razor stays still in the palm.)
				if (!left.playingOneShot(now) && leftMode != Left.GADGET) left.play(now, leftLoop(moving, sprinting), leftPrefix() + "_SwordSlash_" + side);
			}
		}
		wasSwinging = p.handSwinging;
		lastSwingTicks = p.handSwingTicks;
		wasSneaking = sneaking;

		// Left arm: powers, pistol, crossbow or gadget.
		Left l = leftFor(p);
		// Takedowns (backstab, drop assassination, choke) take both hands: the gun or gadget is hidden while one
		// plays, the hand waits lowered (the first frame of its draw, so the takedown fades into that rather than
		// flashing the hand up), and the item is pulled out again once the takedown is over.
		boolean takedown = overlayWeight(now) > 0f;
		if (takedown && !leftPutAway) {
			leftPutAway = true;
			leftSeen = false;
			leftRelease = -1;
			leftMode = l;
			if (leftMode != Left.POWERS) left.holdFirstFrame(now, leftPrefix() + "_Ready_Equip");
		}
		if (leftPutAway && leftRelease < 0) {
			if (l != leftMode) {
				leftMode = l;
				if (leftMode != Left.POWERS) left.holdFirstFrame(now, leftPrefix() + "_Ready_Equip");
			}
			if (!takedown) releaseLeft(now, moving, sprinting); // the hand never left the screen: draw at the end
		}
		if (leftPutAway && leftRelease >= 0 && !takedown) {
			leftPutAway = false;
			leftRelease = -1;
		}
		if (leftHidden()) {
			// The left arm is still in the takedown: leave it alone until it is released.
		} else if (l != leftMode) {
			leftMode = l;
			equipLeft(now, moving, sprinting);
		} else {
			left.setLoop(now, leftLoop(moving, sprinting));
		}

		// Weapon use, detected from the item cooldown each weapon starts when it fires.
		var cd = p.getItemCooldownManager();
		boolean pc = cd.isCoolingDown(ModItems.PISTOL), bc = cd.isCoolingDown(ModItems.WRIST_BOW);
		boolean gc = cd.isCoolingDown(ModItems.GRENADE), rc = cd.isCoolingDown(ModItems.SPRING_RAZOR);
		if (pc && !pistolCooling && leftMode == Left.PISTOL) {
			left.play(now, leftLoop(moving, sprinting), "Pistol_Fire", "Pistol_FastReload");
			gunBones.play(now, "pistol:CorvoGun_Idle", "pistol:CorvoGun_Fire_Hammer", "pistol:CorvoGun_FastReload");
		}
		if (bc && !bowCooling && leftMode == Left.CROSSBOW) {
			left.play(now, leftLoop(moving, sprinting), "Crossbow_Fire", "Crossbow_RapidReload");
			// The crossbow's own fire clip ends unloaded; its reload clip draws the string and seats a new bolt.
			gunBones.play(now, "crossbow:Crossbow_Idle", "crossbow:Crossbow_Fire", "crossbow:Crossbow_RapidReload");
		}
		if (gc && !grenadeCooling && leftMode == Left.GADGET) left.play(now, leftLoop(moving, sprinting), "Gadgets_Grenade_Deploy");
		if (rc && !razorCooling && leftMode == Left.GADGET) left.play(now, leftLoop(moving, sprinting), "Gadgets_SpringRazor_Deploy");
		pistolCooling = pc;
		bowCooling = bc;
		grenadeCooling = gc;
		razorCooling = rc;

		// The choke's release ends in the standing ready pose. Crouched, the blade arm hands over through Dishonored's
		// own crouch-in clip (it starts from that pose) as the choke fades, instead of blending straight to the
		// crouched guard.
		String take = overlay.current();
		if (take != null && take.equals("Sword_Choke_Win_Master") && overlayWeight(now) > 0f && now >= overlayEnd - FADE_OUT) {
			if (!chokeSettled && rightMode == Right.SWORD && sneaking) {
				right.play(now, swordLoop(moving, sprinting, true), "Sword_Sneak_In");
			}
			chokeSettled = true;
		} else if (take == null || !take.equals("Sword_Choke_Win_Master")) {
			chokeSettled = false;
		}

		base.tick(now);
		right.tick(now);
		left.tick(now);
		swordBones.tick(now);
		gunBones.tick(now);
		overlay.tick(now);
		steerView(p, now);
		if (!overlay.active(now) || now >= overlayEnd) overlay.stop();
	}

	/** The takedown is done with the left hand: it draws the gun / gadget again (or goes back to the marked hand). */
	private static void releaseLeft(float now, boolean moving, boolean sprinting) {
		leftRelease = now;
		if (leftMode != Left.POWERS) equipLeft(now, moving, sprinting);
		else left.play(now, leftLoop(moving, sprinting));
	}

	/**
	 * Called by the renderer every frame with the arms' bone matrices: during a takedown, notices the takedown's
	 * left hand leaving the screen (after having been on it) and releases the left arm right then.
	 */
	static void trackLeftHand(FpModel arms, org.joml.Matrix4f[] world, org.joml.Matrix4f view, float now) {
		if (!leftPutAway || leftRelease >= 0) return;
		// Only the assassinations hand the left arm back early. The choke holds the victim with both hands until it
		// is over (its hand dips out of view mid-hold), so the gun / gadget waits for the choke to end.
		String take = overlay.current();
		if (take == null || !take.contains("Assassination")) return;
		Integer hand = arms.index.get("hand_L_jnt");
		if (hand == null) return;
		org.joml.Vector3f p = new org.joml.Matrix4f(view).mul(world[hand]).getTranslation(new org.joml.Vector3f());
		var window = MinecraftClient.getInstance().getWindow();
		float aspect = (float) window.getFramebufferWidth() / Math.max(1, window.getFramebufferHeight());
		float t = (float) Math.tan(Math.toRadians(35)); // Minecraft's 70 degree first-person view
		float depth = -p.z;
		float sx = depth > 0.01f ? Math.abs(p.x / depth) / (t * aspect) : 9f, sy = depth > 0.01f ? Math.abs(p.y / depth) / t : 9f;
		// 1 = the screen edge; the wrist counts as gone a bit past it, so the whole hand is out of view.
		if (sx < 0.9f && sy < 0.9f) leftSeen = true;
		else if (leftSeen && (sx > 1.2f || sy > 1.2f)) {
			ClientPlayerEntity pl = MinecraftClient.getInstance().player;
			boolean moving = pl != null && pl.getVelocity().horizontalLengthSquared() > 0.0025;
			releaseLeft(now, moving, sprintHold > 0);
		}
	}

	/** Draw the left hand's item: Dishonored's equip clip (and the gun's own). */
	private static void equipLeft(float now, boolean moving, boolean sprinting) {
		left.play(now, leftLoop(moving, sprinting), leftPrefix() + "_Ready_Equip");
		// Grenades and spring razors glide through every transition (draw, settle, throw); the guns snap.
		left.alwaysSmooth = leftMode == Left.GADGET;
		if (leftMode == Left.GADGET) left.blendSmoothly();
		if (leftMode == Left.PISTOL) gunBones.play(now, null, "pistol:CorvoGun_Equip");
		if (leftMode == Left.CROSSBOW) gunBones.play(now, null, "crossbow:Crossbow_Equip");
		// Like Dishonored, a gun or gadget draw cuts straight to its first frame. A blend from the marked hand
		// turned the weapon socket 159 degrees and swung the gun / gadget out of the hand.
		if (leftMode == Left.PISTOL || leftMode == Left.CROSSBOW) {
			left.cut();
			gunBones.cut();
		}
		if (leftMode == Left.GADGET) left.cut();
	}

	/** Seconds into a sword slash (at normal speed) when the blade lands. */
	public static final float SLASH_HIT = 0.22f;

	/**
	 * How fast the slash clips play: the clip reaches the point where the next swing may start (half way) just as
	 * the sword is ready again, so swings and their animation keep one rhythm (and Whirlwind speeds both up).
	 */
	public static float swingSpeed(net.minecraft.entity.player.PlayerEntity p) {
		float readyTicks = p.getAttackCooldownProgressPerTick(); // ticks for a full swing at the current attack speed
		float speed = (0.52f * 20f) / Math.max(1f, readyTicks);
		return Math.max(0.8f, Math.min(2.5f, speed));
	}

	private static String swordLoop(boolean moving, boolean sprinting, boolean sneaking) {
		if (sneaking) return moving ? "Sword_Sneak_Walk" : "Sword_Sneak_Idle";
		if (sprinting) return "Sword_Ready_Sprint";
		return moving ? "Sword_Ready_Walk" : "Sword_Ready_Idle";
	}

	private static String leftLoop(boolean moving, boolean sprinting) {
		// Aiming Blink: the marked hand holds Dishonored's aim loop (a twitching, charged hand) until release.
		if (leftMode == Left.POWERS && ClientPowerState.aimingBlink) return "Powers_Cast_Blink_Loop";
		String prefix = leftPrefix();
		// Powers_Sprint barely moves (a held, half-closed hand), so the powers hand keeps its walk cycle.
		if (sprinting && prefix.equals("Guns")) return prefix + "_Sprint";
		if (moving) return prefix + "_Walk";
		return prefix + "_Idle";
	}

	// --- Full-body moments ---

	/** Takedown animations sent by the server (see AnimEventPayload). Each is cut short after the blow. */
	public static void onAnimEvent(int event) {
		if (!FpAssets.available()) return;
		float now = now(0f);
		switch (event) {
			// Dishonored's DropFront clip moves the whole body (root translation this rig doesn't carry), which
			// leaves the arms off screen; the frontal stab reads the same on landing and keeps the hands in frame.
			case com.dishonoredmc.net.AnimEventPayload.DROP_ASSASSINATION -> takedown(now, 1000f, null, "Sword_Ready_Assassination_Front_Master"); // full clip
			case com.dishonoredmc.net.AnimEventPayload.BACK_ASSASSINATION -> takedown(now, 1000f, null, "Sword_Ready_Assassination_Back_Master"); // full clip, as tuned in game
			case com.dishonoredmc.net.AnimEventPayload.FRONT_ASSASSINATION -> takedown(now, 1.0f, null, "Sword_Ready_Assassination_Front_Master");
			case com.dishonoredmc.net.AnimEventPayload.CHOKE_START -> takedown(now, 1000f, "Sword_Choke_Loop_Master", "Sword_Choke_In_Master");
			case com.dishonoredmc.net.AnimEventPayload.CHOKE_WIN -> takedown(now, 1000f, null, "Sword_Choke_Win_Master"); // full clip: ends in the ready pose
			case com.dishonoredmc.net.AnimEventPayload.CHOKE_CANCEL -> overlayEnd = Math.min(overlayEnd, now + FADE_OUT);
			default -> {}
		}
	}

	/** View pitch the camera eases to during a takedown (level, like Dishonored), or NaN when not steering. */
	private static float pitchTarget = Float.NaN;
	private static final float STEER_TIME = 0.3f;

	/** Called every client tick: eases the player's pitch toward level while a takedown plays. */
	static void steerView(ClientPlayerEntity p, float now) {
		if (Float.isNaN(pitchTarget)) return;
		// Snap the view toward the victim at the start of the takedown, then let go so the player can look around.
		if (overlayWeight(now) <= 0f || now - overlayStart > STEER_TIME) {
			pitchTarget = Float.NaN;
			return;
		}
		float pitch = p.getPitch();
		p.setPitch(pitch + (pitchTarget - pitch) * 0.35f);
	}

	private static void takedown(float now, float length, @Nullable String loop, String clip) {
		pitchTarget = 8f;
		if (!overlay.active(now) || overlayEnd < now) overlayStart = now;
		overlay.play(now, loop, clip);
		// Without a loop after it, the takedown is over when its clip is: it fades out over the clip's last part
		// instead of cutting to idle when the clip runs out.
		FpClip c = FpAssets.clip(clip);
		overlayEnd = now + (loop == null && c != null ? Math.min(length, c.duration()) : length);
	}

	/** Blend weight of the takedown layer: fades in, holds, fades out before the cut. */
	static float overlayWeight(float now) {
		if (!overlay.active(now) || now >= overlayEnd) return 0f;
		float in = Math.min(1f, (now - overlayStart) / FADE_IN);
		float out = Math.min(1f, (overlayEnd - now) / FADE_OUT);
		float w = Math.max(0f, Math.min(in, out));
		return w * w * (3 - 2 * w); // eased: the hands glide into and out of a takedown instead of cutting
		
	}

	/**
	 * The takedowns animate Dishonored's whole body around a victim; only the arms are used, so the spine
	 * and head never lean far enough to show the cut edge of the first-person arms mesh at the shoulders.
	 */
	static boolean isArmBone(String b) {
		return (isLeftBone(b) || isRightBone(b)) && !b.contains("leg") && !b.contains("foot") && !b.contains("toe")
			&& !b.startsWith("shoulder_");
	}

	// --- Power events from DishonoredClient ---

	public static void onCast(Power power) {
		if (!FpAssets.available() || leftMode != Left.POWERS) return;
		float now = now(0f);
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		// Short of mana, the server drinks a Piero's Remedy from the inventory and casts anyway: only with none left
		// does the hand show it is out of mana.
		if (p != null && !p.isCreative() && ClientPowerState.mana < power.minMana && p.getInventory().count(ModItems.PIEROS_REMEDY) == 0) {
			left.play(now, "Powers_Idle", "Powers_OutOfMana");
			return;
		}
		String clip = switch (power) {
			case BEND_TIME -> "Powers_Cast_BendTime";
			case WINDBLAST -> "Powers_Cast_Windblast";
			case DEVOURING_SWARM -> "Powers_Cast_Swarm";
			case DARK_VISION -> "Powers_Switch";
			default -> null;
		};
		if (power == Power.POSSESSION) left.play(now, "Powers_Idle", "Powers_Cast_Possession_In", "Powers_Cast_Possession_Out");
		else if (clip != null) left.play(now, "Powers_Idle", clip);
	}

	public static void onBlinkAim(boolean start) {
		if (!FpAssets.available() || leftMode != Left.POWERS) return;
		float now = now(0f);
		if (start) left.play(now, "Powers_Cast_Blink_Loop", "Powers_Cast_Blink_In");
		else left.play(now, "Powers_Idle", "Powers_Cast_Blink_Out");
	}

	public static void onSwitchPower() {
		if (!FpAssets.available() || leftMode != Left.POWERS) return;
		if (!left.playingOneShot(now(0f))) left.play(now(0f), "Powers_Idle", "Powers_Switch");
	}

	/** Bone rotations for the current frame, layered like Dishonored's per-arm slots. */
	static Quaternionf[] pose(FpModel arms, float now) {
		// Advance every frame too, not only on game ticks: while time is slowed (Redirective Blink, the weapon
		// wheel) a tick can take a second, and a finished one-shot would hold its last frame until then.
		for (FpSlot s : new FpSlot[] {base, right, left, swordBones, gunBones, overlay}) s.tick(now);
		Quaternionf[] pose = new Quaternionf[arms.boneCount()];
		base.sample(arms, now, pose, null);
		right.sample(arms, now, pose, b -> !isLeftBone(b));
		// The draw clips turn the left weapon socket (handAttachment_L_jnt); the held loops leave it at
		// its rest angle. During a draw the socket moves to that rest angle together with the START -> END blend,
		// so nothing swings (gadgets) or snaps (guns) when the draw hands over to the held loop.
		Integer att = arms.index.get("handAttachment_L_jnt");
		Quaternionf rest = att == null ? null : pose[att] != null ? new Quaternionf(pose[att]) : new Quaternionf(arms.bindRotation(att));
		left.sample(arms, now, pose, FpController::isLeftBone);
		if (att != null && leftDrawInvolved(now)) {
			float w = leftMode == Left.GADGET ? gadgetHeldWeight(now) : leftMode == Left.POWERS ? 1f : gunGripWeight(now);
			pose[att] = new Quaternionf(pose[att] != null ? pose[att] : rest).slerp(rest, w);
		}
		// Sprinting pulls the marked hand back toward Dishonored's low sprint pose; stopping eases it forward.
		float dt = lastPoseNow < 0 ? 0f : Math.max(0f, Math.min(0.1f, now - lastPoseNow));
		lastPoseNow = now;
		float target = sprintHold > 0 && leftMode == Left.POWERS && !ClientPowerState.aimingBlink ? 1f : 0f;
		sprintPull += (target - sprintPull) * (1f - (float) Math.exp(-dt * SPRINT_PULL_RATE));
		if (sprintPull > 0.01f) runArm(arms, pose);
		blockWeight += ((rightMode == Right.SWORD ? blockTarget : 0f) - blockWeight) * (1f - (float) Math.exp(-dt * 12f));
		float w = overlayWeight(now);
		if (w > 0f) {
			Quaternionf[] take = new Quaternionf[pose.length];
			overlay.sample(arms, now, take, null);
			// The frontal stab (drop assassination) holds the blade in a reverse grip and flips it back to the
			// normal grip as the blade comes back on screen (1.5-1.9 s), which looks like it unfolding again.
			// Switch the grip while the blade is off screen instead, so it comes back up like after a backstab.
			if ("Sword_Ready_Assassination_Front_Master".equals(overlay.current()) && overlay.elapsed(now) > FRONT_GRIP_SWAP) {
				Integer socket = arms.index.get("handAttachment_R_jnt");
				if (socket != null) take[socket] = null;
			}
			// Once the left arm is released (its hand left the screen), it fades out of the takedown quickly.
			float wl = leftRelease < 0 ? w : Math.min(w, Math.max(0f, 1f - (now - leftRelease) / LEFT_RELEASE_FADE));
			for (int i = 0; i < pose.length; i++) {
				if (take[i] == null) continue;
				float wi = isLeftBone(arms.names[i]) ? wl : w;
				if (wi <= 0f) continue;
				pose[i] = pose[i] == null || wi >= 1f ? take[i] : new Quaternionf(pose[i]).slerp(take[i], wi);
			}
		}
		return pose;
	}

	/**
	 * How much of the held-grip correction to apply to the left-hand gun (0..1). Dishonored's equip clip already
	 * brings the gun up correctly, so the correction fades in over the last part of the equip instead of
	 * being there from the first frame.
	 */
	static float gunGripWeight(float now) {
		// While tuning the pull-out, show only its rotation (no blend into the held pose).
		// START -> END between draw frames blendFrom and blendTo (the arm's Guns_Ready_Equip frames).
		if (!"Guns_Ready_Equip".equals(left.current()) || !left.playingOneShot(now)) return 1f;
		float from = com.dishonoredmc.client.HandTuning.blendFrom, to = com.dishonoredmc.client.HandTuning.blendTo;
		float t = Math.max(0f, Math.min(1f, (left.clipFrame(now) - from) / Math.max(0.01f, to - from)));
		return t * t * (3 - 2 * t);
	}

	/** the spring razor's use movement: {START -> END weight, END -> held weight}, or null when not playing. */
	@Nullable
	static float[] razorUseWeights(float now) {
		if (!"Gadgets_SpringRazor_Deploy".equals(left.current()) || !left.playingOneShot(now)) return null;
		float f = left.clipFrame(now);
		return new float[] {
			ramp(f, com.dishonoredmc.client.HandTuning.razorUseFrom, com.dishonoredmc.client.HandTuning.razorUseTo),
			ramp(f, com.dishonoredmc.client.HandTuning.razorBackFrom, com.dishonoredmc.client.HandTuning.razorBackTo)};
	}

	/** 0..1 through the hand's return to idle after placing a spring razor, or -1. */
	static float razorReturnWeight(float now) {
		if (!"Gadgets_SpringRazor_Deploy".equals(left.fadingFrom(now))) return -1f;
		float t = left.fadeProgress(now);
		return t * t * (3 - 2 * t);
	}

	private static float ramp(float f, float from, float to) {
		float t = Math.max(0f, Math.min(1f, (f - from) / Math.max(0.01f, to - from)));
		return t * t * (3 - 2 * t);
	}

	/** a gun / gadget draw is playing, or the hand is still fading out of one. */
	private static boolean leftDrawInvolved(float now) {
		String cur = left.current(), prev = left.fadingFrom(now);
		return (cur != null && (cur.equals("Guns_Ready_Equip") || cur.equals("Gadgets_Ready_Equip")))
			|| (prev != null && (prev.equals("Guns_Ready_Equip") || prev.equals("Gadgets_Ready_Equip")));
	}

	/** How far the grenade / spring razor has moved into its held placement (0 = draw placement, 1 = held). */
	static float gadgetHeldWeight(float now) {
		String cur = left.current();
		if (cur == null || !cur.equals("Gadgets_Ready_Equip") || !left.playingOneShot(now)) return 1f;
		// START -> END between draw frames gadgetBlendFrom and gadgetBlendTo.
		float from = com.dishonoredmc.client.HandTuning.gadgetBlendFrom, to = com.dishonoredmc.client.HandTuning.gadgetBlendTo;
		float t = Math.max(0f, Math.min(1f, (left.clipFrame(now) - from) / Math.max(0.01f, to - from)));
		return t * t * (3 - 2 * t);
	}

	/** False from the shot until the reload has seated a new bolt (halfway through Crossbow_RapidReload). */
	static boolean crossbowLoaded(float now) {
		String cur = gunBones.current();
		if (cur == null) return true;
		if (cur.equals("crossbow:Crossbow_Fire")) return false;
		if (cur.equals("crossbow:Crossbow_RapidReload")) return gunBones.progress(now) > 0.5f;
		return true;
	}

	/**
	 * Sprinting with the marked hand: Dishonored's own sprint clip is a held, half-closed pose, so the open walking
	 * hand is pulled back a little and pumped in step with the player's stride (the same counter that drives view
	 * bobbing, so it always loops in time with the footsteps). Weighted by sprintPull, so it eases in and out.
	 */
	private static void runArm(FpModel arms, Quaternionf[] pose) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		float td = mc.getTickDelta();
		var p = mc.player;
		float stride = p.prevHorizontalSpeed + (p.horizontalSpeed - p.prevHorizontalSpeed) * td;
		float swing = (float) Math.sin(stride * Math.PI * com.dishonoredmc.client.HandTuning.runFreq);
		var t = com.dishonoredmc.client.HandTuning.runArm;
		Integer upper = arms.index.get("upper_arm_L_jnt"), lower = arms.index.get("lower_arm_L_jnt");
		if (upper == null || lower == null) return;
		float k = sprintPull;
		// upper arm: pulled back (t[0..2]) + pump (t[3..5]) ; forearm bends with the pump (t[6])
		rotateLocal(arms, pose, upper, k * (t[0] + t[3] * swing), k * (t[1] + t[4] * swing), k * (t[2] + t[5] * swing));
		rotateLocal(arms, pose, lower, k * t[6] * swing, 0, 0);
	}

	private static void rotateLocal(FpModel arms, Quaternionf[] pose, int bone, float xDeg, float yDeg, float zDeg) {
		Quaternionf q = pose[bone] != null ? new Quaternionf(pose[bone]) : arms.bindRotation(bone);
		pose[bone] = q.rotateXYZ((float) Math.toRadians(xDeg), (float) Math.toRadians(yDeg), (float) Math.toRadians(zDeg));
	}

	/** The gun / gadget is put away for a takedown and not yet being drawn again. */
	private static boolean leftHidden() {
		return leftPutAway && leftRelease < 0;
	}

	/** A takedown (backstab, drop assassination, choke) is playing, fade-out included. */
	public static boolean takedownPlaying() {
		return overlayWeight(now(0f)) > 0f;
	}

	/** A takedown clip is playing. */
	static boolean overlayActive(float now) {
		return overlayWeight(now) > 0f;
	}

	/** The gadget mesh held in the left hand, if any. */
	@Nullable
	static String gadgetFor(ClientPlayerEntity p) {
		if (leftMode != Left.GADGET || leftHidden()) return null;
		if (p.getMainHandStack().isOf(ModItems.GRENADE)) return "grenade";
		if (p.getMainHandStack().isOf(ModItems.SPRING_RAZOR)) return "spring_razor";
		return null;
	}

	@Nullable
	static String weaponFor(boolean rightHand) {
		if (rightHand) {
			// The choke hold animates the blade hand around the victim's neck, pointing the sword at the camera: hide it.
			String take = overlay.current();
			if (take != null && take.contains("Choke") && overlayWeight(now(0f)) > 0f
				&& !(take.equals("Sword_Choke_Win_Master") && overlay.elapsed(now(0f)) > 2.2f)) return null;
			return rightMode == Right.SWORD || "Sword_Ready_UnEquip".equals(right.current()) ? "sword" : null;
		}
		if (leftHidden()) return null;
		return switch (leftMode) {
			case PISTOL -> "pistol";
			case CROSSBOW -> "crossbow";
			default -> null;
		};
	}
}
