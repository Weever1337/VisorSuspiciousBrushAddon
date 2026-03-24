package dev.wvr.visor.susbrush.forge.network;

import dev.wvr.visor.susbrush.core.common.network.NetworkChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ForgeNetworkChannel implements NetworkChannel {
    private static final String PROTOCOL_VERSION = "1";
    private final SimpleChannel channel;
    private final Map<ResourceLocation, PacketHandler> handlers = new HashMap<>();
    private int packetId = 0;

    public ForgeNetworkChannel(ResourceLocation channelName) {
        this.channel = NetworkRegistry.newSimpleChannel(
                channelName,
                () -> PROTOCOL_VERSION,
                s -> true,
                s -> true
        );

        registerGenericPacket();
    }

    private void registerGenericPacket() {
        channel.messageBuilder(GenericPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> {
                    buf.writeResourceLocation(packet.id);
                    buf.writeByteArray(packet.data);
                })
                .decoder(buf -> new GenericPacket(buf.readResourceLocation(), buf.readByteArray()))
                .consumerMainThread(this::handlePacket)
                .add();
    }

    private void handlePacket(GenericPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        PacketHandler handler = handlers.get(packet.id);
        if (handler != null) {
            var context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            handler.handle(packet.toFriendlyByteBuf(), sender);
        }
        contextSupplier.get().setPacketHandled(true);
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        channel.sendToServer(new GenericPacket(id, buf));
    }

    @Override
    public void registerReceiver(ResourceLocation id, NetworkChannel.PacketHandler handler) {
        handlers.put(id, handler);
    }

    public static class GenericPacket {
        public final ResourceLocation id;
        public final byte[] data;

        public GenericPacket(ResourceLocation id, FriendlyByteBuf buf) {
            this.id = id;
            this.data = new byte[buf.readableBytes()];
            buf.readBytes(data);
        }

        public GenericPacket(ResourceLocation id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        public FriendlyByteBuf toFriendlyByteBuf() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            return buf;
        }
    }
}
