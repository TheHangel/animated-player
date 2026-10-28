package dev.hangel.animatedplayer.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import dev.hangel.animatedplayer.AnimatedPlayer;

/**
 * The mod's own Steve (with face details and joint caps), used when a skin cannot be read. A 64x64 version of the
 * 1.7.10 mod's 64x32 texture, made by tools/make_steve64.py.
 */
public final class LegacySteve {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AnimatedPlayer.MODID, "textures/steve_64.png");
    private static SkinInfo info;

    private LegacySteve() {
    }

    public static ResourceLocation texture() {
        return TEXTURE;
    }

    public static SkinInfo skinInfo() {
        if (info == null) {
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(TEXTURE); NativeImage image = NativeImage.read(stream)) {
                info = SkinInfo.of(SkinInfo.FaceFeatures.read(image), image);
            } catch (IOException e) {
                AnimatedPlayer.LOGGER.error("Cannot read {}", TEXTURE, e);
                info = SkinInfo.of(SkinInfo.FaceFeatures.NONE, new NativeImage(64, 64, true));
            }
        }
        return info;
    }

    /** A 64x32 skin in the 64x64 layout, as HttpTexture#processLegacySkin makes it (left limbs mirrored). */
    public static NativeImage toModernLayout(NativeImage legacy) {
        NativeImage image = new NativeImage(64, 64, true);
        image.copyFrom(legacy);
        image.fillRect(0, 32, 64, 32, 0);
        image.copyRect(4, 16, 16, 32, 4, 4, true, false);
        image.copyRect(8, 16, 16, 32, 4, 4, true, false);
        image.copyRect(0, 20, 24, 32, 4, 12, true, false);
        image.copyRect(4, 20, 16, 32, 4, 12, true, false);
        image.copyRect(8, 20, 8, 32, 4, 12, true, false);
        image.copyRect(12, 20, 16, 32, 4, 12, true, false);
        image.copyRect(44, 16, -8, 32, 4, 4, true, false);
        image.copyRect(48, 16, -8, 32, 4, 4, true, false);
        image.copyRect(40, 20, 0, 32, 4, 12, true, false);
        image.copyRect(44, 20, -8, 32, 4, 12, true, false);
        image.copyRect(48, 20, -16, 32, 4, 12, true, false);
        image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return image;
    }

    /** Forget the skin info so it is read again from the (possibly changed) resource packs. */
    public static void reset() {
        info = null;
    }
}
