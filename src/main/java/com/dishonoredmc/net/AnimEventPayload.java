package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

/** Server tells the client to play a first-person takedown animation. */
public record AnimEventPayload(int event) {
	public static final int DROP_ASSASSINATION = 0;
	public static final int BACK_ASSASSINATION = 1;
	public static final int FRONT_ASSASSINATION = 2;
	public static final int CHOKE_START = 3;
	public static final int CHOKE_WIN = 4;
	public static final int CHOKE_CANCEL = 5;

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(event());
	}

	public static AnimEventPayload read(PacketByteBuf buf) {
		return new AnimEventPayload(buf.readVarInt());
	}
}
