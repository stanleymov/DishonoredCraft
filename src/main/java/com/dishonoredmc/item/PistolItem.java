package com.dishonoredmc.item;

import com.dishonoredmc.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Single-shot flintlock: hitscan, heavy damage, loud. Uses Pistol Shot ammo. */
public class PistolItem extends Item {
	private static final double RANGE = 80.0;
	private static final float DAMAGE = 14.0F;

	public PistolItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!Ammo.has(user, ModItems.PISTOL_SHOT)) {
			user.playSound(ModSounds.PISTOL_EMPTY, 0.8F, 1.0F);
			return TypedActionResult.fail(stack);
		}
		user.getItemCooldownManager().set(this, 24);
		if (world.isClient) return TypedActionResult.success(stack, true);
		Ammo.consume(user, ModItems.PISTOL_SHOT);
		fire((ServerWorld) world, user);
		com.dishonoredmc.entity.BulletEntity bullet = new com.dishonoredmc.entity.BulletEntity(world, user);
		bullet.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 6.0F, 0.15F);
		world.spawnEntity(bullet);
		stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
		return TypedActionResult.success(stack, false);
	}

	/** Muzzle flash and smoke; the ball itself is a BulletEntity so Bend Time can freeze it. */
	private static void fire(ServerWorld world, PlayerEntity shooter) {
		Vec3d eye = shooter.getEyePos();
		Vec3d look = shooter.getRotationVec(1.0F);
		Vec3d muzzle = eye.add(look.multiply(1.8)).add(0, -0.2, 0);
		world.spawnParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
		world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.06, 0.06, 0.06, 0.02);
		world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ModSounds.PISTOL_FIRE, SoundCategory.PLAYERS, 2.0F, 1.0F);
	}
}
