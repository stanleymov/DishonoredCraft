package com.dishonoredmc;

import com.dishonoredmc.item.BoneCharmItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;

import java.util.function.BooleanSupplier;

/**
 * Corvo's six bone charm slots. Each player carries them (PlayerEntityMixin); they are extra slots of the player's
 * own inventory screen (PlayerScreenHandlerMixin), shown in a panel the Mark of the Outsider button opens
 * (client: CharmPanel). A charm works while it sits in one of these slots.
 */
public final class BoneCharms {
	public static final int SLOTS = 6;
	/** Where the slots sit relative to the inventory screen: a column left of it, centred vertically. */
	public static final int SLOT_X = -24, SLOT_Y = 30;
	/** Client: whether the charm panel is showing (set by CharmPanel). Hidden slots can't be seen or clicked. */
	public static BooleanSupplier panelOpen = () -> false;

	public interface Holder {
		SimpleInventory dishonored$charms();
	}

	private BoneCharms() {}

	public static SimpleInventory of(PlayerEntity player) {
		return ((Holder) player).dishonored$charms();
	}

	/** True while `charm` is equipped in one of the player's bone charm slots. */
	public static boolean has(PlayerEntity player, Item charm) {
		SimpleInventory inv = of(player);
		for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(charm)) return true;
		return false;
	}

	private static final java.util.UUID SWIFT_SHADOW_ID = java.util.UUID.fromString("3c1f9a7e-5b2d-4e8a-9c61-7d2e4f0a8b11");
	private static final java.util.UUID WHIRLWIND_ID = java.util.UUID.fromString("8e4b2c19-7a3f-4d6e-b0c2-1f9a6e3d5c22");

	/** Swift Shadow: crouch-walking speed multiplier (applied to the crouch slow-down, see KeyboardInputMixin). */
	public static final float SWIFT_SHADOW = 1.3f;

	/** Every tick, both sides: Whirlwind (faster sword swings). */
	public static void tick(PlayerEntity player) {
		// (Older builds gave Swift Shadow as a movement-speed bonus, which also widened the view like sprinting.)
		bonus(player, net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED, SWIFT_SHADOW_ID, 0.0, false);
		bonus(player, net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED, WHIRLWIND_ID, 0.25,
			com.dishonoredmc.item.FoldingBladeItem.bladeOut(player) && has(player, com.dishonoredmc.item.ModItems.CHARM_WHIRLWIND));
	}

	private static void bonus(PlayerEntity player, net.minecraft.entity.attribute.EntityAttribute attribute, java.util.UUID id, double amount, boolean on) {
		var inst = player.getAttributeInstance(attribute);
		if (inst == null) return;
		boolean has = inst.getModifier(id) != null;
		if (on && !has) inst.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(id, "Bone charm", amount,
			net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		else if (!on && has) inst.removeModifier(id);
	}

	public static NbtList write(SimpleInventory inv) {
		NbtList list = new NbtList();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) continue;
			NbtCompound c = s.writeNbt(new NbtCompound());
			c.putByte("Slot", (byte) i);
			list.add(c);
		}
		return list;
	}

	public static void read(SimpleInventory inv, NbtList list) {
		inv.clear();
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			int slot = c.getByte("Slot") & 255;
			if (slot < inv.size()) inv.setStack(slot, ItemStack.fromNbt(c));
		}
	}

	/** Respawn / return from the End: the new player entity keeps its charms. */
	public static void copy(PlayerEntity from, PlayerEntity to) {
		SimpleInventory a = of(from), b = of(to);
		for (int i = 0; i < a.size(); i++) b.setStack(i, a.getStack(i).copy());
	}

	/** One bone charm slot: takes a single charm, and not one already equipped in another slot. */
	public static final class CharmSlot extends Slot {
		private final boolean client;

		public CharmSlot(SimpleInventory inv, int index, boolean client) {
			super(inv, index, SLOT_X, SLOT_Y + index * 18);
			this.client = client;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			if (!(stack.getItem() instanceof BoneCharmItem)) return false;
			for (int i = 0; i < inventory.size(); i++) {
				if (i != getIndex() && inventory.getStack(i).isOf(stack.getItem())) return false;
			}
			return true;
		}

		@Override
		public int getMaxItemCount() {
			return 1;
		}

		@Override
		public boolean isEnabled() {
			return !client || panelOpen.getAsBoolean();
		}
	}
}
