package dev.wvr.visor.susbrush.core.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class NetworkHelper {
    private static NetworkChannel channel;

    public static NetworkChannel getChannel() {
        if (channel == null) {
            throw new IllegalStateException("NetworkChannel not initialized!");
        }
        return channel;
    }

    public static void setChannel(NetworkChannel channel) {
        NetworkHelper.channel = channel;
    }

    public static void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        getChannel().sendToServer(id, buf);
    }

    public static void registerServerReceiver(ResourceLocation id, NetworkChannel.PacketHandler handler) {
        getChannel().registerReceiver(id, handler);
    }
}
