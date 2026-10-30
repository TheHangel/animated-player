package dev.hangel.animatedplayer.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.hangel.animatedplayer.client.PlayerLayers;

/**
 * NeoForge only: same as the common MixinHumanoidArmorLayer, on the 12-argument
 * renderArmorPiece overload NeoForge adds and renders through. On Fabric that overload does not exist.
 */
@Mixin(value = HumanoidArmorLayer.class, priority = 2000)
public abstract class MixinHumanoidArmorLayerNeo {
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void animatedplayer$takePieceNeo(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot,
                                             int light, HumanoidModel<?> model, float limbSwing, float limbSwingAmount, float partialTick,
                                             float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (PlayerLayers.takeVanillaArmorPiece(slot)) {
            ci.cancel();
        }
    }
}
