package com.dishonoredmc.entity;

import com.dishonoredmc.DishonoredMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<PlagueRatEntity> PLAGUE_RAT = register("plague_rat",
		EntityType.Builder.create(PlagueRatEntity::new, SpawnGroup.MONSTER).setDimensions(0.4F, 0.3F).maxTrackingRange(8));
	public static final EntityType<BoltEntity> BOLT = register("bolt",
		EntityType.Builder.<BoltEntity>create(BoltEntity::new, SpawnGroup.MISC).setDimensions(0.5F, 0.5F).maxTrackingRange(4).trackingTickInterval(20));
	public static final EntityType<GrenadeEntity> GRENADE = register("grenade",
		EntityType.Builder.<GrenadeEntity>create(GrenadeEntity::new, SpawnGroup.MISC).setDimensions(0.25F, 0.25F).maxTrackingRange(4).trackingTickInterval(10));
	public static final EntityType<BulletEntity> BULLET = register("bullet",
		EntityType.Builder.<BulletEntity>create(BulletEntity::new, SpawnGroup.MISC).setDimensions(0.12F, 0.12F).maxTrackingRange(8).trackingTickInterval(1));
	public static final EntityType<SpringRazorEntity> SPRING_RAZOR = register("spring_razor",
		EntityType.Builder.<SpringRazorEntity>create(SpringRazorEntity::new, SpawnGroup.MISC).setDimensions(0.5F, 0.3F).maxTrackingRange(6).trackingTickInterval(10));

	private ModEntities() {}

	private static <T extends net.minecraft.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		return Registry.register(Registries.ENTITY_TYPE, DishonoredMod.id(name), builder.build(name));
	}

	public static void init() {}

	public static void registerAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent e) {
		e.put(PLAGUE_RAT, PlagueRatEntity.createRatAttributes().build());
	}
}
