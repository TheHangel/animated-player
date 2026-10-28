package dev.hangel.animatedplayer.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Armor texture and tint, through NeoForge's hooks so modded armor can customize them (loader-specific file). */
final class ArmorTextures {
    private ArmorTextures() {
    }

    static int color(ItemStack stack, AbstractClientPlayer player, ArmorMaterial.Layer layer, int layerIdx) {
        IClientItemExtensions extensions = IClientItemExtensions.of(stack);
        return extensions.getArmorLayerTintColor(stack, player, layer, layerIdx, extensions.getDefaultDyeColor(stack));
    }

    static ResourceLocation texture(AbstractClientPlayer player, ItemStack stack, ArmorMaterial.Layer layer, boolean inner, EquipmentSlot slot) {
        return ClientHooks.getArmorTexture(player, stack, layer, inner, slot);
    }
}
