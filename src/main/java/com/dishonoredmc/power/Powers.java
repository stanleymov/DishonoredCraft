package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.entity.ModEntities;
import com.dishonoredmc.entity.PlagueRatEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/** Server-side casting of every active power. */
public final class Powers {
	private Powers() {}

	public static void cast(ServerPlayerEntity player, Power power) {
		if (player.isSpectator() || player.isDead()) return;

		if (Possession.isPossessing(player)) {
			// Casting Possession again (or anything else) releases the host, as in Dishonored. The unused part of
			// its duration comes back as mana.
			Mana.regenerate(player, Power.POSSESSION.cost * Possession.remainingFraction(player));
			Possession.end(player);
			return;
		}

		if (power == Power.DARK_VISION && DarkVision.isActive(player)) {
			DarkVision.toggle(player, false);
			return;
		}

		ServerWorld world = player.getServerWorld();
		switch (power) {
			case BLINK -> {
				Vec3d target = BlinkTargeting.compute(player);
				if (target == null) {
					fail(player, "message.dishonored.no_target");
					return;
				}
				if (!spend(player, power)) return;
				blink(player, world, target);
			}
			case DARK_VISION -> {
				if (!spend(player, power)) return;
				DarkVision.toggle(player, true);
				world.playSound(null, player.getBlockPos(), ModSounds.DARK_VISION_ON, SoundCategory.PLAYERS, 0.9F, 1.0F);
			}
			case BEND_TIME -> {
				if (BendTime.ticksLeft(world) > 0) {
					// Cast again to release time early: the unused part of it comes back as mana.
					Mana.regenerate(player, power.cost * BendTime.ticksLeft(world) / (float) BendTime.DURATION);
					BendTime.stop(world);
					return;
				}
				if (!spend(player, power)) return;
				BendTime.start(world);
				// (The Bend Time sound plays on each client and fades out when time resumes: BendTimeSound.)
			}
			case POSSESSION -> {
				Entity target = targetEntity(player, 16.0);
				if (!(target instanceof MobEntity mob) || !Possession.canPossess(mob)) {
					fail(player, "message.dishonored.no_host");
					return;
				}
				if (!spend(player, power)) return;
				Possession.start(player, mob);
			}
			case DEVOURING_SWARM -> {
				if (!spend(player, power)) return;
				swarm(player, world);
			}
			case WINDBLAST -> {
				if (!spend(player, power)) return;
				windblast(player, world);
			}
		}
	}

	private static boolean spend(ServerPlayerEntity player, Power power) {
		if ((player.isCreative() || Mana.get(player) >= power.minMana) && Mana.trySpend(player, power.cost)) return true;
		if (Elixirs.drink(player, com.dishonoredmc.item.ElixirItem.Kind.MANA) && Mana.trySpend(player, power.cost)) return true;
		fail(player, "message.dishonored.no_mana");
		return false;
	}

	private static void fail(ServerPlayerEntity player, String key) {
		player.playSound(ModSounds.POWER_EMPTY, SoundCategory.PLAYERS, 0.8F, 1.0F);
		player.sendMessage(Text.translatable(key).formatted(Formatting.DARK_AQUA), true);
	}

	private static void blink(ServerPlayerEntity player, ServerWorld world, Vec3d target) {
		Vec3d from = player.getPos();
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.02);
		player.networkHandler.requestTeleport(target.x, target.y, target.z, player.getYaw(), player.getPitch());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		// The fall so far is kept: blinking down onto the ground mid-fall still hurts (more than the safe 5 blocks),
		// it can't be used to cheat a long fall. A blink from standing adds nothing.
		world.spawnParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 40, 0.3, 0.6, 0.3, 0.6);
		world.playSound(null, from.x, from.y, from.z, ModSounds.BLINK, SoundCategory.PLAYERS, 0.9F, 1.0F);
		world.playSound(null, target.x, target.y, target.z, ModSounds.BLINK_ARRIVE, SoundCategory.PLAYERS, 0.9F, 1.0F);
	}

	private static void swarm(ServerPlayerEntity player, ServerWorld world) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(24.0));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		BlockPos spawnAt = hit.getType() == HitResult.Type.MISS
			? BlockPos.ofFloored(player.getPos().add(player.getRotationVec(1.0F).multiply(3.0)))
			: hit.getBlockPos().offset(hit.getSide());

		for (int i = 0; i < 7; i++) {
			PlagueRatEntity rat = ModEntities.PLAGUE_RAT.create(world);
			if (rat == null) continue;
			double ox = (world.random.nextDouble() - 0.5) * 2.0;
			double oz = (world.random.nextDouble() - 0.5) * 2.0;
			rat.refreshPositionAndAngles(spawnAt.getX() + 0.5 + ox, spawnAt.getY(), spawnAt.getZ() + 0.5 + oz, world.random.nextFloat() * 360F, 0);
			rat.setSummoner(player);
			rat.initialize(world, world.getLocalDifficulty(spawnAt), SpawnReason.MOB_SUMMONED, null, null);
			world.spawnEntity(rat);
		}
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, spawnAt.getX() + 0.5, spawnAt.getY() + 0.5, spawnAt.getZ() + 0.5, 25, 0.8, 0.3, 0.8, 0.01);
		world.playSound(null, player.getBlockPos(), ModSounds.SWARM, SoundCategory.PLAYERS, 1.0F, 1.0F);
		world.playSound(null, spawnAt, ModSounds.SWARM_RATS, SoundCategory.PLAYERS, 1.4F, 1.0F);
	}

	private static void windblast(ServerPlayerEntity player, ServerWorld world) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		double range = 10.0;
		Box area = player.getBoundingBox().stretch(look.multiply(range)).expand(3.0);

		for (Entity e : world.getOtherEntities(player, area)) {
			Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > range || dist < 0.01 || to.normalize().dotProduct(look) < 0.55) continue;
			double strength = 2.6 * (1.0 - dist / (range + 2.0)) + 0.6;
			if (e instanceof ProjectileEntity projectile) {
				// Send arrows and bolts back where they came from.
				projectile.setVelocity(look.multiply(Math.max(1.5, projectile.getVelocity().length())));
				projectile.setOwner(player);
				projectile.velocityModified = true;
				continue;
			}
			e.addVelocity(look.x * strength, 0.45 + look.y * strength * 0.5, look.z * strength);
			e.velocityModified = true;
			if (e instanceof LivingEntity living) {
				living.damage(world.getDamageSources().playerAttack(player), 4.0F);
			}
		}

		// Shatter glass and strip leaves in the cone.
		BlockPos center = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(center.add(-7, -3, -7), center.add(7, 4, 7))) {
			Vec3d to = Vec3d.ofCenter(pos).subtract(eye);
			if (to.length() > 7.0 || to.normalize().dotProduct(look) < 0.7) continue;
			BlockState state = world.getBlockState(pos);
			if (state.isIn(net.minecraftforge.common.Tags.Blocks.GLASS) || state.isIn(net.minecraftforge.common.Tags.Blocks.GLASS_PANES) || state.isIn(BlockTags.LEAVES)) {
				world.breakBlock(pos.toImmutable(), state.isIn(BlockTags.LEAVES), player);
			}
		}

		for (int i = 1; i <= 6; i++) {
			Vec3d p = eye.add(look.multiply(i * 1.4));
			world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.2 * i, 0.2 * i, 0.2 * i, 0);
		}
		world.playSound(null, player.getBlockPos(), ModSounds.WINDBLAST, SoundCategory.PLAYERS, 1.4F, 1.0F);
	}

	@Nullable
	private static Entity targetEntity(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(range));
		BlockHitResult blockHit = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getPos();
		Box box = player.getBoundingBox().stretch(end.subtract(eye)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box, e -> !e.isSpectator() && e.canHit(), eye.squaredDistanceTo(end));
		return hit == null ? null : hit.getEntity();
	}
}
