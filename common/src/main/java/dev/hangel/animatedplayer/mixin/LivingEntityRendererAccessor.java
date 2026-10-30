package dev.hangel.animatedplayer.mixin;

import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Accessor("model")
    void animatedplayer$setModel(EntityModel<?> model);

    @Accessor("layers")
    List<RenderLayer<?, ?>> animatedplayer$getLayers();
}
