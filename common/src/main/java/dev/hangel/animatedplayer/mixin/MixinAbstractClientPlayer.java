package dev.hangel.animatedplayer.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import dev.hangel.animatedplayer.client.PlayerData;

/** Animation state lives on the player itself, so it goes away with it (no map to clean up). */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer implements PlayerData.Holder {
    @Unique
    private PlayerData animatedplayer$data;

    @Override
    public PlayerData animatedplayer$getData() {
        if (this.animatedplayer$data == null) {
            this.animatedplayer$data = new PlayerData();
        }
        return this.animatedplayer$data;
    }
}
