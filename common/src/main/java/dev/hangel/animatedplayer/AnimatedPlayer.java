package dev.hangel.animatedplayer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Mod constants. Everything happens client side (entry points in the neoforge and fabric projects); settings: config.ModSettings. */
public final class AnimatedPlayer {
    public static final String MODID = "animatedplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static long clientTick;

    private AnimatedPlayer() {
    }
}
