package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

public record ManaSyncPayload(float mana, float max) {

	public void write(PacketByteBuf buf) {
		buf.writeFloat(mana());
		buf.writeFloat(max());
	}

	public static ManaSyncPayload read(PacketByteBuf buf) {
		return new ManaSyncPayload(buf.readFloat(), buf.readFloat());
	}
}
