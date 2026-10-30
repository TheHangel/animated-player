package dev.hangel.animatedplayer.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Settings screen of both loaders (NeoForge "Config" button, Fabric Mod Menu): the options on the left, and on the
 * right a 3D player acting out the option under the mouse. Changes apply immediately and are saved on close.
 */
public class SettingsScreen extends Screen {
    private static final String PREFIX = "animatedplayer.configuration.";
    /** What the preview shows while an option is hovered or focused. */
    private static final Map<ModSettings.Option<?>, PlayerPreview.Demo> DEMOS = Map.ofEntries(
            Map.entry(ModSettings.ENABLED, PlayerPreview.Demo.CYCLE),
            Map.entry(ModSettings.INTENSITY, PlayerPreview.Demo.WALK),
            Map.entry(ModSettings.IDLE_ANIMATION, PlayerPreview.Demo.IDLE),
            Map.entry(ModSettings.SWORD_ANIMATION, PlayerPreview.Demo.SWORD),
            Map.entry(ModSettings.SUPERMAN_FLY, PlayerPreview.Demo.CREATIVE_FLIGHT),
            Map.entry(ModSettings.ANIMATE_FACE, PlayerPreview.Demo.LOOK_AROUND),
            Map.entry(ModSettings.EYE_MOVEMENT, PlayerPreview.Demo.LOOK_AROUND),
            Map.entry(ModSettings.EYEBROW_EXPRESSIONS, PlayerPreview.Demo.EYEBROWS),
            Map.entry(ModSettings.HUNGER_EYEBROWS, PlayerPreview.Demo.HUNGER),
            Map.entry(ModSettings.BREATHLESS_MOUTH, PlayerPreview.Demo.BREATHLESS),
            Map.entry(ModSettings.ROUNDED_LIMBS, PlayerPreview.Demo.JOINTS),
            Map.entry(ModSettings.BETTER_HAT, PlayerPreview.Demo.IDLE),
            Map.entry(ModSettings.SKIN_LAYERS_3D, PlayerPreview.Demo.WALK));

    private final Screen parent;
    private final PlayerPreview preview = new PlayerPreview();
    private OptionList list;
    /** Option whose demo is playing: the last one hovered or focused. */
    private ModSettings.Option<?> shown;
    /** Option hovered at the previous tick: hovering takes over only when it changes. */
    private ModSettings.Option<?> lastHovered;
    private boolean draggingPreview;

    public SettingsScreen(Screen parent) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
    }

    private int listWidth() {
        return Math.min(260, this.width * 11 / 20);
    }

    @Override
    protected void init() {
        this.list = new OptionList(this.minecraft, this.listWidth(), this.height - 64, 32);
        this.list.setX(8);
        for (ModSettings.Section section : ModSettings.SECTIONS) {
            this.list.addSection(Component.translatable(PREFIX + "section." + section.key()));
            for (ModSettings.Option<?> option : section.options()) {
                this.list.addOption(option, this.createWidget(option));
            }
        }
        this.addRenderableWidget(this.list);

        int previewX = this.previewLeft();
        int previewWidth = this.width - 8 - previewX;
        this.addRenderableWidget(CycleButton.<Boolean>builder(own -> Component.translatable(PREFIX + (own ? "preview.own_skin" : "preview.steve")))
                .withValues(false, true)
                .withInitialValue(this.preview.usesOwnSkin())
                .create(previewX + previewWidth / 2 - 60, this.height - 54, 120, 20,
                        Component.translatable(PREFIX + "preview.skin"), (button, own) -> this.preview.setOwnSkin(own)));

        int buttonsY = this.height - 27;
        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "reset"), button -> {
            ModSettings.resetAll();
            this.rebuildWidgets();
        }).bounds(this.width / 2 - 154, buttonsY, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 + 4, buttonsY, 150, 20).build());
    }

    private int previewLeft() {
        return 8 + this.listWidth() + 8;
    }

    private AbstractWidget createWidget(ModSettings.Option<?> option) {
        // No tooltips: the description is shown in the preview panel, which a tooltip would cover.
        Component label = Component.translatable(PREFIX + option.key);
        int width = this.listWidth() - 24;
        if (option instanceof ModSettings.BooleanOption bool) {
            return CycleButton.onOffBuilder(bool.on()).create(0, 0, width, 20, label, (button, value) -> bool.set(value));
        }
        if (option instanceof ModSettings.IntOption integer) {
            return new IntSlider(width, label, integer);
        }
        return enumButton((ModSettings.EnumOption<?>) option, width, label);
    }

    private static <E extends Enum<E>> AbstractWidget enumButton(ModSettings.EnumOption<E> option, int width, Component label) {
        return CycleButton.<E>builder(value -> Component.translatable(PREFIX + option.key + "." + value.name().toLowerCase(Locale.ROOT)))
                .withValues(option.values)
                .withInitialValue(option.get())
                .create(0, 0, width, 20, label, (button, value) -> option.set(value));
    }

    @Override
    public void tick() {
        super.tick();
        ModSettings.Option<?> active = this.list.activeOption();
        if (active != null && active != this.lastHovered) {
            this.shown = active;
        }
        this.lastHovered = active;
        PlayerPreview.Demo demo = this.shown == null ? PlayerPreview.Demo.CYCLE : DEMOS.getOrDefault(this.shown, PlayerPreview.Demo.CYCLE);
        if (!ModSettings.ENABLED.on()) {
            demo = PlayerPreview.Demo.VANILLA;
        }
        this.preview.setDemo(demo);
        this.preview.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        int x0 = this.previewLeft();
        int x1 = this.width - 8;
        int y0 = 32;
        int y1 = this.height - 60;
        graphics.fill(x0, y0, x1, y1, 0x40000000);
        int textY = y0 + 6;
        if (this.shown == null) {
            graphics.drawCenteredString(this.font, Component.translatable(PREFIX + "preview.hint"), (x0 + x1) / 2, textY, 0xAAAAAA);
            textY += 12;
        } else {
            graphics.drawCenteredString(this.font, Component.translatable(PREFIX + this.shown.key), (x0 + x1) / 2, textY, 0xFFFFFF);
            textY += 12;
            for (FormattedCharSequence line : this.font.split(Component.translatable(PREFIX + this.shown.key + ".tooltip"), x1 - x0 - 12)) {
                graphics.drawString(this.font, line, x0 + 6, textY, 0xAAAAAA);
                textY += 10;
            }
        }
        this.preview.render(graphics, x0, textY + 4, x1, y1, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= this.previewLeft() && mouseY >= 32 && mouseY < this.height - 60) {
            this.draggingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingPreview) {
            this.preview.rotate((float) -dragX * 2.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void useOwnSkin(boolean own) {
        this.preview.setOwnSkin(own);
    }

    /** Shows another skin in the preview (tests). */
    public void previewSkin(ResourceLocation texture) {
        this.preview.skinOverride = texture;
    }

    /** Pauses the preview on its current frame. */
    public void freezePreview(boolean frozen) {
        this.preview.frozen = frozen;
    }

    /** Turns the preview player, as dragging it does. */
    public void rotatePreview(float degrees) {
        this.preview.rotate(degrees);
    }

    /** Selects an option as if hovered (keyboard navigation, tests). */
    public void showOption(String key) {
        for (ModSettings.Section section : ModSettings.SECTIONS) {
            for (ModSettings.Option<?> option : section.options()) {
                if (option.key.equals(key)) {
                    this.shown = option;
                }
            }
        }
    }

    @Override
    public void onClose() {
        ModSettings.save();
        this.minecraft.setScreen(this.parent);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final ModSettings.IntOption option;
        private final Component label;

        IntSlider(int width, Component label, ModSettings.IntOption option) {
            super(0, 0, width, 20, label, (option.get() - option.min) / (double) (option.max - option.min));
            this.option = option;
            this.label = label;
            this.updateMessage();
        }

        private int current() {
            int raw = this.option.min + (int) Math.round(this.value * (this.option.max - this.option.min));
            return Mth.clamp(Math.round(raw / (float) this.option.step) * this.option.step, this.option.min, this.option.max);
        }

        @Override
        protected void updateMessage() {
            int value = this.current();
            String unit = PREFIX + this.option.key + ".value";
            Component text = value == 0 && this.option == ModSettings.ANIMATION_DISTANCE
                    ? Component.translatable(PREFIX + this.option.key + ".unlimited")
                    : Component.translatable(unit, value);
            this.setMessage(CommonComponents.optionNameValue(this.label, text));
        }

        @Override
        protected void applyValue() {
            this.option.set(this.current());
        }
    }

    private static final class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
        OptionList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
        }

        void addSection(Component title) {
            this.addEntry(new Entry(null, null, title));
        }

        void addOption(ModSettings.Option<?> option, AbstractWidget widget) {
            this.addEntry(new Entry(option, widget, null));
        }

        /** The option under the mouse or with keyboard focus, if any. */
        ModSettings.Option<?> activeOption() {
            for (Entry entry : this.children()) {
                if (entry.widget != null && entry.widget.isHoveredOrFocused()) {
                    return entry.option;
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 24;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() - 6;
        }

        static final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            final ModSettings.Option<?> option;
            final AbstractWidget widget;
            final Component title;

            Entry(ModSettings.Option<?> option, AbstractWidget widget, Component title) {
                this.option = option;
                this.widget = widget;
                this.title = title;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                               boolean hovering, float partialTick) {
                if (this.widget != null) {
                    this.widget.setPosition(left, top + 2);
                    this.widget.render(graphics, mouseX, mouseY, partialTick);
                } else {
                    graphics.drawString(Minecraft.getInstance().font, this.title, left, top + 10, 0xFFE070);
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return this.widget == null ? List.of() : List.of(this.widget);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return this.widget == null ? new ArrayList<>() : List.of(this.widget);
            }
        }
    }
}
