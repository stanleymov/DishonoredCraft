package com.dishonoredmc.net;

import com.dishonoredmc.DishonoredMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The mod's Forge network channel. Every message type is registered on both sides at startup; the server and
 * client code then attach handlers with {@link #onServer} / {@link #onClient}.
 */
public final class Net {
	private static final String VERSION = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(DishonoredMod.id("main"),
		() -> VERSION, VERSION::equals, VERSION::equals);

	private static final Map<Class<?>, BiConsumer<Object, ServerPlayerEntity>> serverHandlers = new HashMap<>();
	private static final Map<Class<?>, Consumer<Object>> clientHandlers = new HashMap<>();
	private static int nextId;

	private Net() {}

	public static void register() {
		toServerType(CastPowerPayload.class, CastPowerPayload::write, CastPowerPayload::read);
		toServerType(MovementPayload.class, MovementPayload::write, MovementPayload::read);
		toServerType(ActionPayload.class, ActionPayload::write, ActionPayload::read);
		toClientType(ManaSyncPayload.class, ManaSyncPayload::write, ManaSyncPayload::read);
		toClientType(PowerStatePayload.class, PowerStatePayload::write, PowerStatePayload::read);
		toClientType(AnimEventPayload.class, AnimEventPayload::write, AnimEventPayload::read);
		toClientType(AwarenessPayload.class, AwarenessPayload::write, AwarenessPayload::read);
	}

	private static <T> void toServerType(Class<T> type, BiConsumer<T, PacketByteBuf> write, Function<PacketByteBuf, T> read) {
		CHANNEL.registerMessage(nextId++, type, write, read, (T msg, Supplier<NetworkEvent.Context> ctx) -> {
			NetworkEvent.Context c = ctx.get();
			ServerPlayerEntity sender = c.getSender();
			BiConsumer<Object, ServerPlayerEntity> h = serverHandlers.get(type);
			if (sender != null && h != null) c.enqueueWork(() -> h.accept(msg, sender));
			c.setPacketHandled(true);
		}, Optional.of(NetworkDirection.PLAY_TO_SERVER));
	}

	private static <T> void toClientType(Class<T> type, BiConsumer<T, PacketByteBuf> write, Function<PacketByteBuf, T> read) {
		CHANNEL.registerMessage(nextId++, type, write, read, (T msg, Supplier<NetworkEvent.Context> ctx) -> {
			NetworkEvent.Context c = ctx.get();
			Consumer<Object> h = clientHandlers.get(type);
			if (h != null) c.enqueueWork(() -> h.accept(msg));
			c.setPacketHandled(true);
		}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}

	@SuppressWarnings("unchecked")
	public static <T> void onServer(Class<T> type, BiConsumer<T, ServerPlayerEntity> handler) {
		serverHandlers.put(type, (BiConsumer<Object, ServerPlayerEntity>) handler);
	}

	@SuppressWarnings("unchecked")
	public static <T> void onClient(Class<T> type, Consumer<T> handler) {
		clientHandlers.put(type, (Consumer<Object>) handler);
	}

	public static void toServer(Object msg) {
		CHANNEL.sendToServer(msg);
	}

	public static void toPlayer(ServerPlayerEntity player, Object msg) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
	}
}
