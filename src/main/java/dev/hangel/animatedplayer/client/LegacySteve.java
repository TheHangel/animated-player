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

    /** Forget the skin info so it is read again from the (possibly changed) resource packs. */
    public static void reset() {
        info = null;
    }
}
