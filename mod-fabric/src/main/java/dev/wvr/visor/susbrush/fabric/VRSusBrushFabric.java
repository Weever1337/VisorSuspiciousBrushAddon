package dev.wvr.visor.susbrush.fabric;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import dev.wvr.visor.susbrush.core.client.AddonClient;
import dev.wvr.visor.susbrush.core.common.network.NetworkHelper;
import dev.wvr.visor.susbrush.core.server.AddonServer;
import dev.wvr.visor.susbrush.fabric.network.FabricNetworkChannel;
import net.fabricmc.api.ModInitializer;

public class VRSusBrushFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        NetworkHelper.setChannel(new FabricNetworkChannel());
        
        if(ModLoader.get().isDedicatedServer()){
            VisorAPI.registerAddon(
                    new AddonServer()
            );
        }else{
            VisorAPI.registerAddon(
                    new AddonClient()
            );
        }
    }
}
