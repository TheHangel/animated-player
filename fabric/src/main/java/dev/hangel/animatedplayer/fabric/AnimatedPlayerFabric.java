package dev.hangel.animatedplayer.fabric;

import dev.hangel.animatedplayer.client.ClientProxy;
import dev.hangel.animatedplayer.config.ModSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric entry point (client only). The renderer setup is done by the common mixins (see MixinPlayerRenderer). */
public class AnimatedPlayerFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModSettings.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ClientProxy.onClientTick());
    }
}
