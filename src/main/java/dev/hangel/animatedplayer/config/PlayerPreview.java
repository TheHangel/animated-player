package dev.hangel.animatedplayer.config;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hangel.animatedplayer.client.ClientProxy;
import dev.hangel.animatedplayer.client.LegacySteve;
import dev.hangel.animatedplayer.client.ModelPlayer;
import dev.hangel.animatedplayer.client.PlayerData;
import dev.hangel.animatedplayer.client.SkinInfo;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

/**
 * The animated player of the settings screen. No player entity is needed (it works from the title screen): the
 * mod's model is posed directly, with an animation state driven here to demonstrate the option being looked at.
 * Settings are read live by the model, so toggling an option changes the preview right away.
 */
public final class PlayerPreview {
    /** What the preview acts out. */
    public enum Demo {
        /** Stands, walks, sprints, in a loop. */
        CYCLE, IDLE, WALK, SWORD, CREATIVE_FLIGHT, EYEBROWS, HUNGER, BREATHLESS, LOOK_AROUND, VANILLA,
        /** Bent elbows and knees: sneaking, then hovering. */
        JOINTS
    }

    private static final int FULL_BRIGHT = 15728880;

    private final PlayerData data = new PlayerData();
    private ModelPlayer wideModel;
    private ModelPlayer slimModel;
    private Demo demo = Demo.CYCLE;
    private int tick;
    private float limbSwing;
    private float prevLimbSwing;
    private float limbAmount;
    private float prevLimbAmount;
    private int swingTick = -1;
    private int foodLevel = 20;
    /** Body yaw, turned by dragging the preview. 180: facing the viewer. */
    private float yaw = 155.0F;
    private boolean ownSkin;
    /** Skin shown instead of Steve / the player's skin (tests); its info must be readable by SkinInfo. */
    public ResourceLocation skinOverride;
    /** Pauses the demo on its current frame (side-by-side comparisons). */
    public boolean frozen;

    public void setDemo(Demo demo) {
        if (this.demo != demo) {
            this.demo = demo;
            this.tick = 0;
        }
    }

    public Demo demo() {
        return this.demo;
    }

    public void rotate(float degrees) {
        this.yaw += degrees;
    }

    public boolean usesOwnSkin() {
        return this.ownSkin;
    }

    public void setOwnSkin(boolean ownSkin) {
        this.ownSkin = ownSkin;
    }

    /** One game tick of the demo: the same state the mod's client tick computes for real players. */
    public void tick() {
        if (this.frozen) {
            return;
        }
        this.tick++;
        PlayerData d = this.data;
        boolean moving;
        boolean sprinting = false;
        boolean flying = false;
        boolean arch = false;
        boolean breathless = false;
        boolean sneaking = false;
        boolean forceHover = false;
        this.foodLevel = 20;
        switch (this.demo) {
            case CYCLE, VANILLA -> {
                int t = this.tick % 160;
                moving = t >= 40 && t < 140;
                sprinting = t >= 95 && t < 140;
            }
            case WALK -> moving = true;
            case SWORD -> {
                moving = false;
                arch = true;
                if (this.tick % 25 == 5) {
                    this.swingTick = 0;
                }
            }
            case CREATIVE_FLIGHT -> {
                moving = true;
                flying = true;
            }
            case EYEBROWS -> {
                moving = false;
                arch = this.tick % 60 < 30;
            }
            case JOINTS -> {
                int t = this.tick % 120;
                sneaking = t < 60;
                moving = sneaking;
                flying = !sneaking;
                forceHover = true;
            }
            case HUNGER -> {
                moving = false;
                this.foodLevel = 2;
            }
            case BREATHLESS -> {
                moving = false;
                breathless = true;
            }
            default -> moving = false;
        }

        // Limb swing, as LivingEntity#calculateEntityAnimation (0.2 blocks/tick walking, 0.28 sprinting).
        this.prevLimbSwing = this.limbSwing;
        this.prevLimbAmount = this.limbAmount;
        float speed = moving ? Math.min(1.0F, (sprinting ? 0.28F : sneaking ? 0.065F : 0.2F) * 4.0F) : 0.0F;
        this.limbAmount += (speed - this.limbAmount) * 0.4F;
        this.limbSwing += this.limbAmount;

        // Arm swing (Player#swing: 6 ticks).
        if (this.swingTick >= 0 && ++this.swingTick > 6) {
            this.swingTick = -1;
        }

        d.moveBackwards = false;
        d.moveBackwards2 = d.moveBackwards;
        d.holdingSword = this.demo == Demo.SWORD && ModSettings.SWORD_ANIMATION.on();
        d.isAirborne = flying;
        d.prevSprintTick = d.sprintTick;
        d.sprintTick = step(d.sprintTick, sprinting, 8);
        d.prevBrowTick = d.browTick;
        d.browTick = step(d.browTick, arch || sprinting, 8);
        d.prevJumpTick = d.jumpTick;
        d.jumpTick = flying ? Math.min(10, d.jumpTick + 1) : 0;
        d.prevSneakTick = d.sneakTick;
        d.sneakTick = step(d.sneakTick, sneaking, 6);
        d.prevExhaustionTick = d.exhaustionTick;
        d.exhaustionTick = step(d.exhaustionTick, breathless, 10);
        boolean superman = flying && !forceHover && ModSettings.SUPERMAN_FLY.on();
        d.prevFlyingTick = d.flyingTick;
        d.flyingTick = step(d.flyingTick, superman, 5);
        d.prevHoverTick = d.hoverTick;
        d.hoverTick = step(d.hoverTick, flying && !superman, 5);
        d.prevPosTick = d.posTick;
        float pos = moving ? (d.moveBackwards ? -0.5F : 0.9F) : 0.0F;
        d.posTick = d.prevPosTick + (pos - d.prevPosTick) * 0.3F;
        d.prevPitch = d.pitch;
        d.pitch = d.pitch * 0.9F;
    }

    private static int step(int value, boolean up, int max) {
        return up ? Math.min(max, value + 1) : Math.max(0, value - 1);
    }

    /** Draws the preview centred in the box, the head following the mouse (or looking around for that demo). */
    public void render(GuiGraphics graphics, int x0, int y0, int x1, int y1, int mouseX, int mouseY, float partialTick) {
        if (this.frozen) {
            partialTick = 1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = LegacySteve.texture();
        SkinInfo info = LegacySteve.skinInfo();
        boolean slim = false;
        if (this.skinOverride != null && SkinInfo.get(this.skinOverride) != null) {
            texture = this.skinOverride;
            info = SkinInfo.get(this.skinOverride);
        } else if (this.ownSkin) {
            PlayerSkin skin = ownSkin();
            // Same texture as in game: default skins are drawn with the mod's copies of them.
            ResourceLocation animated = ClientProxy.animatedDefaultSkin(skin.texture());
            ResourceLocation ownTexture = animated != null && ModSettings.ENABLED.on() ? animated : skin.texture();
            // The skin is shown even before its info is read (face animation then appears when it is).
            texture = ownTexture;
            info = SkinInfo.get(ownTexture);
            slim = skin.model() == PlayerSkin.Model.SLIM;
        }
        this.data.textureInfo.setFrom(info);
        ModelPlayer model = this.model(slim);
        model.applyLayout(slim, true, true, true, true, true, true);

        float cx = (x0 + x1) / 2.0F;
        float cy = (y0 + y1) / 2.0F;
        float size = Math.min((x1 - x0) * 0.6F, (y1 - y0) / 2.1F);
        float headYaw;
        float headPitch;
        if (this.demo == Demo.LOOK_AROUND) {
            float t = this.tick + partialTick;
            headYaw = Mth.sin(t * 0.06F) * 50.0F;
            headPitch = Mth.sin(t * 0.045F) * 20.0F;
        } else {
            // Same mapping as the inventory player: the head turns towards the mouse.
            headYaw = Mth.clamp((float) Math.atan((cx - mouseX) / 40.0F) * 40.0F, -60.0F, 60.0F)
                    * Mth.cos((this.yaw - 180.0F) * Mth.DEG_TO_RAD);
            headPitch = Mth.clamp(-(float) Math.atan((cy - size * 0.9F - mouseY) / 40.0F) * 20.0F, -30.0F, 30.0F);
        }
        float swing = Mth.lerp(partialTick, this.prevLimbSwing, this.limbSwing);
        float amount = Mth.lerp(partialTick, this.prevLimbAmount, this.limbAmount);
        float attack = this.swingTick < 0 ? 0.0F : Math.min(1.0F, (this.swingTick + partialTick) / 6.0F);
        boolean sword = this.demo == Demo.SWORD;
        if (this.demo == Demo.VANILLA) {
            model.poseVanillaPreview(this.data, swing, amount, headYaw, headPitch);
        } else {
            model.posePreview(this.data, this.foodLevel, swing, amount, this.tick + partialTick, headYaw, headPitch,
                    partialTick, attack, sword);
        }

        float bodyYaw = this.yaw;

        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Same transforms as InventoryScreen#renderEntityInInventory, then LivingEntityRenderer#render.
        pose.translate(cx, cy, 100.0F);
        pose.scale(size, size, -size);
        pose.translate(0.0F, 0.9F, 0.0F);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        model.renderToBuffer(pose, buffer.getBuffer(model.renderType(texture)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        if (sword) {
            pose.pushPose();
            model.translateToHand(HumanoidArm.RIGHT, pose);
            // ItemInHandLayer#renderArmWithItem.
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(1.0F / 16.0F, 0.125F, -0.625F);
            minecraft.getItemRenderer().renderStatic(new ItemStack(Items.IRON_SWORD), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffer, null, 0);
            pose.popPose();
        }
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }

    /** Profile with its textures, fetched once from the session server (the session's profile has none). */
    private static CompletableFuture<GameProfile> ownProfile;

    /**
     * The player's own skin: the one drawn in game when in a world; otherwise looked up from the account's profile
     * (default skin while it loads).
     */
    private static PlayerSkin ownSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.getSkin();
        }
        GameProfile session = minecraft.getGameProfile();
        if (ownProfile == null) {
            ownProfile = CompletableFuture.supplyAsync(() -> {
                ProfileResult result = minecraft.getMinecraftSessionService().fetchProfile(session.getId(), false);
                return result != null ? result.profile() : session;
            }, Util.backgroundExecutor()).exceptionally(e -> session);
        }
        GameProfile profile = ownProfile.getNow(null);
        return profile != null ? minecraft.getSkinManager().getInsecureSkin(profile) : DefaultPlayerSkin.get(session);
    }

    private ModelPlayer model(boolean slim) {
        Minecraft minecraft = Minecraft.getInstance();
        if (slim) {
            if (this.slimModel == null) {
                this.slimModel = new ModelPlayer(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true, 0.0F, false);
            }
            return this.slimModel;
        }
        if (this.wideModel == null) {
            this.wideModel = new ModelPlayer(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false, 0.0F, false);
        }
        return this.wideModel;
    }
}
