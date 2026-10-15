package com.dishonoredmc.entity;

import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.power.SleepDarts;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Wrist-bow ammunition: plain bolts, sleep darts and incendiary bolts. */
public class BoltEntity extends PersistentProjectileEntity {
	public enum Kind { BOLT, SLEEP_DART, INCENDIARY }

	private static final TrackedData<Integer> KIND = DataTracker.registerData(BoltEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public BoltEntity(EntityType<? extends BoltEntity> type, World world) {
		super(type, world);
	}

	public BoltEntity(World world, LivingEntity owner, ItemStack ammo, ItemStack weapon, Kind kind) {
		super(ModEntities.BOLT, owner, world);
		this.setKind(kind);
		switch (kind) {
			case BOLT -> this.setDamage(2.2);
			case SLEEP_DART -> this.setDamage(0.2);
			case INCENDIARY -> {
				this.setDamage(1.8);
				this.setOnFireFor(100);
			}
		}
		if (kind != Kind.BOLT) this.pickupType = PickupPermission.DISALLOWED;
		else if (owner instanceof PlayerEntity p && p.isCreative()) this.pickupType = PickupPermission.CREATIVE_ONLY;
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(KIND, 0);
	}

	public Kind getKind() {
		return Kind.values()[Math.floorMod(this.dataTracker.get(KIND), Kind.values().length)];
	}

	private void setKind(Kind kind) {
		this.dataTracker.set(KIND, kind.ordinal());
	}

	@Override
	protected ItemStack asItemStack() {
		return new ItemStack(ModItems.CROSSBOW_BOLT);
	}

	@Override
	protected void onHit(LivingEntity target) {
		super.onHit(target);
		switch (getKind()) {
			case SLEEP_DART -> {
				if (target instanceof MobEntity mob) SleepDarts.sleep(mob);
			}
			case INCENDIARY -> target.setOnFireFor(8);
			default -> {}
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (getKind() == Kind.INCENDIARY && !this.getWorld().isClient) {
			BlockPos firePos = hit.getBlockPos().offset(hit.getSide());
			if (this.getWorld().isAir(firePos)) {
				this.getWorld().setBlockState(firePos, AbstractFireBlock.getState(this.getWorld(), firePos));
			}
			this.discard();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient && !this.inGround) {
			if (getKind() == Kind.SLEEP_DART) {
				this.getWorld().addParticle(ParticleTypes.EFFECT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
			} else if (getKind() == Kind.INCENDIARY) {
				this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
			}
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("BoltKind", this.dataTracker.get(KIND));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(KIND, nbt.getInt("BoltKind"));
	}
}
