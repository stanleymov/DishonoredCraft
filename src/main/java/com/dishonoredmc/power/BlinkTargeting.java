package com.dishonoredmc.power;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Where Blink would put the player's feet. Shared by the client preview and the server cast. */
public final class BlinkTargeting {
	public static final double RANGE = 15.0;
	/** How far above the aimed point a ledge can be and still be mantled onto. */
	public static final double MANTLE_REACH = 2.0;

	/** A blink at a wall that pulls Corvo up onto its ledge: the aimed point on the face, the face, the ledge top. */
	public record Mantle(Vec3d hit, Direction side, double ledgeY) {}

	public record Target(Vec3d feet, @Nullable Mantle mantle) {}

	private BlinkTargeting() {}

	@Nullable
	public static Vec3d compute(PlayerEntity player) {
		Target t = target(player);
		return t == null ? null : t.feet();
	}

	@Nullable
	public static Target target(PlayerEntity player) {
		World world = player.getWorld();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

		double height = player.getHeight();
		double halfWidth = player.getWidth() / 2.0;
		Vec3d feet;
		if (hit.getType() == HitResult.Type.MISS) {
			feet = end.subtract(0, 0.9, 0);
		} else {
			Vec3d pos = hit.getPos();
			BlockPos bp = hit.getBlockPos();
			Direction side = hit.getSide();
			if (side == Direction.UP) {
				feet = pos;
			} else if (side == Direction.DOWN) {
				feet = pos.subtract(0, height + 0.05, 0);
			} else {
				// Aiming at a wall with a ledge within reach above mantles onto it, like in Dishonored.
				Mantle mantle = ledge(player, bp, side, pos);
				if (mantle != null) {
					BlockPos top = BlockPos.ofFloored(pos.x - side.getOffsetX() * 0.5, mantle.ledgeY() - 0.5, pos.z - side.getOffsetZ() * 0.5);
					return new Target(new Vec3d(top.getX() + 0.5, mantle.ledgeY(), top.getZ() + 0.5), mantle);
				}
				feet = new Vec3d(pos.x + side.getOffsetX() * (halfWidth + 0.05), pos.y - 0.9, pos.z + side.getOffsetZ() * (halfWidth + 0.05));
			}
		}

		Vec3d back = look.multiply(-0.3);
		double[] nudges = {0, 0.5, 1.0, -0.5, -1.0, 1.5};
		for (int step = 0; step < 12; step++) {
			for (double dy : nudges) {
				Vec3d candidate = feet.add(0, dy, 0);
				if (fits(player, candidate)) return new Target(candidate, null);
			}
			feet = feet.add(back);
		}
		return null;
	}

	/**
	 * The ledge of the wall the player aims at: climbing the column of solid blocks from the one hit, the first
	 * top with room to stand on it, if that is within MANTLE_REACH above the aimed point.
	 */
	@Nullable
	private static Mantle ledge(PlayerEntity player, BlockPos hitBlock, Direction side, Vec3d pos) {
		World world = player.getWorld();
		BlockPos top = hitBlock;
		for (int k = 0; k < 4; k++) {
			BlockPos above = top.up();
			if (world.getBlockState(above).getCollisionShape(world, above).isEmpty()) break;
			top = above;
		}
		var shape = world.getBlockState(top).getCollisionShape(world, top);
		if (shape.isEmpty()) return null;
		double ledgeY = top.getY() + shape.getMax(Direction.Axis.Y);
		if (ledgeY - pos.y > MANTLE_REACH) return null;
		Vec3d feet = new Vec3d(top.getX() + 0.5, ledgeY, top.getZ() + 0.5);
		return fits(player, feet) ? new Mantle(pos, side, ledgeY) : null;
	}

	private static boolean fits(PlayerEntity player, Vec3d feet) {
		Box box = player.getDimensions(player.getPose()).getBoxAt(feet);
		return player.getWorld().isSpaceEmpty(player, box);
	}
}
