package dev.hangel.animatedplayer.client.model;

/**
 * Port of AnimationAPI's ModelJoint: a virtual pivot whose real model is a child, allowing rotations
 * that are not tied to the real model's own pivot.
 */
public class ModelJoint extends ModelRenderer {
    private ModelRenderer model;

    public ModelJoint(ModelBase base) {
        this(base, (String) null);
        super.addChild(this.model = new ModelRenderer(base));
    }

    public ModelJoint(ModelBase base, String name) {
        super(base, name);
        this.model = new ModelRenderer(base, name);
        this.model.setTextureOffset(0, 0);
        super.addChild(this.model);
    }

    public ModelJoint(ModelBase base, int x, int y) {
        this(base);
        this.model = new ModelRenderer(base, x, y);
        this.model.setTextureOffset(x, y);
        super.addChild(this.model);
    }

    public ModelRenderer setModel(ModelRenderer newModel) {
        this.childModels.remove(this.model);
        this.model = newModel;
        super.addChild(newModel);
        return this;
    }

    @Override
    public ModelRenderer setTextureOffset(int x, int y) {
        if (this.model != null) {
            this.model.setTextureOffset(x, y);
        }
        return this;
    }

    @Override
    public ModelRenderer addBox(float x, float y, float z, int w, int h, int d) {
        this.model.addBox(x, y, z, w, h, d);
        return this;
    }

    @Override
    public void addBox(float x, float y, float z, int w, int h, int d, float offset) {
        this.model.addBox(x, y, z, w, h, d, offset);
    }

    @Override
    public void addChild(ModelRenderer child) {
        this.model.addChild(child);
    }

    public ModelRenderer getModel() {
        return this.model;
    }
}
