package dev.hangel.animatedplayer.debug;

import dev.hangel.animatedplayer.config.PlayerPreview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One player of the settings preview on a flat magenta background, drawn at partial tick 1. The "gifs" scenario
 * advances the preview one tick, renders and captures, frame by frame, then keys the magenta out.
 */
final class GifScreen extends Screen {
    static final int KEY = 0xFFFF00FF;

    final PlayerPreview preview = new PlayerPreview();
    /** Captured box, in GUI pixels, centred: width and height. */
    int boxW = 160;
    int boxH = 240;
    /** Player box (GUI pixels, centred on the captured box, then offset): its size sets the player's scale. */
    int playerW = 160;
    int playerH = 240;
    int playerDx;
    int playerDy;

    GifScreen() {
        super(Component.literal("gif"));
    }

    int[] box() {
        return new int[]{(this.width - this.boxW) / 2, (this.height - this.boxH) / 2, this.boxW, this.boxH};
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, KEY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int[] b = this.box();
        int x0 = b[0] + (b[2] - this.playerW) / 2 + this.playerDx;
        int y0 = b[1] + (b[3] - this.playerH) / 2 + this.playerDy;
        graphics.enableScissor(b[0], b[1], b[0] + b[2], b[1] + b[3]);
        this.preview.render(graphics, x0, y0, x0 + this.playerW, y0 + this.playerH, 0, 0, 1.0F);
        graphics.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
