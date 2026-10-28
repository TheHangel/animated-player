package dev.hangel.animatedplayer.mixin;

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
 * While the mod runs the vanilla armor layer (see PlayerLayers.AnimatedArmorLayer), the plain armor pieces vanilla
 * would draw are recorded and skipped: the mod draws them on its animated body instead. Mods that draw their own
 * armor from this layer (Immersive Armors, GeckoLib armor...) still run and draw on the synced vanilla parts.
 * The high priority places this check after other mods' injections, so a piece another mod took over is left to it.
 * This is the vanilla 6-argument overload; NeoForge renders through its own 12-argument one (MixinHumanoidArmorLayerNeo).
 */
@Mixin(value = HumanoidArmorLayer.class, priority = 2000)
public abstract class MixinHumanoidArmorLayer {
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void animatedplayer$takePiece(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot,
                                          int light, HumanoidModel<?> model, CallbackInfo ci) {
        if (PlayerLayers.takeVanillaArmorPiece(slot)) {
            ci.cancel();
        }
    }
}
