package com.dishonoredmc;

import com.dishonoredmc.item.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Corvo's loadout, given once per player per world: weapons and gadgets in the hotbar, ammo and elixirs in the backpack. */
public final class StarterKit {
	/** Saved with the player (command tags persist), so the kit is never given twice in a world. */
	private static final String TAG = "dishonored_starter_kit";

	private StarterKit() {}

	/** Put a stack in the first free main-inventory slot (9-35), or drop it if the backpack is full. */
	private static void stash(ServerPlayerEntity player, ItemStack stack) {
		PlayerInventory inv = player.getInventory();
		for (int i = 9; i < inv.main.size(); i++) {
			if (inv.getStack(i).isEmpty()) {
				inv.setStack(i, stack);
				return;
			}
		}
		player.dropItem(stack, false);
	}

	public static void giveOnce(ServerPlayerEntity player) {
		if (player.getCommandTags().contains(TAG)) return;
		player.addCommandTag(TAG);
		PlayerInventory inv = player.getInventory();
		Object[][] kit = {
			{ModItems.FOLDING_BLADE, 1},
			{ModItems.PISTOL, 1},
			{ModItems.WRIST_BOW, 1},
			{ModItems.GRENADE, 5},
			{ModItems.SPRING_RAZOR, 5},
		};
		for (int slot = 0; slot < kit.length; slot++) {
			ItemStack stack = new ItemStack((Item) kit[slot][0], (Integer) kit[slot][1]);
			ItemStack existing = inv.getStack(slot);
			// Anything already in the hotbar moves to the main inventory (or drops if it is full).
			if (!existing.isEmpty()) {
				inv.setStack(slot, ItemStack.EMPTY);
				stash(player, existing);
			}
			inv.setStack(slot, stack);
		}
		// Ammo and elixirs go in the backpack (main inventory), not the hotbar.
		stash(player, new ItemStack(ModItems.PISTOL_SHOT, 16));
		stash(player, new ItemStack(ModItems.CROSSBOW_BOLT, 16));
		stash(player, new ItemStack(ModItems.SLEEP_DART, 16));
		stash(player, new ItemStack(ModItems.INCENDIARY_BOLT, 16));
		stash(player, new ItemStack(ModItems.SOKOLOVS_ELIXIR, 3));
		stash(player, new ItemStack(ModItems.PIEROS_REMEDY, 3));
		inv.selectedSlot = 0;
	}
}
