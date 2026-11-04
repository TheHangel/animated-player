package dev.hangel.animatedplayer.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.hangel.animatedplayer.config.SettingsScreen;

/** "modmenu" entrypoint: only loaded when Mod Menu is installed, which stays optional. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
