package dev.wvr.visor.susbrush.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import dev.wvr.visor.susbrush.core.client.AddonClient;
import dev.wvr.visor.susbrush.core.common.VRSusBrush;
import dev.wvr.visor.susbrush.core.common.network.NetworkHelper;
import dev.wvr.visor.susbrush.core.server.AddonServer;
import dev.wvr.visor.susbrush.forge.network.ForgeNetworkChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

@Mod(VRSusBrush.MOD_ID)
public class VRSusBrushForge {
    public VRSusBrushForge(){
        NetworkHelper.setChannel(new ForgeNetworkChannel(ResourceLocation.parse(VRSusBrush.MOD_ID + ":network")));
        
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
