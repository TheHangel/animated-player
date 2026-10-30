package dev.hangel.animatedplayer.debug;

import dev.hangel.animatedplayer.AnimatedPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ModListScreen;

/** NeoForge entry of the debug harness (runDebugClient): ticks DebugHarness and gives it NeoForge's screens. */
@EventBusSubscriber(modid = AnimatedPlayer.MODID, value = Dist.CLIENT)
public final class DebugNeoForge {
    static {
        DebugHarness.modListScreen = ModListScreen::new;
        DebugHarness.configScreen = parent -> {
            ModContainer container = ModList.get().getModContainerById(AnimatedPlayer.MODID).orElseThrow();
            return container.getCustomExtension(IConfigScreenFactory.class).orElseThrow().createScreen(container, parent);
        };
    }

    private DebugNeoForge() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        DebugHarness.onTick();
    }
}
