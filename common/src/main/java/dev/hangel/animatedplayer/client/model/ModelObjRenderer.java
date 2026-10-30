package dev.hangel.animatedplayer.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.hangel.animatedplayer.config.ModSettings;

/**
 * Port of AnimationAPI's ModelObjRenderer: a ModelRenderer whose geometry is an OBJ mesh.
 * The mesh is drawn scaled by 0.76 and flipped 180° on X (as in the original display list), then by {@link #setScale}.
 */
public class ModelObjRenderer extends ModelRenderer {
    public WavefrontObject model;
    private float theScale;

    public ModelObjRenderer(ModelBase base) {
        this(base, null, 1.0F);
    }

    public ModelObjRenderer(ModelBase base, WavefrontObject shape) {
        this(base, shape, 1.0F);
    }

    public ModelObjRenderer(ModelBase base, WavefrontObject shape, float scale) {
        super(base);
        this.theScale = scale;
        this.model = shape;
    }

    public void setScale(float scale) {
        this.theScale = scale;
    }

    /** With the RoundedLimbs option off, the part's plain cubes (when it has some) are drawn instead of the mesh. */
    @Override
    protected void renderOwn(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (!ModSettings.ROUNDED_LIMBS.on() && !this.cubeList.isEmpty()) {
            super.renderOwn(poseStack, consumer, light, overlay, color);
            return;
        }
        if (this.model == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(this.theScale, this.theScale, this.theScale);
        poseStack.scale(0.76F, 0.76F, 0.76F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        this.model.renderAll(poseStack.last(), consumer, light, overlay, color);
        poseStack.popPose();
    }
}
