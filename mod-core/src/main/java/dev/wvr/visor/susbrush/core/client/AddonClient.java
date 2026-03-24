package dev.wvr.visor.susbrush.core.client;

import dev.wvr.visor.susbrush.core.common.AddonNetworking;
import dev.wvr.visor.susbrush.core.common.VRSusBrush;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.visor.api.common.addon.VisorAddon;

public class AddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        AddonNetworking.initCommon();
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "dev.wvr.visor.susbrush.core.client";
    }

    @Override
    public @NotNull String getAddonId() {
        return VRSusBrush.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VRSusBrush.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VRSusBrush.MOD_ID;
    }
}
