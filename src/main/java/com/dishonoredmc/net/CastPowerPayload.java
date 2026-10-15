package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

/** Client asks the server to cast a power. */
public record CastPowerPayload(int power) {

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(power());
	}

	public static CastPowerPayload read(PacketByteBuf buf) {
		return new CastPowerPayload(buf.readVarInt());
	}
}
