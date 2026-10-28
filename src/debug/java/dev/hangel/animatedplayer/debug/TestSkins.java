package dev.hangel.animatedplayer.debug;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import dev.hangel.animatedplayer.AnimatedPlayer;

/**
 * Debug-only skins and client-side players wearing them. The generated skins colour every cube face differently
 * (right side red, front green, left side blue, back yellow, top white, bottom magenta) with a brightness gradient
 * along U, the second layer as a lighter checkerboard, so wrong or mirrored UVs are obvious on screenshots.
 */
final class TestSkins {
    private static final int RED = 0xFF3030;
    private static final int GREEN = 0x30C030;
    private static final int BLUE = 0x3060FF;
    private static final int YELLOW = 0xF0E020;
    private static final int WHITE = 0xF0F0F0;
    private static final int MAGENTA = 0xD030D0;

    private TestSkins() {
    }

    static ResourceLocation generate(String name, boolean slim) {
        NativeImage img = new NativeImage(64, 64, true);
        int arm = slim ? 3 : 4;
        cube(img, 0, 0, 8, 8, 8, false);
        cube(img, 32, 0, 8, 8, 8, true);
        cube(img, 16, 16, 8, 12, 4, false);
        cube(img, 16, 32, 8, 12, 4, true);
        cube(img, 40, 16, arm, 12, 4, false);
        cube(img, 40, 32, arm, 12, 4, true);
        cube(img, 32, 48, arm, 12, 4, false);
        cube(img, 48, 48, arm, 12, 4, true);
        cube(img, 0, 16, 4, 12, 4, false);
        cube(img, 0, 32, 4, 12, 4, true);
        cube(img, 16, 48, 4, 12, 4, false);
        cube(img, 0, 48, 4, 12, 4, true);
        // Eyes on the face so the head orientation is readable.
        img.setPixelRGBA(10, 12, 0xFF000000);
        img.setPixelRGBA(13, 12, 0xFF000000);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AnimatedPlayer.MODID, "debug/" + name);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(img));
        return location;
    }

    /** Cape texture: red front, yellow back, so its orientation is readable. */
    static ResourceLocation generateCape(String name) {
        NativeImage img = new NativeImage(64, 32, true);
        face(img, 1, 1, 10, 16, RED, false);
        face(img, 12, 1, 10, 16, YELLOW, false);
        face(img, 0, 1, 1, 16, BLUE, false);
        face(img, 11, 1, 1, 16, BLUE, false);
        face(img, 1, 0, 10, 1, WHITE, false);
        face(img, 11, 0, 10, 1, WHITE, false);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AnimatedPlayer.MODID, "debug/" + name);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(img));
        return location;
    }

    private static void cube(NativeImage img, int ox, int oy, int w, int h, int d, boolean overlay) {
        face(img, ox, oy + d, d, h, RED, overlay);
        face(img, ox + d, oy + d, w, h, GREEN, overlay);
        face(img, ox + d + w, oy + d, d, h, BLUE, overlay);
        face(img, ox + d + w + d, oy + d, w, h, YELLOW, overlay);
        face(img, ox + d, oy, w, d, WHITE, overlay);
        face(img, ox + d + w, oy, w, d, MAGENTA, overlay);
    }

    private static void face(NativeImage img, int x0, int y0, int w, int h, int rgb, boolean overlay) {
        for (int x = 0; x < w; x++) {
            float shade = overlay ? 1.0F : 0.35F + 0.65F * x / Math.max(1, w - 1);
            for (int y = 0; y < h; y++) {
                if (overlay && (x + y) % 2 == 1) {
                    continue;
                }
                int r = (int) (((rgb >> 16) & 0xFF) * shade);
                int g = (int) (((rgb >> 8) & 0xFF) * shade);
                int b = (int) ((rgb & 0xFF) * shade);
                if (overlay) {
                    r = (r + 255) / 2;
                    g = (g + 255) / 2;
                    b = (b + 255) / 2;
                }
                // NativeImage is ABGR.
                img.setPixelRGBA(x0 + x, y0 + y, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        if (!overlay) {
            img.setPixelRGBA(x0, y0, 0xFF000000);
        }
    }

    /** A player only this client knows about, wearing the given skin. */
    static final class FakePlayer extends RemotePlayer {
        private final PlayerSkin skin;

        FakePlayer(ClientLevel level, String name, PlayerSkin skin) {
            super(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
            this.skin = skin;
            this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        }

        @Override
        public PlayerSkin getSkin() {
            return this.skin;
        }

        void giveParrots() {
            CompoundTag left = new CompoundTag();
            left.putString("id", "minecraft:parrot");
            left.putInt("Variant", 0);
            CompoundTag right = left.copy();
            right.putInt("Variant", 3);
            this.setShoulderEntityLeft(left);
            this.setShoulderEntityRight(right);
        }
    }

    static PlayerSkin skin(ResourceLocation texture, boolean slim) {
        return new PlayerSkin(texture, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
    }
}
