package dev.hangel.animatedplayer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.hangel.animatedplayer.config.ModSettings;
import dev.hangel.animatedplayer.client.model.JointCap;
import dev.hangel.animatedplayer.client.model.ModelBase;
import dev.hangel.animatedplayer.client.model.ModelBox;
import dev.hangel.animatedplayer.client.model.ModelJoint;
import dev.hangel.animatedplayer.client.model.ModelObjRenderer;
import dev.hangel.animatedplayer.client.model.ModelRenderer;

/**
 * The mod's articulated player model. It is a vanilla PlayerModel so it can sit in the vanilla PlayerRenderer: the
 * vanilla parts are never drawn, but they are posed to follow the mod's joints so layers from other mods (and the
 * vanilla ones kept: held items, head items, spin attack, bee stingers) land on the animated body.
 */
public class ModelPlayer extends PlayerModel<AbstractClientPlayer> implements ModelBase {
    public final List<ModelRenderer> boxList = new ArrayList<>();
    public int heldItemRight = 0;
    public int aimedBow = 0;
    public boolean chargingSpear;
    /** Draw the vanilla parts with their vanilla pose (see {@link ClientProxy#useVanilla}). */
    public boolean vanillaPose;
    public final boolean isArmor;
    /** Set while vanilla draws the first-person arm, which only needs the vanilla pose. */
    public static boolean renderingFirstPersonHand;

    public ModelRenderer bipedEars;
    public ModelRenderer bipedCloak;

    public ModelJoint head;
    public ModelRenderer headwear;
    /** Main model only: the two hat styles under {@link #headwear}, switched live by the BetterHat option. */
    private ModelRenderer hat3D;
    private ModelRenderer hatFlat;
    public ModelRenderer brow1;
    public ModelRenderer brow2;
    public ModelRenderer[][] iris;
    public ModelRenderer[] rightEye;
    public ModelRenderer[] leftEye;
    public ModelRenderer upperLip;
    public ModelRenderer lowerLip;
    public ModelRenderer insideMouth;
    public ModelRenderer mouth;
    public ModelRenderer upperTeeth;
    public ModelRenderer lowerTeeth;
    public ModelRenderer chest;
    public ModelRenderer pelvis;
    public ModelRenderer arm1;
    public ModelRenderer arm2;
    public ModelObjRenderer forearm1;
    public ModelObjRenderer forearm2;
    public ModelJoint leg1;
    public ModelJoint leg2;
    public ModelJoint foot1;
    public ModelJoint foot2;
    public ModelObjRenderer footObj1;
    public ModelObjRenderer footObj2;
    /** Modern skin variants of the main model (null on armor models), switched by {@link #setSkinLayout}. */
    private ModelRenderer armWide1, armSlim1, armWide2, armSlim2, legModern2;
    private ModelRenderer sleeveWide1, sleeveSlim1, sleeveWide2, sleeveSlim2;
    private ModelRenderer lowerSleeveWide1, lowerSleeveSlim1, lowerSleeveWide2, lowerSleeveSlim2;
    private ModelRenderer jacketLayer, pants1, pants2, lowerPants1, lowerPants2;
    private boolean slim;
    private final boolean slimRenderer;
    /** Plain cube versions of the mesh forearms (RoundedLimbs off), for wide and slim arms. */
    private List<ModelBox> forearmCubesWide1, forearmCubesSlim1, forearmCubesWide2, forearmCubesSlim2;
    private final PoseStack syncStack = new PoseStack();
    public static final float PI = (float) Math.PI;
    private static final int BOW_USE_DURATION = 72000;
    public float partialTick = 0.0F;
    /** 1 for the mod's relaxed standing stance and breathing, 0 for vanilla's straight stance (IdleAnimation option). */
    private float stance = 1.0F;

    /** The rendered player, or null when posed without one (settings screen preview). */
    private AbstractClientPlayer entity;
    /** Animation state, food level and mount of what is being posed: set from the player, or by the preview. */
    private PlayerData poseData;
    private int foodLevel = 20;
    private boolean ridingMob;
    /** Paddle cycle (Boat#getRowingTime) of the right / left paddle when steering a boat, else NaN. */
    private float rowRight = Float.NaN;
    private float rowLeft = Float.NaN;
    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float netHeadYaw;
    private float headPitch;

    @Override
    public List<ModelRenderer> getBoxList() {
        return this.boxList;
    }

    /** Player skins are 64x64; armor textures (and capes) keep the 64x32 layout. */
    @Override
    public int getTextureHeight() {
        return this.isArmor ? 32 : 64;
    }

    /** The two parts of the 1.7.10 ModelBiped the mod still used. */
    private void initBipedParts() {
        this.bipedCloak = new ModelRenderer(this, 0, 0);
        this.bipedCloak.setTextureSize(64, 32);
        this.bipedCloak.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1, 0.0F);
        this.bipedEars = new ModelRenderer(this, 24, 0);
        this.bipedEars.addBox(-3.0F, -6.0F, -1.0F, 6, 6, 1, 0.0F);
    }

    private void initImprovedHat(ModelRenderer target, float f) {
        ModelBox box = null;
        float scale = 1.14F;

        for (int x = -4; x < 4; x++) {
            for (int z = -4; z < 4; z++) {
                box = ModelBoxUV.addBox(target, x * scale, -7.5F * scale - 0.01F, z * scale, 1, 1, 1, f + 0.07F);
                box.setAllUV(44 + x, 3 - z);
                box = ModelBoxUV.addBox(target, x * scale, -0.5F * scale + 0.01F, z * scale, 1, 1, 1, f + 0.07F);
                box.setAllUV(52 + x, 3 - z);
            }
        }

        for (int x = -4; x < 4; x++) {
            for (int y = -8; y < 0; y++) {
                box = ModelBoxUV.addBox(target, x * scale, (y + 0.5F) * scale, -4.0F * scale - 0.01F, 1, 1, 1, f + 0.07F);
                box.setAllUV(44 + x, 16 + y);
                box = ModelBoxUV.addBox(target, x * scale, (y + 0.5F) * scale, 3.0F * scale + 0.01F, 1, 1, 1, f + 0.07F);
                box.setAllUV(59 - x, 16 + y);
            }
        }

        for (int z = -4; z < 4; z++) {
            for (int y = -8; y < 0; y++) {
                box = ModelBoxUV.addBox(target, -4.0F * scale - 0.01F, (y + 0.5F) * scale, z * scale, 1, 1, 1, f + 0.07F);
                box.setAllUV(35 - z, 16 + y);
                box = ModelBoxUV.addBox(target, 3.0F * scale + 0.01F, (y + 0.5F) * scale, z * scale, 1, 1, 1, f + 0.07F);
                box.setAllUV(52 + z, 16 + y);
            }
        }

        box.initQuads();
    }

    private void initEyes(int position, float f) {
        int index = position - 1;
        int yOffset = position - 2;
        ModelBox box;
        this.iris[0][index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.iris[0][index], -2.0F, -4.0F + yOffset, -4.001F, 1, 1, 1, f);
        box.setAllUV(27, index).initQuads();
        this.head.addChild(this.iris[0][index]);
        this.iris[1][index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.iris[1][index], -3.0F, -4.0F + yOffset, -4.001F, 1, 1, 1, f);
        box.setAllUV(27, index).initQuads();
        this.head.addChild(this.iris[1][index]);
        this.iris[2][index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.iris[2][index], 1.0F, -4.0F + yOffset, -4.001F, 1, 1, 1, f);
        box.setAllUV(31, index).initQuads();
        this.head.addChild(this.iris[2][index]);
        this.iris[3][index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.iris[3][index], 2.0F, -4.0F + yOffset, -4.001F, 1, 1, 1, f);
        box.setAllUV(31, index).initQuads();
        this.head.addChild(this.iris[3][index]);
        this.rightEye[index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.rightEye[index], -0.5F, 0.0F, 0.0F, 1, 1, 1, f);
        box.setAllUV(28, index).initQuads();
        this.rightEye[index].setRotationPoint(-1.7F, -4.0F + yOffset, -4.002F);
        this.head.addChild(this.rightEye[index]);
        this.leftEye[index] = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.leftEye[index], -0.5F, 0.0F, 0.0F, 1, 1, 1, f);
        box.setAllUV(30, index).initQuads();
        this.leftEye[index].setRotationPoint(1.7F, -4.0F + yOffset, -4.002F);
        this.head.addChild(this.leftEye[index]);
    }

    /** {@code root} is a baked vanilla player model, only used for the vanilla parts other layers rely on. */
    public ModelPlayer(ModelPart root, boolean slimRenderer, float f, boolean armor) {
        super(root, slimRenderer);
        this.isArmor = armor;
        this.slimRenderer = slimRenderer;
        this.young = false;
        this.initBipedParts();
        ModelBox box;
        this.pelvis = new ModelRenderer(this);
        this.pelvis.setRotationPoint(0.0F, 12.0F, 0.0F);
        if (armor) {
            this.chest = new ModelRenderer(this, 16, 16);
            this.chest.addBox(-4.0F, -12.0F, -2.0F, 8, 12, 4, f);
        } else {
            this.chest = new ModelObjRenderer(this);
        }

        this.pelvis.addChild(this.chest);
        this.head = new ModelJoint(this, 0, 0);
        this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, f);
        this.head.setRotationPoint(0.0F, -12.0F, 0.0F);
        this.chest.addChild(this.head);
        this.headwear = new ModelRenderer(this, 32, 0);
        if (armor) {
            this.headwear.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, f + 0.5F);
        } else {
            this.hat3D = new ModelRenderer(this, 32, 0);
            this.initImprovedHat(this.hat3D, f);
            this.headwear.addChild(this.hat3D);
            this.hatFlat = new ModelRenderer(this, 32, 0);
            this.hatFlat.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, f + 0.5F);
            this.headwear.addChild(this.hatFlat);
        }

        this.head.addChild(this.headwear);
        this.iris = new ModelRenderer[4][3];
        this.rightEye = new ModelRenderer[3];
        this.leftEye = new ModelRenderer[3];
        this.initEyes(1, f);
        this.initEyes(2, f);
        this.initEyes(3, f);
        this.brow1 = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.brow1, -1.5F, -0.5F, 0.0F, 3, 1, 1, f - 0.24F);
        box.setAllUV(24, 0).initQuads();
        this.brow1.setRotationPoint(-2.0F, -4.6F, -4.3F);
        this.head.addChild(this.brow1);
        this.brow2 = new ModelRenderer(this);
        this.brow2.mirror = true;
        box = ModelBoxUV.addBox(this.brow2, -1.5F, -0.5F, 0.0F, 3, 1, 1, f - 0.24F);
        box.setAllUV(24, 0).initQuads();
        this.brow2.setRotationPoint(2.0F, -4.6F, -4.3F);
        this.head.addChild(this.brow2);
        this.upperLip = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.upperLip, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(11, 13).initQuads();
        this.upperLip.setRotationPoint(0.0F, -2.0F, -4.004F);
        this.head.addChild(this.upperLip);
        this.lowerLip = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.lowerLip, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(11, 15).initQuads();
        this.lowerLip.setRotationPoint(0.0F, 0.0F, -4.004F);
        this.head.addChild(this.lowerLip);
        this.insideMouth = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.insideMouth, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(24, 2).initQuads();
        this.insideMouth.setRotationPoint(0.0F, -1.0F, -4.001F);
        this.head.addChild(this.insideMouth);
        this.mouth = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.mouth, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(11, 14).initQuads();
        this.mouth.setRotationPoint(0.0F, -1.2F, -4.003F);
        this.head.addChild(this.mouth);
        this.upperTeeth = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.upperTeeth, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(24, 1).initQuads();
        this.upperTeeth.setRotationPoint(0.0F, 0.1F, 0.001F);
        this.mouth.addChild(this.upperTeeth);
        this.lowerTeeth = new ModelRenderer(this);
        box = ModelBoxUV.addBox(this.lowerTeeth, -1.0F, -1.0F, 0.0F, 2, 1, 1, f);
        box.setAllUV(24, 3).initQuads();
        this.lowerTeeth.setRotationPoint(0.0F, -0.1F, -4.002F);
        this.head.addChild(this.lowerTeeth);
        this.arm1 = new ModelRenderer(this, 40, 16);
        this.arm2 = new ModelRenderer(this, 40, 16);
        if (armor) {
            box = ModelBoxUV.addBox(this.arm1, -2.0F, -2.0F, -2.0F, 4, 6, 4, f + 0.01F);
            box.setSideUV(3, 48, 16).initQuads();
            this.arm2.mirror = true;
            box = ModelBoxUV.addBox(this.arm2, -2.0F, -2.0F, -2.0F, 4, 6, 4, f + 0.01F);
            box.setSideUV(3, 48, 16).initQuads();
        } else {
            // 64x64 skins: separate left arm texture at (32, 48) and 3px slim arms (inner edge kept at the body).
            this.armWide1 = this.upperLimb(this.arm1, false, false, false, -2.0F, -2.0F, f + 0.01F);
            this.armSlim1 = this.upperLimb(this.arm1, false, false, true, -1.0F, -2.0F, f + 0.01F);
            this.armWide2 = this.upperLimb(this.arm2, false, true, false, -2.0F, -2.0F, f + 0.01F);
            this.armSlim2 = this.upperLimb(this.arm2, false, true, true, -2.0F, -2.0F, f + 0.01F);
            this.armWide1.addChild(this.sleeveWide1 = SkinLayer.build(this, -2.0F, -2.0F, -2.0F, 4, 4, 0, 6, 40, 32, true, true));
            this.armSlim1.addChild(this.sleeveSlim1 = SkinLayer.build(this, -1.0F, -2.0F, -2.0F, 3, 4, 0, 6, 40, 32, true, true));
            this.armWide2.addChild(this.sleeveWide2 = SkinLayer.build(this, -2.0F, -2.0F, -2.0F, 4, 4, 0, 6, 48, 48, true, true));
            this.armSlim2.addChild(this.sleeveSlim2 = SkinLayer.build(this, -2.0F, -2.0F, -2.0F, 3, 4, 0, 6, 48, 48, true, true));
        }
        this.arm1.setRotationPoint(-6.0F, -10.0F, 0.0F);
        this.chest.addChild(this.arm1);
        this.arm2.setRotationPoint(6.0F, -10.0F, 0.0F);
        this.chest.addChild(this.arm2);
        this.forearm1 = new ModelObjRenderer(this);
        this.forearm1.setRotationPoint(0.0F, 4.0F, 0.0F);
        this.arm1.addChild(this.forearm1);
        this.forearm2 = new ModelObjRenderer(this);
        this.forearm2.setRotationPoint(0.0F, 4.0F, 0.0F);
        this.arm2.addChild(this.forearm2);
        if (armor) {
            // Armor textures keep the 1.7.10 layout: the left arm mirrors the right one.
            this.forearm1.cubeList = lowerLimbCubes(this.forearm1, 40, 16, -2.0F, 4, f + 0.01F);
            this.forearm2.mirror = true;
            this.forearm2.cubeList = lowerLimbCubes(this.forearm2, 40, 16, -2.0F, 4, f + 0.01F);
            this.forearm2.mirror = false;
        }
        if (!armor) {
            this.forearmCubesWide1 = this.lowerLimb(this.forearm1, false, false, false, -2.0F, f + 0.01F);
            this.forearmCubesSlim1 = this.lowerLimb(this.forearm1, false, false, true, -1.0F, f + 0.01F);
            this.forearmCubesWide2 = this.lowerLimb(this.forearm2, false, true, false, -2.0F, f + 0.01F);
            this.forearmCubesSlim2 = this.lowerLimb(this.forearm2, false, true, true, -2.0F, f + 0.01F);
            this.chest.setTextureOffset(16, 16);
            this.chest.cubeList.add(new ModelBox(this.chest, 16, 16, -4.0F, -12.0F, -2.0F, 8, 12, 4, f).initQuads());
        }
        if (!armor) {
            this.forearm1.addChild(this.lowerSleeveWide1 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 6, 12, 40, 32, true, true));
            this.forearm1.addChild(this.lowerSleeveSlim1 = SkinLayer.build(this, -1.0F, 0.0F, -2.0F, 3, 4, 6, 12, 40, 32, true, true));
            this.forearm2.addChild(this.lowerSleeveWide2 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 6, 12, 48, 48, true, true));
            this.forearm2.addChild(this.lowerSleeveSlim2 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 3, 4, 6, 12, 48, 48, true, true));
            this.chest.addChild(this.jacketLayer = SkinLayer.build(this, -4.0F, -12.0F, -2.0F, 8, 4, 0, 12, 16, 32, true, true));
        }
        this.leg1 = new ModelJoint(this, 0, 16);
        box = ModelBoxUV.addBox(this.leg1.getModel(), -2.0F, 0.0F, -2.0F, 4, 6, 4, f + 0.1F);
        box.setSideUV(3, 8, 16).initQuads();
        if (!armor) {
            box.hideSide(3);
            this.leg1.getModel().addChild(this.jointCap(true, false, false, -2.0F, 6.0F, true, f + 0.1F, () -> true));
        }
        this.leg1.setRotationPoint(-2.0F, 0.0F, 0.0F);
        this.pelvis.addChild(this.leg1);
        this.leg2 = new ModelJoint(this, 0, 16);
        if (armor) {
            this.leg2.getModel().mirror = true;
            box = ModelBoxUV.addBox(this.leg2.getModel(), -2.0F, 0.0F, -2.0F, 4, 6, 4, f + 0.1F);
            box.setSideUV(3, 8, 16).initQuads();
        } else {
            this.legModern2 = this.upperLimb(this.leg2.getModel(), true, true, false, -2.0F, 0.0F, f + 0.1F);
            this.leg1.getModel().addChild(this.pants1 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 0, 6, 0, 32, true, true));
            this.legModern2.addChild(this.pants2 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 0, 6, 0, 48, true, true));
        }
        this.leg2.setRotationPoint(2.0F, 0.0F, 0.0F);
        this.pelvis.addChild(this.leg2);
        this.foot1 = new ModelJoint(this, 0, 22);
        if (armor) {
            box = ModelBoxUV.addBox(this.foot1.getModel(), -2.0F, 0.0F, -2.0F, 4, 6, 4, f);
            box.setSideUV(2, 0, 16).setSideUV(3, 8, 16).initQuads();
        } else {
            this.foot1.setModel(this.footObj1 = new ModelObjRenderer(this));
        }

        if (!armor) {
            this.footObj1.cubeList = this.lowerLimb(this.footObj1, true, false, false, -2.0F, f + 0.1F);
        }
        this.foot1.setRotationPoint(0.0F, 6.0F, 0.0F);
        this.leg1.addChild(this.foot1);
        this.foot2 = new ModelJoint(this, 0, 22);
        this.foot2.getModel().mirror = true;
        if (armor) {
            box = ModelBoxUV.addBox(this.foot2.getModel(), -2.0F, 0.0F, -2.0F, 4, 6, 4, f);
            box.setSideUV(2, 0, 16).setSideUV(3, 8, 16).initQuads();
        } else {
            this.foot2.setModel(this.footObj2 = new ModelObjRenderer(this));
        }

        if (!armor) {
            this.footObj2.cubeList = this.lowerLimb(this.footObj2, true, true, false, -2.0F, f + 0.1F);
        }
        this.foot2.setRotationPoint(0.0F, 6.0F, 0.0F);
        this.leg2.addChild(this.foot2);
        if (!armor) {
            this.foot1.getModel().addChild(this.lowerPants1 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 6, 12, 0, 32, true, true));
            this.foot2.getModel().addChild(this.lowerPants2 = SkinLayer.build(this, -2.0F, 0.0F, -2.0F, 4, 4, 6, 12, 0, 48, true, true));
        }
        this.bipedCloak.setRotationPoint(0.0F, -12.0F, 2.0F);
    }

    /** Lower half (rows 6 to 12) of a 12px limb whose texture starts at (u, v), as a plain 6px tall cube. */
    private List<ModelBox> lowerLimbCubes(ModelRenderer part, int u, int v, float x, int w, float add) {
        ModelBox box = new ModelBox(part, u, v, x, 0.0F, -2.0F, w, 6, 4, add);
        int d = 4;
        box.setSideUV(0, u + d + w, v + d + 6).setSideUV(1, u, v + d + 6)
                .setSideUV(4, u + d, v + d + 6).setSideUV(5, u + d + w + d, v + d + 6).initQuads();
        List<ModelBox> list = new ArrayList<>();
        list.add(box);
        return list;
    }

    /** Upper half of a 64x64 skin limb: a 6px tall box closed at the elbow / knee by a {@link JointCap}. */
    private ModelRenderer upperLimb(ModelRenderer parent, boolean leg, boolean left, boolean slim, float x, float y, float add) {
        int u = ClientProxy.limbU(leg, left);
        int v = ClientProxy.limbV(left);
        int w = slim ? 3 : 4;
        ModelRenderer part = new ModelRenderer(this, u, v);
        ModelBoxUV.addBox(part, x, y, -2.0F, w, 6, 4, add).hideSide(3).initQuads();
        part.addChild(this.jointCap(leg, left, slim, x, y + 6.0F, true, add, () -> true));
        parent.addChild(part);
        return part;
    }

    /**
     * Lower half as a plain cube (RoundedLimbs off), closed at the joint by a {@link JointCap} added to {@code part};
     * the cap only shows with the cube.
     */
    private List<ModelBox> lowerLimb(ModelRenderer part, boolean leg, boolean left, boolean slim, float x, float add) {
        int u = ClientProxy.limbU(leg, left);
        int v = ClientProxy.limbV(left);
        int w = slim ? 3 : 4;
        List<ModelBox> cubes = this.lowerLimbCubes(part, u, v, x, w, add);
        cubes.get(0).hideSide(2);
        BooleanSupplier shown = leg ? () -> !ModSettings.ROUNDED_LIMBS.on()
                : () -> !ModSettings.ROUNDED_LIMBS.on() && this.slim == slim;
        part.addChild(this.jointCap(leg, left, slim, x, 0.0F, false, add, shown));
        return cubes;
    }

    private JointCap jointCap(boolean leg, boolean left, boolean slim, float x, float y, boolean down, float add, BooleanSupplier shown) {
        int w = slim ? 3 : 4;
        return new JointCap(this, x, w, 4, add, y, down, shown, ClientProxy.limbU(leg, left), ClientProxy.limbV(left),
                down ? 5 : 6, ClientProxy.capU(leg, left, slim), ClientProxy.limbV(left),
                left ? ClientProxy.capU(leg, false, slim) : -1, 16);
    }

    /** Picks the limbs matching the skin (optional 3px arms) and which parts of the 3D second layer are shown. */
    public void setSkinLayout(AbstractClientPlayer player) {
        if (this.isArmor) {
            return;
        }
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        // The 3D layer would poke through armor pieces, so it is hidden under them.
        boolean chestArmor = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
        boolean legArmor = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ArmorItem
                || player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof ArmorItem;
        this.applyLayout(slim, player.isModelPartShown(PlayerModelPart.HAT),
                !chestArmor && player.isModelPartShown(PlayerModelPart.JACKET),
                !chestArmor && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
                !chestArmor && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
                !legArmor && player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG),
                !legArmor && player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG));
    }

    public void applyLayout(boolean slim, boolean hat, boolean jacket, boolean rightSleeve, boolean leftSleeve,
                            boolean rightPants, boolean leftPants) {
        this.slim = slim;
        this.armWide1.showModel = !slim;
        this.armSlim1.showModel = slim;
        this.armWide2.showModel = !slim;
        this.armSlim2.showModel = slim;
        this.forearm1.cubeList = slim ? this.forearmCubesSlim1 : this.forearmCubesWide1;
        this.forearm2.cubeList = slim ? this.forearmCubesSlim2 : this.forearmCubesWide2;
        ((ModelObjRenderer) this.chest).model = ClientProxy.skinChest;

        this.sleeveWide1.showModel = this.sleeveSlim1.showModel = rightSleeve;
        this.lowerSleeveWide1.showModel = rightSleeve && !slim;
        this.lowerSleeveSlim1.showModel = rightSleeve && slim;
        this.sleeveWide2.showModel = this.sleeveSlim2.showModel = leftSleeve;
        this.lowerSleeveWide2.showModel = leftSleeve && !slim;
        this.lowerSleeveSlim2.showModel = leftSleeve && slim;
        this.headwear.showModel = hat;
        this.hat3D.showModel = ModSettings.BETTER_HAT.on();
        this.hatFlat.showModel = !ModSettings.BETTER_HAT.on();
        this.jacketLayer.showModel = jacket;
        this.pants1.showModel = this.lowerPants1.showModel = rightPants;
        this.pants2.showModel = this.lowerPants2.showModel = leftPants;
    }

    /** Rounded limb meshes whose joint end matches the skin (same choice as JointCap). */
    private void selectMeshes(ModelBox.SkinPixels skin) {
        int slim = this.slim ? 1 : 0;
        this.forearm1.model = ClientProxy.SKIN_FOREARMS[slim][0][capTexture(skin, false, false, this.slim)];
        this.forearm2.model = ClientProxy.SKIN_FOREARMS[slim][1][capTexture(skin, false, true, this.slim)];
        this.footObj1.model = ClientProxy.SKIN_FEET[0][capTexture(skin, true, false, false)];
        this.footObj2.model = ClientProxy.SKIN_FEET[1][capTexture(skin, true, true, false)];
    }

    private static int capTexture(ModelBox.SkinPixels skin, boolean leg, boolean left, boolean slim) {
        if (capPainted(skin, ClientProxy.capU(leg, left, slim), ClientProxy.limbV(left), slim)) {
            return ClientProxy.CAP_ZONE;
        }
        if (left && capPainted(skin, ClientProxy.capU(leg, false, slim), 16, slim)) {
            return ClientProxy.CAP_MIRROR;
        }
        return ClientProxy.CAP_EDGE;
    }

    private static boolean capPainted(ModelBox.SkinPixels skin, int u0, int v0, boolean slim) {
        if (skin == null) {
            return false;
        }
        for (int v = v0; v < v0 + 4; v++) {
            for (int u = u0; u < u0 + (slim ? 3 : 4); u++) {
                if (skin.isPainted(u + v * 64)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSlim() {
        return this.slim;
    }

    /** Armor models use the shipped meshes (64x32 armor textures, mirrored left limbs as in 1.7.10). */
    private void initModels() {
        if (this.isArmor) {
            this.forearm1.model = ClientProxy.forearm1;
            this.forearm2.model = ClientProxy.forearm2;
        }
    }

    /** Per-frame state that 1.7.10 set in RenderPlayer before rendering. */
    @Override
    public void prepareMobModel(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        this.partialTick = partialTick;
        ItemStack stack = player.getMainHandItem();
        this.heldItemRight = stack.isEmpty() ? 0 : 1;
        boolean usingMainHand = !stack.isEmpty() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        this.aimedBow = usingMainHand && stack.getUseAnimation() == UseAnim.BOW ? player.getUseItemRemainingTicks() : 0;
        this.chargingSpear = usingMainHand && stack.getUseAnimation() == UseAnim.SPEAR;
        this.setSkinLayout(player);
    }

    @Override
    public void setupAnim(AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // The vanilla parts were moved onto the animated body last frame (syncVanillaParts), and vanilla's setupAnim
        // does not set every position: start from their rest pose, or a vanilla-drawn player would be dislocated.
        if (!this.isArmor) {
            for (ModelPart part : new ModelPart[]{super.head, this.hat, this.body, this.jacket, this.rightArm, this.leftArm,
                    this.rightSleeve, this.leftSleeve, this.rightLeg, this.leftLeg, this.rightPants, this.leftPants}) {
                part.resetPose();
            }
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.vanillaPose = !this.isArmor && ClientProxy.useVanilla(entity);
        if (renderingFirstPersonHand || this.vanillaPose) {
            return;
        }
        this.entity = entity;
        this.poseData = PlayerData.getPlayerData(entity);
        this.foodLevel = entity.getFoodData().getFoodLevel();
        this.ridingMob = entity.getVehicle() instanceof Mob;
        if (entity.getVehicle() instanceof Boat boat && boat.getControllingPassenger() == entity) {
            // Boat paddle 0 is on the left, 1 on the right.
            this.rowLeft = boat.getRowingTime(0, this.partialTick);
            this.rowRight = boat.getRowingTime(1, this.partialTick);
        } else {
            this.rowLeft = this.rowRight = Float.NaN;
        }
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
        this.pose();
        if (!this.isArmor) {
            this.syncVanillaParts();
        }
    }

    /** Draws the hierarchy from the pelvis; the joints were posed by {@link #setupAnim}. */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (this.poseData == null) {
            return;
        }
        if (this.vanillaPose) {
            super.renderToBuffer(poseStack, consumer, light, overlay, color);
            return;
        }
        if (!this.isArmor) {
            this.applyVanillaVisibility();
            ModelBox.visiblePixels = this.poseData.textureInfo.skinInfo;
            this.selectMeshes(this.poseData.textureInfo.skinInfo);
        }
        this.pelvis.render(poseStack, consumer, light, overlay, color);
        ModelBox.visiblePixels = null;
    }

    /**
     * Where vanilla (and other mods) attach held items: the mod's forearm instead of the stiff vanilla arm.
     * The mod used translate(0.03, 0.135, -0.01) on the forearm where 1.7.10 vanilla used (-1/16, 7/16, 1/16) on the
     * arm; the vanilla offset is undone because ItemInHandLayer applies the modern equivalent right after.
     */
    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        if (this.poseData == null || this.isArmor || this.vanillaPose) {
            super.translateToHand(arm, poseStack);
            return;
        }
        boolean left = arm == HumanoidArm.LEFT;
        float side = left ? -1.0F : 1.0F;
        this.pelvis.translateAndRotate(poseStack);
        this.chest.translateAndRotate(poseStack);
        (left ? this.arm2 : this.arm1).translateAndRotate(poseStack);
        (left ? this.forearm2 : this.forearm1).translateAndRotate(poseStack);
        poseStack.translate(0.03F * side, 0.135F, -0.01F);
        if (this.slim) {
            // Vanilla moves items half a pixel towards the body for 3px arms.
            poseStack.translate(0.5F / 16.0F * side, 0.0F, 0.0F);
        }
        poseStack.translate(0.0625F * side, -0.4375F, -0.0625F);
    }

    /**
     * Other mods hide vanilla parts to hide body parts (First-person Model hides the head when drawing the body
     * in first person, for instance): the matching joints follow. Vanilla also uses these flags for the skin
     * customization options (hat, jacket, sleeves, pants).
     */
    private void applyVanillaVisibility() {
        this.head.showModel = super.head.visible;
        this.headwear.showModel = this.headwear.showModel && this.hat.visible;
        this.chest.hideOwn = !this.body.visible;
        this.jacketLayer.showModel = this.jacketLayer.showModel && this.jacket.visible;
        this.arm1.showModel = this.rightArm.visible;
        this.arm2.showModel = this.leftArm.visible;
        this.leg1.showModel = this.rightLeg.visible;
        this.leg2.showModel = this.leftLeg.visible;
        boolean rightSleeve = this.rightSleeve.visible;
        boolean leftSleeve = this.leftSleeve.visible;
        this.sleeveWide1.showModel &= rightSleeve;
        this.sleeveSlim1.showModel &= rightSleeve;
        this.lowerSleeveWide1.showModel &= rightSleeve;
        this.lowerSleeveSlim1.showModel &= rightSleeve;
        this.sleeveWide2.showModel &= leftSleeve;
        this.sleeveSlim2.showModel &= leftSleeve;
        this.lowerSleeveWide2.showModel &= leftSleeve;
        this.lowerSleeveSlim2.showModel &= leftSleeve;
        this.pants1.showModel &= this.rightPants.visible;
        this.lowerPants1.showModel &= this.rightPants.visible;
        this.pants2.showModel &= this.leftPants.visible;
        this.lowerPants2.showModel &= this.leftPants.visible;
    }

    /** Whether the vanilla part an armor piece covers is shown (hidden by another mod, or not). */
    public boolean isVanillaPartVisible(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> super.head.visible;
            case CHEST -> this.body.visible || this.rightArm.visible || this.leftArm.visible;
            case LEGS, FEET -> this.rightLeg.visible || this.leftLeg.visible;
            default -> true;
        };
    }

    /** Copies the world transform of the mod's joints onto the matching vanilla parts. */
    private void syncVanillaParts() {
        float slimY = this.slimRenderer ? 0.5F : 0.0F;
        this.sync(super.head, 0.0F, 0.0F, this.pelvis, this.chest, this.head, this.head.getModel());
        this.sync(this.body, 0.0F, -12.0F, this.pelvis, this.chest);
        this.sync(this.rightArm, 1.0F, slimY, this.pelvis, this.chest, this.arm1);
        this.sync(this.leftArm, -1.0F, slimY, this.pelvis, this.chest, this.arm2);
        this.sync(this.rightLeg, 0.1F, 0.0F, this.pelvis, this.leg1, this.leg1.getModel());
        this.sync(this.leftLeg, -0.1F, 0.0F, this.pelvis, this.leg2, this.leg2.getModel());
        this.hat.copyFrom(super.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }

    /** {@code dx, dy}: vanilla pivot in the last joint's frame, in pixels. */
    private void sync(ModelPart target, float dx, float dy, ModelRenderer... chain) {
        PoseStack stack = this.syncStack;
        stack.setIdentity();
        for (ModelRenderer joint : chain) {
            joint.translateAndRotate(stack);
        }
        stack.translate(dx / 16.0F, dy / 16.0F, 0.0F);
        Matrix4f m = stack.last().pose();
        target.x = m.m30() * 16.0F;
        target.y = m.m31() * 16.0F;
        target.z = m.m32() * 16.0F;
        Vector3f angles = m.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        target.xRot = angles.x();
        target.yRot = angles.y();
        target.zRot = angles.z();
    }

    /**
     * Poses the model without a player entity, for the settings screen preview: the caller drives the animation
     * state ({@code data}) and the vanilla model inputs.
     */
    public void posePreview(PlayerData data, int foodLevel, float limbSwing, float limbSwingAmount, float ageInTicks,
                            float netHeadYaw, float headPitch, float partialTick, float attackTime, boolean holdingItem) {
        this.vanillaPose = false;
        this.poseData = data;
        this.foodLevel = foodLevel;
        this.ridingMob = false;
        this.rowLeft = this.rowRight = Float.NaN;
        this.riding = false;
        this.attackTime = attackTime;
        this.partialTick = partialTick;
        this.heldItemRight = holdingItem ? 1 : 0;
        this.aimedBow = 0;
        this.chargingSpear = false;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
        this.pose();
    }

    /** Vanilla walk pose without a player entity, for the preview of the "Animated player: off" setting. */
    public void poseVanillaPreview(PlayerData data, float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch) {
        this.poseData = data;
        this.vanillaPose = true;
        this.riding = false;
        this.crouching = false;
        this.swimAmount = 0.0F;
        float armY = this.slimRenderer ? 2.5F : 2.0F;
        resetPart(super.head, 0.0F, 0.0F);
        super.head.xRot = headPitch * PI / 180.0F;
        super.head.yRot = netHeadYaw * PI / 180.0F;
        resetPart(this.body, 0.0F, 0.0F);
        resetPart(this.rightArm, -5.0F, armY);
        resetPart(this.leftArm, 5.0F, armY);
        resetPart(this.rightLeg, -1.9F, 12.0F);
        resetPart(this.leftLeg, 1.9F, 12.0F);
        // HumanoidModel#setupAnim walk cycle.
        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + PI) * limbSwingAmount;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount;
        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + PI) * 1.4F * limbSwingAmount;
        this.hat.copyFrom(super.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }

    private static void resetPart(ModelPart part, float x, float y) {
        part.setPos(x, y, 0.0F);
        part.setRotation(0.0F, 0.0F, 0.0F);
    }

    /** The animation part of the original render(entity, f, f1, f2, f3, f4, f5). */
    public void pose() {
        PlayerData data = this.poseData;
        this.initModels();
        this.setShowModels(data);
        // IdleAnimation off: the standing stance and breathing fade out as the player stops moving.
        this.stance = ModSettings.IDLE_ANIMATION.on() ? 1.0F : Math.min(1.0F, this.limbSwingAmount * 4.0F);
        this.setAngles(data);
        // Creative flight keeps the hovering pose while moving: fade the walk cycle out.
        float hover = (data.prevHoverTick + (data.hoverTick - data.prevHoverTick) * partialTick) / 5.0F;
        this.animate(data, this.limbSwing, this.limbSwingAmount * (1.0F - hover), this.ageInTicks, this.netHeadYaw, this.headPitch);
        this.applyIntensity();

        for (int i = 0; i < this.rightEye.length; i++) {
            if (this.rightEye[i].rotationPointX < -2.5F) {
                this.rightEye[i].rotationPointX = -2.5F;
            }
            if (this.rightEye[i].rotationPointX > -1.5F) {
                this.rightEye[i].rotationPointX = -1.5F;
            }
            if (this.leftEye[i].rotationPointX < 1.5F) {
                this.leftEye[i].rotationPointX = 1.5F;
            }
            if (this.leftEye[i].rotationPointX > 2.5F) {
                this.leftEye[i].rotationPointX = 2.5F;
            }
        }
    }

    /**
     * AnimationIntensity option: scales the body joints' animation (look direction excluded). Not applied to
     * poses that must stay exact: aiming a bow or trident, riding.
     */
    private void applyIntensity() {
        float k = ModSettings.INTENSITY.get() / 100.0F;
        if (k == 1.0F || this.aimedBow > 0 || this.chargingSpear || this.riding) {
            return;
        }
        multiplyAngles(k, this.pelvis, this.chest, this.head);
        multiplyAngles(k, this.arm1, this.arm2, this.forearm1, this.forearm2);
        multiplyAngles(k, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
        multiplyAngles(k, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
        this.pelvis.rotationPointY = 12.0F + (this.pelvis.rotationPointY - 12.0F) * k;
        this.pelvis.rotationPointZ *= k;
    }

    /**
     * Places {@code poseStack} on a random body part for a stuck arrow and returns that part's box
     * (x1, y1, z1, x2, y2, z2 in pixels). Mesh parts (chest, forearms, feet) use their box equivalent.
     */
    public float[] randomArrowTarget(RandomSource random, PoseStack poseStack) {
        float armX = this.slim ? -1.0F : -2.0F;
        float armW = this.slim ? 3.0F : 4.0F;
        int part = random.nextInt(10);
        this.pelvis.translateAndRotate(poseStack);
        switch (part) {
            case 0 -> {
                this.chest.translateAndRotate(poseStack);
                this.head.translateAndRotate(poseStack);
                this.head.getModel().translateAndRotate(poseStack);
                return new float[]{-4, -8, -4, 4, 0, 4};
            }
            case 1 -> {
                this.chest.translateAndRotate(poseStack);
                return new float[]{-4, -12, -2, 4, 0, 2};
            }
            case 2, 3, 4, 5 -> {
                boolean left = part >= 4;
                this.chest.translateAndRotate(poseStack);
                (left ? this.arm2 : this.arm1).translateAndRotate(poseStack);
                float x1 = left ? -2.0F : armX;
                if (part % 2 == 0) {
                    return new float[]{x1, -2, -2, x1 + armW, 4, 2};
                }
                (left ? this.forearm2 : this.forearm1).translateAndRotate(poseStack);
                return new float[]{x1, 0, -2, x1 + armW, 6, 2};
            }
            default -> {
                boolean left = part >= 8;
                ModelJoint leg = left ? this.leg2 : this.leg1;
                leg.translateAndRotate(poseStack);
                leg.getModel().translateAndRotate(poseStack);
                if (part % 2 == 1) {
                    ModelJoint foot = left ? this.foot2 : this.foot1;
                    foot.translateAndRotate(poseStack);
                    foot.getModel().translateAndRotate(poseStack);
                }
                return new float[]{-2, 0, -2, 2, 6, 2};
            }
        }
    }

    private void setShowModels(PlayerData data) {
        // Checked every frame so the AnimateFace option applies as soon as it is changed.
        boolean face = ModSettings.ANIMATE_FACE.on();
        boolean eyes1 = face && data.textureInfo.animateEyes1;
        boolean eyes2 = face && data.textureInfo.animateEyes2;
        boolean eyes3 = face && data.textureInfo.animateEyes3;
        boolean brows = face && data.textureInfo.animateEyebrows;
        boolean mouth = face && data.textureInfo.animateMouth;
        for (ModelRenderer[] irisRow : this.iris) {
            irisRow[0].showModel = eyes1;
            irisRow[1].showModel = eyes2;
            irisRow[2].showModel = eyes3;
        }

        this.rightEye[0].showModel = eyes1;
        this.rightEye[1].showModel = eyes2;
        this.rightEye[2].showModel = eyes3;
        this.leftEye[0].showModel = eyes1;
        this.leftEye[1].showModel = eyes2;
        this.leftEye[2].showModel = eyes3;
        this.brow1.showModel = brows;
        this.brow2.showModel = brows;
        this.mouth.showModel = mouth;
        this.insideMouth.showModel = mouth;
        this.upperTeeth.showModel = mouth;
        this.lowerTeeth.showModel = mouth;
    }

    /**
     * Not in 1.7.10 (no paddles): the arm holds the boat paddle on its side and follows it, with the same cycle as
     * the paddle in BoatModel#animatePaddle (dip, then sweep back). {@code side}: 1 for the right arm, -1 the left.
     */
    private void row(ModelRenderer arm, ModelRenderer forearm, float time, float side) {
        float dip = (Mth.sin(-time) + 1.0F) / 2.0F;
        float sweep = (Mth.sin(-time + 1.0F) + 1.0F) / 2.0F;
        arm.rotateAngleX = -0.35F - 0.8F * sweep;
        arm.rotateAngleY = side * 0.15F;
        arm.rotateAngleZ = side * (0.35F + 0.25F * dip);
        forearm.rotateAngleX = -0.5F - 0.5F * (1.0F - sweep);
    }

    public static void resetAngles(ModelRenderer... boxes) {
        for (ModelRenderer box : boxes) {
            box.rotateAngleX = 0.0F;
            box.rotateAngleY = 0.0F;
            box.rotateAngleZ = 0.0F;
        }
    }

    public static void multiplyAngles(float scale, ModelRenderer... boxes) {
        for (ModelRenderer box : boxes) {
            box.rotateAngleX *= scale;
            box.rotateAngleY *= scale;
            box.rotateAngleZ *= scale;
        }
    }

    public void setAngles(PlayerData data) {
        resetAngles(this.head, this.headwear, this.chest, this.pelvis);
        resetAngles(this.arm1, this.arm2, this.forearm1, this.forearm2);
        resetAngles(this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
        resetAngles(this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
        int browAdd = 0;
        if (data.textureInfo.animateEyes1) {
            browAdd = -1;
        } else if (!data.textureInfo.animateEyes2 && data.textureInfo.animateEyes3) {
            browAdd = 1;
        }

        this.pelvis.rotationPointZ = 0.0F;

        for (int i = 0; i < this.rightEye.length; i++) {
            this.rightEye[i].rotationPointX = -1.7F;
            this.leftEye[i].rotationPointX = 1.7F;
        }

        this.brow1.rotationPointY = -4.6F + browAdd;
        this.brow1.rotateAngleZ = 0.12083049F;
        this.brow2.rotationPointY = -4.6F + browAdd;
        this.brow2.rotateAngleZ = -0.12083049F;
        this.mouth.rotationPointY = -1.2F;
        float stance = this.stance;
        this.pelvis.rotationPointY = 12.0F + 0.8F * stance;
        this.arm1.rotateAngleX = 0.1F * stance;
        this.arm1.rotateAngleZ = 0.1F * stance;
        this.arm2.rotateAngleX = 0.1F * stance;
        this.arm2.rotateAngleZ = -0.1F * stance;
        this.forearm1.rotateAngleX = -0.3F * stance;
        this.forearm2.rotateAngleX = -0.3F * stance;
        this.leg1.rotateAngleX = -0.15F * stance;
        this.leg1.rotateAngleZ = 0.08F * stance;
        this.leg2.rotateAngleX = -0.15F * stance;
        this.leg2.rotateAngleZ = -0.08F * stance;
        this.foot1.rotateAngleX = 0.25F * stance;
        this.foot1.rotateAngleZ = -0.04F * stance;
        this.foot2.rotateAngleX = 0.25F * stance;
        this.foot2.rotateAngleZ = 0.04F * stance;
    }

    public void animate(PlayerData data, float f, float f1, float f2, float f3, float f4) {
        float oldF1 = Math.min(1.0F, f1);
        float invF1 = 1.0F - f1;
        float breatheAnim = Mth.sin(f2 * 0.1F);
        float breatheStance = this.stance;
        float faceYaw = f3 * PI / 180.0F;
        float facePitch = f4 * PI / 180.0F;
        float f6;
        float hunger = ModSettings.HUNGER_EYEBROWS.on() ? 1.0F - this.foodLevel / 20.0F : 0.0F;
        this.brow1.rotateAngleZ = this.brow1.rotateAngleZ - hunger * PI / 13.0F;
        this.brow2.rotateAngleZ = this.brow2.rotateAngleZ + hunger * PI / 13.0F;
        float browAnim = ModSettings.EYEBROW_EXPRESSIONS.on() ? data.prevBrowTick + (data.browTick - data.prevBrowTick) * partialTick : 0.0F;
        f1 = browAnim / 8.0F;
        f6 = 1.0F - f1;
        this.brow1.rotationPointY -= f6 * 0.2F;
        this.brow2.rotationPointY -= f6 * 0.2F;
        if (this.brow1.rotateAngleZ > 0.0F) {
            this.brow1.rotateAngleZ *= 1.0F - f6 * 0.8F;
        }
        if (this.brow2.rotateAngleZ < 0.0F) {
            this.brow2.rotateAngleZ *= 1.0F - f6 * 0.8F;
        }

        float walkSpeed = 0.6F;
        float walkAnim1 = Mth.sin(f * walkSpeed);
        float walkAnim2 = -Mth.sin(f * walkSpeed + (data.moveBackwards ? -1.4F : 1.4F));
        if (!data.isAirborne) {
            this.pelvis.rotationPointY = this.pelvis.rotationPointY + Math.abs(walkAnim1) * 1.4F * oldF1;
        }

        this.arm1.rotateAngleX -= walkAnim1 * 0.8F * oldF1;
        this.arm2.rotateAngleX += walkAnim1 * 0.8F * oldF1;
        this.forearm1.rotateAngleX = this.forearm1.rotateAngleX - Math.max(0.0F, walkAnim1) * 0.5F * oldF1;
        this.forearm2.rotateAngleX = this.forearm2.rotateAngleX + Math.min(0.0F, walkAnim1) * 0.5F * oldF1;
        this.leg1.rotateAngleX += walkAnim1 * 0.9F * oldF1;
        this.leg2.rotateAngleX -= walkAnim1 * 0.9F * oldF1;
        this.foot1.rotateAngleX += (0.5F + walkAnim2 * 0.9F) * oldF1;
        this.foot2.rotateAngleX += (0.5F - walkAnim2 * 0.9F) * oldF1;
        this.arm1.rotateAngleZ -= oldF1 * 0.06F;
        this.arm2.rotateAngleZ += oldF1 * 0.06F;
        this.leg1.rotateAngleZ -= oldF1 * 0.05F;
        this.leg2.rotateAngleZ += oldF1 * 0.05F;
        if (data.sprintTick > 0 || data.prevSprintTick > 0) {
            float sprint = data.prevSprintTick + (data.sprintTick - data.prevSprintTick) * partialTick;
            f6 = 1.0F - sprint / 8.0F;
            if (!data.isAirborne) {
                this.pelvis.rotationPointY = 12.0F + (this.pelvis.rotationPointY - 12.0F) * f6;
            }

            multiplyAngles(f6, this.chest, this.pelvis);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            float runSpeed = 0.7F;
            float runAnim1 = Mth.sin(f * runSpeed);
            float runAnim2 = -Mth.sin(f * runSpeed + 1.4F);
            f1 = sprint / 10.0F;
            if (!data.isAirborne) {
                this.pelvis.rotationPointY = this.pelvis.rotationPointY + Math.abs(runAnim1) * 1.8F * f1;
            }

            this.chest.rotateAngleX += f1 * 0.3F;
            this.head.rotateAngleX -= f1 * 0.3F;
            this.arm1.rotateAngleX -= runAnim1 * 1.2F * f1;
            this.arm2.rotateAngleX += runAnim1 * 1.2F * f1;
            this.forearm1.rotateAngleX = this.forearm1.rotateAngleX - Math.max(0.3F, runAnim1) * 1.2F * f1;
            this.forearm2.rotateAngleX = this.forearm2.rotateAngleX + Math.min(-0.3F, runAnim1) * 1.2F * f1;
            this.leg1.rotateAngleX += runAnim1 * 1.4F * f1;
            this.leg2.rotateAngleX -= runAnim1 * 1.4F * f1;
            this.foot1.rotateAngleX += (0.9F + runAnim2 * 1.1F) * f1;
            this.foot2.rotateAngleX += (0.9F - runAnim2 * 1.1F) * f1;
            this.arm1.rotateAngleZ += f1 * 0.16F;
            this.arm2.rotateAngleZ -= f1 * 0.16F;
            this.leg1.rotateAngleZ += f1 * 0.03F;
            this.leg2.rotateAngleZ -= f1 * 0.03F;
        }

        if (data.sneakTick > 0 || data.prevSneakTick > 0) {
            float sneak = data.prevSneakTick + (data.sneakTick - data.prevSneakTick) * partialTick;
            f1 = sneak / 6.0F;
            f6 = 1.0F - f1;
            multiplyAngles(f6, this.chest, this.pelvis);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            this.chest.rotateAngleX += (PI / 8) * f1;
            this.head.rotateAngleX -= (PI / 6) * f1;
            this.arm1.rotateAngleX += (PI / 12) * f1;
            this.arm1.rotateAngleZ += (PI / 14) * f1;
            this.arm2.rotateAngleX += (PI / 12) * f1;
            this.arm2.rotateAngleZ -= (PI / 14) * f1;
            this.forearm1.rotateAngleX -= (PI / 6) * f1;
            this.forearm2.rotateAngleX -= (PI / 6) * f1;
            this.leg1.getModel().rotateAngleX -= (PI / 6) * f1;
            this.leg1.getModel().rotateAngleZ += (PI / 14) * f1;
            this.leg2.getModel().rotateAngleX -= (PI / 6) * f1;
            this.leg2.getModel().rotateAngleZ -= (PI / 14) * f1;
            this.foot1.rotateAngleX += (PI / 4) * f1;
            this.foot1.rotateAngleZ -= (PI / 16) * f1;
            this.foot2.rotateAngleX += (PI / 4) * f1;
            this.foot2.rotateAngleZ += (PI / 16) * f1;
            float sneakSpeed = 0.6F;
            float sneakAnim1 = Mth.sin(f * sneakSpeed);
            float sneakAnim2 = -Mth.sin(f * sneakSpeed + (data.moveBackwards2 ? -0.8F : 0.8F));
            this.pelvis.rotationPointY = this.pelvis.rotationPointY + (-0.5F + Math.abs(sneakAnim1) * 1.4F) * f1;
            this.arm1.rotateAngleX -= sneakAnim1 * 0.8F * f1;
            this.arm2.rotateAngleX += sneakAnim1 * 0.8F * f1;
            this.forearm1.rotateAngleX = this.forearm1.rotateAngleX - Math.max(0.0F, sneakAnim1) * 0.6F * f1;
            this.forearm2.rotateAngleX = this.forearm2.rotateAngleX + Math.min(0.0F, sneakAnim1) * 0.6F * f1;
            this.leg1.rotateAngleX += (-0.1F + sneakAnim1 * 0.7F) * f1;
            this.leg2.rotateAngleX += (-0.1F - sneakAnim1 * 0.7F) * f1;
            this.foot1.getModel().rotateAngleX += (0.4F + sneakAnim2 * 0.6F) * f1;
            this.foot2.getModel().rotateAngleX += (0.4F - sneakAnim2 * 0.6F) * f1;
        }

        if (data.blockTick > 0 || data.prevBlockTick > 0) {
            f1 = data.prevBlockTick + (data.blockTick - data.prevBlockTick) * partialTick;
            f6 = 1.0F - f1;
            float blockF1 = Math.min(0.25F, oldF1);
            multiplyAngles(f6, this.chest, this.pelvis, this.head);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            this.pelvis.rotationPointY = this.pelvis.rotationPointY + 1.0F + Math.abs(Mth.sin(f * 0.85F) * 0.6F) * blockF1 * f1;
            this.chest.rotateAngleX += (PI / 12) * f1;
            this.head.rotateAngleX -= (PI / 12) * f1;
            this.arm1.rotateAngleX -= (PI / 8) * f1;
            this.arm1.rotateAngleY -= (PI / 4) * f1;
            this.arm1.rotateAngleZ += (PI / 4) * f1;
            this.arm2.rotateAngleZ -= (PI / 6) * f1;
            this.forearm1.rotateAngleX -= (PI / 10) * f1;
            this.forearm2.rotateAngleX -= (PI / 6) * f1;
            this.leg1.rotateAngleX -= (PI / 8) * f1;
            this.leg1.rotateAngleZ += (PI / 14) * f1;
            this.leg2.rotateAngleX -= (PI / 8) * f1;
            this.leg2.rotateAngleZ -= (PI / 14) * f1;
            this.foot1.rotateAngleZ -= 0.17453294F * f1;
            this.foot2.rotateAngleZ += 0.17453294F * f1;
            this.foot1.getModel().rotateAngleX += (PI / 6) * f1;
            this.foot2.getModel().rotateAngleX += (PI / 6) * f1;
            this.leg1.rotateAngleX = this.leg1.rotateAngleX + Mth.sin(f * 0.85F) * 2.1F * blockF1 * f1;
            this.leg2.rotateAngleX = this.leg2.rotateAngleX - Mth.sin(f * 0.85F) * 2.1F * blockF1 * f1;
            this.foot1.rotateAngleX = this.foot1.rotateAngleX + (0.5F + Mth.sin(f * 0.85F - 1.2F) * 2.1F) * blockF1 * f1;
            this.foot2.rotateAngleX = this.foot2.rotateAngleX + (0.5F - Mth.sin(f * 0.85F - 1.2F) * 2.1F) * blockF1 * f1;
        }

        if (data.jumpTick > 0 || data.prevJumpTick > 0) {
            float jumpAnim = data.prevJumpTick + (data.jumpTick - data.prevJumpTick) * partialTick;
            f1 = Math.min(4.0F, jumpAnim) / 5.0F;
            f6 = 1.0F - f1;
            float jumpClamp = jumpAnim / 10.0F;
            float jumpMove = Mth.sin(jumpClamp * PI) * (data.jumpRight ? 1 : -1);
            multiplyAngles(f6, this.pelvis, this.head);
            multiplyAngles(f6 * 0.6F, this.chest);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            this.pelvis.rotateAngleX += (PI / 12) * f1;
            this.head.rotateAngleX -= (PI / 12) * f1;
            this.arm1.rotateAngleX += PI / (4.0F - oldF1 * 2.0F) * jumpMove * f1;
            this.arm1.rotateAngleZ = this.arm1.rotateAngleZ + ((PI / 12) + Mth.sin(f2 * 0.3F - 0.7F) * 0.2F) * f1;
            this.arm2.rotateAngleX += -PI / (4.0F - oldF1 * 2.0F) * jumpMove * f1;
            this.arm2.rotateAngleZ = this.arm2.rotateAngleZ - ((PI / 12) + Mth.sin(f2 * 0.3F - 1.5F) * 0.2F) * f1;
            this.forearm1.rotateAngleX -= (PI / 6) * f1;
            this.forearm2.rotateAngleX -= (PI / 6) * f1;
            this.leg1.rotateAngleX += ((-PI / 4) + -PI / (5.0F - oldF1 * 2.5F) * jumpMove) * f1;
            this.leg1.rotateAngleX = this.leg1.rotateAngleX + Mth.sin(f2 * 0.3F) * 0.3F * f1;
            this.leg1.rotateAngleZ += (PI / 14) * f1;
            this.leg2.rotateAngleX += ((-PI / 4) + PI / (5.0F - oldF1 * 2.5F) * jumpMove) * f1;
            this.leg2.rotateAngleX = this.leg2.rotateAngleX + Mth.sin(f2 * 0.3F - 1.1F) * 0.3F * f1;
            this.leg2.rotateAngleZ -= (PI / 14) * f1;
            this.foot1.rotateAngleX += (float) (Math.PI * 2.0 / 5.0) * f1;
            this.foot2.rotateAngleX += (float) (Math.PI * 2.0 / 5.0) * f1;
        }

        if ((data.swimTick > 0 || data.prevSwimTick > 0) && data.flyingTick <= 0 && data.prevFlyingTick <= 0) {
            float swimAnim = data.prevSwimTick + (data.swimTick - data.prevSwimTick) * partialTick;
            float pitch = data.prevPitch + (data.pitch - data.prevPitch) * partialTick - (PI / 2);
            float swing = data.prevSwimSwing + (data.swimSwing - data.prevSwimSwing) * partialTick;
            f1 = swimAnim / 16.0F;
            f6 = 1.0F - f1;
            multiplyAngles(f6, this.chest, this.pelvis);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            float swimF1 = Math.min(1.0F, oldF1 * 2.5F);
            float swimInvF1 = 1.0F - swimF1;
            this.pelvis.rotationPointY += f1 * 6.0F * swimInvF1;
            this.pelvis.rotateAngleX += ((PI / 7) + 1.2F * swimF1) * f1;
            this.pelvis.rotateAngleX += (0.448799F + 1.2F * swimF1 + swimF1 * pitch) * f1;
            this.head.rotateAngleX = this.head.rotateAngleX - this.pelvis.rotateAngleX * f1;
            this.arm1.rotateAngleX = this.arm1.rotateAngleX - (Mth.sin(swing * 0.5F) * 1.4F + 1.8F) * swimF1 * f1;
            this.arm1.rotateAngleY = this.arm1.rotateAngleY - Math.min(0.0F, Mth.sin(swing * 0.5F + 2.2F) * 1.6F) * swimF1 * f1;
            this.arm1.rotateAngleZ = this.arm1.rotateAngleZ + ((PI / 4) + Mth.sin(f2 * 0.15F) * 0.2F) * swimInvF1 * f1;
            this.arm2.rotateAngleX = this.arm2.rotateAngleX - (Mth.sin(swing * 0.5F) * 1.4F + 1.8F) * swimF1 * f1;
            this.arm2.rotateAngleY = this.arm2.rotateAngleY + Math.min(0.0F, Mth.sin(swing * 0.5F + 2.2F) * 1.6F) * swimF1 * f1;
            this.arm2.rotateAngleZ = this.arm2.rotateAngleZ - ((PI / 4) + Mth.sin(f2 * 0.15F) * 0.2F) * swimInvF1 * f1;
            this.forearm1.rotateAngleX -= (PI / 6) * swimInvF1 * f1;
            this.forearm2.rotateAngleX -= (PI / 6) * swimInvF1 * f1;
            this.leg1.rotateAngleX = this.leg1.rotateAngleX
                    - ((PI / 10) + Mth.sin(f2 * 0.3F) * 0.2F * swimInvF1 + Mth.sin(swing * 1.1F) * 0.8F * swimF1) * f1;
            this.leg1.rotateAngleZ += ((PI / 8) - 0.4F * swimF1) * f1;
            this.leg2.rotateAngleX = this.leg2.rotateAngleX
                    - ((PI / 10) - Mth.sin(f2 * 0.3F) * 0.2F * swimInvF1 - Mth.sin(swing * 1.1F) * 0.8F * swimF1) * f1;
            this.leg2.rotateAngleZ -= ((PI / 8) - 0.4F * swimF1) * f1;
            this.foot1.rotateAngleX += (PI / 6) * invF1 * f1;
            this.foot2.rotateAngleX += (PI / 6) * invF1 * f1;
        }

        if (data.climbingTick > 0 || data.prevClimbingTick > 0) {
            float climbAnim = data.prevClimbingTick + (data.climbingTick - data.prevClimbingTick) * partialTick;
            f1 = climbAnim / 8.0F;
            f6 = 1.0F - f1;
            multiplyAngles(f6, this.chest, this.pelvis);
            multiplyAngles(f6, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(f6, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(f6, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            float offset = data.prevClimbingOffset + (data.climbingOffset - data.prevClimbingOffset) * partialTick;
            float climb1 = Mth.sin(offset * 0.6F);
            float climb2 = Mth.sin(offset * 0.6F + 1.4F);
            this.arm1.rotateAngleX -= (PI * 4.0F / 9.0F) * f1;
            this.arm2.rotateAngleX -= (PI * 4.0F / 9.0F) * f1;
            this.forearm1.rotateAngleX -= (PI / 5) * f1;
            this.forearm2.rotateAngleX -= (PI / 5) * f1;
            this.leg1.rotateAngleZ += 0.09817477F * f1;
            this.leg1.getModel().rotateAngleX -= (PI / 3) * f1;
            this.leg2.rotateAngleZ -= 0.09817477F;
            this.leg2.getModel().rotateAngleX -= (PI / 3) * f1;
            this.foot1.rotateAngleX += (PI / 4) * f1;
            this.foot2.rotateAngleX += (PI / 4) * f1;
            this.arm1.rotateAngleX += climb1 * 0.6F * f1;
            this.arm2.rotateAngleX -= climb1 * 0.6F * f1;
            this.forearm1.rotateAngleX += climb2 * 0.6F * f1;
            this.forearm2.rotateAngleX -= climb2 * 0.6F * f1;
            this.leg1.rotateAngleX -= climb1 * 0.5F * f1;
            this.leg2.rotateAngleX += climb1 * 0.5F * f1;
            this.foot1.rotateAngleX += (0.4F + climb2) * 0.5F * f1;
            this.foot2.rotateAngleX += (0.4F - climb2) * 0.5F * f1;
        }

        if (data.flyingTick > 0 || data.prevFlyingTick > 0) {
            float flyAnim = data.prevFlyingTick + (data.flyingTick - data.prevFlyingTick) * partialTick;
            float pitch = data.prevPitch + (data.pitch - data.prevPitch) * partialTick + (PI / 2);
            float pos = data.posTick + (data.posTick - data.prevPosTick) * partialTick;
            f1 = flyAnim / 5.0F;
            float temp = 1.0F - Math.abs(pos * 0.8F) * f1;
            float temp1 = 1.0F - Math.abs(pos) * f1;
            multiplyAngles(temp, this.chest, this.pelvis);
            multiplyAngles(temp, this.arm1, this.arm2, this.forearm1, this.forearm2);
            multiplyAngles(temp1, this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            multiplyAngles(temp, this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            temp = pos * f1;
            this.pelvis.rotateAngleX += pitch * temp;
            this.head.rotateAngleX += -pitch * temp;
            this.arm1.rotateAngleX += (PI / 6) * temp;
            this.arm1.rotateAngleZ += 0.12566371F * temp;
            this.arm2.rotateAngleX += (PI / 6) * temp;
            this.arm2.rotateAngleZ += -0.12566371F * temp;
            this.leg1.rotateAngleZ += 0.03926991F * temp;
            this.leg2.rotateAngleZ += -0.03926991F * temp;
        }

        if (data.eatTick > 0 || data.prevEatTick > 0) {
            float eatAnim = data.prevEatTick + (data.eatTick - data.prevEatTick) * partialTick;
            f1 = eatAnim / 8.0F;
            f6 = 1.0F - f1;
            multiplyAngles(f6, this.head, this.arm1, this.forearm1);
            facePitch *= f6;
            faceYaw *= f6;
            float eatMove = Mth.sin(f2 * 1.1F) * 0.2F;
            this.head.rotateAngleX += ((PI / 16) + eatMove) * f1 * f1 * f1;
            this.arm1.rotateAngleX -= (PI / 5) * f1;
            this.arm1.rotateAngleY -= (PI / 5) * f1;
            this.forearm1.rotateAngleX -= (PI / 5) * f1;
        }

        if (data.drinkTick > 0 || data.prevDrinkTick > 0) {
            float drinkAnim = data.prevDrinkTick + (data.drinkTick - data.prevDrinkTick) * partialTick;
            f1 = drinkAnim / 8.0F;
            f6 = 1.0F - f1;
            multiplyAngles(f6, this.head, this.arm1, this.forearm1);
            facePitch *= f6;
            faceYaw *= f6;
            this.head.rotateAngleX -= (PI / 16) * f1;
            this.arm1.rotateAngleX -= (PI / 4) * f1;
            this.arm1.rotateAngleY -= (PI / 6) * f1;
            this.forearm1.rotateAngleX -= (PI / 5) * f1;
        }

        if (this.riding) {
            resetAngles(this.chest, this.pelvis, this.head);
            resetAngles(this.arm1, this.arm2, this.forearm1, this.forearm2);
            resetAngles(this.leg1, this.leg2, this.leg1.getModel(), this.leg2.getModel());
            resetAngles(this.foot1, this.foot2, this.foot1.getModel(), this.foot2.getModel());
            this.pelvis.rotationPointY = 12.0F;
            faceYaw = Math.min(1.57F, faceYaw);
            faceYaw = Math.max(-1.57F, faceYaw);
            if (this.ridingMob) {
                this.pelvis.rotationPointZ += 4.0F;
                this.chest.rotateAngleX += (PI / 10);
                this.head.rotateAngleX -= (PI / 10);
                this.arm1.rotateAngleX -= (PI / 6);
                this.arm2.rotateAngleX -= (PI / 6);
                this.forearm1.rotateAngleX -= (PI / 7);
                this.forearm2.rotateAngleX -= (PI / 7);
                this.leg1.rotateAngleX--;
                this.leg1.rotateAngleY += (PI / 6);
                this.leg2.rotateAngleX--;
                this.leg2.rotateAngleY -= (PI / 6);
                this.foot1.rotateAngleX += (PI / 5);
                this.foot2.rotateAngleX += (PI / 5);
            } else {
                this.pelvis.rotationPointZ += 2.0F;
                this.arm1.rotateAngleX -= (PI / 5);
                this.arm2.rotateAngleX -= (PI / 5);
                this.leg1.rotateAngleX--;
                this.leg1.rotateAngleY += (PI / 10);
                this.leg2.rotateAngleX--;
                this.leg2.rotateAngleY -= (PI / 10);
                if (!Float.isNaN(this.rowRight)) {
                    this.row(this.arm1, this.forearm1, this.rowRight, 1.0F);
                    this.row(this.arm2, this.forearm2, this.rowLeft, -1.0F);
                }
            }
        }

        if (this.aimedBow > 0) {
            if (this.entity != null) {
                this.entity.yBodyRot = this.entity.getYRot() - 40.0F;
            }
            float aimTick = ((BOW_USE_DURATION - this.aimedBow) + partialTick) / 22.0F;
            aimTick = Math.min(1.0F, aimTick);
            resetAngles(this.arm1, this.arm2, this.forearm1, this.forearm2);
            this.arm1.rotateAngleX += facePitch - 1.5F;
            this.arm1.rotateAngleY += faceYaw - 0.3F;
            this.arm2.rotateAngleX = this.arm2.rotateAngleX + (Math.max(-0.79F, Math.min(0.5F, facePitch)) - 1.5F);
            this.arm2.rotateAngleY += faceYaw + 0.6F - aimTick * 0.8F;
            this.forearm2.rotateAngleZ += aimTick * 0.8F;
        }

        if (this.chargingSpear) {
            // Not in 1.7.10 (no tridents): arm raised over the head, as vanilla's THROW_SPEAR pose.
            resetAngles(this.arm1, this.forearm1);
            this.arm1.rotateAngleX = facePitch * 0.5F - PI;
            this.arm1.rotateAngleZ = 0.1F;
            this.forearm1.rotateAngleX = -0.2F;
            this.chest.rotateAngleY -= 0.3F;
            this.head.rotateAngleY += 0.3F;
        }

        float onGround = this.attackTime;
        if (onGround > 1.0E-4F) {
            resetAngles(this.arm1);
            this.arm1.rotateAngleX = 0.1F;
            this.arm1.rotateAngleZ = 0.1F;
            if (!data.holdingSword) {
                if (onGround < 0.2F) {
                    float tick = onGround / 0.2F;
                    float rot = Mth.sin(tick * PI / 2.0F);
                    this.chest.rotateAngleY += rot * PI / 12.0F;
                    this.arm1.rotateAngleX += -rot * PI / 2.5F;
                    this.arm1.rotateAngleZ += rot * PI / 10.0F;
                    this.arm2.rotateAngleZ += -rot * PI / 20.0F;
                } else if (onGround < 0.5F) {
                    float tick = (onGround - 0.2F) / 0.3F;
                    float rot = Mth.sin(tick * PI / 2.0F);
                    float neg = 1.0F - rot;
                    this.chest.rotateAngleY += -rot * PI / 12.0F + neg * PI / 12.0F;
                    this.arm1.rotateAngleX += rot * PI / 8.0F - neg * PI / 2.5F;
                    this.arm1.rotateAngleY += -rot * PI / 16.0F;
                    this.arm1.rotateAngleZ += rot * PI / 16.0F - neg * PI / 10.0F;
                    this.arm2.rotateAngleX += rot * PI / 14.0F;
                    this.arm2.rotateAngleZ += (-PI / 20);
                } else if (onGround < 0.6F) {
                    this.chest.rotateAngleY += (-PI / 12);
                    this.arm1.rotateAngleX += (PI / 8);
                    this.arm1.rotateAngleY += (-PI / 16);
                    this.arm1.rotateAngleZ += (PI / 16);
                    this.arm2.rotateAngleX += (PI / 14);
                    this.arm2.rotateAngleZ += (-PI / 20);
                } else if (onGround < 0.8F) {
                    float tick = (onGround - 0.6F) / 0.2F;
                    float rot = Mth.cos(tick * PI / 2.0F);
                    this.chest.rotateAngleY += -rot * PI / 12.0F;
                    this.arm1.rotateAngleX += rot * PI / 8.0F;
                    this.arm1.rotateAngleY += -rot * PI / 16.0F;
                    this.arm1.rotateAngleZ += rot * PI / 16.0F;
                    this.arm2.rotateAngleX += rot * PI / 14.0F;
                    this.arm2.rotateAngleZ += -rot * PI / 20.0F;
                }
            } else if (onGround < 0.2F) {
                float tick = onGround / 0.2F;
                float rot = Mth.sin(tick * PI / 2.0F);
                this.chest.rotateAngleY += rot * PI / 10.0F;
                this.arm1.rotateAngleX += rot * PI / 8.0F;
                this.arm1.rotateAngleZ += rot * PI / 6.0F;
                this.arm2.rotateAngleX += -rot * PI / 12.0F;
                this.arm2.rotateAngleZ += -rot * PI / 12.0F;
            } else if (onGround < 0.5F) {
                float tick = (onGround - 0.2F) / 0.3F;
                float rot = Mth.sin(tick * PI / 2.0F);
                float neg = 1.0F - rot;
                this.chest.rotateAngleY += neg * PI / 10.0F - rot * PI / 8.0F;
                this.arm1.rotateAngleX += neg * PI / 8.0F - rot * PI / 2.5F;
                this.arm1.rotateAngleZ += neg * PI / 6.0F + rot * PI / 2.75F;
                this.arm2.rotateAngleX += -neg * PI / 12.0F + rot * PI / 10.0F;
                this.arm2.rotateAngleZ += (-PI / 12);
            } else if (onGround < 0.6F) {
                this.chest.rotateAngleY += (-PI / 8);
                this.arm1.rotateAngleX += (float) (-Math.PI * 2.0 / 5.0);
                this.arm1.rotateAngleZ++;
                this.arm2.rotateAngleX += (PI / 10);
                this.arm2.rotateAngleZ += (-PI / 12);
            } else if (onGround < 0.9F) {
                float tick = (onGround - 0.6F) / 0.3F;
                float rot = Mth.cos(tick * PI / 2.0F);
                this.chest.rotateAngleY += -rot * PI / 8.0F;
                this.arm1.rotateAngleX += -rot * PI / 2.5F;
                this.arm1.rotateAngleZ += rot * PI / 2.75F;
                this.arm2.rotateAngleX += rot * PI / 10.0F;
                this.arm2.rotateAngleZ += -rot * PI / 12.0F;
            }
        }

        this.head.getModel().rotateAngleX = facePitch;
        this.head.getModel().rotateAngleY = faceYaw;
        float rightEyeMul = faceYaw < 0.0F ? 0.6F : 3.0F;
        float leftEyeMul = faceYaw > 0.0F ? 0.6F : 3.0F;

        for (int i = 0; i < this.rightEye.length && ModSettings.EYE_MOVEMENT.on(); i++) {
            this.rightEye[i].rotationPointX -= faceYaw * rightEyeMul / (float) (Math.PI * 3.0 / 2.0);
            this.leftEye[i].rotationPointX -= faceYaw * leftEyeMul / (float) (Math.PI * 3.0 / 2.0);
        }

        this.head.rotateAngleX += breatheAnim * 0.01F * breatheStance;
        if (this.chest instanceof ModelObjRenderer chestObj) {
            chestObj.setScale(1.0F + breatheAnim * 0.015F * breatheStance);
        }

        this.chest.rotateAngleX -= (breatheAnim * 0.02F - 0.02F) * breatheStance;
        this.brow1.rotationPointY -= breatheAnim * 0.06F * breatheStance;
        this.brow2.rotationPointY -= breatheAnim * 0.06F * breatheStance;
        this.arm1.rotateAngleZ += (breatheAnim * 0.03F + 0.03F) * breatheStance;
        this.arm2.rotateAngleZ -= (breatheAnim * 0.03F + 0.03F) * breatheStance;
        float mouthAnim = ModSettings.BREATHLESS_MOUTH.on() ? data.prevExhaustionTick + (data.exhaustionTick - data.prevExhaustionTick) * partialTick : 0.0F;
        f1 = mouthAnim / 10.0F;
        this.mouth.rotationPointY -= (breatheAnim * 0.25F + 0.25F) * f1;
    }
}
