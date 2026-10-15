package com.dishonoredmc.item;

import com.dishonoredmc.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.function.BiFunction;

/** Grenades and spring razors: thrown from the hand like a snowball. */
public class ThrowableItem extends Item {
	private final BiFunction<World, PlayerEntity, ThrownItemEntity> factory;
	private final float speed;

	public ThrowableItem(Settings settings, float speed, BiFunction<World, PlayerEntity, ThrownItemEntity> factory) {
		super(settings);
		this.speed = speed;
		this.factory = factory;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), this == ModItems.GRENADE ? ModSounds.GRENADE_PIN : SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8F, this == ModItems.GRENADE ? 1.0F : 0.5F);
		user.getItemCooldownManager().set(this, 12);
		if (!world.isClient) {
			ThrownItemEntity entity = factory.apply(world, user);
			entity.setItem(stack.copyWithCount(1));
			// Thrown from Corvo's left hand (down, left and ahead of the eyes), not from between the eyes.
			Vec3d look = user.getRotationVec(1.0F);
			Vec3d left = new Vec3d(look.z, 0, -look.x).normalize();
			Vec3d palm = user.getEyePos().add(look.multiply(0.45)).add(left.multiply(0.2)).add(0, -0.12, 0);
			entity.setPosition(palm);
			// Aim from the hand at the point under the crosshair (20 blocks out), so the throw lines up with it.
			Vec3d aim = user.getEyePos().add(look.multiply(20)).subtract(palm).normalize();
			entity.setVelocity(aim.x, aim.y, aim.z, speed, 1.0F);
			world.spawnEntity(entity);
		}
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		return TypedActionResult.success(stack, world.isClient);
	}
}
