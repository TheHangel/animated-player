package dev.hangel.animatedplayer.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Port of the 1.7.10 ModelBox merged with the mod's ModelBoxUV: a cuboid whose six faces can each get
 * their own texture origin. Plain boxes use the vanilla UV layout.
 */
public class ModelBox {
    private static final int[][] VECTOR_INDEX = {{5, 1, 2, 6}, {0, 4, 7, 3}, {5, 4, 0, 1}, {2, 3, 7, 6}, {1, 0, 3, 2}, {4, 5, 6, 7}};

    public final float textureWidth;
    public final float textureHeight;
    public final float posX1;
    public final float posY1;
    public final float posZ1;
    public final float posX2;
    public final float posY2;
    public final float posZ2;
    public String boxName;
    private final boolean mirror;
    private final float[][] vertexPositions;
    private int[][] quadDataList;
    private Quad[] quadList;
    /** Skin pixel (u + v * 64) shown on every face when set with {@link #setAllUV}, else -1. */
    private int pixel = -1;

    /**
     * Opaque pixels of the skin being drawn, or null to draw everything. Single-pixel boxes (3D hat and second
     * layer voxels) whose pixel is transparent are skipped instead of being sent to the GPU.
     */
    public static SkinPixels visiblePixels;

    public interface SkinPixels {
        boolean isOpaque(int pixel);

        /** Whether the skin's author painted this pixel (checked before the game makes skin areas opaque). */
        default boolean isPainted(int pixel) {
            return this.isOpaque(pixel);
        }
    }

    /** Sides not drawn (bit per side index), e.g. a limb end replaced by a {@link JointCap}. */
    private int hiddenSides;

    public ModelBox hideSide(int side) {
        this.hiddenSides |= 1 << side;
        return this;
    }

    public ModelBox(ModelRenderer renderer, int textureX, int textureY, float x1, float y1, float z1, int w, int h, int d, float add) {
        this(renderer, textureX, textureY, x1, y1, z1, w, h, d, w, h, d, add);
    }

    /** Box of any size textured with a single pixel on every face, as the mod's 3D hat does. */
    public ModelBox(ModelRenderer renderer, int pixelX, int pixelY, float x1, float y1, float z1, float w, float h, float d) {
        this(renderer, pixelX, pixelY, x1, y1, z1, w, h, d, 0, 0, 0, 0.0F);
        this.setAllUV(pixelX, pixelY);
    }

    private ModelBox(ModelRenderer renderer, int textureX, int textureY, float x1, float y1, float z1, float w, float h, float d,
                     int uvW, int uvH, int uvD, float add) {
        this.textureWidth = renderer.textureWidth;
        this.textureHeight = renderer.textureHeight;
        this.posX1 = x1;
        this.posY1 = y1;
        this.posZ1 = z1;
        this.posX2 = x1 + w;
        this.posY2 = y1 + h;
        this.posZ2 = z1 + d;
        this.mirror = renderer.mirror;
        float x2 = x1 + w;
        float y2 = y1 + h;
        float z2 = z1 + d;
        x1 -= add;
        y1 -= add;
        z1 -= add;
        x2 += add;
        y2 += add;
        z2 += add;
        if (this.mirror) {
            float tmp = x2;
            x2 = x1;
            x1 = tmp;
        }
        this.vertexPositions = new float[][]{
                {x1, y1, z1}, {x2, y1, z1}, {x2, y2, z1}, {x1, y2, z1},
                {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}, {x1, y2, z2}
        };
        this.quadDataList = new int[6][];
        for (int side = 0; side < 6; side++) {
            this.quadDataList[side] = uvW == 0 ? new int[]{textureX, textureY, textureX + 1, textureY + 1} : defaultUV(textureX, textureY, uvW, uvH, uvD, side);
        }
    }

    private static int[] defaultUV(int tx, int ty, int w, int h, int d, int side) {
        return switch (side) {
            case 0 -> new int[]{tx + d + w, ty + d, tx + d + w + d, ty + d + h};
            case 1 -> new int[]{tx, ty + d, tx + d, ty + d + h};
            case 2 -> new int[]{tx + d, ty, tx + d + w, ty + d};
            case 3 -> new int[]{tx + d + w, ty + d, tx + d + w + w, ty};
            case 4 -> new int[]{tx + d, ty + d, tx + d + w, ty + d + h};
            default -> new int[]{tx + d + w + d, ty + d, tx + d + w + d + w, ty + d + h};
        };
    }

    public ModelBox setSideUV(int side, int textureX, int textureY) {
        int[] uvPos = this.quadDataList[side];
        int deltaX = Math.abs(uvPos[2] - uvPos[0]);
        int deltaY = Math.abs(uvPos[3] - uvPos[1]);
        uvPos[0] = textureX;
        uvPos[1] = textureY;
        uvPos[2] = textureX + deltaX;
        uvPos[3] = textureY + deltaY;
        if (side == 3) {
            uvPos[1] = textureY + deltaY;
            uvPos[3] = textureY;
        }
        return this;
    }

    public ModelBox setAllUV(int textureX, int textureY) {
        for (int i = 0; i < this.quadDataList.length; i++) {
            this.setSideUV(i, textureX, textureY);
        }
        this.pixel = textureX + textureY * 64;
        return this;
    }

    public ModelBox initQuads() {
        this.quadList = new Quad[6];
        for (int i = 0; i < 6; i++) {
            int[] uv = this.quadDataList[i];
            int[] idx = VECTOR_INDEX[i];
            this.quadList[i] = new Quad(
                    this.vertexPositions[idx[0]], this.vertexPositions[idx[1]], this.vertexPositions[idx[2]], this.vertexPositions[idx[3]],
                    uv[0], uv[1], uv[2], uv[3], this.textureWidth, this.textureHeight, this.mirror);
        }
        this.quadDataList = null;
        return this;
    }

    public ModelBox setBoxName(String name) {
        this.boxName = name;
        return this;
    }

    public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        if (this.pixel >= 0 && visiblePixels != null && !visiblePixels.isOpaque(this.pixel)) {
            return;
        }
        if (this.quadList == null) {
            this.initQuads();
        }
        Matrix4f matrix = pose.pose();
        Vector4f pos = new Vector4f();
        Vector3f normal = new Vector3f();
        for (int side = 0; side < this.quadList.length; side++) {
            if ((this.hiddenSides & 1 << side) != 0) {
                continue;
            }
            Quad quad = this.quadList[side];
            pose.transformNormal(quad.nx, quad.ny, quad.nz, normal);
            for (int v = 0; v < 4; v++) {
                matrix.transform(quad.x[v] / 16.0F, quad.y[v] / 16.0F, quad.z[v] / 16.0F, 1.0F, pos);
                consumer.addVertex(pos.x(), pos.y(), pos.z(), color, quad.u[v], quad.v[v], overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }

    /** 1.7.10 TexturedQuad: same UV corner assignment, optional flipFace(), normal from the final winding. */
    private static final class Quad {
        final float[] x = new float[4];
        final float[] y = new float[4];
        final float[] z = new float[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        final float nx;
        final float ny;
        final float nz;

        Quad(float[] p0, float[] p1, float[] p2, float[] p3, int u1, int v1, int u2, int v2, float texW, float texH, boolean flip) {
            float[][] p = {p0, p1, p2, p3};
            float[] us = {u2 / texW, u1 / texW, u1 / texW, u2 / texW};
            float[] vs = {v1 / texH, v1 / texH, v2 / texH, v2 / texH};
            for (int i = 0; i < 4; i++) {
                int src = flip ? 3 - i : i;
                this.x[i] = p[src][0];
                this.y[i] = p[src][1];
                this.z[i] = p[src][2];
                this.u[i] = us[src];
                this.v[i] = vs[src];
            }
            Vector3f a = new Vector3f(this.x[2] - this.x[1], this.y[2] - this.y[1], this.z[2] - this.z[1]);
            Vector3f b = new Vector3f(this.x[0] - this.x[1], this.y[0] - this.y[1], this.z[0] - this.z[1]);
            Vector3f n = a.cross(b);
            if (n.lengthSquared() > 0.0F) {
                n.normalize();
            }
            this.nx = n.x();
            this.ny = n.y();
            this.nz = n.z();
        }
    }
}
