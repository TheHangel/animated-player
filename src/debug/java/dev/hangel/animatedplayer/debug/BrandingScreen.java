package dev.hangel.animatedplayer.debug;

import dev.hangel.animatedplayer.config.PlayerPreview;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Draws the mod icon or banner (gradient, posed player, title) to be captured by the "branding" scenario. */
final class BrandingScreen extends Screen {
    enum Layout { ICON, BANNER }

    PlayerPreview preview = new PlayerPreview();
    Layout layout = Layout.ICON;
    int topColor = 0xFF1E6F78;
    int bottomColor = 0xFF2B2463;
    /** Icon framing: 1 whole body, larger values zoom on the upper body. */
    float zoom = 1.0F;
    Style style = Style.GRADIENT;
    private ResourceLocation backgroundTexture;
    private Style backgroundStyle;

    /** Background styles; GRADIENT uses topColor / bottomColor, the others are generated images or block textures. */
    enum Style {
        GRADIENT, RADIAL, SUNBURST, NIGHT_SKY, STRIPES, HALFTONE, BLUEPRINT, GRASS, STONE, PIXEL_CLOUDS
    }

    BrandingScreen() {
        super(Component.literal("Animated Player"));
    }

    /** Area captured, in GUI pixels: a square for the icon, 4:1 for the banner, centred. */
    int[] area() {
        if (this.layout == Layout.ICON) {
            int s = Math.min(this.width, this.height) - 20;
            return new int[]{(this.width - s) / 2, (this.height - s) / 2, s, s};
        }
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
        // Diagonal-ish gradient: deep teal to indigo, like a Minecraft night sky.
        this.drawBackground(graphics, x, y, w, h);
        int cx = this.layout == Layout.ICON ? x + w / 2 : x + w * 3 / 4;
        int cy = y + h / 2;
        // Thin light line along the bottom, as a ground.
        graphics.fillGradient(x, y + h - h / 12, x + w, y + h, 0x00FFFFFF, 0x30FFFFFF);
        if (this.layout == Layout.ICON) {
            int pad = h / 10;
            float z = this.zoom;
            int boxH = (int) (h * 1.1F * z);
            int top = y + h / 25 + (int) ((z - 1.0F) * h * 0.08F);
            graphics.enableScissor(x, y, x + w, y + h);
            this.preview.render(graphics, cx - boxH, top, cx + boxH, top + boxH, cx - h, cy - h, 1.0F);
            graphics.disableScissor();
        } else {
            graphics.enableScissor(x, y, x + w, y + h);
            this.preview.render(graphics, x + w / 2, y + h / 20, x + w, y + h * 6 / 5, cx - h, cy - h / 2, 1.0F);
            graphics.disableScissor();
            float scale = h / 40.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + w * 0.07F, y + h * 0.30F, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(this.font, "Animated Player", 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popPose();
            float small = scale * 0.42F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + w * 0.07F + 2, y + h * 0.30F + 12 * scale, 0.0F);
            graphics.pose().scale(small, small, 1.0F);
            graphics.drawString(this.font, "Smooth, articulated player animations", 0, 0, 0xFFB8E6EC, true);
            graphics.pose().popPose();
        }
    }

    private void drawBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        switch (this.style) {
            case GRADIENT -> graphics.fillGradient(x, y, x + w, y + h, this.topColor, this.bottomColor);
            case GRASS, STONE -> {
                ResourceLocation block = ResourceLocation.withDefaultNamespace(
                        this.style == Style.GRASS ? "textures/block/grass_block_side.png" : "textures/block/stone.png");
                // Big pixels: 4 blocks across, tiled (block textures repeat).
                int tile = Math.max(1, w / 4);
                graphics.blit(block, x, y, 0.0F, 0.0F, w, h, tile, tile);
                graphics.fillGradient(x, y, x + w, y + h, this.topColor, this.bottomColor);
            }
            default -> {
                if (this.backgroundStyle != this.style) {
                    NativeImage image = generate(this.style, this.topColor, this.bottomColor);
                    this.backgroundTexture = ResourceLocation.fromNamespaceAndPath("animatedplayer", "debug/brand_bg_" + this.style.name().toLowerCase(java.util.Locale.ROOT));
                    Minecraft.getInstance().getTextureManager().register(this.backgroundTexture, new DynamicTexture(image));
                    this.backgroundStyle = this.style;
                }
                graphics.blit(this.backgroundTexture, x, y, 0.0F, 0.0F, w, h, w, h);
            }
        }
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** ARGB to NativeImage ABGR. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF;
    }

    /** 128x128 pixel-art backgrounds (drawn stretched, so pixels stay crisp). */
    private static NativeImage generate(Style style, int top, int bottom) {
        int n = 128;
        NativeImage image = new NativeImage(n, n, false);
        java.util.Random random = new java.util.Random(7);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                float u = (px + 0.5F) / n;
                float v = (py + 0.5F) / n;
                float dx = u - 0.5F;
                float dy = v - 0.48F;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                int c = mix(top, bottom, v);
                switch (style) {
                    case RADIAL -> c = mix(mix(top, 0xFFFFFFFF, 0.55F), bottom, dist * 1.6F);
                    case SUNBURST -> {
                        double angle = Math.atan2(dy, dx);
                        boolean ray = ((int) Math.floor((angle + Math.PI) / (Math.PI / 8))) % 2 == 0;
                        c = mix(ray ? top : mix(top, bottom, 0.45F), bottom, dist * 1.3F);
                    }
                    case NIGHT_SKY -> {
                        c = mix(top, bottom, v * 1.1F);
                        if (random.nextFloat() < 0.012F) {
                            c = random.nextBoolean() ? 0xFFFFFFFF : 0xFFBFD8FF;
                        }
                        if (px >= 96 && px < 108 && py >= 12 && py < 24) {
                            c = 0xFFF2F0D8;
                        }
                    }
                    case STRIPES -> c = ((px + py) / 10) % 2 == 0 ? mix(top, bottom, v) : mix(mix(top, 0xFFFFFFFF, 0.15F), bottom, v);
                    case HALFTONE -> {
                        int cell = 8;
                        float cx = (px % cell) - cell / 2.0F + 0.5F;
                        float cy = (py % cell) - cell / 2.0F + 0.5F;
                        float radius = 3.2F * (1.0F - v);
                        if (cx * cx + cy * cy < radius * radius) {
                            c = mix(c, 0xFFFFFFFF, 0.25F);
                        }
                    }
                    case BLUEPRINT -> {
                        c = mix(top, bottom, dist * 1.2F);
                        if (px % 16 == 0 || py % 16 == 0) {
                            c = mix(c, 0xFFFFFFFF, 0.3F);
                        } else if (px % 4 == 0 || py % 4 == 0) {
                            c = mix(c, 0xFFFFFFFF, 0.08F);
                        }
                    }
                    case PIXEL_CLOUDS -> {
                        c = mix(top, bottom, v);
                        int bx = px / 8;
                        int by = py / 8;
                        boolean cloud = by >= 2 && by <= 3 && (bx * 7 + by * 3) % 11 < 5
                                || by == 9 && (bx * 5 + 3) % 13 < 6;
                        if (cloud) {
                            c = mix(c, 0xFFFFFFFF, 0.75F);
                        }
                    }
                    default -> {
                    }
                }
                image.setPixelRGBA(px, py, abgr(c));
            }
        }
        return image;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
