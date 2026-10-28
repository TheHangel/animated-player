package dev.hangel.animatedplayer;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Mod constants. Everything happens client side (see client.AnimatedPlayerClient); settings: config.ModSettings. */
@Mod(AnimatedPlayer.MODID)
public class AnimatedPlayer {
    public static final String MODID = "animatedplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static long clientTick;
}
