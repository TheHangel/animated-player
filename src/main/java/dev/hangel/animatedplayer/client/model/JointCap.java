package dev.hangel.animatedplayer.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BooleanSupplier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Flat end of a limb half at the elbow or knee, one quad per skin pixel. Its texture, in order of preference:
 * <ol>
 * <li>the limb's joint cap area, the skin pixels the 1.7.10 mod reserved for it, when the skin painted them;</li>
 * <li>for a left limb, the right limb's cap area mirrored (1.7.10 skins only had right limbs);</li>
 * <li>otherwise the limb's side faces at the joint, extended inwards: each pixel takes the nearest side pixel.</li>
 * </ol>
 * Checked against {@link ModelBox#visiblePixels} (the skin being drawn); with no skin info, the side faces are used.
 */
public class JointCap extends ModelRenderer {
    private final float x0;
    private final float x1;
    private final float z0;
    private final float z1;
    private final float y;
    private final float normalY;
    private final int w;
    private final int d;
    private final BooleanSupplier enabled;
    /** Texture origin of the limb (vanilla cube layout) and the side faces' row at the joint. */
    private final int limbU;
    private final int sideV;
    /** Cap areas (w x 4 pixels): this limb's own, and the right limb's for a left limb (-1 if none). */
    private final int zoneU;
    private final int zoneV;
    private final int mirrorZoneU;
    private final int mirrorZoneV;

    /**
     * @param x       left edge of the limb (pixels); {@code w}, {@code d}: limb width and depth; {@code add}: inflation
     * @param y       height of the end plane in the part's frame; {@code down}: true for the bottom end
     * @param row     row of the side faces at the joint, from the top of the side faces
     */
    public JointCap(ModelBase base, float x, int w, int d, float add, float y, boolean down, BooleanSupplier enabled,
                    int limbU, int limbV, int row, int zoneU, int zoneV, int mirrorZoneU, int mirrorZoneV) {
        super(base);
        this.x0 = x - add;
        this.x1 = x + w + add;
        this.z0 = -d / 2.0F - add;
        this.z1 = d / 2.0F + add;
        this.y = down ? y + add : y - add;
        this.normalY = down ? 1.0F : -1.0F;
        this.w = w;
        this.d = d;
        this.enabled = enabled;
        this.limbU = limbU;
        this.sideV = limbV + d + row;
        this.zoneU = zoneU;
        this.zoneV = zoneV;
        this.mirrorZoneU = mirrorZoneU;
        this.mirrorZoneV = mirrorZoneV;
    }

    private boolean painted(ModelBox.SkinPixels skin, int u0, int v0) {
        if (skin == null || u0 < 0) {
            return false;
        }
        for (int v = v0; v < v0 + 4; v++) {
            for (int u = u0; u < u0 + this.w; u++) {
                if (skin.isPainted(u + v * 64)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void renderOwn(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (!this.enabled.getAsBoolean()) {
            return;
        }
        ModelBox.SkinPixels skin = ModelBox.visiblePixels;
        int mode = this.painted(skin, this.zoneU, this.zoneV) ? 1 : this.painted(skin, this.mirrorZoneU, this.mirrorZoneV) ? 2 : 0;
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Vector3f();
        pose.transformNormal(0.0F, this.normalY, 0.0F, normal);
        Vector4f p = new Vector4f();
        float cellX = (this.x1 - this.x0) / this.w;
        float cellZ = (this.z1 - this.z0) / this.d;
        float th = this.textureHeight;
        for (int i = 0; i < this.w; i++) {
            for (int k = 0; k < this.d; k++) {
                int pu;
                int pv;
                if (mode == 1) {
                    pu = this.zoneU + i;
                    pv = this.zoneV + k;
                } else if (mode == 2) {
                    pu = this.mirrorZoneU + this.w - 1 - i;
                    pv = this.mirrorZoneV + k;
                } else {
                    int[] side = this.edgePixel(i, k);
                    pu = side[0];
                    pv = side[1];
                }
                float u0 = (pu + 0.05F) / 64.0F;
                float u1 = (pu + 0.95F) / 64.0F;
                float v0 = (pv + 0.05F) / th;
                float v1 = (pv + 0.95F) / th;
                float ax = this.x0 + i * cellX;
                float bx = ax + cellX;
                float az = this.z0 + k * cellZ;
                float bz = az + cellZ;
                vertex(consumer, matrix, p, ax, az, u0, v0, color, overlay, light, normal);
                vertex(consumer, matrix, p, bx, az, u1, v0, color, overlay, light, normal);
                vertex(consumer, matrix, p, bx, bz, u1, v1, color, overlay, light, normal);
                vertex(consumer, matrix, p, ax, bz, u0, v1, color, overlay, light, normal);
            }
        }
    }

    /** Nearest side face pixel at the joint row (front is -Z, as the vanilla cube layout). */
    private int[] edgePixel(int i, int k) {
        int u = this.limbU;
        int w = this.w;
        int d = this.d;
        int toFront = k;
        int toBack = d - 1 - k;
        int toMinX = i;
        int toMaxX = w - 1 - i;
        int min = Math.min(Math.min(toFront, toBack), Math.min(toMinX, toMaxX));
        if (min == toFront) {
            return new int[]{u + d + i, this.sideV};
        }
        if (min == toBack) {
            return new int[]{u + d + w + d + (w - 1 - i), this.sideV};
        }
        if (min == toMinX) {
            return new int[]{u + d - 1 - k, this.sideV};
        }
        return new int[]{u + d + w + k, this.sideV};
    }

    private void vertex(VertexConsumer consumer, Matrix4f matrix, Vector4f p, float x, float z, float u, float v,
                        int color, int overlay, int light, Vector3f normal) {
        matrix.transform(x / 16.0F, this.y / 16.0F, z / 16.0F, 1.0F, p);
        consumer.addVertex(p.x(), p.y(), p.z(), color, u, v, overlay, light, normal.x(), normal.y(), normal.z());
    }
}
