package dev.hangel.animatedplayer.client;

import dev.hangel.animatedplayer.client.model.ModelBox;
import dev.hangel.animatedplayer.client.model.ModelRenderer;

public final class ModelBoxUV {
    private ModelBoxUV() {
    }

    /** Adds a box using the renderer's current texture offset; quads are built lazily so UVs can still be changed. */
    public static ModelBox addBox(ModelRenderer renderer, float x, float y, float z, int w, int h, int d, float add) {
        ModelBox box = new ModelBox(renderer, renderer.getTextureOffsetX(), renderer.getTextureOffsetY(), x, y, z, w, h, d, add);
        renderer.cubeList.add(box);
        return box;
    }
}
