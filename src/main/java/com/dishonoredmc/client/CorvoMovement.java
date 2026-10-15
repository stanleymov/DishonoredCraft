package com.dishonoredmc.client;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.net.MovementPayload;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * Corvo's movement, client side (player movement is client-authoritative in Minecraft):
 * mantling onto ledges, Agility's double jump, the sprint slide and leaning around corners.
 */
public final class CorvoMovement {
	private static boolean jumpWasDown;
	private static boolean sneakWasDown;
	private static boolean doubleJumped;
	private static int airTicks;

	private static double mantleTargetY = Double.NaN;
	private static Vec3d mantleDir = Vec3d.ZERO;
	private static int mantleTicks;

	private static int slideTicks;
	private static Vec3d slideDir = Vec3d.ZERO;
	private static final int SLIDE_TICKS = 14;

	private static float lean, prevLean;
	private static float stealth, prevStealth;

	private CorvoMovement() {}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		prevLean = lean;
		prevStealth = stealth;
		if (player == null) return;

		boolean jumpDown = client.options.jumpKey.isPressed();
		boolean sneakDown = client.options.sneakKey.isPressed();
		boolean canMove = client.currentScreen == null && !player.getAbilities().flying && !player.hasVehicle()
			&& !player.isTouchingWater() && !player.isClimbing() && !player.isFallFlying() && !player.isSpectator();

		if (player.isOnGround()) {
			doubleJumped = false;
			airTicks = 0;
		} else {
			airTicks++;
		}

		// Mantle: jump into a ledge up to 3 blocks above your feet and pull yourself onto it.
		if (!Double.isNaN(mantleTargetY)) {
			tickMantle(player);
		} else if (canMove && jumpDown && player.horizontalCollision && player.input.movementForward > 0) {
			tryStartMantle(player);
		}

		// Agility: a second jump in mid-air.
		if (canMove && jumpDown && !jumpWasDown && !player.isOnGround() && airTicks > 2 && !doubleJumped && Double.isNaN(mantleTargetY)) {
			doubleJumped = true;
			Vec3d v = player.getVelocity();
			player.setVelocity(v.x * 1.15, 0.52, v.z * 1.15);
			player.fallDistance = 0;
			for (int i = 0; i < 10; i++) {
				player.getWorld().addParticle(ParticleTypes.CLOUD, player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6,
					player.getY(), player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6, 0, -0.05, 0);
			}
			player.playSound(ModSounds.SUPER_JUMP, 0.6F, 1.0F);
			com.dishonoredmc.net.Net.toServer(new MovementPayload(MovementPayload.DOUBLE_JUMP));
		}

		// Slide: tap sneak while sprinting on the ground.
		if (canMove && sneakDown && !sneakWasDown && player.isSprinting() && player.isOnGround() && slideTicks == 0) {
			Vec3d v = player.getVelocity();
			Vec3d flat = new Vec3d(v.x, 0, v.z);
			slideDir = flat.lengthSquared() > 1.0E-4 ? flat.normalize() : Vec3d.fromPolar(0, player.getYaw()).normalize();
			slideTicks = SLIDE_TICKS;
			player.playSound(ModSounds.SLIDE, 0.7F, 0.9F);
			com.dishonoredmc.net.Net.toServer(new MovementPayload(MovementPayload.SLIDE));
		}
		if (slideTicks > 0) {
			slideTicks--;
			if (!sneakDown || !player.isOnGround() && slideTicks < SLIDE_TICKS - 3) {
				slideTicks = 0;
			} else {
				double speed = 0.18 + 0.42 * slideTicks / SLIDE_TICKS;
				player.setVelocity(slideDir.x * speed, player.getVelocity().y, slideDir.z * speed);
				BlockState below = player.getWorld().getBlockState(player.getBlockPos().down());
				if (!below.isAir()) {
					player.getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, below),
						player.getX(), player.getY() + 0.1, player.getZ(), -slideDir.x, 0.2, -slideDir.z);
				}
			}
		}

		// Lean: hold the lean key and A/D to peek. Purely a camera move (see CameraMixin).
		float leanTarget = 0f;
		if (canMove && DishonoredClient.lean.isPressed()) {
			if (client.options.rightKey.isPressed()) leanTarget += 1f;
			if (client.options.leftKey.isPressed()) leanTarget -= 1f;
		}
		lean += (leanTarget - lean) * 0.3f;
		if (Math.abs(lean) < 0.001f) lean = 0f;

		// Stealth: crouching darkens the screen edges, as in Dishonored.
		float stealthTarget = player.isInSneakingPose() && slideTicks == 0 ? 1f : 0f;
		stealth += (stealthTarget - stealth) * 0.15f;

		jumpWasDown = jumpDown;
		sneakWasDown = sneakDown;
	}

	private static void tryStartMantle(ClientPlayerEntity player) {
		Vec3d forward = Vec3d.fromPolar(0, player.getYaw()).normalize();
		double reach = player.getWidth() / 2 + 0.35;
		Vec3d probe = player.getPos().add(forward.multiply(reach));
		for (double h = 0.3; h <= 3.0; h += 0.25) {
			BlockPos pos = BlockPos.ofFloored(probe.x, player.getY() + h, probe.z);
			BlockState state = player.getWorld().getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(player.getWorld(), pos);
			if (shape.isEmpty()) continue;
			double top = pos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
			double rise = top - player.getY();
			if (rise < 0.6 || rise > 3.0) continue;
			Vec3d stand = new Vec3d(probe.x + forward.x * 0.3, top + 0.01, probe.z + forward.z * 0.3);
			Box box = player.getDimensions(player.getPose()).getBoxAt(stand);
			// No room on this block (more wall above it): keep climbing the wall to its real top.
			if (!player.getWorld().isSpaceEmpty(player, box)) continue;
			mantleTargetY = top + 0.02;
			mantleDir = forward;
			mantleTicks = 0;
			player.playSound(ModSounds.MANTLE, 0.8F, 1.0F);
			com.dishonoredmc.net.Net.toServer(new MovementPayload(MovementPayload.MANTLE));
			return;
		}
	}

	private static void tickMantle(ClientPlayerEntity player) {
		mantleTicks++;
		player.fallDistance = 0;
		if (player.getY() < mantleTargetY && mantleTicks < 14) {
			double up = MathHelper.clamp((mantleTargetY - player.getY()) * 0.6, 0.18, 0.42);
			player.setVelocity(mantleDir.x * 0.02, up, mantleDir.z * 0.02);
		} else {
			player.setVelocity(mantleDir.x * 0.28, 0.05, mantleDir.z * 0.28);
			mantleTargetY = Double.NaN;
		}
	}

	public static float lean(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLean, lean);
	}

	public static boolean isLeaning() {
		return DishonoredClient.lean != null && DishonoredClient.lean.isPressed();
	}

	public static float stealth(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevStealth, stealth);
	}

	public static boolean isSliding() {
		return slideTicks > 0;
	}
}
