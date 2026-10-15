package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

/** Client-driven Agility moves the server needs to know about (fall-damage reset, sounds for others). */
public record MovementPayload(int move) {
	public static final int DOUBLE_JUMP = 0;
	public static final int MANTLE = 1;
	public static final int SLIDE = 2;

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(move());
	}

	public static MovementPayload read(PacketByteBuf buf) {
		return new MovementPayload(buf.readVarInt());
	}
}
