package dev.hangel.animatedplayer.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Armor texture and tint. Same rules as vanilla HumanoidArmorLayer by default; a loader can replace {@link #provider}
 * with its own hooks (NeoForge lets modded armor customize them).
 */
public final class ArmorTextures {
    public interface Provider {
        int color(ItemStack stack, AbstractClientPlayer player, ArmorMaterial.Layer layer, int layerIdx);

        ResourceLocation texture(AbstractClientPlayer player, ItemStack stack, ArmorMaterial.Layer layer, boolean inner, EquipmentSlot slot);
    }

    public static final Provider VANILLA = new Provider() {
        @Override
        public int color(ItemStack stack, AbstractClientPlayer player, ArmorMaterial.Layer layer, int layerIdx) {
            if (!layer.dyeable()) {
                return -1;
            }
            return stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR)) : -1;
        }

        @Override
        public ResourceLocation texture(AbstractClientPlayer player, ItemStack stack, ArmorMaterial.Layer layer, boolean inner, EquipmentSlot slot) {
            return layer.texture(inner);
        }
    };

    public static Provider provider = VANILLA;

    private ArmorTextures() {
    }

    static int color(ItemStack stack, AbstractClientPlayer player, ArmorMaterial.Layer layer, int layerIdx) {
        return provider.color(stack, player, layer, layerIdx);
    }

    static ResourceLocation texture(AbstractClientPlayer player, ItemStack stack, ArmorMaterial.Layer layer, boolean inner, EquipmentSlot slot) {
        return provider.texture(player, stack, layer, inner, slot);
    }
}
