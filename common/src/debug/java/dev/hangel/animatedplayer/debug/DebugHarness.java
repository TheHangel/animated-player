package dev.hangel.animatedplayer.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.hangel.animatedplayer.config.ModSettings;
import dev.hangel.animatedplayer.config.PlayerPreview;
import dev.hangel.animatedplayer.config.SettingsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import dev.hangel.animatedplayer.AnimatedPlayer;
import dev.hangel.animatedplayer.client.LegacySteve;

/**
 * Dev-only test driver (runDebugClient): creates/opens a flat creative world, poses the player through the
 * animations and saves screenshots to run/screenshots, then quits. Not part of the shipped jar.
 */
public final class DebugHarness {
    /** Loader-specific screens, set by the loader's debug entry point (DebugNeoForge / DebugFabric). */
    static java.util.function.UnaryOperator<net.minecraft.client.gui.screens.Screen> modListScreen = parent -> null;
    static java.util.function.UnaryOperator<net.minecraft.client.gui.screens.Screen> configScreen = SettingsScreen::new;

    private static final String WORLD = "aptest";
    private static final boolean ENABLED = Boolean.getBoolean("animatedplayer.debug");
    private static final Map<Integer, Runnable> STEPS = new HashMap<>();
    private static boolean started;
    /** Set when a scenario leaves the world on purpose: steps keep running on the title screen. */
    private static boolean leavingWorld;
    /** Distance of the following side camera, in blocks. */
    private static double sideDistance = 3.0;
    private static TestSkins.FakePlayer limbsFake;
    private static int tick;
    private static int cursor;
    /** Client-only entity used as a side camera that follows the player at 3 blocks on its right. */
    private static ArmorStand sideCam;
    /** x, y, z, yaw, pitch of a fixed camera (skins tour), or null to follow the player. */
    private static double[] fixedCam;
    private static final List<TestSkins.FakePlayer> FAKES = new ArrayList<>();
    /** Fake player moved 1 block per tick along +Z, the camera following it. */
    private static TestSkins.FakePlayer flyer;

    private DebugHarness() {
    }

    private static void after(int delay, Runnable action) {
        cursor += delay;
        STEPS.put(cursor, action);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static {
        String scenario = System.getProperty("animatedplayer.debug.scenario");
        if ("skins".equals(scenario)) {
            skinsTour();
        } else if ("download".equals(scenario)) {
            downloadTour();
        } else if ("banners".equals(scenario)) {
            bannersTour();
        } else if ("icons".equals(scenario)) {
            iconsTour();
        } else if ("branding".equals(scenario)) {
            brandingTour();
        } else if ("compare".equals(scenario)) {
            compareTour();
        } else if ("items".equals(scenario)) {
            itemsTour();
        } else if ("boat".equals(scenario)) {
            boatTour();
        } else if ("toggle".equals(scenario)) {
            toggleTour();
        } else if ("limbs".equals(scenario)) {
            limbsTour();
        } else if ("config".equals(scenario)) {
            configTour();
        } else if ("compat".equals(scenario)) {
            compatTour();
        } else {
            fullTour();
        }
    }

    /** Fixed camera looking at a row of client-side players wearing test and default skins. */
    private static void skinsTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            cmd("clear @s");
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("weather clear");
            cmd("fill -8 -61 -8 8 -55 40 air");
            cmd("fill -8 -62 -8 8 -62 40 grass_block");
            cmd("tp @s 0 -61 -4 0 0");
        });
        after(20, () -> {
            String[] names = {"wide_test", "slim_test", "alex", "zuri", "legacy"};
            PlayerSkin[] skins = {
                    TestSkins.skin(TestSkins.generate("wide_test", false), false),
                    TestSkins.skin(TestSkins.generate("slim_test", true), true),
                    TestSkins.skin(ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png"), true),
                    TestSkins.skin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png"), false),
                    // Old 64x32 skins reach the renderer converted to 64x64, as the mod's fallback Steve.
                    TestSkins.skin(LegacySteve.texture(), false)};
            for (int i = 0; i < names.length; i++) {
                TestSkins.FakePlayer fake = new TestSkins.FakePlayer(mc().level, names[i], skins[i]);
                fake.setPos(3.0 - i * 2.0, -61.0, 4.0);
                fake.xo = fake.getX();
                fake.yo = fake.getY();
                fake.zo = fake.getZ();
                fake.setOnGround(true);
                mc().level.addEntity(fake);
                FAKES.add(fake);
            }
            FAKES.get(1).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            FAKES.get(2).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
            turnFakes(180.0F);
            camera(0.0, -61.0, -1.5, 0.0F, 8.0F);
        });
        after(40, () -> shot("s01_front"));
        after(2, () -> turnFakes(0.0F));
        after(10, () -> shot("s02_back"));
        after(2, () -> turnFakes(90.0F));
        after(10, () -> shot("s03_side_right"));
        after(2, () -> turnFakes(-90.0F));
        after(10, () -> shot("s04_side_left"));
        after(2, () -> camera(2.0, -61.2, 1.3, 0.0F, 8.0F));
        after(2, () -> turnFakes(180.0F));
        after(10, () -> shot("s05_close_front"));
        after(2, () -> turnFakes(0.0F));
        after(10, () -> shot("s06_close_back"));
        after(2, () -> turnFakes(90.0F));
        after(10, () -> shot("s06b_close_right"));
        after(2, () -> turnFakes(-90.0F));
        after(10, () -> shot("s06c_close_left"));
        after(2, () -> {
            turnFakes(180.0F);
            camera(-2.0, -61.2, 1.3, 0.0F, 8.0F);
        });
        after(10, () -> shot("s06d_close_defaults"));
        after(2, () -> {
            for (TestSkins.FakePlayer fake : FAKES) {
                fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            }
            camera(0.0, -61.0, -1.5, 0.0F, 8.0F);
        });
        after(20, () -> shot("s07_elytra_back"));
        after(2, () -> camera(2.0, -61.2, 1.3, 0.0F, 8.0F));
        after(10, () -> shot("s07b_elytra_close"));
        after(2, () -> camera(0.0, -61.0, -1.5, 0.0F, 8.0F));
        after(2, () -> turnFakes(90.0F));
        after(10, () -> shot("s08_elytra_side"));
        after(2, () -> {
            for (TestSkins.FakePlayer fake : FAKES) {
                fake.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                fake.giveParrots();
            }
        });
        after(5, () -> shot("s08b_parrots_side"));
        after(2, () -> turnFakes(180.0F));
        after(5, () -> shot("s08c_parrots_front"));
        // Fast creative flight with a cape: the cape must stay roughly horizontal.
        after(2, () -> {
            ResourceLocation cape = TestSkins.generateCape("cape");
            PlayerSkin skin = new PlayerSkin(TestSkins.generate("caped", false), null, cape, null, PlayerSkin.Model.WIDE, true);
            flyer = new TestSkins.FakePlayer(mc().level, "caped", skin);
            flyer.setPos(0.0, -55.0, 0.0);
            flyer.getAbilities().flying = true;
            mc().level.addEntity(flyer);
            FAKES.clear();
            FAKES.add(flyer);
            turnFakes(0.0F);
            camera(-3.0, -56.0, 0.0, -90.0F, 0.0F);
        });
        after(30, () -> shot("s09_cape_fast_flight"));
        after(1, () -> ModSettings.SUPERMAN_FLY.set(true));
        after(20, () -> shot("s09b_cape_superman"));
        after(1, () -> ModSettings.SUPERMAN_FLY.set(false));
        after(2, () -> flyer = null);
        after(30, () -> shot("s10_cape_hover"));
        after(10, () -> mc().stop());
    }

    /** Shoulder parrots are server state: tame two parrots on the integrated server and seat them. */
    private static void putParrotsOnShoulders() {
        MinecraftServer server = mc().getSingleplayerServer();
        UUID id = mc().player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            for (int i = 0; i < 2; i++) {
                Parrot parrot = EntityType.PARROT.create(player.serverLevel());
                parrot.setVariant(Parrot.Variant.byId(i * 2));
                parrot.tame(player);
                parrot.moveTo(player.getX(), player.getY(), player.getZ());
                player.serverLevel().addFreshEntity(parrot);
                parrot.setEntityOnShoulder(player);
            }
        });
    }

    /**
     * A skin loaded the way downloaded skins are (HttpTexture from the skin cache, -Danimatedplayer.debug.skinFile),
     * shown on a fake player and in the settings preview.
     */
    private static void downloadTour() {
        ResourceLocation location = ResourceLocation.withDefaultNamespace("skins/apdebug");
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("kill @e[type=parrot]");
            cmd("tp @s 0 -61 -4 0 0");
            java.io.File file = new java.io.File(System.getProperty("animatedplayer.debug.skinFile", "skin.png"));
            mc().getTextureManager().register(location, new net.minecraft.client.renderer.texture.HttpTexture(file,
                    "http://localhost/none.png", net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture(), true, null));
        });
        after(20, () -> {
            System.out.println("[AnimatedPlayer debug] texture=" + mc().getTextureManager().getTexture(location, null)
                    + " skinInfo=" + dev.hangel.animatedplayer.client.SkinInfo.get(location));
            // Simulate a skin the mod could not read while it was downloaded: the cache file must be used.
            try {
                Object texture = mc().getTextureManager().getTexture(location, null);
                java.lang.reflect.Field field = texture.getClass().getDeclaredField("animatedplayer$skinInfo");
                field.setAccessible(true);
                field.set(texture, null);
            } catch (ReflectiveOperationException e) {
                System.out.println("[AnimatedPlayer debug] ERROR: " + e);
            }
            dev.hangel.animatedplayer.client.SkinInfo.clearCache();
            System.out.println("[AnimatedPlayer debug] skinInfo from cache file=" + dev.hangel.animatedplayer.client.SkinInfo.get(location));
            TestSkins.FakePlayer fake = new TestSkins.FakePlayer(mc().level, "download_test", TestSkins.skin(location, false));
            fake.setPos(0.0, -61.0, -1.8);
            fake.xo = fake.getX();
            fake.yo = fake.getY();
            fake.zo = fake.getZ();
            fake.setOnGround(true);
            mc().level.addEntity(fake);
            FAKES.add(fake);
            turnFakes(180.0F);
            camera(0.0, -60.4, 0.6, 180.0F, 5.0F);
        });
        after(30, () -> shot("w01_fake_downloaded"));
        after(2, () -> {
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.hideGui = false;
            SettingsScreen screen = new SettingsScreen(null);
            mc().setScreen(screen);
            screen.previewSkin(location);
        });
        after(30, () -> {
            System.out.println("[AnimatedPlayer debug] skinInfo later=" + dev.hangel.animatedplayer.client.SkinInfo.get(location));
            shot("w02_preview_downloaded");
        });
        after(10, () -> mc().stop());
    }

    /** Icon variants: colours, pose, orientation and framing, captured as brand_icon_v1..v8. */
    /** Banner variants: a row of poses on the icon's sunburst background, captured as brand_banner_b1..b4. */
    private static void bannersTour() {
        PlayerPreview.Demo idle = PlayerPreview.Demo.IDLE, walk = PlayerPreview.Demo.WALK, sprint = PlayerPreview.Demo.SPRINT,
                sword = PlayerPreview.Demo.SWORD, sneak = PlayerPreview.Demo.SNEAK, jump = PlayerPreview.Demo.JUMP,
                hover = PlayerPreview.Demo.HOVER, eat = PlayerPreview.Demo.EAT, climb = PlayerPreview.Demo.CLIMB,
                swim = PlayerPreview.Demo.SWIM, crawl = PlayerPreview.Demo.CRAWL;
        java.util.function.Function<Object[], BannerScreen.Pose> p = o -> new BannerScreen.Pose((PlayerPreview.Demo) o[0], (int) o[1], (boolean) o[2]);
        Object[] I = {idle, 40, false}, W = {walk, 30, false}, R = {sprint, 27, false}, J = {jump, 4, false}, S = {sneak, 30, false},
                X = {sword, 8, true}, H = {hover, 25, false}, E = {eat, 20, false}, C = {climb, 20, false}, L = {crawl, 30, false},
                N = {swim, 30, false};
        Object[][][] rows = {
                {W, R, S, X},
                {I, W, R, J, S, X},
                {I, W, R, J, S, X, H, E},
                {I, W, R, J, S, L, X, H, E, C},
                {W, R, S, J, X, H},
                {R, S, X, J, H}};
        BannerScreen.TitlePlace[] titles = {BannerScreen.TitlePlace.LEFT, BannerScreen.TitlePlace.RIGHT, BannerScreen.TitlePlace.TOP,
                BannerScreen.TitlePlace.NONE, BannerScreen.TitlePlace.CENTER, BannerScreen.TitlePlace.TOP};
        int[][] colors = {{0xFFFFC94A, 0xFFE0602A}};
        after(10, () -> mc().options.hideGui = true);
        for (int i = 0; i < rows.length; i++) {
            int index = i;
            after(2, () -> {
                java.util.List<BannerScreen.Pose> poses = new java.util.ArrayList<>();
                for (Object[] o : rows[index]) {
                    poses.add(p.apply(o));
                }
                BannerScreen screen = new BannerScreen(poses);
                screen.title = titles[index];
                screen.raysColor = colors[0][0];
                screen.edgeColor = colors[0][1];
                mc().setScreen(screen);
            });
            after(6, () -> shot("brand_banner_c" + (index + 1)));
        }
        after(5, () -> mc().stop());
    }

    private static void iconsTour() {
        // name, background style, top colour, bottom colour, demo, ticks, body yaw offset, sword, zoom
        Object[][] variants = {
                {"s1", "RADIAL", 0xFF6FE0D0, 0xFF16324F, "SWORD", 8, -25.0F, true, 1.0F},
                {"s2", "SUNBURST", 0xFFFFC94A, 0xFFE0602A, "CYCLE", 112, -60.0F, true, 1.0F},
                {"s3", "NIGHT_SKY", 0xFF101634, 0xFF2E2470, "CREATIVE_FLIGHT", 25, -70.0F, false, 1.0F},
                {"s4", "GRASS", 0x40000000, 0xC0000000, "CYCLE", 112, -60.0F, true, 1.0F},
                {"s5", "STRIPES", 0xFF8A4FD8, 0xFFE86AA8, "IDLE", 40, -15.0F, false, 1.6F},
                {"s6", "HALFTONE", 0xFF2F8FE0, 0xFF13306A, "SWORD", 8, -25.0F, true, 1.0F},
                {"s7", "BLUEPRINT", 0xFF2C5DA8, 0xFF0E2350, "JOINTS", 30, -50.0F, false, 1.0F},
                {"s8", "STONE", 0x30000000, 0xB0101830, "SWORD", 8, -25.0F, true, 1.0F},
                {"s9", "PIXEL_CLOUDS", 0xFF7EC8F2, 0xFFBDE6FF, "WALK", 30, -40.0F, false, 1.0F}};
        after(10, () -> mc().options.hideGui = true);
        for (Object[] v : variants) {
            after(2, () -> {
                BrandingScreen screen = new BrandingScreen();
                screen.style = BrandingScreen.Style.valueOf((String) v[1]);
                screen.topColor = (int) v[2];
                screen.bottomColor = (int) v[3];
                screen.zoom = (float) v[8];
                screen.preview.holdSword = (boolean) v[7];
                screen.preview.rotate((float) v[6]);
                screen.preview.setDemo(dev.hangel.animatedplayer.config.PlayerPreview.Demo.valueOf((String) v[4]));
                for (int i = 0; i < (int) v[5]; i++) {
                    screen.preview.tick();
                }
                screen.preview.frozen = true;
                mc().setScreen(screen);
            });
            after(6, () -> shot("brand_icon_" + v[0]));
        }
        after(5, () -> mc().stop());
    }

    /** Mod icon and banner: the mod's Steve frozen mid-sprint with a sword, captured from BrandingScreen. */
    private static void brandingTour() {
        after(10, () -> {
            mc().options.hideGui = true;
            BrandingScreen screen = new BrandingScreen();
            screen.preview.holdSword = true;
            screen.preview.rotate(-60.0F);
            screen.preview.setDemo(dev.hangel.animatedplayer.config.PlayerPreview.Demo.CYCLE);
            // Cycle: standing until tick 40, walking, sprinting from 95.
            for (int i = 0; i < 112; i++) {
                screen.preview.tick();
            }
            screen.preview.frozen = true;
            mc().setScreen(screen);
        });
        after(10, () -> shot("brand_icon"));
        after(2, () -> {
            if (mc().screen instanceof BrandingScreen screen) {
                screen.layout = BrandingScreen.Layout.BANNER;
            }
        });
        after(10, () -> shot("brand_banner"));
        after(2, () -> {
            if (mc().screen instanceof BrandingScreen screen) {
                int[] icon;
                screen.layout = BrandingScreen.Layout.ICON;
                icon = screen.area();
                screen.layout = BrandingScreen.Layout.BANNER;
                int[] banner = screen.area();
                double k = mc().getWindow().getGuiScale();
                System.out.println("[AnimatedPlayer debug] AREA icon " + (int) (icon[0] * k) + " " + (int) (icon[1] * k) + " " + (int) (icon[2] * k) + " " + (int) (icon[3] * k)
                        + " banner " + (int) (banner[0] * k) + " " + (int) (banner[1] * k) + " " + (int) (banner[2] * k) + " " + (int) (banner[3] * k));
            }
            mc().stop();
        });
    }

    /** Same poses with the mod on then off (to compare with other player animation mods / packs). */
    private static void compareTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("time set 6000");
            cmd("weather clear");
            cmd("kill @e[type=parrot]");
            cmd("kill @e[type=boat]");
            cmd("clear @s");
            cmd("fill -12 -61 -12 12 -45 60 air");
            cmd("fill -12 -63 -12 12 -62 60 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            cmd("item replace entity @s weapon.mainhand with iron_sword");
            fly(false);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        String[] states = {"on", "off"};
        for (String state : states) {
            after(5, () -> {
                ModSettings.ENABLED.set(state.equals("on"));
                cmd("tp @s 0 -61 0 0 0");
                sideCam = null;
                mc().setCameraEntity(mc().player);
                mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            });
            after(40, () -> shot("v01_idle_front_" + state));
            after(2, () -> {
                mc().options.setCameraType(CameraType.FIRST_PERSON);
                sideDistance = 3.0;
                sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
                press(mc().options.keyUp, true);
            });
            after(20, () -> shot("v02_walk_side_" + state));
            after(2, () -> press(mc().options.keySprint, true));
            after(20, () -> shot("v03_sprint_side_" + state));
            after(2, () -> {
                stopMoving();
                press(mc().options.keyShift, true);
            });
            after(15, () -> shot("v04_sneak_side_" + state));
            after(2, () -> {
                press(mc().options.keyShift, false);
                mc().player.swing(InteractionHand.MAIN_HAND);
            });
            after(3, () -> shot("v05_swing_side_" + state));
            after(10, () -> press(mc().options.keyJump, true));
            after(5, () -> shot("v06_jump_side_" + state));
            after(1, () -> press(mc().options.keyJump, false));
        }
        after(20, () -> {
            ModSettings.ENABLED.set(true);
            cmd("clear @s");
            mc().stop();
        });
    }

    /** Spyglass, goat horn, crossbow (loading, then loaded) from the front, then crawling in a glass tunnel. */
    private static void itemsTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("time set 6000");
            cmd("kill @e[type=parrot]");
            cmd("kill @e[type=boat]");
            cmd("clear @s");
            cmd("fill -12 -61 -12 12 -45 60 air");
            cmd("fill -12 -63 -12 12 -62 60 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            fly(false);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            cmd("item replace entity @s weapon.mainhand with spyglass");
        });
        after(20, () -> press(mc().options.keyUse, true));
        after(15, () -> shot("i01_spyglass"));
        after(2, () -> {
            press(mc().options.keyUse, false);
            cmd("item replace entity @s weapon.mainhand with goat_horn[instrument=\"minecraft:ponder_goat_horn\"]");
        });
        after(10, () -> press(mc().options.keyUse, true));
        after(10, () -> shot("i02_horn"));
        after(1, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideDistance = 3.0;
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        });
        after(3, () -> shot("i02b_side_horn"));
        after(1, () -> {
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        after(2, () -> {
            press(mc().options.keyUse, false);
            cmd("item replace entity @s weapon.mainhand with crossbow");
            cmd("give @s arrow 8");
        });
        after(10, () -> press(mc().options.keyUse, true));
        after(12, () -> shot("i03_crossbow_charge"));
        after(30, () -> press(mc().options.keyUse, false));
        after(10, () -> shot("i04_crossbow_loaded"));
        after(2, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideDistance = 3.0;
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        });
        after(10, () -> shot("i05_side_crossbow_loaded"));
        // Crawling: a one block high glass tunnel.
        after(2, () -> {
            cmd("clear @s");
            cmd("fill -1 -60 -2 1 -60 20 glass");
            cmd("tp @s 0 -61 0 0 0");
        });
        after(20, () -> shot("i06_side_crawl_idle"));
        after(2, () -> press(mc().options.keyUp, true));
        after(15, () -> shot("i07_side_crawl_a"));
        after(4, () -> shot("i08_side_crawl_b"));
        after(2, () -> {
            stopMoving();
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        after(10, () -> shot("i09_front_crawl"));
        after(2, () -> {
            cmd("fill -1 -60 -2 1 -60 20 air");
            mc().stop();
        });
    }

    /** Rowing a boat forward, side view, several frames of the paddle cycle. */
    private static void boatTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("kill @e[type=parrot]");
            cmd("clear @s");
            cmd("time set 6000");
            cmd("fill -12 -61 -12 12 -45 60 air");
            cmd("fill -12 -63 -12 12 -62 60 water");
            cmd("tp @s 0 -61 0 0 0");
            cmd("summon boat 0 -62 0 {Type:\"oak\"}");
            fly(false);
        });
        after(20, () -> cmd("ride @s mount @e[type=boat,limit=1,sort=nearest]"));
        after(10, () -> {
            sideDistance = 3.0;
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            press(mc().options.keyUp, true);
        });
        after(20, () -> shot("b01_row"));
        after(3, () -> shot("b02_row"));
        after(3, () -> shot("b03_row"));
        after(2, () -> {
            stopMoving();
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            press(mc().options.keyUp, true);
        });
        after(10, () -> shot("b04_row_back"));
        after(2, () -> {
            stopMoving();
            cmd("ride @s dismount");
            cmd("kill @e[type=boat]");
            mc().stop();
        });
    }

    /** Animated player switched off and on again while walking, with a sword and a shield (vanilla pose check). */
    private static void toggleTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("kill @e[type=parrot]");
            cmd("clear @s");
            cmd("fill -8 -61 -8 8 -55 40 air");
            cmd("fill -8 -62 -8 8 -62 40 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            cmd("item replace entity @s weapon.mainhand with iron_sword");
            cmd("item replace entity @s weapon.offhand with shield");
            fly(false);
            sideDistance = 3.0;
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        });
        after(20, () -> press(mc().options.keyUp, true));
        after(20, () -> shot("t01_walk_on"));
        after(2, () -> ModSettings.ENABLED.set(false));
        after(10, () -> shot("t02_walk_off"));
        after(2, () -> {
            stopMoving();
            press(mc().options.keyShift, true);
        });
        after(15, () -> shot("t03_sneak_off"));
        after(2, () -> {
            press(mc().options.keyShift, false);
            ModSettings.ENABLED.set(true);
            press(mc().options.keyUp, true);
        });
        after(15, () -> shot("t04_walk_on_again"));
        after(2, () -> {
            stopMoving();
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc().setCameraEntity(mc().player);
            sideCam = null;
            ModSettings.ENABLED.set(false);
        });
        after(10, () -> shot("t05_front_off"));
        after(2, () -> ModSettings.ENABLED.set(true));
        after(10, () -> shot("t06_front_on"));
        after(5, () -> {
            cmd("clear @s");
            mc().stop();
        });
    }

    /** Rounded mesh limbs (forearms, lower legs, chest) against plain cubes, close side view, same frame pairs. */
    private static void limbsTour() {
        after(20, () -> {
            ModSettings.resetAll();
            ModSettings.INTENSITY.set(150);
            SettingsScreen screen = new SettingsScreen(null);
            mc().setScreen(screen);
            screen.rotatePreview(-50.0F);
        });
        after(3, () -> showOption("AnimationIntensity"));
        String[] frames = {"l01_walk", "l02_walk", "l03_walk"};
        for (String frame : frames) {
            after(7, () -> freezePreview(true));
            after(1, () -> shot(frame + "_rounded"));
            after(1, () -> ModSettings.ROUNDED_LIMBS.set(false));
            after(1, () -> shot(frame + "_cubes"));
            after(1, () -> {
                ModSettings.ROUNDED_LIMBS.set(true);
                freezePreview(false);
            });
        }
        after(2, () -> showOption("RoundedLimbs"));
        after(80, () -> freezePreview(true));
        after(1, () -> shot("l04_hover_rounded"));
        after(1, () -> ModSettings.ROUNDED_LIMBS.set(false));
        after(1, () -> shot("l04_hover_cubes"));
        after(2, () -> {
            ModSettings.ROUNDED_LIMBS.set(true);
            if (mc().screen instanceof SettingsScreen screen) {
                screen.rotatePreview(-25.0F);
            }
        });
        after(2, () -> shot("l05_side_rounded"));
        after(1, () -> ModSettings.ROUNDED_LIMBS.set(false));
        after(1, () -> shot("l05_side_cubes"));
        // Test skin with a second layer (checkerboard), bent at the joints, from behind.
        after(1, () -> {
            if (mc().screen instanceof SettingsScreen screen) {
                screen.rotatePreview(-60.0F);
            }
        });
        after(2, () -> shot("l05_back_cubes_steve"));
        after(1, () -> ModSettings.ROUNDED_LIMBS.set(true));
        after(1, () -> shot("l05_back_rounded_steve"));
        after(1, () -> {
            ModSettings.ROUNDED_LIMBS.set(false);
            if (mc().screen instanceof SettingsScreen screen) {
                screen.previewSkin(TestSkins.generate("limbs_layers", false));
            }
        });
        after(1, () -> ModSettings.SKIN_LAYERS_3D.set(false));
        after(2, () -> shot("l05_back_cubes_edges"));
        after(1, () -> ModSettings.SKIN_LAYERS_3D.set(true));
        after(2, () -> shot("l05_back_cubes_ownskin"));
        after(1, () -> ModSettings.ROUNDED_LIMBS.set(true));
        after(1, () -> shot("l05_back_rounded_ownskin"));
        // Armor follows the same style (in game, on the player).
        after(2, () -> {
            ModSettings.ROUNDED_LIMBS.set(true);
            freezePreview(false);
            mc().setScreen(null);
            mc().options.hideGui = true;
            cmd("item replace entity @s armor.chest with iron_chestplate");
            cmd("item replace entity @s armor.legs with iron_leggings");
            cmd("item replace entity @s armor.feet with iron_boots");
            sideDistance = 2.2;
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            press(mc().options.keyShift, true);
        });
        after(25, () -> shot("l06_armor_sneak_rounded"));
        after(1, () -> ModSettings.ROUNDED_LIMBS.set(false));
        after(1, () -> shot("l06_armor_sneak_cubes"));
        after(2, () -> {
            press(mc().options.keyShift, false);
            cmd("clear @s");
            ModSettings.resetAll();
        });
        after(10, () -> mc().stop());
    }

    /** Settings screens (Mods list, config screen) and live effect of the options on a test skin. */
    private static void configTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("weather clear");
            cmd("fill -8 -61 -8 8 -55 40 air");
            cmd("fill -8 -62 -8 8 -62 40 grass_block");
            cmd("tp @s 4 -61 -4 0 0");
            mc().options.setCameraType(CameraType.FIRST_PERSON);
        });
        after(20, () -> {
            TestSkins.FakePlayer fake = new TestSkins.FakePlayer(mc().level, "config_test",
                    TestSkins.skin(TestSkins.generate("config_test", false), false));
            fake.setPos(0.0, -61.0, -1.8);
            fake.xo = fake.getX();
            fake.yo = fake.getY();
            fake.zo = fake.getZ();
            fake.setOnGround(true);
            mc().level.addEntity(fake);
            FAKES.add(fake);
            turnFakes(180.0F);
            // The fake faces north (yaw 180), towards the camera; the player stands aside, out of the shot.
            camera(0.0, -60.4, -4.2, 0.0F, 5.0F);
        });
        after(30, () -> shot("k01_options_on"));
        after(2, () -> {
            ModSettings.BETTER_HAT.set(false);
            ModSettings.SKIN_LAYERS_3D.set(false);
            ModSettings.ANIMATE_FACE.set(false);
        });
        after(5, () -> shot("k02_options_off"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.IDLE_ANIMATION.set(false);
        });
        after(5, () -> shot("k02b_idle_off"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.ENABLED.set(false);
        });
        after(5, () -> shot("k02c_mod_disabled"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.APPLY_TO.set(ModSettings.ApplyTo.SELF);
        });
        after(5, () -> shot("k02d_only_me"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.ANIMATION_DISTANCE.set(8);
            camera(0.0, -60.4, -14.0, 0.0F, 5.0F);
        });
        after(5, () -> shot("k02e_distance_8"));
        after(2, () -> {
            ModSettings.resetAll();
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.hideGui = false;
            mc().setScreen(modListScreen.apply(null));
        });
        after(20, () -> shot("k03_mod_list"));
        after(2, () -> mc().setScreen(configScreen.apply(modListScreen.apply(null))));
        after(30, () -> shot("k04_config_screen"));
        after(2, () -> {
            if (mc().screen instanceof SettingsScreen screen) {
                screen.useOwnSkin(true);
                screen.showOption("AnimateFace");
            }
        });
        after(20, () -> shot("k04b_preview_own_skin"));
        after(2, () -> {
            if (mc().screen instanceof SettingsScreen screen) {
                screen.useOwnSkin(false);
            }
        });
        // Demo of each option in the preview, with the option on then off.
        String[][] demos = {{"SwordAnimation", "k05_sword"}, {"SupermanFly", "k06_superman"}, {"HungerEyebrows", "k07_hunger"},
                {"BreathlessMouth", "k08_breathless"}, {"AnimationIntensity", "k10_intensity"}};
        for (String[] demo : demos) {
            after(2, () -> showOption(demo[0]));
            after(25, () -> shot(demo[1]));
        }
        after(2, () -> {
            ModSettings.SUPERMAN_FLY.set(true);
            showOption("SupermanFly");
        });
        after(25, () -> shot("k06b_superman_on"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.INTENSITY.set(150);
            showOption("AnimationIntensity");
        });
        after(10, () -> shot("k10b_intensity_150"));
        after(2, () -> {
            ModSettings.resetAll();
            ModSettings.ENABLED.set(false);
        });
        after(25, () -> shot("k11_preview_vanilla"));
        // Same screen from the title screen (no world, no player entity).
        after(2, () -> {
            ModSettings.resetAll();
            leavingWorld = true;
            mc().level.disconnect();
            mc().disconnect();
        });
        after(40, () -> mc().setScreen(new SettingsScreen(new TitleScreen())));
        after(30, () -> shot("k12_title_screen_preview"));
        after(2, () -> {
            if (mc().screen instanceof SettingsScreen screen) {
                screen.useOwnSkin(true);
            }
        });
        after(40, () -> shot("k13_title_own_skin"));
        after(10, () -> mc().stop());
    }

    /**
     * Run with third-party mods in run/mods: poses that involve them (backpack worn on the back, Better Combat attack,
     * Emotecraft emote, first-person body). Actions for a missing mod simply do nothing.
     */
    private static void compatTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            cmd("clear @s");
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule sendCommandFeedback false");
            cmd("time set 6000");
            cmd("weather clear");
            cmd("fill -8 -61 -8 8 -55 40 air");
            cmd("fill -8 -62 -8 8 -62 40 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            fly(false);
        });
        after(10, () -> {
            loadSetup();
            // A caped player beside the test player, for cape mods (the dev player has no cape).
            ResourceLocation cape = TestSkins.generateCape("compat_cape");
            PlayerSkin skin = new PlayerSkin(TestSkins.generate("compat_caped", false), null, cape, null, PlayerSkin.Model.WIDE, true);
            TestSkins.FakePlayer caped = new TestSkins.FakePlayer(mc().level, "caped", skin);
            caped.setPos(1.5, -61.0, 0.5);
            caped.xo = caped.getX();
            caped.yo = caped.getY();
            caped.zo = caped.getZ();
            caped.setOnGround(true);
            mc().level.addEntity(caped);
        });
        // Setup lines run one every 5 ticks from here.
        after(80, () -> shot("c01_front"));
        after(2, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            press(mc().options.keyUp, true);
        });
        after(25, () -> shot("c02_side_walk"));
        after(2, () -> {
            stopMoving();
            cmd("tp @s 0 -61 0 0 0");
            equipBackpack();
        });
        after(20, () -> shot("c03_side_backpack"));
        after(2, () -> press(mc().options.keyUp, true));
        after(20, () -> shot("c04_side_backpack_walk"));
        after(2, () -> {
            stopMoving();
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        after(10, () -> shot("c05_back_backpack"));
        after(2, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            cmd("tp @s 0 -61 0 0 0");
            cmd("item replace entity @s weapon.mainhand with diamond_sword");
        });
        after(20, DebugHarness::attack);
        after(4, () -> shot("c06_side_attack_a"));
        after(4, () -> shot("c07_side_attack_b"));
        after(20, () -> {
            cmd("item replace entity @s weapon.mainhand with air");
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            // Emote names are case-sensitive ("Waving" in assets/emotecraft/emotes/waving.json).
            cmdIfExists("emotes", "emotes play Waving");
        });
        after(20, () -> shot("c08_emote_a"));
        after(10, () -> shot("c09_emote_b"));
        after(20, () -> {
            cmdIfExists("emotes", "emotes stop");
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().player.setXRot(70.0F);
            mc().options.hideGui = false;
        });
        after(10, () -> shot("c10_first_person_down"));
        // Elytra glide (Do a Barrel Roll and other flight mods).
        after(2, () -> {
            mc().options.hideGui = true;
            mc().player.setXRot(10.0F);
            cmd("item replace entity @s armor.chest with elytra");
            cmd("tp @s 0 -20 0 0 25");
        });
        after(10, () -> {
            mc().player.startFallFlying();
            mc().player.connection.send(new ServerboundPlayerCommandPacket(mc().player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        });
        after(20, () -> shot("c11_side_elytra"));
        after(10, () -> mc().stop());
    }

    /**
     * Extra per-run setup from the file in -Danimatedplayer.debug.setup (compat-test/mods/<loader>/<group>/setup.txt):
     * one command per line ("#" comments), or "!use" to use the main hand item (equips accessories).
     */
    private static void loadSetup() {
        String path = System.getProperty("animatedplayer.debug.setup", "");
        if (path.isEmpty()) {
            return;
        }
        try {
            int delay = 0;
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))) {
                String command = line.trim();
                if (command.isEmpty() || command.startsWith("#")) {
                    continue;
                }
                delay += 5;
                STEPS.put(tick + delay, () -> {
                    if (command.equals("!use")) {
                        mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
                    } else {
                        cmd(command);
                    }
                });
            }
        } catch (java.io.IOException e) {
            System.out.println("[AnimatedPlayer debug] ERROR: cannot read setup " + path + ": " + e);
        }
    }

    /** Traveler's Backpack has no command to wear a backpack: call its API on the integrated server. */
    private static void equipBackpack() {
        MinecraftServer server = mc().getSingleplayerServer();
        UUID id = mc().player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ItemStack backpack = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("travelersbackpack", "standard")).getDefaultInstance();
            if (backpack.isEmpty() || backpack.is(Items.AIR)) {
                return;
            }
            for (String name : new String[]{"com.tiviacz.travelersbackpack.capability.AttachmentUtils", "com.tiviacz.travelersbackpack.component.ComponentUtils"}) {
                try {
                    for (java.lang.reflect.Method m : Class.forName(name).getMethods()) {
                        if (m.getName().equals("equipBackpack") && m.getParameterCount() == 2) {
                            m.invoke(null, player, backpack);
                            return;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        });
    }

    /**
     * Same as a left click (what Better Combat hooks), aimed at nothing. The crosshair target is computed from the
     * camera entity, which is the side camera here: it would pick the player itself, and attacking yourself gets
     * you kicked ("Attempting to attack an invalid entity").
     */
    private static void attack() {
        Vec3 eyes = mc().player.getEyePosition();
        mc().hitResult = BlockHitResult.miss(eyes, Direction.UP, BlockPos.containing(eyes));
        try {
            java.lang.reflect.Method m = Minecraft.class.getDeclaredMethod("startAttack");
            m.setAccessible(true);
            m.invoke(mc());
        } catch (ReflectiveOperationException e) {
            mc().player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void freezePreview(boolean frozen) {
        if (mc().screen instanceof SettingsScreen screen) {
            screen.freezePreview(frozen);
        }
    }

    private static void showOption(String key) {
        if (mc().screen instanceof SettingsScreen screen) {
            screen.showOption(key);
        }
    }

    private static void turnFakes(float yaw) {
        for (TestSkins.FakePlayer fake : FAKES) {
            fake.setYRot(yaw);
            fake.yRotO = yaw;
            fake.yBodyRot = yaw;
            fake.yBodyRotO = yaw;
            fake.yHeadRot = yaw;
            fake.yHeadRotO = yaw;
        }
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        fixedCam = new double[]{x, y, z, yaw, pitch};
        if (sideCam == null) {
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        }
    }

    private static void fullTour() {
        after(20, () -> {
            mc().options.hideGui = true;
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            cmd("clear @s");
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("weather clear");
            cmd("tp @s 0 -60 0 0 0");
            cmd("fill -8 -61 -8 8 -55 40 air");
            cmd("fill -8 -62 -8 8 -62 40 grass_block");
            fly(false);
        });
        after(40, () -> shot("01_idle_front"));
        after(10, () -> press(mc().options.keyUp, true));
        after(30, () -> shot("02_walk"));
        after(10, () -> press(mc().options.keySprint, true));
        after(30, () -> shot("03_sprint"));
        after(10, () -> {
            stopMoving();
            press(mc().options.keyShift, true);
        });
        after(25, () -> shot("04_sneak"));
        after(5, () -> {
            press(mc().options.keyShift, false);
            cmd("tp @s 0 -60 0 0 0");
            cmd("item replace entity @s armor.head with diamond_helmet");
            cmd("item replace entity @s armor.chest with diamond_chestplate");
            cmd("item replace entity @s armor.legs with diamond_leggings");
            cmd("item replace entity @s armor.feet with diamond_boots");
            cmd("item replace entity @s weapon.mainhand with diamond_sword");
        });
        after(40, () -> shot("05_armor_sword"));
        after(5, () -> mc().player.swing(InteractionHand.MAIN_HAND));
        after(2, () -> shot("06_swing"));
        after(13, () -> press(mc().options.keyJump, true));
        after(5, () -> shot("07_jump"));
        after(1, () -> press(mc().options.keyJump, false));
        after(14, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        after(10, () -> shot("08_back"));
        after(5, () -> {
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            cmd("item replace entity @s armor.head with air");
            cmd("item replace entity @s armor.chest with leather_chestplate[dyed_color=16711680]");
            cmd("item replace entity @s weapon.mainhand with bread");
        });
        after(10, () -> press(mc().options.keyUse, true));
        after(15, () -> shot("09_eat"));
        after(5, () -> {
            press(mc().options.keyUse, false);
            cmd("item replace entity @s weapon.mainhand with bow");
        });
        after(5, () -> press(mc().options.keyUse, true));
        after(20, () -> shot("10_bow"));
        after(5, () -> {
            press(mc().options.keyUse, false);
            cmd("item replace entity @s weapon.mainhand with torch");
            fly(true);
            press(mc().options.keyUp, true);
        });
        after(25, () -> shot("11_fly"));

        // Side view.
        after(5, () -> {
            stopMoving();
            fly(false);
            cmd("tp @s 0 -60 0 0 0");
            cmd("clear @s");
            cmd("item replace entity @s weapon.mainhand with iron_sword");
            cmd("item replace entity @s weapon.offhand with shield");
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().setCameraEntity(sideCam);
        });
        after(30, () -> shot("13_side_idle"));
        after(5, () -> press(mc().options.keyUp, true));
        after(30, () -> shot("14_side_walk"));
        after(5, () -> press(mc().options.keySprint, true));
        after(30, () -> shot("15_side_sprint"));
        after(5, () -> stopMoving());
        after(20, () -> mc().player.swing(InteractionHand.MAIN_HAND));
        after(3, () -> shot("16_side_swing"));
        after(10, () -> press(mc().options.keyShift, true));
        after(25, () -> shot("17_side_sneak"));
        after(5, () -> {
            press(mc().options.keyShift, false);
            press(mc().options.keyUse, true); // raise shield (off hand)
        });
        after(20, () -> shot("18_side_block"));
        after(5, () -> {
            press(mc().options.keyUse, false);
            cmd("tp @s 0 -60 0 0 0");
            cmd("fill -6 -64 -6 6 -59 40 water");
        });
        after(20, () -> press(mc().options.keyUp, true));
        after(40, () -> shot("19_side_swim"));
        after(5, () -> {
            stopMoving();
            cmd("fill -8 -64 -8 8 -55 40 air");
            cmd("fill -8 -64 -8 8 -62 40 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            cmd("summon horse 0 -61 0 {Tame:1b,SaddleItem:{id:\"minecraft:saddle\",count:1}}");
        });
        after(20, () -> cmd("ride @s mount @e[type=horse,limit=1,sort=nearest]"));
        after(20, () -> shot("20_side_ride"));
        after(5, () -> {
            cmd("ride @s dismount");
            cmd("kill @e[type=horse]");
            cmd("tp @s 0 -58 0 0 0");
            fly(true);
        });
        after(25, () -> shot("22_side_hover"));
        after(5, () -> press(mc().options.keyUp, true));
        after(25, () -> shot("23_side_fly_move"));
        after(5, () -> {
            stopMoving();
            fly(false);
            cmd("fill -8 -64 -8 8 -55 60 water");
            cmd("tp @s 0 -62 0 0 0");
        });
        after(20, () -> {
            press(mc().options.keyUp, true);
            press(mc().options.keySprint, true);
        });
        after(40, () -> shot("24_side_swim_sprint"));
        after(5, () -> {
            stopMoving();
            cmd("fill -8 -64 -8 8 -55 60 air");
            cmd("fill -8 -64 -8 8 -62 60 grass_block");
            cmd("item replace entity @s armor.chest with elytra");
            cmd("tp @s 0 -61 0 0 0");
        });
        after(20, () -> shot("25_side_elytra_idle"));
        after(5, () -> cmd("tp @s 0 -20 0 0 25"));
        after(8, () -> {
            mc().player.startFallFlying();
            mc().player.connection.send(new ServerboundPlayerCommandPacket(mc().player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        });
        after(20, () -> shot("26_side_elytra_glide"));
        after(2, () -> {
            mc().setCameraEntity(mc().player);
            sideCam = null;
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        after(3, () -> shot("27_back_elytra_glide"));
        // Trident: charging, then a riptide spin in water.
        after(5, () -> {
            cmd("tp @s 0 -61 0 0 0");
            cmd("clear @s");
            cmd("item replace entity @s weapon.mainhand with trident");
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
        });
        after(20, () -> press(mc().options.keyUse, true));
        after(15, () -> shot("28_side_trident_charge"));
        after(2, () -> {
            press(mc().options.keyUse, false);
            cmd("fill -8 -64 -8 8 -58 60 water");
            cmd("tp @s 0 -61 0 0 0");
            cmd("item replace entity @s weapon.mainhand with trident[enchantments={levels:{\"minecraft:riptide\":3}}]");
        });
        after(20, () -> press(mc().options.keyUse, true));
        after(15, () -> press(mc().options.keyUse, false));
        after(3, () -> shot("29_side_riptide"));
        // Parrots on both shoulders, standing then walking.
        after(40, () -> {
            cmd("fill -8 -64 -8 8 -55 60 air");
            cmd("fill -8 -64 -8 8 -62 60 grass_block");
            cmd("tp @s 0 -61 0 0 0");
            cmd("clear @s");
        });
        after(20, DebugHarness::putParrotsOnShoulders);
        after(10, () -> {
            sideCam = null;
            mc().setCameraEntity(mc().player);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        after(10, () -> shot("30_parrots_front"));
        after(2, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc().level);
            press(mc().options.keyUp, true);
        });
        after(20, () -> shot("31_side_parrots_walk"));
        after(2, () -> {
            stopMoving();
            cmd("tp @s 0 -61 0 0 0");
            cmd("clear @s");
            mc().setCameraEntity(mc().player);
            sideCam = null;
            mc().gui.getChat().clearMessages(false);
            mc().getToasts().clear();
            // In creative, InventoryScreen immediately becomes the creative inventory, which has no player view.
            cmd("gamemode survival");
        });
        after(5, () -> {
            mc().options.hideGui = false;
            mc().setScreen(new InventoryScreen(mc().player));
        });
        after(15, () -> shot("21_inventory"));
        after(10, () -> mc().stop());
    }

    /** The camera entity of side views, or null (Fabric's entry point draws the player for it). */
    static ArmorStand sideCamera() {
        return sideCam;
    }

    /** Called by the loader's debug entry point at the end of every client tick. */
    static void onTick() {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (leavingWorld) {
                Runnable step = STEPS.get(++tick);
                if (step != null) {
                    step.run();
                }
                return;
            }
            if (tick > 0) {
                // Lost the world mid-scenario (kicked, crash...): fail fast instead of idling on the menu.
                System.out.println("[AnimatedPlayer debug] ERROR: left the world at tick " + tick + ", stopping");
                mc.stop();
                return;
            }
            if (!started && mc.getOverlay() == null && mc.screen != null) {
                started = true;
                mc.options.onboardAccessibility = false;
                mc.options.pauseOnLostFocus = false;
                if (mc.getLevelSource().levelExists(WORLD)) {
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> {
                    });
                } else {
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(42L, false, false),
                            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                            mc.screen);
                }
            }
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen) && !(mc.screen instanceof SettingsScreen) && !(mc.screen instanceof BrandingScreen) && !(mc.screen instanceof BannerScreen)
                && !"config".equals(System.getProperty("animatedplayer.debug.scenario"))) {
            mc.setScreen(null);
        }
        LocalPlayer player = mc.player;
        if (flyer != null) {
            flyer.xo = flyer.getX();
            flyer.yo = flyer.getY();
            flyer.zo = flyer.getZ();
            flyer.setPos(flyer.getX(), flyer.getY(), flyer.getZ() + 1.0);
            fixedCam[2] += 1.0;
        }
        if (sideCam != null && fixedCam != null) {
            sideCam.setPos(fixedCam[0], fixedCam[1], fixedCam[2]);
            sideCam.xo = fixedCam[0];
            sideCam.yo = fixedCam[1];
            sideCam.zo = fixedCam[2] - (flyer != null ? 1.0 : 0.0);
            sideCam.setYRot((float) fixedCam[3]);
            sideCam.yRotO = (float) fixedCam[3];
            sideCam.yHeadRot = (float) fixedCam[3];
            sideCam.yHeadRotO = (float) fixedCam[3];
            sideCam.setXRot((float) fixedCam[4]);
            sideCam.xRotO = (float) fixedCam[4];
            if (mc.getCameraEntity() != sideCam) {
                mc.setCameraEntity(sideCam);
            }
        } else if (sideCam != null) {
            sideCam.setPos(player.getX() - sideDistance, player.getY(), player.getZ());
            sideCam.xo = player.xo - sideDistance;
            sideCam.yo = player.yo;
            sideCam.zo = player.zo;
            sideCam.setYRot(-90.0F);
            sideCam.yRotO = -90.0F;
            sideCam.yHeadRot = -90.0F;
            sideCam.yHeadRotO = -90.0F;
            sideCam.setXRot(10.0F);
            sideCam.xRotO = 10.0F;
            if (mc.getCameraEntity() != sideCam) {
                mc.setCameraEntity(sideCam);
            }
        }
        Runnable step = STEPS.get(++tick);
        if (step != null) {
            step.run();
        }
    }

    private static void fly(boolean flying) {
        LocalPlayer player = Minecraft.getInstance().player;
        player.getAbilities().flying = flying;
        player.onUpdateAbilities();
    }

    private static void stopMoving() {
        Minecraft mc = Minecraft.getInstance();
        press(mc.options.keySprint, false);
        press(mc.options.keyUp, false);
        mc.player.setSprinting(false);
    }

    private static void press(KeyMapping key, boolean down) {
        key.setDown(down);
    }

    /** Only sends commands a mod registered when that mod is loaded, so runs without it stay error-free. */
    private static void cmdIfExists(String root, String command) {
        MinecraftServer server = mc().getSingleplayerServer();
        if (server != null && server.getCommands().getDispatcher().getRoot().getChild(root) != null) {
            cmd(command);
        }
    }

    private static void cmd(String command) {
        Minecraft.getInstance().player.connection.sendUnsignedCommand(command);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> System.out.println("[AnimatedPlayer debug] " + msg.getString()));
    }
}
