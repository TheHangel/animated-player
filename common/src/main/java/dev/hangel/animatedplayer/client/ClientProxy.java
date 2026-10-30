package dev.hangel.animatedplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import dev.hangel.animatedplayer.AnimatedPlayer;
import dev.hangel.animatedplayer.config.ModSettings;
import net.minecraft.world.entity.Entity;
import dev.hangel.animatedplayer.client.model.WavefrontObject;

public class ClientProxy {
    /** Meshes as shipped, UVs for 64x32 textures: used by the armor models (armor textures are 64x32). */
    public static WavefrontObject forearm1;
    public static WavefrontObject forearm2;
    public static WavefrontObject foot1;
    public static WavefrontObject foot2;
    /** Player meshes for 64x64 skins (see {@link #derivePlayerMeshes}). */
    public static WavefrontObject skinChest;
    /** [slim][left][joint cap texture: {@link #CAP_EDGE}, {@link #CAP_ZONE}, {@link #CAP_MIRROR}]. */
    public static final WavefrontObject[][][] SKIN_FOREARMS = new WavefrontObject[2][2][3];
    /** [left][joint cap texture]. */
    public static final WavefrontObject[][] SKIN_FEET = new WavefrontObject[2][3];
    /** Joint end textured from the limb's side faces at the joint (skins without cap pixels). */
    public static final int CAP_EDGE = 0;
    /** From the limb's own cap pixels, as painted by the skin (1.7.10 convention). */
    public static final int CAP_ZONE = 1;
    /** Left limb: from the right limb's cap pixels, mirrored (skins made for 1.7.10). */
    public static final int CAP_MIRROR = 2;

    /** One skin pixel in mesh units (meshes are drawn scaled by 0.76). */
    private static final float MESH_PIXEL = 1.0F / 16.0F / 0.76F;

    private static WavefrontObject load(ResourceManager manager, String path) {
        return WavefrontObject.load(manager, ResourceLocation.fromNamespaceAndPath(AnimatedPlayer.MODID, path));
    }

    /**
     * Called (by MixinPlayerRenderer) each time vanilla builds its wide or slim player renderer, i.e. on every
     * resource reload: swaps in the mod's model and replaces the vanilla layers it reimplements.
     */
    public static void install(PlayerRenderer renderer, EntityRendererProvider.Context context, boolean slim) {
        if (!slim) {
            ResourceManager manager = context.getResourceManager();
            forearm1 = load(manager, "models/forearm/steve_right_forearm.obj");
            forearm2 = load(manager, "models/forearm/steve_left_forearm.obj");
            foot1 = load(manager, "models/legs/steve_right_leg.obj");
            foot2 = load(manager, "models/legs/steve_left_leg.obj");
            WavefrontObject chest = load(manager, "models/body/steve_body.obj");
            derivePlayerMeshes(chest);
            SkinInfo.clearCache();
            ANIMATED_DEFAULTS.clear();
            LegacySteve.reset();
        }
        PlayerLayers.install(renderer, context, slim);
    }

    /**
     * True when this player is drawn the vanilla way: the mod is turned off in its settings, or another mod
     * animates the vanilla model right now (see {@link ExternalAnimations}).
     */
    public static boolean useVanilla(AbstractClientPlayer player) {
        if (!ModSettings.ENABLED.on() || ExternalAnimations.isActive(player)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean self = player == minecraft.player;
        ModSettings.ApplyTo applyTo = ModSettings.APPLY_TO.get();
        if (applyTo == ModSettings.ApplyTo.SELF && !self || applyTo == ModSettings.ApplyTo.OTHERS && self) {
            return true;
        }
        int distance = ModSettings.ANIMATION_DISTANCE.get();
        Entity camera = minecraft.getCameraEntity();
        return distance > 0 && !self && camera != null && player.distanceToSqr(camera) > (double) distance * distance;
    }

    /**
     * Texture drawn instead of the player's skin, or null: the game's default skins (Steve, Alex, ...) are swapped
     * for the mod's copies of them, which look the same but add the face animation and joint pixels (made by
     * tools/make_default_skins.py). Any other skin is drawn as is.
     */
    public static ResourceLocation skinOverride(AbstractClientPlayer player) {
        return useVanilla(player) ? null : animatedDefaultSkin(player.getSkin().texture());
    }

    private static final String DEFAULT_SKINS = "textures/entity/player/";
    /** Which mod copies exist (a resource pack could remove some): looked up once per resource reload. */
    private static final Map<ResourceLocation, ResourceLocation> ANIMATED_DEFAULTS = new HashMap<>();

    /** The mod's version of a game default skin, or null if {@code skin} is not one or the mod has no copy of it. */
    public static ResourceLocation animatedDefaultSkin(ResourceLocation skin) {
        if (!skin.getNamespace().equals("minecraft") || !skin.getPath().startsWith(DEFAULT_SKINS)) {
            return null;
        }
        ResourceLocation copy = ANIMATED_DEFAULTS.computeIfAbsent(skin, s -> {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AnimatedPlayer.MODID,
                    "textures/skins/" + s.getPath().substring(DEFAULT_SKINS.length()));
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : s;
        });
        return copy.equals(skin) ? null : copy;
    }

    /**
     * The mod's left meshes mirror the right texture, as 1.7.10 skins had no left limbs. 64x64 skins do: the left
     * limbs reuse the right meshes moved to the left texture areas. Slim arms are the wide ones squeezed to 3px,
     * their inner edge kept against the body, with UVs moved to the slim layout. Each comes in three versions for
     * the rounded joint end (same choice as the flat ends, see {@link dev.hangel.animatedplayer.client.model.JointCap}).
     */
    private static void derivePlayerMeshes(WavefrontObject chest) {
        skinChest = chest.derive(1.0F, 0.0F, (u, v, cu, cv) -> new float[]{u, v}, 64);
        for (int cap = 0; cap < 3; cap++) {
            for (int left = 0; left < 2; left++) {
                for (int slim = 0; slim < 2; slim++) {
                    SKIN_FOREARMS[slim][left][cap] = limbMesh(forearm1, false, slim == 1, left == 1, cap);
                }
                SKIN_FEET[left][cap] = limbMesh(foot1, true, false, left == 1, cap);
            }
        }
    }

    /** Texture origin of a limb (vanilla cube layout) in a 64x64 skin. */
    public static int limbU(boolean leg, boolean left) {
        return leg ? (left ? 16 : 0) : (left ? 32 : 40);
    }

    public static int limbV(boolean left) {
        return left ? 48 : 16;
    }

    /** Joint cap pixels of a limb: the unused w x 4 area right after the limb's bottom face. */
    public static int capU(boolean leg, boolean left, boolean slim) {
        return limbU(leg, left) + 4 + 2 * (slim ? 3 : 4);
    }

    private static WavefrontObject limbMesh(WavefrontObject mesh, boolean leg, boolean slim, boolean left, int cap) {
        float w = slim ? 3.0F : 4.0F;
        float capStart = leg ? 12.0F : 52.0F;
        int u0 = limbU(leg, left);
        int v0 = limbV(left);
        float scaleX = slim ? 0.75F : 1.0F;
        float shiftX = slim ? (left ? -0.5F : 0.5F) * MESH_PIXEL : 0.0F;
        return mesh.derive(scaleX, shiftX, (u, v, cu, cv) -> {
            if (cv < 20.0F && cu >= capStart) {
                // The mesh's rounded joint end, textured from the right limb's cap pixels in the authored mesh.
                float x = (u - capStart) * w / 4.0F;
                float y = v - 16.0F;
                return switch (cap) {
                    case CAP_ZONE -> new float[]{capU(leg, left, slim) + x, v0 + y};
                    case CAP_MIRROR -> new float[]{capU(leg, false, slim) + w - x, 16.0F + y};
                    default -> new float[]{u0 + 4 + x, v0 + 4 + 6.5F};
                };
            }
            float[] uv = slim ? slimArmUV(u, v, cu, cv) : new float[]{u, v};
            if (left) {
                uv[0] += leg ? 16.0F : -8.0F;
                uv[1] += 32.0F;
            }
            return uv;
        }, 64);
    }

    /** Right arm texture (40, 16): 4px wide faces become 3px wide, each mesh face stays on its cube side. */
    private static float[] slimArmUV(float u, float v, float cu, float cv) {
        float nu;
        if (cv < 20.0F) {
            // Top, then bottom (the elbow end is handled by limbMesh).
            if (cu < 44.0F) {
                nu = u;
            } else if (cu < 48.0F) {
                nu = remap(u, 44.0F, 44.0F);
            } else if (cu < 52.0F) {
                nu = remap(u, 48.0F, 47.0F);
            } else {
                nu = remap(u, 52.0F, 47.0F);
            }
        } else if (cu < 44.0F) {
            nu = u;
        } else if (cu < 48.0F) {
            nu = remap(u, 44.0F, 44.0F);
        } else if (cu < 52.0F) {
            nu = u - 1.0F;
        } else {
            nu = remap(u, 52.0F, 51.0F);
        }
        return new float[]{nu, v};
    }

    /** 4px wide face starting at {@code from} to a 3px wide one starting at {@code to}. */
    private static float remap(float u, float from, float to) {
        return to + (u - from) * 0.75F;
    }

    public static void onClientTick() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        AnimatedPlayer.clientTick++;

        for (AbstractClientPlayer player : world.players()) {
            PlayerData data = PlayerData.getPlayerData(player);
            ItemStack currentItem = player.getMainHandItem();
            ItemStack usedItem = player.isUsingItem() ? player.getUseItem() : currentItem;
            if (player.tickCount > 0) {
                data.initTextureInfo(player);
            }

            // 1.7.10 nudged remote players down/up to compute onGround client-side; modern servers sync it.

            double dX = player.getX() - player.xo;
            double dZ = player.getZ() - player.zo;
            double dY = player.getY() - player.yo;
            double motion = dX * dX + dZ * dZ;
            float yaw = -((float) Math.atan2(dX, dZ)) * 180.0F / (float) Math.PI;

            while (yaw <= player.getYRot() - 180.0F) {
                yaw += 360.0F;
            }
            while (yaw > player.getYRot() + 180.0F) {
                yaw -= 360.0F;
            }

            float tempYaw = player.getYRot() - yaw;
            data.moveBackwards = motion > 1.0E-4 && Math.abs(tempYaw) > 100.0F;
            data.moveBackwards2 = motion > 1.0E-4 && Math.abs(tempYaw) > 100.0F || motion < 1.0E-4 && data.moveBackwards2;

            data.holdingSword = ModSettings.SWORD_ANIMATION.on() && !currentItem.isEmpty() && currentItem.is(ItemTags.SWORDS);

            data.prevExhaustionTick = data.exhaustionTick;
            if (data.updateExhaustion(player)) {
                data.exhaustionTick = Math.min(10, data.exhaustionTick + 1);
            } else {
                data.exhaustionTick = Math.max(0, data.exhaustionTick - 1);
            }

            data.prevBrowTick = data.browTick;
            if (data.shouldArchBrows(player, currentItem)) {
                data.browTick = Math.min(8, data.browTick + 1);
            } else {
                data.browTick = Math.max(0, data.browTick - 1);
            }

            data.prevSprintTick = data.sprintTick;
            if (player.isSprinting() && !player.getAbilities().flying) {
                data.sprintTick = Math.min(8, data.sprintTick + 1);
            } else {
                data.sprintTick = Math.max(0, data.sprintTick - 1);
            }

            data.prevSneakTick = data.sneakTick;
            if (player.isShiftKeyDown()) {
                data.sneakTick = Math.min(6, data.sneakTick + 1);
            } else {
                data.sneakTick = Math.max(0, data.sneakTick - 1);
            }

            data.prevJumpTick = data.jumpTick;
            if (data.isAirborne(player)) {
                data.jumpTick = Math.min(10, data.jumpTick + 1);
            } else {
                data.jumpTick = 0;
            }

            if (data.jumpTick == 0 && data.prevJumpTick != 0) {
                data.jumpRight = !data.jumpRight;
            }

            data.prevCrawlTick = data.crawlTick;
            data.crawlTick = player.isVisuallyCrawling() ? Math.min(6, data.crawlTick + 1) : Math.max(0, data.crawlTick - 1);
            data.prevSwimTick = data.swimTick;
            data.prevSwimSwing = data.swimSwing;
            data.swimSwing += Math.min(player.walkAnimation.speed(), 0.4F);
            data.prevPitch = data.pitch;
            float pitch = (float) Math.atan2(-dY, Math.sqrt(dX * dX + dZ * dZ));
            data.pitch = data.pitch + (pitch - data.pitch) * 0.1F;
            if (player.isInWater() && !player.onGround()) {
                data.swimTick = Math.min(16, data.swimTick + 1);
            } else if (data.swimTick > 12) {
                data.swimTick = Math.max(0, data.swimTick - 1);
            } else {
                data.swimTick = Math.max(0, data.swimTick - 2);
            }

            data.prevBlockTick = data.blockTick;
            data.blockTick = data.isBlocking(player, usedItem) ? 1 : 0;

            data.prevClimbingTick = data.climbingTick;
            data.prevClimbingOffset = data.climbingOffset;
            if (player.onClimbable()) {
                data.climbingTick = Math.min(8, data.climbingTick + 1);
                data.climbingOffset = (float) (data.climbingOffset + (player.getY() - player.yo) / 0.2);
            } else {
                data.climbingTick = Math.max(0, data.climbingTick - 1);
            }

            data.prevEatTick = data.eatTick;
            if (data.isEating(player, usedItem)) {
                data.eatTick = Math.min(8, data.eatTick + 1);
            } else {
                data.eatTick = Math.max(0, data.eatTick - 1);
            }

            data.prevDrinkTick = data.drinkTick;
            if (data.isDrinking(player, usedItem)) {
                data.drinkTick = Math.min(8, data.drinkTick + 1);
            } else {
                data.drinkTick = Math.max(0, data.drinkTick - 1);
            }

            data.prevFlyingTick = data.flyingTick;
            data.prevPosTick = data.posTick;
            double d = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
            float pos = (float) Math.min(1.0, d * 4.0) * (data.moveBackwards ? -0.5F : 0.9F);
            data.posTick = data.prevPosTick + (pos - data.prevPosTick) * 0.3F;
            // Elytra gliding; creative flight too with the SupermanFly option (as in 1.7.10), else the hovering pose.
            boolean superman = ModSettings.SUPERMAN_FLY.on();
            if (player.isFallFlying() || superman && player.getAbilities().flying) {
                data.flyingTick = Math.min(5, data.flyingTick + 1);
            } else {
                data.flyingTick = Math.max(0, data.flyingTick - 1);
            }

            data.prevHoverTick = data.hoverTick;
            if (player.getAbilities().flying && !superman) {
                data.hoverTick = Math.min(5, data.hoverTick + 1);
            } else {
                data.hoverTick = Math.max(0, data.hoverTick - 1);
            }
        }
    }
}
