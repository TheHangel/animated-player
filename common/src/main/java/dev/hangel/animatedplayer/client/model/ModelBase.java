package dev.hangel.animatedplayer.client.model;

import java.util.List;

/**
 * Minimal stand-in for the 1.7.10 ModelBase: owns the list of every part created for it
 * (used for random arrow placement) and the texture size used to compute UVs.
 */
public interface ModelBase {
    List<ModelRenderer> getBoxList();

    default int getTextureWidth() {
        return 64;
    }

    default int getTextureHeight() {
        return 32;
    }
}
