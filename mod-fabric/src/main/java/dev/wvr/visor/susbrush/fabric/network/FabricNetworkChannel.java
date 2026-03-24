package dev.wvr.visor.susbrush.fabric.network;

import dev.wvr.visor.susbrush.core.common.network.NetworkChannel;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class FabricNetworkChannel implements NetworkChannel {
    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        FriendlyByteBuf packetBuf = PacketByteBufs.create();
        packetBuf.writeByteArray(data);
        ClientPlayNetworking.send(id, packetBuf);
    }

    @Override
    public void registerReceiver(ResourceLocation id, PacketHandler handler) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler1, buf, responseSender) -> {
            byte[] data = buf.readByteArray();
            FriendlyByteBuf newBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            handler.handle(newBuf, player);
        });
    }
}
