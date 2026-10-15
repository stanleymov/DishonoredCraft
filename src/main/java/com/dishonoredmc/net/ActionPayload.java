package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

/**
 * Client requests from the HUD and weapon wheel.
 * QUICK_HEALTH / QUICK_MANA drink an elixir from the inventory; EQUIP brings `item` (a registry id) into the
 * selected hotbar slot, with `arg` = crossbow ammo mode for the wrist bow (-1 = unchanged).
 */
public record ActionPayload(int action, String item, int arg) {
	public static final int QUICK_HEALTH = 0;
	public static final int QUICK_MANA = 1;
	public static final int EQUIP = 2;
	/** Attack pressed while falling onto a mob: drop assassination on whatever is below. */
	public static final int DROP_ASSASSINATE = 3;
	/** Weapon wheel opened (arg 1) or closed (arg 0): slows the world down while it is open. */
	public static final int WHEEL = 4;
	/** Folding blade swing at the block whose BlockPos.asLong() is in `item`. */
	public static final int BLADE_BREAK = 5;
	/** Redirective Blink: the player started (arg 1) or stopped (arg 0) holding time still. */
	public static final int REDIRECTIVE = 6;
	/** Cycle the wrist bow's bolt type (key V). */
	public static final int CYCLE_BOLT = 7;
	/** Block with the blade while a gun or gadget is in hand: started (arg 1) or stopped (arg 0). */
	public static final int BLADE_BLOCK = 8;
	/** The block / choke button pressed on the mob whose entity id is `arg`: choke it if it can be choked. */
	public static final int CHOKE = 9;

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(action());
		buf.writeString(item());
		buf.writeVarInt(arg());
	}

	public static ActionPayload read(PacketByteBuf buf) {
		return new ActionPayload(buf.readVarInt(), buf.readString(), buf.readVarInt());
	}
}
