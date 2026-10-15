package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

/** Tells a client how long Bend Time lasts in its world and whether its Dark Vision is on. */
public record PowerStatePayload(int bendTimeTicks, boolean darkVision, int possessedId) {

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(bendTimeTicks());
		buf.writeBoolean(darkVision());
		buf.writeVarInt(possessedId() + 1);
	}

	public static PowerStatePayload read(PacketByteBuf buf) {
		return new PowerStatePayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt() - 1);
	}
}
