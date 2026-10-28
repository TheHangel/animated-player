package dev.hangel.animatedplayer.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.HttpTexture;
import java.io.File;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.hangel.animatedplayer.client.SkinInfo;

/** Reads what the mod needs from a downloaded skin while it is still a CPU image (download or cache thread). */
@Mixin(HttpTexture.class)
public abstract class MixinHttpTexture implements SkinInfo.SkinInfoHolder {
    @Shadow
    @Final
    private File file;
    @Unique
    private SkinInfo.FaceFeatures animatedplayer$face;
    @Unique
    private volatile SkinInfo animatedplayer$skinInfo;

    /** Face features need the pixels as painted, before the game makes the head area opaque. */
    @Inject(method = "processLegacySkin", at = @At("HEAD"))
    private void animatedplayer$readFace(NativeImage image, CallbackInfoReturnable<NativeImage> cir) {
        this.animatedplayer$face = SkinInfo.FaceFeatures.read(image);
    }

    /** Opaque pixels are taken from the final 64x64 image, the one that gets drawn. */
    @Inject(method = "processLegacySkin", at = @At("RETURN"))
    private void animatedplayer$readPixels(NativeImage image, CallbackInfoReturnable<NativeImage> cir) {
        NativeImage processed = cir.getReturnValue();
        if (processed != null && this.animatedplayer$face != null) {
            this.animatedplayer$skinInfo = SkinInfo.of(this.animatedplayer$face, processed);
        }
    }

    @Override
    public SkinInfo animatedplayer$getSkinInfo() {
        return this.animatedplayer$skinInfo;
    }

    @Override
    public File animatedplayer$getCacheFile() {
        return this.file;
    }
}
