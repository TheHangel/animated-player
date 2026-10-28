package dev.hangel.animatedplayer.debug;

import dev.hangel.animatedplayer.AnimatedPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ModListScreen;

/** Loader-specific access to the settings screens for the "config" scenario (NeoForge). */
final class DebugConfig {
    private DebugConfig() {
    }

    static Screen modList(Screen parent) {
        return new ModListScreen(parent);
    }

    /** The screen the "Config" button of the Mods list opens. */
    static Screen configScreen(Screen parent) {
        ModContainer container = ModList.get().getModContainerById(AnimatedPlayer.MODID).orElseThrow();
        return container.getCustomExtension(IConfigScreenFactory.class).orElseThrow().createScreen(container, parent);
    }
}
