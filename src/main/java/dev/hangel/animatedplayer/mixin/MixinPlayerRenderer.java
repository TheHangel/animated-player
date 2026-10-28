package dev.hangel.animatedplayer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.hangel.animatedplayer.client.ClientProxy;
import dev.hangel.animatedplayer.client.ModelPlayer;

/**
 * The vanilla player renderer is kept (so layers added by other mods still run) with the mod's model swapped in.
 * The whole-body rotations and offsets vanilla applies for swimming, elytra gliding and sneaking are turned off:
 * the mod animates those poses itself, as in 1.7.10. They are kept when the player is drawn the vanilla way
 * (mod turned off in its settings, or another mod animating the vanilla model).
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void animatedplayer$install(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ClientProxy.install((PlayerRenderer) (Object) this, context, slim);
    }

    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isFallFlying()Z"))
    private boolean animatedplayer$noGlideRotation(AbstractClientPlayer player) {
        return ClientProxy.useVanilla(player) && player.isFallFlying();
    }

    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSwimAmount(F)F"))
    private float animatedplayer$noSwimRotation(AbstractClientPlayer player, float partialTick) {
        return ClientProxy.useVanilla(player) ? player.getSwimAmount(partialTick) : 0.0F;
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private void animatedplayer$noSneakOffset(AbstractClientPlayer player, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!ClientProxy.useVanilla(player)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void animatedplayer$textureFallback(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture = ClientProxy.skinOverride(player);
        if (texture != null) {
            cir.setReturnValue(texture);
        }
    }

    /** First-person arms stay vanilla: the model must not animate (nor move the vanilla arm) meanwhile. */
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void animatedplayer$handStart(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                          ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        ModelPlayer.renderingFirstPersonHand = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void animatedplayer$handEnd(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                        ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        ModelPlayer.renderingFirstPersonHand = false;
    }
}
