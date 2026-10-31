package dev.hangel.animatedplayer.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import dev.hangel.animatedplayer.AnimatedPlayer;
import dev.hangel.animatedplayer.client.model.ModelBox;

/**
 * What the mod needs to know about a skin, read on the CPU when the skin is loaded (1.7.10 read the downloaded image;
 * the first port read the texture back from the GPU instead, which stalls rendering and is gone in later versions).
 * <ul>
 * <li>the face features drawn in the hidden area (x 24..31, y 0..3): eyes, eyebrows, mouth;</li>
 * <li>which pixels are opaque, so transparent voxels of the 3D layers are skipped.</li>
 * </ul>
 */
public final class SkinInfo implements ModelBox.SkinPixels {
    private static final Map<ResourceLocation, SkinInfo> CACHE = new HashMap<>();

    public final boolean eyes1;
    public final boolean eyes2;
    public final boolean eyes3;
    public final boolean eyebrows;
    public final boolean mouth;
    private final long[] opaqueRows = new long[64];
    /** Pixels the skin's author painted: non-transparent in the image as authored (64 rows; 32 for old skins). */
    private final long[] paintedRows;

    private SkinInfo(FaceFeatures face, NativeImage image) {
        this.eyes1 = face.eyes1;
        this.eyes2 = face.eyes2;
        this.eyes3 = face.eyes3;
        this.eyebrows = face.eyebrows;
        this.mouth = face.mouth;
        this.paintedRows = face.painted;
        int height = Math.min(64, image.getHeight());
        int width = Math.min(64, image.getWidth());
        for (int y = 0; y < height; y++) {
            long row = 0L;
            for (int x = 0; x < width; x++) {
                if ((image.getPixelRGBA(x, y) >>> 24) != 0) {
                    row |= 1L << x;
                }
            }
            this.opaqueRows[y] = row;
        }
    }

    public static SkinInfo of(FaceFeatures face, NativeImage finalImage) {
        return new SkinInfo(face, finalImage);
    }

    @Override
    public boolean isPainted(int pixel) {
        int y = pixel >> 6;
        return y < 64 && (this.paintedRows[y] >>> (pixel & 63) & 1L) != 0;
    }

    @Override
    public boolean isOpaque(int pixel) {
        int y = pixel >> 6;
        return y < 64 && (this.opaqueRows[y] >>> (pixel & 63) & 1L) != 0;
    }

    /** Info for a skin texture, or null while it is not loaded yet. */
    public static SkinInfo get(ResourceLocation location) {
        SkinInfo cached = CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location, null);
        SkinInfo info = null;
        if (texture instanceof SkinInfoHolder holder) {
            // Downloaded skin: filled in by MixinHttpTexture when the image was processed. If it was not (another
            // mod loading skins its own way, for instance), read the downloaded file from the game's skin cache.
            info = holder.animatedplayer$getSkinInfo();
            if (info == null) {
                info = readCacheFile(location, holder.animatedplayer$getCacheFile());
            }
        } else if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
            info = new SkinInfo(FaceFeatures.read(dynamic.getPixels()), dynamic.getPixels());
        } else if (texture == null || texture instanceof SimpleTexture) {
            // Resource skins (default skins, the mod's copies of them), loaded or not yet drawn.
            info = readResource(location);
        }
        if (info != null) {
            CACHE.put(location, info);
        }
        return info;
    }

    private static final java.util.Set<ResourceLocation> LOGGED = new java.util.HashSet<>();

    private static SkinInfo readCacheFile(ResourceLocation location, File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream stream = new FileInputStream(file); NativeImage raw = NativeImage.read(stream)) {
            if (LOGGED.add(location)) {
                AnimatedPlayer.LOGGER.info("Skin {} read from the skin cache ({})", location, file);
            }
            FaceFeatures face = FaceFeatures.read(raw);
            if (raw.getHeight() == 32) {
                // Old 64x32 skin: same layout as the game makes of it.
                try (NativeImage converted = toModernLayout(raw)) {
                    return new SkinInfo(face, converted);
                }
            }
            return new SkinInfo(face, raw);
        } catch (IOException | IllegalArgumentException e) {
            if (LOGGED.add(location)) {
                AnimatedPlayer.LOGGER.warn("Cannot read skin {} from {}", location, file, e);
            }
            return null;
        }
    }

    /** A 64x32 skin in the 64x64 layout, as HttpTexture#processLegacySkin makes it (left limbs mirrored). */
    private static NativeImage toModernLayout(NativeImage legacy) {
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

    /** Default skins (Steve, Alex...) and resource pack skins are plain resources. */
    private static SkinInfo readResource(ResourceLocation location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return new SkinInfo(FaceFeatures.read(image), image);
        } catch (IOException e) {
            AnimatedPlayer.LOGGER.warn("Cannot read skin {}", location, e);
            return null;
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Implemented on HttpTexture by MixinHttpTexture. */
    public interface SkinInfoHolder {
        SkinInfo animatedplayer$getSkinInfo();

        /** Where the downloaded skin is cached, or null. */
        File animatedplayer$getCacheFile();
    }

    /**
     * The mod's face convention. Only pixels actually painted count: the game makes the whole head area of
     * downloaded skins opaque, which turned erased (transparent but coloured) pixels into "eyes" before.
     * Must be read on the image as downloaded, before that processing.
     */
    public record FaceFeatures(boolean eyes1, boolean eyes2, boolean eyes3, boolean eyebrows, boolean mouth, long[] painted) {
        public static final FaceFeatures NONE = new FaceFeatures(false, false, false, false, false, new long[64]);

        public static FaceFeatures read(NativeImage img) {
            if (img.getWidth() < 64 || img.getHeight() < 32) {
                return NONE;
            }
            long[] painted = new long[64];
            for (int y = 0; y < Math.min(64, img.getHeight()); y++) {
                for (int x = 0; x < 64; x++) {
                    if ((img.getPixelRGBA(x, y) >>> 24) != 0) {
                        painted[y] |= 1L << x;
                    }
                }
            }
            return new FaceFeatures(eyes(img, 0), eyes(img, 1), eyes(img, 2), eyebrows(img), mouth(img), painted);
        }

        /** As 1.7.10, which saw the pixels made opaque: no transparent, black or white pixel. */
        private static boolean isSuitableColor(int abgr) {
            if ((abgr >>> 24) == 0) {
                return false;
            }
            int rgb = abgr & 0xFFFFFF;
            return rgb != 0 && rgb != 0xFFFFFF;
        }

        private static boolean eyes(NativeImage img, int row) {
            for (int x = 0; x < 5; x++) {
                if (isSuitableColor(img.getPixelRGBA(x + 27, row))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean eyebrows(NativeImage img) {
            for (int x = 0; x < 3; x++) {
                if (isSuitableColor(img.getPixelRGBA(x + 24, 0))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean mouth(NativeImage img) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 2; x++) {
                    if (isSuitableColor(img.getPixelRGBA(x + 24, y + 1))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
