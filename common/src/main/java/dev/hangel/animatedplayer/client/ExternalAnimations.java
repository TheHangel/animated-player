package dev.hangel.animatedplayer.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.player.AbstractClientPlayer;
import dev.hangel.animatedplayer.AnimatedPlayer;

/**
 * Animations played by other mods on the vanilla player model. playerAnimator (used by Emotecraft, Better Combat and
 * others) poses the vanilla parts, which the mod does not draw: while one of its animations runs, the player is
 * drawn the vanilla way so that animation shows. Looked up by reflection, so playerAnimator stays optional.
 */
public final class ExternalAnimations {
    private static final Class<?> ANIMATED_PLAYER;
    private static final MethodHandle GET_ANIMATION;
    private static final MethodHandle IS_ACTIVE;

    static {
        Class<?> type = null;
        MethodHandle getAnimation = null;
        MethodHandle isActive = null;
        try {
            type = Class.forName("dev.kosmx.playerAnim.impl.IAnimatedPlayer");
            Class<?> processor = Class.forName("dev.kosmx.playerAnim.core.impl.AnimationProcessor");
            Class<?> applier = Class.forName("dev.kosmx.playerAnim.impl.animation.AnimationApplier");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getAnimation = lookup.findVirtual(type, "playerAnimator_getAnimation", MethodType.methodType(applier));
            isActive = lookup.findVirtual(processor, "isActive", MethodType.methodType(boolean.class));
            AnimatedPlayer.LOGGER.info("playerAnimator found: its animations will be shown on the vanilla model");
        } catch (ReflectiveOperationException | LinkageError e) {
            type = null;
        }
        ANIMATED_PLAYER = type;
        GET_ANIMATION = getAnimation;
        IS_ACTIVE = isActive;
    }

    private ExternalAnimations() {
    }

    /** True while another mod animates this player's vanilla model. */
    public static boolean isActive(AbstractClientPlayer player) {
        if (ANIMATED_PLAYER == null || !ANIMATED_PLAYER.isInstance(player)) {
            return false;
        }
        try {
            Object animation = GET_ANIMATION.invoke(player);
            return animation != null && (boolean) IS_ACTIVE.invoke(animation);
        } catch (Throwable e) {
            return false;
        }
    }
}
