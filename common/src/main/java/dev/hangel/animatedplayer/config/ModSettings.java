package dev.hangel.animatedplayer.config;

import dev.hangel.animatedplayer.AnimatedPlayer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client settings, shared by the NeoForge and Fabric versions: config/animatedplayer-client.toml (one "Key = value"
 * per line, with a comment above each). Read once at startup, written by the settings screen; every option is read
 * live while rendering, so changes apply right away.
 */
public final class ModSettings {
    private static final List<Option<?>> OPTIONS = new ArrayList<>();
    private static Path file;

    // General
    public static final BooleanOption ENABLED = bool("Enabled", true,
            "Animated player model. Off: players are drawn exactly as in vanilla.");
    public static final EnumOption<ApplyTo> APPLY_TO = enumOption("ApplyTo", ApplyTo.ALL, ApplyTo.values(),
            "Which players are animated: all, only you, or only the other players.");
    public static final IntOption ANIMATION_DISTANCE = integer("AnimationDistance", 0, 0, 256, 8,
            "Other players farther than this many blocks are drawn as in vanilla, for performance. 0: no limit.");
    public static final IntOption INTENSITY = integer("AnimationIntensity", 100, 0, 150, 5,
            "Strength of the body animations, in percent. 100: as designed; 0: straight vanilla-like body.");

    // Animations
    public static final BooleanOption IDLE_ANIMATION = bool("IdleAnimation", true,
            "Relaxed stance and breathing when standing still. Off: vanilla straight stance when not moving; every other animation stays.");
    public static final BooleanOption SWORD_ANIMATION = bool("SwordAnimation", true,
            "Dedicated attack animation when holding a sword. Off: the regular punch swing.");
    public static final BooleanOption SUPERMAN_FLY = bool("SupermanFly", false,
            "Superman pose when flying in creative mode (the original mod's animation). Off: the hovering pose.");

    // Face
    public static final BooleanOption ANIMATE_FACE = bool("AnimateFace", true,
            "Animate eyes, eyebrows and mouth, for skins that draw them in the hidden head area.");
    public static final BooleanOption EYE_MOVEMENT = bool("EyeMovement", true,
            "Eyes follow where the player looks.");
    public static final BooleanOption EYEBROW_EXPRESSIONS = bool("EyebrowExpressions", true,
            "Eyebrows rise when fighting, sneaking or aiming.");
    public static final BooleanOption HUNGER_EYEBROWS = bool("HungerEyebrows", true,
            "Eyebrows frown as the hunger bar empties.");
    public static final BooleanOption BREATHLESS_MOUTH = bool("BreathlessMouth", true,
            "Mouth opens to catch breath after sprinting or jumping.");

    // Skin
    public static final BooleanOption ROUNDED_LIMBS = bool("RoundedLimbs", true,
            "Rounded forearms, lower legs and chest, as in the original mod. Off: plain Minecraft cubes.");
    public static final BooleanOption BETTER_HAT = bool("BetterHat", true,
            "Render the hat layer as small cubes that follow the head shape.");
    public static final BooleanOption SKIN_LAYERS_3D = bool("SkinLayers3D", true,
            "Render the second skin layer (jacket, sleeves, pants) in 3D, like the hat. Off: flat, as in vanilla (for mods such as 3D Skin Layers).");

    /** Screen sections, in display order: title key suffix, then its options. */
    public static final List<Section> SECTIONS = List.of(
            new Section("general", List.of(ENABLED, APPLY_TO, ANIMATION_DISTANCE, INTENSITY)),
            new Section("animations", List.of(IDLE_ANIMATION, SWORD_ANIMATION, SUPERMAN_FLY)),
            new Section("face", List.of(ANIMATE_FACE, EYE_MOVEMENT, EYEBROW_EXPRESSIONS, HUNGER_EYEBROWS, BREATHLESS_MOUTH)),
            new Section("skin", List.of(ROUNDED_LIMBS, BETTER_HAT, SKIN_LAYERS_3D)));

    public enum ApplyTo {
        ALL, SELF, OTHERS
    }

    public record Section(String key, List<Option<?>> options) {
    }

    private ModSettings() {
    }

    /** Called once by the loader entrypoint with its config folder. */
    public static void load(Path configDir) {
        file = configDir.resolve(AnimatedPlayer.MODID + "-client.toml");
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf('=');
                    if (trimmed.startsWith("#") || eq < 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim().replace("\"", "");
                    for (Option<?> option : OPTIONS) {
                        if (option.key.equals(key)) {
                            option.parse(value);
                        }
                    }
                }
            }
        } catch (IOException e) {
            AnimatedPlayer.LOGGER.warn("Could not read {}", file, e);
        }
        save();
    }

    public static void save() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Option<?> option : OPTIONS) {
            lines.add("#" + option.comment);
            lines.add(option.key + " = " + option.serialize());
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AnimatedPlayer.LOGGER.warn("Could not write {}", file, e);
        }
    }

    public static void resetAll() {
        for (Option<?> option : OPTIONS) {
            option.reset();
        }
    }

    private static BooleanOption bool(String key, boolean def, String comment) {
        BooleanOption option = new BooleanOption(key, def, comment);
        OPTIONS.add(option);
        return option;
    }

    private static IntOption integer(String key, int def, int min, int max, int step, String comment) {
        IntOption option = new IntOption(key, def, min, max, step, comment);
        OPTIONS.add(option);
        return option;
    }

    private static <E extends Enum<E>> EnumOption<E> enumOption(String key, E def, E[] values, String comment) {
        EnumOption<E> option = new EnumOption<>(key, def, values, comment);
        OPTIONS.add(option);
        return option;
    }

    public abstract static sealed class Option<T> permits BooleanOption, IntOption, EnumOption {
        public final String key;
        public final T defaultValue;
        final String comment;
        protected T value;

        Option(String key, T defaultValue, String comment) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.comment = comment;
            this.value = defaultValue;
        }

        public T get() {
            return this.value;
        }

        public void set(T value) {
            this.value = value;
        }

        public void reset() {
            this.value = this.defaultValue;
        }

        abstract void parse(String text);

        String serialize() {
            return String.valueOf(this.value);
        }
    }

    public static final class BooleanOption extends Option<Boolean> {
        BooleanOption(String key, boolean def, String comment) {
            super(key, def, comment);
        }

        public boolean on() {
            return this.value;
        }

        @Override
        void parse(String text) {
            this.value = Boolean.parseBoolean(text);
        }
    }

    public static final class IntOption extends Option<Integer> {
        public final int min;
        public final int max;
        public final int step;

        IntOption(String key, int def, int min, int max, int step, String comment) {
            super(key, def, comment);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public void set(Integer value) {
            super.set(Math.max(this.min, Math.min(this.max, value)));
        }

        @Override
        void parse(String text) {
            try {
                this.set(Integer.parseInt(text));
            } catch (NumberFormatException e) {
                this.reset();
            }
        }
    }

    public static final class EnumOption<E extends Enum<E>> extends Option<E> {
        public final E[] values;

        EnumOption(String key, E def, E[] values, String comment) {
            super(key, def, comment);
            this.values = values;
        }

        @Override
        void parse(String text) {
            for (E e : this.values) {
                if (e.name().equalsIgnoreCase(text)) {
                    this.value = e;
                    return;
                }
            }
            this.reset();
        }

        @Override
        String serialize() {
            return "\"" + this.value.name().toLowerCase(Locale.ROOT) + "\"";
        }
    }
}
