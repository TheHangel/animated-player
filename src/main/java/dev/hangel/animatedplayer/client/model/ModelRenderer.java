package dev.hangel.animatedplayer.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;

/**
 * Port of the 1.7.10 ModelRenderer on top of PoseStack/VertexConsumer.
 * Modern ModelPart is final and baked, so the original mod's mutable joint hierarchy is rebuilt here
 * with the exact same semantics (rotation point in pixels, Z-Y-X rotation order, showModel hides children).
 */
public class ModelRenderer {
    public float textureWidth;
    public float textureHeight;
    private int textureOffsetX;
    private int textureOffsetY;
    public float rotationPointX;
    public float rotationPointY;
    public float rotationPointZ;
    public float rotateAngleX;
    public float rotateAngleY;
    public float rotateAngleZ;
    public boolean mirror;
    public boolean showModel = true;
    public boolean isHidden;
    /** Skips this part's own geometry but still draws its children. */
    public boolean hideOwn;
    public List<ModelBox> cubeList = new ArrayList<>();
    public List<ModelRenderer> childModels;
    public final String boxName;
    public float offsetX;
    public float offsetY;
    public float offsetZ;

    public ModelRenderer(ModelBase base, String name) {
        this.textureWidth = base.getTextureWidth();
        this.textureHeight = base.getTextureHeight();
        this.boxName = name;
        base.getBoxList().add(this);
    }

    public ModelRenderer(ModelBase base) {
        this(base, null);
    }

    public ModelRenderer(ModelBase base, int texOffX, int texOffY) {
        this(base);
        this.setTextureOffset(texOffX, texOffY);
    }

    public void addChild(ModelRenderer child) {
        if (this.childModels == null) {
            this.childModels = new ArrayList<>();
        }
        this.childModels.add(child);
    }

    public ModelRenderer setTextureOffset(int x, int y) {
        this.textureOffsetX = x;
        this.textureOffsetY = y;
        return this;
    }

    public int getTextureOffsetX() {
        return this.textureOffsetX;
    }

    public int getTextureOffsetY() {
        return this.textureOffsetY;
    }

    public ModelRenderer addBox(float x, float y, float z, int w, int h, int d) {
        this.cubeList.add(new ModelBox(this, this.textureOffsetX, this.textureOffsetY, x, y, z, w, h, d, 0.0F).initQuads());
        return this;
    }

    public void addBox(float x, float y, float z, int w, int h, int d, float inflate) {
        this.cubeList.add(new ModelBox(this, this.textureOffsetX, this.textureOffsetY, x, y, z, w, h, d, inflate).initQuads());
    }

    public void setRotationPoint(float x, float y, float z) {
        this.rotationPointX = x;
        this.rotationPointY = y;
        this.rotationPointZ = z;
    }

    public void setTextureSize(int w, int h) {
        this.textureWidth = w;
        this.textureHeight = h;
    }

    /** Equivalent of render(0.0625F). */
    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (this.isHidden || !this.showModel) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(this.offsetX, this.offsetY, this.offsetZ);
        this.translateAndRotate(poseStack);
        if (!this.hideOwn) {
            this.renderOwn(poseStack, consumer, light, overlay, color);
        }
        if (this.childModels != null) {
            for (ModelRenderer child : this.childModels) {
                child.render(poseStack, consumer, light, overlay, color);
            }
        }
        poseStack.popPose();
    }

    protected void renderOwn(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        PoseStack.Pose pose = poseStack.last();
        for (ModelBox box : this.cubeList) {
            box.render(pose, consumer, light, overlay, color);
        }
    }

    /** Equivalent of postRender(0.0625F): applies this part's transform without drawing. */
    public void postRender(PoseStack poseStack) {
        if (!this.isHidden && this.showModel) {
            this.translateAndRotate(poseStack);
        }
    }

    /** This part's transform, applied even when hidden (used to place things on the animated body). */
    public void translateAndRotate(PoseStack poseStack) {
        if (this.rotationPointX != 0.0F || this.rotationPointY != 0.0F || this.rotationPointZ != 0.0F) {
            poseStack.translate(this.rotationPointX / 16.0F, this.rotationPointY / 16.0F, this.rotationPointZ / 16.0F);
        }
        if (this.rotateAngleX != 0.0F || this.rotateAngleY != 0.0F || this.rotateAngleZ != 0.0F) {
            // glRotate Z, then Y, then X — same as the original.
            poseStack.mulPose(new Quaternionf().rotationZYX(this.rotateAngleZ, this.rotateAngleY, this.rotateAngleX));
        }
    }
}
