package dev.hangel.animatedplayer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.level.Level;
import dev.hangel.animatedplayer.mixin.LivingEntityRendererAccessor;

/**
 * Puts the mod into a vanilla PlayerRenderer: the model is swapped and the vanilla layers that the mod draws its own
 * way (armor on every joint, stuck arrows, deadmau5 ears, cape, elytra, shoulder parrots) are replaced in place.
 * Everything else is left alone: vanilla held items, head items, spin attack and bee stingers, and every layer
 * added by other mods, which find the vanilla parts posed on the animated body.
 */
public final class PlayerLayers {
    private static final ResourceLocation ELYTRA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Armor pieces left to the mod by the vanilla layer, while {@link #collectingArmor} (render thread only). */
    private static final Set<EquipmentSlot> VANILLA_ARMOR_PIECES = EnumSet.noneOf(EquipmentSlot.class);
    private static boolean collectingArmor;

    private PlayerLayers() {
    }

    /** Called by MixinHumanoidArmorLayer for a plain piece vanilla is about to draw; true to skip it. */
    public static boolean takeVanillaArmorPiece(EquipmentSlot slot) {
        if (!collectingArmor) {
            return false;
        }
        VANILLA_ARMOR_PIECES.add(slot);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install(PlayerRenderer renderer, EntityRendererProvider.Context context, boolean slim) {
        ModelPlayer model = new ModelPlayer(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim, 0.0F, false);
        LivingEntityRendererAccessor access = (LivingEntityRendererAccessor) renderer;
        access.animatedplayer$setModel(model);
        List<RenderLayer> layers = (List) access.animatedplayer$getLayers();
        ArmorModels armor = new ArmorModels(context);
        for (int i = 0; i < layers.size(); i++) {
            // Exact classes only: subclasses come from other mods and stay.
            Class<?> type = layers.get(i).getClass();
            RenderLayer replacement = null;
            if (type == HumanoidArmorLayer.class) {
                replacement = new AnimatedArmorLayer(renderer, armor, context);
            } else if (type == ArrowLayer.class) {
                replacement = new StuckArrowsLayer(renderer, context.getEntityRenderDispatcher());
            } else if (type == Deadmau5EarsLayer.class) {
                replacement = new EarsLayer(renderer);
            } else if (type == CapeLayer.class) {
                replacement = new CloakLayer(renderer);
            } else if (type == ElytraLayer.class) {
                replacement = new WingsLayer(renderer, context);
            } else if (type == ParrotOnShoulderLayer.class) {
                replacement = new ShoulderParrotLayer(renderer, context);
            }
            if (replacement != null) {
                ((AnimatedLayer) replacement).original = layers.get(i);
                layers.set(i, replacement);
            }
        }
    }

    private abstract static class AnimatedLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        /** The vanilla layer this one replaces, used while another mod animates the vanilla model. */
        RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> original;

        AnimatedLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        public final void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                 float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (this.model().vanillaPose) {
                this.original.render(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            } else {
                this.renderAnimated(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        }

        abstract void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                     float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch);

        ModelPlayer model() {
            return (ModelPlayer) this.getParentModel();
        }

        /** Moves to the mod's chest frame, which pivots at the waist. */
        void toChest(PoseStack poseStack) {
            this.model().pelvis.translateAndRotate(poseStack);
            this.model().chest.translateAndRotate(poseStack);
        }
    }

    /** Port of shouldRenderPass(): armor drawn with ModelPlayer instances so it follows every joint. */
    private static final class AnimatedArmorLayer extends AnimatedLayer {
        private final ArmorModels armor;
        private final TextureAtlas armorTrimAtlas;

        AnimatedArmorLayer(PlayerRenderer renderer, ArmorModels armor, EntityRendererProvider.Context context) {
            super(renderer);
            this.armor = armor;
            this.armorTrimAtlas = context.getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ModelPlayer main = this.model();
            // Run the vanilla layer first, for mods that draw their own armor from it; the plain pieces it would
            // draw are recorded instead and drawn below on the animated body.
            VANILLA_ARMOR_PIECES.clear();
            collectingArmor = true;
            try {
                this.original.render(poseStack, buffer, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            } finally {
                collectingArmor = false;
            }
            for (int par2 = 0; par2 < 4; par2++) {
                EquipmentSlot slot = ARMOR_SLOTS[par2];
                ItemStack itemstack = player.getItemBySlot(slot);
                if (!VANILLA_ARMOR_PIECES.contains(slot) || !(itemstack.getItem() instanceof ArmorItem armorItem)
                        || armorItem.getEquipmentSlot() != slot || !main.isVanillaPartVisible(slot)) {
                    continue;
                }
                ModelPlayer model = par2 == 2 ? this.armor.mainArmor : this.armor.chest;
                if (par2 == 0) {
                    model = this.armor.head;
                }

                model.head.showModel = par2 == 0;
                model.headwear.showModel = par2 == 0;
                model.chest.showModel = par2 == 1 || par2 == 2 || par2 == 0;
                model.arm1.showModel = par2 == 1;
                model.arm2.showModel = par2 == 1;
                model.leg1.showModel = par2 == 2 || par2 == 3;
                model.leg2.showModel = par2 == 2 || par2 == 3;
                model.foot1.showModel = par2 == 2 || par2 == 3;
                model.foot2.showModel = par2 == 2 || par2 == 3;

                model.attackTime = main.attackTime;
                model.riding = main.riding;
                model.young = main.young;
                model.partialTick = main.partialTick;
                model.heldItemRight = main.heldItemRight;
                model.aimedBow = main.aimedBow;
                model.chargingSpear = main.chargingSpear;
                model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

                boolean inner = slot == EquipmentSlot.LEGS;
                ArmorMaterial material = armorItem.getMaterial().value();
                for (int layerIdx = 0; layerIdx < material.layers().size(); layerIdx++) {
                    ArmorMaterial.Layer layer = material.layers().get(layerIdx);
                    int color = ArmorTextures.color(itemstack, player, layer, layerIdx);
                    if (color != 0) {
                        ResourceLocation texture = ArmorTextures.texture(player, itemstack, layer, inner, slot);
                        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, color);
                    }
                }

                ArmorTrim trim = itemstack.get(DataComponents.TRIM);
                if (trim != null) {
                    this.renderTrim(armorItem.getMaterial(), poseStack, buffer, light, trim, model, inner);
                }
                if (itemstack.hasFoil()) {
                    model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
                }
            }
        }

        private void renderTrim(Holder<ArmorMaterial> material, PoseStack poseStack, MultiBufferSource buffer, int light, ArmorTrim trim, ModelPlayer model, boolean inner) {
            TextureAtlasSprite sprite = this.armorTrimAtlas.getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material));
            VertexConsumer consumer = sprite.wrap(buffer.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
            model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Arrows stuck in the player, on the mod's joints (1.7.10 renderArrowsStuckInEntity). */
    private static final class StuckArrowsLayer extends AnimatedLayer {
        private final EntityRenderDispatcher dispatcher;
        /** One arrow entity reused for drawing, instead of a new one per arrow and per frame. */
        private Arrow arrow;

        StuckArrowsLayer(PlayerRenderer renderer, EntityRenderDispatcher dispatcher) {
            super(renderer);
            this.dispatcher = dispatcher;
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int count = player.getArrowCount();
            if (count <= 0) {
                return;
            }
            Level level = player.level();
            if (this.arrow == null || this.arrow.level() != level) {
                this.arrow = new Arrow(level, 0.0, 0.0, 0.0, new ItemStack(Items.ARROW), null);
            }
            this.arrow.setPos(player.getX(), player.getY(), player.getZ());
            RandomSource random = RandomSource.create(player.getId());
            for (int j = 0; j < count; j++) {
                poseStack.pushPose();
                float[] box = this.model().randomArrowTarget(random, poseStack);
                float f1 = random.nextFloat();
                float f2 = random.nextFloat();
                float f3 = random.nextFloat();
                float f4 = (box[0] + (box[3] - box[0]) * f1) / 16.0F;
                float f5 = (box[1] + (box[4] - box[1]) * f2) / 16.0F;
                float f6 = (box[2] + (box[5] - box[2]) * f3) / 16.0F;
                poseStack.translate(f4, f5, f6);
                f1 = -(f1 * 2.0F - 1.0F);
                f2 = -(f2 * 2.0F - 1.0F);
                f3 = -(f3 * 2.0F - 1.0F);
                float f7 = Mth.sqrt(f1 * f1 + f3 * f3);
                this.arrow.setYRot((float) (Math.atan2(f1, f3) * 180.0 / Math.PI));
                this.arrow.setXRot((float) (Math.atan2(f2, f7) * 180.0 / Math.PI));
                this.arrow.yRotO = this.arrow.getYRot();
                this.arrow.xRotO = this.arrow.getXRot();
                this.dispatcher.render(this.arrow, 0.0, 0.0, 0.0, 0.0F, partialTick, poseStack, buffer, light);
                poseStack.popPose();
            }
        }
    }

    private static final class EarsLayer extends AnimatedLayer {
        EarsLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!player.getName().getString().equals("deadmau5") || player.isInvisible() || !hasCustomSkin(player)) {
                return;
            }
            ModelPlayer playerModel = this.model();
            VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(player.getSkin().texture()));
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
            for (int i = 0; i < 2; i++) {
                float f5 = player.yRotO + (player.getYRot() - player.yRotO) * partialTick
                        - (player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick);
                float f3 = player.xRotO + (player.getXRot() - player.xRotO) * partialTick;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(f5));
                poseStack.mulPose(Axis.XP.rotationDegrees(f3));
                poseStack.translate(0.375F * (i * 2 - 1), 0.0F, 0.0F);
                poseStack.translate(0.0F, -0.375F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-f3));
                poseStack.mulPose(Axis.YP.rotationDegrees(-f5));
                float f4 = 1.3333334F;
                poseStack.scale(f4, f4, f4);
                // 1.7.10 copied the angles of the never-animated ModelBiped head, i.e. zero.
                playerModel.bipedEars.rotateAngleY = 0.0F;
                playerModel.bipedEars.rotateAngleX = 0.0F;
                playerModel.bipedEars.rotationPointX = 0.0F;
                playerModel.bipedEars.rotationPointY = 0.0F;
                playerModel.bipedEars.render(poseStack, consumer, light, overlay, -1);
                poseStack.popPose();
            }
        }

        private static boolean hasCustomSkin(AbstractClientPlayer player) {
            ResourceLocation skin = player.getSkin().texture();
            return !(skin.getNamespace().equals("minecraft") && skin.getPath().startsWith("textures/entity/player/"));
        }
    }

    /** 1.7.10 cape formula, attached to the mod's chest. */
    private static final class CloakLayer extends AnimatedLayer {
        CloakLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ResourceLocation cape = player.getSkin().capeTexture();
            if (cape == null || player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE)
                    || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                return;
            }
            ModelPlayer playerModel = this.model();
            poseStack.pushPose();
            double d0 = player.xCloakO + (player.xCloak - player.xCloakO) * partialTick - (player.xo + (player.getX() - player.xo) * partialTick);
            double d1 = player.yCloakO + (player.yCloak - player.yCloakO) * partialTick - (player.yo + (player.getY() - player.yo) * partialTick);
            double d2 = player.zCloakO + (player.zCloak - player.zCloakO) * partialTick - (player.zo + (player.getZ() - player.zo) * partialTick);
            float f6 = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick;
            double d3 = Mth.sin(f6 * (float) Math.PI / 180.0F);
            double d4 = -Mth.cos(f6 * (float) Math.PI / 180.0F);
            float f7 = (float) d1 * 10.0F;
            if (f7 < -6.0F) {
                f7 = -6.0F;
            }
            if (f7 > 32.0F) {
                f7 = 32.0F;
            }
            float f8 = (float) (d0 * d3 + d2 * d4) * 100.0F;
            // 1.7.10 had no upper bound: at fast creative flight speeds the cape swung past horizontal and
            // pointed straight up. Modern vanilla caps it at 150 (cape at most ~80° with level flight).
            f8 = Mth.clamp(f8, 0.0F, 150.0F);
            float f10 = player.oBob + (player.bob - player.oBob) * partialTick;
            f7 += Mth.sin((player.walkDistO + (player.walkDist - player.walkDistO) * partialTick) * 6.0F) * 32.0F * f10;
            // The cape hangs from the chest, which the animation can tilt forward (up to horizontal in the superman
            // flight pose): the angle computed above is from the vertical, so remove that tilt, without letting the
            // cape go into the back.
            float lean = (playerModel.pelvis.rotateAngleX + playerModel.chest.rotateAngleX) * 180.0F / (float) Math.PI;
            float angle = Math.max(6.0F, 6.0F + f8 / 2.0F + f7 - Math.max(0.0F, lean));
            playerModel.bipedCloak.rotateAngleX = -angle * (float) Math.PI / 180.0F;
            playerModel.bipedCloak.rotateAngleY = (float) Math.PI;
            this.toChest(poseStack);
            playerModel.bipedCloak.render(poseStack, buffer.getBuffer(RenderType.entitySolid(cape)), light, OverlayTexture.NO_OVERLAY, -1);
            poseStack.popPose();
        }
    }

    /** Elytras did not exist in 1.7.10: vanilla ElytraLayer, attached to the mod's animated chest. */
    private static final class WingsLayer extends AnimatedLayer {
        private final ElytraModel<AbstractClientPlayer> elytraModel;

        WingsLayer(PlayerRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer);
            this.elytraModel = new ElytraModel<>(context.bakeLayer(ModelLayers.ELYTRA));
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!stack.is(Items.ELYTRA)) {
                return;
            }
            PlayerSkin skin = player.getSkin();
            ResourceLocation texture;
            if (skin.elytraTexture() != null) {
                texture = skin.elytraTexture();
            } else if (skin.capeTexture() != null && player.isModelPartShown(PlayerModelPart.CAPE)) {
                texture = skin.capeTexture();
            } else {
                texture = ELYTRA_TEXTURE;
            }
            ElytraModel<AbstractClientPlayer> elytra = this.elytraModel;
            poseStack.pushPose();
            this.toChest(poseStack);
            // The mod's chest pivots at the waist; vanilla wings hang from the shoulders.
            poseStack.translate(0.0F, -0.75F, 0.125F);
            // Also clears EntityModel#young (true by default), which drew the wings half-sized and 1.5 blocks low.
            this.getParentModel().copyPropertiesTo(elytra);
            elytra.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffer, RenderType.armorCutoutNoCull(texture), stack.hasFoil());
            elytra.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /** Vanilla ParrotOnShoulderLayer places parrots relative to the whole entity; here they sit on the animated chest. */
    private static final class ShoulderParrotLayer extends AnimatedLayer {
        private final ParrotModel parrotModel;

        ShoulderParrotLayer(PlayerRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer);
            this.parrotModel = new ParrotModel(context.bakeLayer(ModelLayers.PARROT));
        }

        @Override
        void renderAnimated(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            this.renderParrot(poseStack, buffer, light, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, true);
            this.renderParrot(poseStack, buffer, light, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, false);
        }

        private void renderParrot(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                  float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch, boolean left) {
            CompoundTag tag = left ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
            if (EntityType.byString(tag.getString("id")).filter(type -> type == EntityType.PARROT).isEmpty()) {
                return;
            }
            poseStack.pushPose();
            this.toChest(poseStack);
            // Vanilla offsets, from the neck (12 pixels above the mod's chest pivot); sneaking is in the animation.
            poseStack.translate(left ? 0.4F : -0.4F, -0.75F - 1.5F, 0.0F);
            Parrot.Variant variant = Parrot.Variant.byId(tag.getInt("Variant"));
            VertexConsumer consumer = buffer.getBuffer(this.parrotModel.renderType(ParrotRenderer.getVariantTexture(variant)));
            this.parrotModel.renderOnShoulder(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, limbSwing, limbSwingAmount,
                    netHeadYaw, headPitch, player.tickCount);
            poseStack.popPose();
        }
    }

    private static final class ArmorModels {
        final ModelPlayer mainArmor;
        final ModelPlayer chest;
        final ModelPlayer head;

        ArmorModels(EntityRendererProvider.Context context) {
            this.mainArmor = new ModelPlayer(context.bakeLayer(ModelLayers.PLAYER), false, 0.5F, true);
            this.chest = new ModelPlayer(context.bakeLayer(ModelLayers.PLAYER), false, 1.0F, true);
            this.head = new ModelPlayer(context.bakeLayer(ModelLayers.PLAYER), false, 0.5F, true);
            this.head.chest.cubeList.clear();
        }
    }
}
