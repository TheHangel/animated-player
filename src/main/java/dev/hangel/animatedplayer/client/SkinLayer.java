package dev.hangel.animatedplayer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hangel.animatedplayer.client.model.ModelBase;
import dev.hangel.animatedplayer.client.model.ModelBox;
import dev.hangel.animatedplayer.client.model.ModelRenderer;
import dev.hangel.animatedplayer.config.ModSettings;

/**
 * Second skin layer (jacket, sleeves, pants) of modern 64x64 skins, built like the mod's 3D hat: one small voxel
 * per pixel, each showing that pixel on all its faces. Transparent pixels give invisible voxels.
 * Pixel placement follows the vanilla cube UV layout, so it matches the second layer vanilla draws. Limbs are split
 * at the elbow and knee: each segment is closed at both ends (with the limb's top / bottom pixels), so a bent joint
 * never shows the inside of the shell. With the SkinLayers3D option off, the vanilla flat layer is drawn instead.
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
        ModelRenderer voxels = new ModelRenderer(base);
        ModelRenderer layer = voxels;
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
        return new Switch(base, voxels, flat(base, x0, y0, z0, w, d, rowFrom, rowTo, ox, oy));
    }

    /** Vanilla's flat second layer for the same rows: a box 0.25 pixel larger than the limb (vanilla inflation). */
    private static ModelRenderer flat(ModelBase base, float x0, float y0, float z0, int w, int d, int rowFrom, int rowTo, int ox, int oy) {
        ModelRenderer flat = new ModelRenderer(base);
        int h = rowTo - rowFrom;
        ModelBox box = new ModelBox(flat, ox, oy, x0, y0, z0, w, h, d, 0.25F);
        int sideV = oy + d + rowFrom;
        box.setSideUV(0, ox + d + w, sideV).setSideUV(1, ox, sideV).setSideUV(4, ox + d, sideV).setSideUV(5, ox + d + w + d, sideV);
        // Only the limb's real ends get a face; the elbow / knee ends are inside the joint.
        if (rowFrom != 0) {
            box.hideSide(2);
        }
        if (rowTo != 12) {
            box.hideSide(3);
        }
        flat.cubeList.add(box.initQuads());
        return flat;
    }

    /** Draws the 3D voxels or the flat layer, as set by the SkinLayers3D option. */
    private static final class Switch extends ModelRenderer {
        private final ModelRenderer voxels;
        private final ModelRenderer flat;

        Switch(ModelBase base, ModelRenderer voxels, ModelRenderer flat) {
            super(base);
            this.voxels = voxels;
            this.flat = flat;
        }

        @Override
        public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
            if (this.isHidden || !this.showModel) {
                return;
            }
            (ModSettings.SKIN_LAYERS_3D.on() ? this.voxels : this.flat).render(poseStack, consumer, light, overlay, color);
        }
    }

    private static void voxel(ModelRenderer layer, float cx, float cy, float cz, float sx, float sy, float sz, int u, int v) {
        layer.cubeList.add(new ModelBox(layer, u, v, cx - sx / 2.0F, cy - sy / 2.0F, cz - sz / 2.0F, sx, sy, sz).initQuads());
    }
}
