package com.dishonoredmc.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class Ammo {
	private Ammo() {}

	/** Takes one round from the inventory. Creative players never run dry. */
	static boolean consume(PlayerEntity player, Item ammo) {
		if (player.isCreative()) return true;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(ammo)) {
				stack.decrement(1);
				return true;
			}
		}
		return false;
	}

	static boolean has(PlayerEntity player, Item ammo) {
		return player.isCreative() || player.getInventory().contains(new ItemStack(ammo));
	}

	/** Pistol shot, crossbow bolt, sleep dart or incendiary bolt. */
	public static boolean isAmmo(net.minecraft.item.ItemStack stack) {
		return stack.isOf(ModItems.PISTOL_SHOT) || stack.isOf(ModItems.CROSSBOW_BOLT) || stack.isOf(ModItems.SLEEP_DART)
			|| stack.isOf(ModItems.INCENDIARY_BOLT);
	}
}
