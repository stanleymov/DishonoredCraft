package com.dishonoredmc.item;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.entity.BoltEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import org.jetbrains.annotations.Nullable;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Corvo's wrist-mounted crossbow. The cycle-bolt key (V) or the weapon wheel switches between bolts, sleep darts and incendiary bolts. */
public class WristBowItem extends Item {
	public WristBowItem(Settings settings) {
		super(settings);
	}

	private static BoltEntity.Kind getMode(ItemStack stack) {
		int i = stack.hasNbt() ? stack.getNbt().getInt("Mode") : 0;
		return BoltEntity.Kind.values()[Math.floorMod(i, BoltEntity.Kind.values().length)];
	}

	public static void setModeIndex(ItemStack stack, int index) {
		setMode(stack, BoltEntity.Kind.values()[Math.floorMod(index, BoltEntity.Kind.values().length)]);
	}

	/** The cycle-bolt key (V): bolt -> sleep dart -> incendiary bolt. */
	public static void cycle(PlayerEntity user, ItemStack stack) {
		BoltEntity.Kind next = BoltEntity.Kind.values()[(getMode(stack).ordinal() + 1) % BoltEntity.Kind.values().length];
		setMode(stack, next);
		user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.CROSSBOW_RELOAD, SoundCategory.PLAYERS, 0.8F, 1.0F);
		user.sendMessage(Text.translatable("message.dishonored.bow_mode", ammoFor(next).getName()).formatted(Formatting.GOLD), true);
	}

	public static int getModeIndex(ItemStack stack) {
		return getMode(stack).ordinal();
	}

	private static void setMode(ItemStack stack, BoltEntity.Kind kind) {
		stack.getOrCreateNbt().putInt("Mode", kind.ordinal());
	}

	private static Item ammoFor(BoltEntity.Kind kind) {
		return switch (kind) {
			case BOLT -> ModItems.CROSSBOW_BOLT;
			case SLEEP_DART -> ModItems.SLEEP_DART;
			case INCENDIARY -> ModItems.INCENDIARY_BOLT;
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BoltEntity.Kind mode = getMode(stack);

		Item ammo = ammoFor(mode);
		if (!Ammo.has(user, ammo)) {
			user.playSound(ModSounds.PISTOL_EMPTY, 0.6F, 1.3F);
			return TypedActionResult.fail(stack);
		}
		user.getItemCooldownManager().set(this, 16);
		if (!world.isClient) {
			Ammo.consume(user, ammo);
			BoltEntity bolt = new BoltEntity(world, user, new ItemStack(ammo), stack, mode);
			// Fired from the wrist bow on Corvo's left arm, aimed at the point under the crosshair.
			net.minecraft.util.math.Vec3d look = user.getRotationVec(1.0F);
			net.minecraft.util.math.Vec3d leftDir = new net.minecraft.util.math.Vec3d(look.z, 0, -look.x).normalize();
			net.minecraft.util.math.Vec3d muzzle = user.getEyePos().add(look.multiply(0.45)).add(leftDir.multiply(0.2)).add(0, -0.12, 0);
			bolt.setPosition(muzzle);
			net.minecraft.util.math.Vec3d aim = user.getEyePos().add(look.multiply(30)).subtract(muzzle).normalize();
			bolt.setVelocity(aim.x, aim.y, aim.z, 3.8F, 0.4F);
			world.spawnEntity(bolt);
			stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.CROSSBOW_FIRE, SoundCategory.PLAYERS, 1.0F, 1.0F);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.dishonored.bow_mode", ammoFor(getMode(stack)).getName()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.dishonored.bow_switch").formatted(Formatting.DARK_GRAY));
	}
}
