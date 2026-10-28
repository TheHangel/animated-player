package dev.hangel.animatedplayer.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Small replacement for Forge 1.7.10's WavefrontObject / IModelCustom#renderAll().
 * Reproduces its quirks: V is flipped on load, each face gets a flat normal and UVs are nudged
 * 0.0005 towards the face's UV centre to avoid bleeding.
 */
public class WavefrontObject {
    private static final float TEXTURE_OFFSET = 0.0005F;

    private final List<Face> faces = new ArrayList<>();

    private WavefrontObject() {
    }

    public static WavefrontObject load(ResourceManager manager, ResourceLocation location) {
        WavefrontObject obj = new WavefrontObject();
        List<float[]> vertices = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(manager.open(location), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] t = line.split("\\s+");
                    vertices.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                } else if (line.startsWith("vt ")) {
                    String[] t = line.split("\\s+");
                    texCoords.add(new float[]{Float.parseFloat(t[1]), 1.0F - Float.parseFloat(t[2])});
                } else if (line.startsWith("f ")) {
                    String[] t = line.split("\\s+");
                    int n = t.length - 1;
                    float[][] pos = new float[n][];
                    float[][] uv = new float[n][];
                    for (int i = 0; i < n; i++) {
                        String[] idx = t[i + 1].split("/");
                        pos[i] = vertices.get(Integer.parseInt(idx[0]) - 1);
                        uv[i] = idx.length > 1 && !idx[1].isEmpty() ? texCoords.get(Integer.parseInt(idx[1]) - 1) : new float[]{0.0F, 0.0F};
                    }
                    obj.faces.add(new Face(pos, uv));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("[AnimatedPlayer] Could not load model " + location, e);
        }
        return obj;
    }

    /** Maps a face's UVs, in texture pixels of the 64x32 layout; receives the face's UV centre to pick the cube side. */
    public interface UVMapper {
        float[] map(float u, float v, float centreU, float centreV);
    }

    /**
     * Copy of this mesh with X scaled then shifted (in mesh units) and UVs remapped to a 64 x textureHeight texture:
     * used to derive the 64x64 skin variants (3px slim arms, separate left limb textures) from the mod's meshes.
     */
    public WavefrontObject derive(float scaleX, float shiftX, UVMapper mapper, int textureHeight) {
        WavefrontObject obj = new WavefrontObject();
        for (Face face : this.faces) {
            int n = face.positions.length;
            float[][] pos = new float[n][];
            float[][] uv = new float[n][];
            float cu = 0.0F;
            float cv = 0.0F;
            for (float[] t : face.uv) {
                cu += t[0] * 64.0F / n;
                cv += t[1] * 32.0F / n;
            }
            for (int i = 0; i < n; i++) {
                float[] p = face.positions[i];
                pos[i] = new float[]{p[0] * scaleX + shiftX, p[1], p[2]};
                float[] m = mapper.map(face.uv[i][0] * 64.0F, face.uv[i][1] * 32.0F, cu, cv);
                uv[i] = new float[]{m[0] / 64.0F, m[1] / textureHeight};
            }
            obj.faces.add(new Face(pos, uv));
        }
        return obj;
    }

    public void renderAll(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = pose.pose();
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        for (Face face : this.faces) {
            pose.transformNormal(face.normal.x(), face.normal.y(), face.normal.z(), n);
            int count = face.positions.length;
            if (count == 4) {
                for (int id = 0; id < 4; id++) {
                    this.vertex(face, id, matrix, p, n, consumer, light, overlay, color);
                }
                continue;
            }
            // The entity buffers are QUADS: other polygons are fanned into triangles sent as degenerate quads.
            for (int i = 1; i + 1 < count; i++) {
                this.vertex(face, 0, matrix, p, n, consumer, light, overlay, color);
                this.vertex(face, i, matrix, p, n, consumer, light, overlay, color);
                this.vertex(face, i + 1, matrix, p, n, consumer, light, overlay, color);
                this.vertex(face, i + 1, matrix, p, n, consumer, light, overlay, color);
            }
        }
    }

    private void vertex(Face face, int id, Matrix4f matrix, Vector4f p, Vector3f n, VertexConsumer consumer, int light, int overlay, int color) {
        float[] v = face.positions[id];
        matrix.transform(v[0], v[1], v[2], 1.0F, p);
        consumer.addVertex(p.x(), p.y(), p.z(), color, face.u[id], face.v[id], overlay, light, n.x(), n.y(), n.z());
    }

    private static final class Face {
        final float[][] positions;
        final float[][] uv;
        final float[] u;
        final float[] v;
        final Vector3f normal;

        Face(float[][] positions, float[][] uv) {
            this.positions = positions;
            this.uv = uv;
            Vector3f a = new Vector3f(positions[1][0] - positions[0][0], positions[1][1] - positions[0][1], positions[1][2] - positions[0][2]);
            Vector3f b = new Vector3f(positions[2][0] - positions[0][0], positions[2][1] - positions[0][1], positions[2][2] - positions[0][2]);
            this.normal = a.cross(b);
            if (this.normal.lengthSquared() > 0.0F) {
                this.normal.normalize();
            }
            int n = positions.length;
            float avgU = 0.0F;
            float avgV = 0.0F;
            for (float[] t : uv) {
                avgU += t[0];
                avgV += t[1];
            }
            avgU /= n;
            avgV /= n;
            this.u = new float[n];
            this.v = new float[n];
            for (int i = 0; i < n; i++) {
                float offU = uv[i][0] > avgU ? -TEXTURE_OFFSET : TEXTURE_OFFSET;
                float offV = uv[i][1] > avgV ? -TEXTURE_OFFSET : TEXTURE_OFFSET;
                this.u[i] = uv[i][0] + offU;
                this.v[i] = uv[i][1] + offV;
            }
        }
    }
}
