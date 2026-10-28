package dev.hangel.animatedplayer.client;

import dev.hangel.animatedplayer.AnimatedPlayer;
import dev.hangel.animatedplayer.config.ModSettings;
import dev.hangel.animatedplayer.config.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** The renderer setup is done by the mixins (see MixinPlayerRenderer), shared with the Fabric version. */
@Mod(value = AnimatedPlayer.MODID, dist = Dist.CLIENT)
public class AnimatedPlayerClient {
    public AnimatedPlayerClient(ModContainer container) {
        ModSettings.load(FMLPaths.CONFIGDIR.get());
        // "Config" button of the mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientProxy.onClientTick());
    }
}
