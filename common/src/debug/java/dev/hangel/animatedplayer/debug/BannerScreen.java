package dev.hangel.animatedplayer.debug;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hangel.animatedplayer.config.PlayerPreview;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Mod banner (4:1): sunburst background, a row of players in different poses seen from the same angle, optional title. */
final class BannerScreen extends Screen {
    /** One pose of the row: demo, ticks played before freezing, sword in hand. */
    record Pose(PlayerPreview.Demo demo, int ticks, boolean sword) {
    }

    final List<PlayerPreview> players = new ArrayList<>();
    int raysColor = 0xFFFFC94A;
    int edgeColor = 0xFFE0602A;
    enum TitlePlace { NONE, LEFT, RIGHT, CENTER, TOP }

    TitlePlace title = TitlePlace.NONE;
    private ResourceLocation background;

    BannerScreen(List<Pose> poses) {
        super(Component.literal("Animated Player"));
        for (Pose pose : poses) {
            PlayerPreview preview = new PlayerPreview();
            preview.holdSword = pose.sword();
            preview.fixedHead = true;
            // Same orientation as the icon: turned to the right.
            preview.rotate(-60.0F);
            preview.setDemo(pose.demo());
            for (int i = 0; i < pose.ticks(); i++) {
                preview.tick();
            }
            preview.frozen = true;
            this.players.add(preview);
        }
    }

    int[] area() {
        int w = this.width - 20;
        int h = w / 4;
        return new int[]{10, (this.height - h) / 2, w, h};
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF101418);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int[] a = this.area();
        int x = a[0], y = a[1], w = a[2], h = a[3];
        if (this.background == null) {
            this.background = ResourceLocation.fromNamespaceAndPath("animatedplayer", "debug/banner_bg_" + Integer.toHexString(this.raysColor));
            Minecraft.getInstance().getTextureManager().register(this.background, new DynamicTexture(sunburst(this.raysColor, this.edgeColor)));
        }
        graphics.blit(this.background, x, y, 0.0F, 0.0F, w, h, w, h);
        graphics.fillGradient(x, y + h - h / 8, x + w, y + h, 0x00000000, 0x40000000);

        int n = this.players.size();
        TitlePlace place = this.title;
        int titleW = place == TitlePlace.LEFT || place == TitlePlace.RIGHT || place == TitlePlace.CENTER ? w * 2 / 5 : 0;
        int rowTop = place == TitlePlace.TOP ? y + h * 2 / 5 : y + h / 12;
        int rowX = place == TitlePlace.LEFT ? x + titleW : x;
        int slotW = (w - titleW) / n;
        graphics.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < n; i++) {
            int sx = rowX + i * slotW + (place == TitlePlace.CENTER && i >= n / 2 ? titleW : 0);
            this.players.get(i).render(graphics, sx, rowTop, sx + slotW, y + h, 0, 0, 1.0F);
        }
        graphics.disableScissor();

        if (place != TitlePlace.NONE) {
            float centre;
            float top;
            float scale;
            switch (place) {
                case LEFT -> centre = x + titleW / 2.0F;
                case RIGHT -> centre = x + w - titleW / 2.0F;
                case CENTER -> centre = x + (n / 2) * slotW + titleW / 2.0F;
                default -> centre = x + w / 2.0F;
            }
            if (place == TitlePlace.TOP) {
                scale = h / 38.0F;
                top = y + h * 0.06F;
            } else {
                scale = h / 52.0F;
                top = y + h * 0.38F;
            }
            String text = "Animated Player";
            graphics.pose().pushPose();
            graphics.pose().translate(centre, top, 100.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(this.font, text, -this.font.width(text) / 2, 0, 0xFFFFFFFF, true);
            graphics.pose().popPose();
            String sub = "Smooth, articulated player animations";
            float small = scale * 0.4F;
            graphics.pose().pushPose();
            graphics.pose().translate(centre, top + 11 * scale, 100.0F);
            graphics.pose().scale(small, small, 1.0F);
            graphics.drawString(this.font, sub, -this.font.width(sub) / 2, 0, 0xFFFFF4D8, true);
            graphics.pose().popPose();
        }
    }

    /** 256x64 pixel-art rays from the centre, darker towards the edges (same style as the icon). */
    private static NativeImage sunburst(int rays, int edge) {
        int nw = 256;
        int nh = 64;
        NativeImage image = new NativeImage(nw, nh, false);
        for (int py = 0; py < nh; py++) {
            for (int px = 0; px < nw; px++) {
                float dx = (px + 0.5F - nw / 2.0F) / nh;
                float dy = (py + 0.5F - nh * 0.48F) / nh;
                double angle = Math.atan2(dy, dx);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                boolean ray = ((int) Math.floor((angle + Math.PI) / (Math.PI / 12))) % 2 == 0;
                int c = mix(ray ? rays : mix(rays, edge, 0.45F), edge, dist * 0.55F);
                image.setPixelRGBA(px, py, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
            }
        }
        return image;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
