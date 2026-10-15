package com.dishonoredmc.entity;

import com.dishonoredmc.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/**
 * A pistol ball. Fast and gravity-free, but a real projectile, so Bend Time can stop it mid-air and
 * release it when time resumes, as in Dishonored.
 */
public class BulletEntity extends ThrownItemEntity {
	public static final float DAMAGE = 14.0F;

	public BulletEntity(EntityType<? extends BulletEntity> type, World world) {
		super(type, world);
	}

	public BulletEntity(World world, LivingEntity owner) {
		super(ModEntities.BULLET, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.PISTOL_SHOT;
	}

	@Override
	protected float getGravity() {
		return 0.0F;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			this.getWorld().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
		} else if (this.age > 60) {
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		Entity target = hit.getEntity();
		Entity owner = this.getOwner();
		target.damage(this.getDamageSources().thrown(this, owner), DAMAGE);
		if (target instanceof LivingEntity living) {
			var v = this.getVelocity().normalize();
			living.takeKnockback(0.6, -v.x, -v.z);
		}
		if (this.getWorld() instanceof ServerWorld world) {
			var at = hit.getPos();
			world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
		}
		this.discard();
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (this.getWorld() instanceof ServerWorld world) {
			BlockState state = world.getBlockState(hit.getBlockPos());
			if (state.isIn(net.minecraftforge.common.Tags.Blocks.GLASS) || state.isIn(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)) {
				world.breakBlock(hit.getBlockPos(), false, this.getOwner());
			} else {
				// At the impact point: the ball can still be at the muzzle (in the player's face) when it hits close by.
				var at = hit.getPos();
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 10, 0.05, 0.05, 0.05, 0.15);
			}
		}
		this.discard();
	}
}
