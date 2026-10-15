package com.dishonoredmc.net;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Detection gauges of the hostile mobs near the player: entity id + level 0..250 (255 = alerted). */
public record AwarenessPayload(List<Integer> ids, List<Integer> levels) {
	public void write(PacketByteBuf buf) {
		buf.writeVarInt(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			buf.writeVarInt(ids.get(i));
			buf.writeByte(levels.get(i));
		}
	}

	public static AwarenessPayload read(PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<Integer> ids = new ArrayList<>(n), levels = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ids.add(buf.readVarInt());
			levels.add((int) buf.readUnsignedByte());
		}
		return new AwarenessPayload(ids, levels);
	}
}
