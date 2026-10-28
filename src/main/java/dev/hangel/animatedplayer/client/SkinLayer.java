package dev.hangel.animatedplayer.client;

import dev.hangel.animatedplayer.client.model.ModelBase;
import dev.hangel.animatedplayer.client.model.ModelBox;
import dev.hangel.animatedplayer.client.model.ModelRenderer;

/**
 * Second skin layer (jacket, sleeves, pants) of modern 64x64 skins, built like the mod's 3D hat: one small voxel
 * per pixel, each showing that pixel on all its faces. Transparent pixels give invisible voxels.
 * Pixel placement follows the vanilla cube UV layout, so it matches the second layer vanilla draws. Limbs are split
 * at the elbow and knee: each segment is closed at both ends (with the limb's top / bottom pixels), so a bent joint
 * never shows the inside of the shell.
 */
public final class SkinLayer {
    /** Spacing of the voxels across a face, slightly larger than a pixel so the layer wraps around the limb. */
    private static final float SPREAD = 1.125F;
    /** Voxel centre distance outside the surface; voxels are 1 pixel thick. */
    private static final float OUT = 0.25F;

    private SkinLayer() {
    }

    /**
     * Layer for the rows [rowFrom, rowTo) of a w x 12 x d limb whose second layer texture starts at (ox, oy).
     * The segment occupies y in [y0, y0 + rowTo - rowFrom) of the parent's frame.
     */
    public static ModelRenderer build(ModelBase base, float x0, float y0, float z0, int w, int d, int rowFrom, int rowTo,
                                      int ox, int oy, boolean top, boolean bottom) {
        ModelRenderer layer = new ModelRenderer(base);
        float mx = x0 + w / 2.0F;
        float mz = z0 + d / 2.0F;
        for (int row = rowFrom; row < rowTo; row++) {
            float y = y0 + (row - rowFrom) + 0.5F;
            int pv = oy + d + row;
            for (int i = 0; i < w; i++) {
                float x = mx + (i + 0.5F - w / 2.0F) * SPREAD;
                voxel(layer, x, y, z0 - OUT, SPREAD, 1.0F, 1.0F, ox + d + i, pv);
                voxel(layer, x, y, z0 + d + OUT, SPREAD, 1.0F, 1.0F, ox + d + w + d + w - 1 - i, pv);
            }
            for (int k = 0; k < d; k++) {
                float z = mz + (k + 0.5F - d / 2.0F) * SPREAD;
                voxel(layer, x0 - OUT, y, z, 1.0F, 1.0F, SPREAD, ox + d - 1 - k, pv);
                voxel(layer, x0 + w + OUT, y, z, 1.0F, 1.0F, SPREAD, ox + d + w + k, pv);
            }
        }
        float yBottom = y0 + (rowTo - rowFrom);
        for (int i = 0; i < w; i++) {
            float x = mx + (i + 0.5F - w / 2.0F) * SPREAD;
            for (int k = 0; k < d; k++) {
                float z = mz + (k + 0.5F - d / 2.0F) * SPREAD;
                if (top) {
                    voxel(layer, x, y0 - OUT, z, SPREAD, 1.0F, SPREAD, ox + d + i, oy + d - 1 - k);
                }
                if (bottom) {
                    voxel(layer, x, yBottom + OUT, z, SPREAD, 1.0F, SPREAD, ox + d + w + i, oy + d - 1 - k);
                }
            }
        }
        return layer;
    }

    private static void voxel(ModelRenderer layer, float cx, float cy, float cz, float sx, float sy, float sz, int u, int v) {
        layer.cubeList.add(new ModelBox(layer, u, v, cx - sx / 2.0F, cy - sy / 2.0F, cz - sz / 2.0F, sx, sy, sz).initQuads());
    }
}
