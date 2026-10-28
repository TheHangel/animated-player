package dev.hangel.animatedplayer.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import dev.hangel.animatedplayer.AnimatedPlayer;

public class PlayerData {
    public int exhaustionTick;
    public int prevExhaustionTick;
    public int exhaustionOffset;
    public int browTick;
    public int prevBrowTick;
    public int sprintTick;
    public int prevSprintTick;
    public int sneakTick;
    public int prevSneakTick;
    public int jumpTick;
    public int prevJumpTick;
    public int swimTick;
    public int prevSwimTick;
    public int blockTick;
    public int prevBlockTick;
    public int climbingTick;
    public int prevClimbingTick;
    public int eatTick;
    public int prevEatTick;
    public int drinkTick;
    public int prevDrinkTick;
    public int flyingTick;
    public int prevFlyingTick;
    /** Creative flight: posed like hovering whether moving or not; flyingTick is for elytra gliding only. */
    public int hoverTick;
    public int prevHoverTick;
    /** Limb swing used while swimming, capped to 1.7.10 swim speed (modern sprint-swimming is twice as fast). */
    public float swimSwing;
    public float prevSwimSwing;
    public float climbingOffset;
    public float prevClimbingOffset;
    public float pitch;
    public float prevPitch;
    public float posTick;
    public float prevPosTick;
    public boolean moveBackwards;
    public boolean moveBackwards2;
    public boolean isAirborne;
    public boolean jumpRight;
    public boolean holdingSword;
    public final TextureInfo textureInfo = new TextureInfo();

    public PlayerData() {
    }

    /** Runs every tick until the current skin has been read (instantly for cached skins). */
    public void initTextureInfo(AbstractClientPlayer player) {
        ResourceLocation skin = player.getSkin().texture();
        // A player starts with a default skin until theirs is downloaded (or changes skin): read again on every change.
        if (this.textureInfo.init && skin.equals(this.textureInfo.skin)) {
            return;
        }
        this.textureInfo.skin = skin;
        // Default skins are drawn with the mod's copies of them (see ClientProxy#skinOverride).
        ResourceLocation animated = ClientProxy.animatedDefaultSkin(skin);
        SkinInfo info = SkinInfo.get(animated != null ? animated : skin);
        if (info == null) {
            // Not downloaded yet: the player's skin is drawn as is (no face animation), and read again next tick.
            // 1.7.10 gave up after a while and swapped the skin for its Steve; a real skin is never replaced now.
            this.textureInfo.clear();
            return;
        }
        this.textureInfo.skinInfo = info;
        this.textureInfo.init = true;
        // What the skin provides; the AnimateFace option is applied when drawing (ModelPlayer#setShowModels).
        this.textureInfo.animateEyes1 = info.eyes1;
        this.textureInfo.animateEyes2 = info.eyes2;
        this.textureInfo.animateEyes3 = info.eyes3;
        this.textureInfo.animateEyebrows = info.eyebrows;
        this.textureInfo.animateMouth = info.mouth;
    }

    public boolean updateExhaustion(Player player) {
        boolean flag = false;
        boolean jump = false;
        if (player instanceof LocalPlayer local) {
            jump = local.input.jumping;
        }
        if (player.isSprinting() || jump || player.isInWater() || player.hurtTime > 0) {
            flag = true;
        }
        if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.hasEffect(MobEffects.HUNGER)) {
            flag = true;
        }
        if (flag) {
            this.exhaustionOffset = Math.min(160, this.exhaustionOffset + 3);
        } else {
            this.exhaustionOffset = Math.max(0, this.exhaustionOffset - 1);
        }
        return !player.isInWater() && this.exhaustionOffset > 0;
    }

    public boolean shouldArchBrows(Player player, ItemStack stack) {
        if (player.swinging || player.isShiftKeyDown()) {
            return true;
        }
        if (!stack.isEmpty()) {
            if (stack.is(ItemTags.SWORDS)) {
                return true;
            }
            return isUsing(player, stack, UseAnim.BOW);
        }
        return false;
    }

    public boolean isAirborne(Player player) {
        return this.isAirborne = !player.onGround() && !player.isInWater() && !player.isEyeInFluid(FluidTags.LAVA) && !player.isSleeping();
    }

    public boolean isBlocking(Player player, ItemStack stack) {
        return isUsing(player, stack, UseAnim.BLOCK);
    }

    public boolean isEating(Player player, ItemStack stack) {
        return isUsing(player, stack, UseAnim.EAT);
    }

    public boolean isDrinking(Player player, ItemStack stack) {
        return isUsing(player, stack, UseAnim.DRINK);
    }

    static boolean isUsing(Player player, ItemStack stack, UseAnim anim) {
        return !stack.isEmpty() && player.getUseItemRemainingTicks() > 0 && stack.getUseAnimation() == anim;
    }

    public static PlayerData getPlayerData(AbstractClientPlayer player) {
        return ((Holder) player).animatedplayer$getData();
    }

    /** Implemented on AbstractClientPlayer by MixinAbstractClientPlayer. */
    public interface Holder {
        PlayerData animatedplayer$getData();
    }

    public static class TextureInfo {
        private boolean init = false;
        private ResourceLocation skin;
        /** Opaque pixels of the drawn texture, for the 3D layers. */
        public SkinInfo skinInfo;
        public boolean animateEyes1;
        public boolean animateEyes2;
        public boolean animateEyes3;
        public boolean animateEyebrows;
        public boolean animateMouth;

        /** Fills this from an already read skin (settings screen preview). */
        public void setFrom(SkinInfo info) {
            if (info == null) {
                this.clear();
                return;
            }
            this.skinInfo = info;
            this.animateEyes1 = info.eyes1;
            this.animateEyes2 = info.eyes2;
            this.animateEyes3 = info.eyes3;
            this.animateEyebrows = info.eyebrows;
            this.animateMouth = info.mouth;
            this.init = true;
        }

        /** No skin info yet: no face animation, every 3D layer voxel drawn. */
        public void clear() {
            this.skinInfo = null;
            this.animateEyes1 = this.animateEyes2 = this.animateEyes3 = false;
            this.animateEyebrows = this.animateMouth = false;
            this.init = false;
        }
    }
}
