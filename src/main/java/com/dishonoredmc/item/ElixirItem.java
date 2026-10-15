package com.dishonoredmc.item;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.power.Mana;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Piero's Spiritual Remedy (mana) and Sokolov's Elixir (health). */
public class ElixirItem extends Item {
	public enum Kind { HEALTH, MANA }

	private final Kind kind;

	public ElixirItem(Settings settings, Kind kind) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 24;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient) com.dishonoredmc.power.Elixirs.apply(user, kind);
		PlayerEntity player = user instanceof PlayerEntity p ? p : null;
		if (player != null) {
			player.incrementStat(Stats.USED.getOrCreateStat(this));
			if (!player.getAbilities().creativeMode) stack.decrement(1);
		}
		if (player == null || !player.isCreative()) {
			if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
			if (player != null) player.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE));
		}
		return stack;
	}
}
