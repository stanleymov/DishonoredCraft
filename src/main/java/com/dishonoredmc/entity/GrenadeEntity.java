package com.dishonoredmc.entity;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Explodes on impact. Hurts mobs but leaves the terrain alone, as in Dishonored. */
public class GrenadeEntity extends ThrownItemEntity {
	public GrenadeEntity(EntityType<? extends GrenadeEntity> type, World world) {
		super(type, world);
	}

	public GrenadeEntity(World world, LivingEntity owner) {
		super(ModEntities.GRENADE, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.GRENADE;
	}

	/** Set during the second, block-breaking blast (see DishonoredMod's explosion handler). */
	public boolean craterBlast;

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1, this.getZ(), 0, 0.02, 0);
		}
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (!this.getWorld().isClient) {
			// Damage reach of a strong blast, and a crater as big as a TNT block's (stone included).
			this.getWorld().createExplosion(this, this.getX(), this.getY(), this.getZ(), 2.8F, World.ExplosionSourceType.NONE);
			this.craterBlast = true;
			// Against a wall or floor the blast goes off just outside the surface it hit, so the crater opens into it.
			net.minecraft.util.math.Vec3d at = this.getPos();
			if (hit instanceof net.minecraft.util.hit.BlockHitResult bh) {
				at = bh.getPos().add(net.minecraft.util.math.Vec3d.of(bh.getSide().getVector()).multiply(0.3));
			}
			this.getWorld().createExplosion(this, at.x, at.y, at.z, 4.0F, World.ExplosionSourceType.TNT);
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GRENADE_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS, 3.0F, 1.0F);
			this.discard();
		}
	}
}
