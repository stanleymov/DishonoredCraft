package com.dishonoredmc.power;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.item.ElixirItem;
import com.dishonoredmc.item.ModItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Sokolov's Elixir and Piero's Remedy effects, plus Dishonored's quick-drink from the inventory. */
public final class Elixirs {
	private Elixirs() {}

	public static void apply(LivingEntity user, ElixirItem.Kind kind) {
		user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
			kind == ElixirItem.Kind.MANA ? ModSounds.ELIXIR_MANA : ModSounds.ELIXIR_HEALTH, SoundCategory.PLAYERS, 1.0F, 1.0F);
		switch (kind) {
			case HEALTH -> {
				user.heal(12.0F);
				user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
			}
			case MANA -> {
				if (user instanceof ServerPlayerEntity player) Mana.set(player, Mana.MAX);
			}
		}
	}

	/** Drink one elixir of this kind from anywhere in the inventory. Returns false if none is carried. */
	public static boolean drink(ServerPlayerEntity player, ElixirItem.Kind kind) {
		Item item = kind == ElixirItem.Kind.MANA ? ModItems.PIEROS_REMEDY : ModItems.SOKOLOVS_ELIXIR;
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isOf(item)) continue;
			if (!player.isCreative()) {
				stack.decrement(1);
				inv.offerOrDrop(new ItemStack(Items.GLASS_BOTTLE));
			}
			apply(player, kind);
			player.sendMessage(Text.translatable(kind == ElixirItem.Kind.MANA ? "message.dishonored.drank_mana" : "message.dishonored.drank_health")
				.formatted(kind == ElixirItem.Kind.MANA ? Formatting.AQUA : Formatting.RED), true);
			return true;
		}
		return false;
	}

	public static int count(ServerPlayerEntity player, ElixirItem.Kind kind) {
		Item item = kind == ElixirItem.Kind.MANA ? ModItems.PIEROS_REMEDY : ModItems.SOKOLOVS_ELIXIR;
		return player.getInventory().count(item);
	}
}
