package dev.hangel.animatedplayer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Fabric entry of the debug harness (runDebugClient, its own "animatedplayer_debug" mod). */
public final class DebugFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DebugHarness.modListScreen = ModsScreen::new;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> DebugHarness.onTick());
        WorldRenderEvents.AFTER_ENTITIES.register(DebugFabric::renderPlayerForSideCam);
    }

    /**
     * Vanilla never draws the local player when another entity is the camera (NeoForge patches that in), so the
     * side camera would show an empty world: draw the player ourselves.
     */
    private static void renderPlayerForSideCam(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (DebugHarness.sideCamera() == null || player == null || mc.getCameraEntity() == player || context.consumers() == null) {
            return;
        }
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = context.camera().getPosition();
        double x = Mth.lerp(partialTick, player.xo, player.getX()) - cam.x;
        double y = Mth.lerp(partialTick, player.yo, player.getY()) - cam.y;
        double z = Mth.lerp(partialTick, player.zo, player.getZ()) - cam.z;
        PoseStack poseStack = context.matrixStack() != null ? context.matrixStack() : new PoseStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        int light = dispatcher.getPackedLightCoords(player, partialTick);
        dispatcher.render(player, x, y, z, Mth.lerp(partialTick, player.yRotO, player.getYRot()), partialTick, poseStack, context.consumers(), light);
    }
}
