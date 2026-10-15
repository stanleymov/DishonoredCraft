package com.dishonoredmc.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Devouring Swarm rats: short-lived, hunt hostile mobs, never the player. */
public class PlagueRatEntity extends SilverfishEntity {
	private static final int LIFETIME = 20 * 30;
	@Nullable
	private UUID summoner;

	public PlagueRatEntity(EntityType<? extends PlagueRatEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createRatAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	public void setSummoner(PlayerEntity player) {
		this.summoner = player.getUuid();
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(3, new MeleeAttackGoal(this, 1.3, false));
		this.goalSelector.add(6, new WanderAroundGoal(this, 1.0));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this, PlagueRatEntity.class, PlayerEntity.class));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, HostileEntity.class, 5, true, false, this::isPrey));
	}

	private boolean isPrey(LivingEntity entity) {
		return !(entity instanceof PlagueRatEntity) && !entity.getUuid().equals(this.summoner);
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return !(target instanceof PlayerEntity) && !(target instanceof PlagueRatEntity) && super.canTarget(target);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient && this.age > LIFETIME) {
			((ServerWorld) this.getWorld()).spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.2, 0.1, 0.2, 0.01);
			this.discard();
		}
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}
}
