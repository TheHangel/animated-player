package dev.hangel.animatedplayer.neoforge;

import dev.hangel.animatedplayer.AnimatedPlayer;
import dev.hangel.animatedplayer.client.ArmorTextures;
import dev.hangel.animatedplayer.client.ClientProxy;
import dev.hangel.animatedplayer.config.ModSettings;
import dev.hangel.animatedplayer.config.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge entry point (client only). The renderer setup is done by the common mixins (see MixinPlayerRenderer). */
@Mod(value = AnimatedPlayer.MODID, dist = Dist.CLIENT)
public class AnimatedPlayerNeoForge {
    public AnimatedPlayerNeoForge(ModContainer container) {
        ModSettings.load(FMLPaths.CONFIGDIR.get());
        ArmorTextures.provider = new NeoForgeArmorTextures();
        // "Config" button of the mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientProxy.onClientTick());
    }
}
