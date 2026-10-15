package com.dishonoredmc.entity;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/** A thrown trap: sticks where it lands, arms, and shreds anything that walks close. */
public class SpringRazorEntity extends ThrownItemEntity {
	private static final TrackedData<Boolean> ARMED = DataTracker.registerData(SpringRazorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final int ARM_DELAY = 20;
	private static final int MAX_AGE = 20 * 60 * 10;
	private int armedTicks;

	public SpringRazorEntity(EntityType<? extends SpringRazorEntity> type, World world) {
		super(type, world);
	}

	public SpringRazorEntity(World world, LivingEntity owner) {
		super(ModEntities.SPRING_RAZOR, owner, world);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(ARMED, false);
	}

	public boolean isArmed() {
		return this.dataTracker.get(ARMED);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SPRING_RAZOR;
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (isArmed()) return;
		this.setPosition(hit.getPos());
		this.setVelocity(0, 0, 0);
		this.setNoGravity(true);
		this.dataTracker.set(ARMED, true);
		this.playSound(ModSounds.RAZOR_INSTALL, 1.0F, 1.0F);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		if (!this.getWorld().isClient) detonate();
	}

	@Override
	public void tick() {
		if (!isArmed()) {
			super.tick();
			return;
		}
		this.baseTick();
		if (this.getWorld().isClient) return;
		armedTicks++;
		if (this.age > MAX_AGE) {
			this.dropStack(new ItemStack(getDefaultItem()));
			this.discard();
			return;
		}
		if (armedTicks == 1) this.playSound(ModSounds.RAZOR_ARMING, 0.8F, 1.0F);
		if (armedTicks < ARM_DELAY) return;
		Entity owner = this.getOwner();
		boolean triggered = !this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(2.2),
			e -> e.isAlive() && e != owner && !e.isSpectator() && !(e instanceof PlayerEntity p && p.isCreative())).isEmpty();
		if (triggered) detonate();
	}

	private void detonate() {
		ServerWorld world = (ServerWorld) this.getWorld();
		Entity owner = this.getOwner();
		DamageSource source = world.getDamageSources().thrown(this, owner);
		for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(4.0), e -> e.isAlive() && e != owner)) {
			float falloff = (float) Math.max(0.4, 1.0 - victim.distanceTo(this) / 6.0);
			victim.damage(source, 30.0F * falloff);
		}
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8;
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(a) * 2, this.getY() + 0.6, this.getZ() + Math.sin(a) * 2, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.5, this.getZ(), 60, 1.5, 0.6, 1.5, 0.6);
		world.playSound(null, this.getBlockPos(), ModSounds.RAZOR_SHOT, SoundCategory.PLAYERS, 1.6F, 1.0F);
		this.discard();
	}

	@Override
	public boolean canHit() {
		return isArmed();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (isArmed() && !this.getWorld().isClient && !this.isRemoved()) detonate();
		return true;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		// Disarm and pick it back up.
		if (!isArmed()) return ActionResult.PASS;
		if (!this.getWorld().isClient) {
			if (!player.isCreative()) player.getInventory().offerOrDrop(new ItemStack(getDefaultItem()));
			this.playSound(SoundEvents.BLOCK_TRIPWIRE_DETACH, 1.0F, 1.0F);
			this.discard();
		}
		return ActionResult.success(this.getWorld().isClient);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Armed", isArmed());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(ARMED, nbt.getBoolean("Armed"));
		if (isArmed()) this.setNoGravity(true);
	}
}
