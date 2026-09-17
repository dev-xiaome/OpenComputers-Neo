package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fStack;

// Neo: Exceptionally add a static wildcard import to make the patch bearable, and comments to avoid the detection by spotless rules.
/* space for import change */ import static net.neoforged.neoforge.client.gui.VanillaGuiLayers.* /* space for wildcard import */;

@OnlyIn(Dist.CLIENT)
public class Gui {
    private static final ResourceLocation CROSSHAIR_SPRITE = ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/crosshair_attack_indicator_background"
    );
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/crosshair_attack_indicator_progress"
    );
    private static final ResourceLocation EFFECT_BACKGROUND_AMBIENT_SPRITE = ResourceLocation.withDefaultNamespace("hud/effect_background_ambient");
    private static final ResourceLocation EFFECT_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/effect_background");
    private static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/hotbar_attack_indicator_background"
    );
    private static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/hotbar_attack_indicator_progress"
    );
    private static final ResourceLocation JUMP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation JUMP_BAR_COOLDOWN_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_cooldown");
    private static final ResourceLocation JUMP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");
    private static final ResourceLocation EXPERIENCE_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation EXPERIENCE_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final ResourceLocation ARMOR_EMPTY_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_empty");
    private static final ResourceLocation ARMOR_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_half");
    private static final ResourceLocation ARMOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation FOOD_EMPTY_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
    private static final ResourceLocation FOOD_HALF_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
    private static final ResourceLocation FOOD_FULL_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full_hunger");
    private static final ResourceLocation FOOD_EMPTY_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation AIR_SPRITE = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation AIR_BURSTING_SPRITE = ResourceLocation.withDefaultNamespace("hud/air_bursting");
    private static final ResourceLocation HEART_VEHICLE_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_container");
    private static final ResourceLocation HEART_VEHICLE_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_full");
    private static final ResourceLocation HEART_VEHICLE_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_half");
    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final ResourceLocation PUMPKIN_BLUR_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/pumpkinblur.png");
    public static final ResourceLocation SPYGLASS_SCOPE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");
    private static final ResourceLocation POWDER_SNOW_OUTLINE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static final Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER = Comparator.comparing(PlayerScoreEntry::value)
        .reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final Component DEMO_EXPIRED_TEXT = Component.translatable("demo.demoExpired");
    private static final Component SAVING_TEXT = Component.translatable("menu.savingLevel");
    private static final float MIN_CROSSHAIR_ATTACK_SPEED = 5.0F;
    private static final int NUM_HEARTS_PER_ROW = 10;
    private static final int LINE_HEIGHT = 10;
    private static final String SPACER = ": ";
    private static final float PORTAL_OVERLAY_ALPHA_MIN = 0.2F;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SEPARATION = 8;
    private static final float AUTOSAVE_FADE_SPEED_FACTOR = 0.2F;
    private final RandomSource random = RandomSource.create();
    private final Minecraft minecraft;
    private final ChatComponent chat;
    private int tickCount;
    @Nullable
    private Component overlayMessageString;
    private int overlayMessageTime;
    private boolean animateOverlayMessageColor;
    private boolean chatDisabledByPlayerShown;
    public float vignetteBrightness = 1.0F;
    private int toolHighlightTimer;
    private ItemStack lastToolHighlight = ItemStack.EMPTY;
    private final DebugScreenOverlay debugOverlay;
    private final SubtitleOverlay subtitleOverlay;
    /**
     * The spectator GUI for this in-game GUI instance
     */
    private final SpectatorGui spectatorGui;
    private final PlayerTabOverlay tabList;
    private final BossHealthOverlay bossOverlay;
    /**
     * A timer for the current title and subtitle displayed
     */
    private int titleTime;
    /**
     * The current title displayed
     */
    @Nullable
    private Component title;
    /**
     * The current sub-title displayed
     */
    @Nullable
    private Component subtitle;
    /**
     * The time that the title take to fade in
     */
    private int titleFadeInTime;
    /**
     * The time that the title is display
     */
    private int titleStayTime;
    /**
     * The time that the title take to fade out
     */
    private int titleFadeOutTime;
    private int lastHealth;
    private int displayHealth;
    /**
     * The last recorded system time
     */
    private long lastHealthTime;
    /**
     * Used with updateCounter to make the heart bar flash
     */
    private long healthBlinkTime;
    private float autosaveIndicatorValue;
    private float lastAutosaveIndicatorValue;
    /** Neo: This is empty and unused, rendering goes through {@link #layerManager} instead. */
    @Deprecated
    private final LayeredDraw layers = new LayeredDraw();
    private final net.neoforged.neoforge.client.gui.GuiLayerManager layerManager = new net.neoforged.neoforge.client.gui.GuiLayerManager();
    private float scopeScale;

    /**
     * Neo: This variable controls the height of overlays on the left of the hotbar (e.g. health, armor).
     */
    public int leftHeight;
    /**
     * Neo: This variable controls the height of overlays on the right of the hotbar (e.g. food, vehicle health, air).
     */
    public int rightHeight;

    public Gui(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.debugOverlay = new DebugScreenOverlay(minecraft);
        this.spectatorGui = new SpectatorGui(minecraft);
        this.chat = new ChatComponent(minecraft);
        this.tabList = new PlayerTabOverlay(minecraft, this);
        this.bossOverlay = new BossHealthOverlay(minecraft);
        this.subtitleOverlay = new SubtitleOverlay(minecraft);
        this.resetTitleTimes();
        var playerHealthComponents = new net.neoforged.neoforge.client.gui.GuiLayerManager()
                .add(PLAYER_HEALTH, (guiGraphics, partialTick) -> renderHealthLevel(guiGraphics))
                .add(ARMOR_LEVEL, (guiGraphics, partialTick) -> renderArmorLevel(guiGraphics))
                .add(FOOD_LEVEL, (guiGraphics, partialTick) -> renderFoodLevel(guiGraphics));
        var layereddraw = new net.neoforged.neoforge.client.gui.GuiLayerManager()
            .add(CAMERA_OVERLAYS, this::renderCameraOverlays)
            .add(CROSSHAIR, this::renderCrosshair)
            .add(HOTBAR, this::renderHotbar)
            .add(JUMP_METER, this::maybeRenderJumpMeter)
            .add(EXPERIENCE_BAR, this::maybeRenderExperienceBar)
            .add(playerHealthComponents, () -> this.minecraft.gameMode.canHurtPlayer())
            .add(VEHICLE_HEALTH, this::maybeRenderVehicleHealth)
            // Air goes above vehicle health, it must render after it for `rightHeight` to work!
            .add(AIR_LEVEL, (guiGraphics, partialTick) -> { if (this.minecraft.gameMode.canHurtPlayer()) renderAirLevel(guiGraphics); })
            .add(SELECTED_ITEM_NAME, this::maybeRenderSelectedItemName)
            .add(SPECTATOR_TOOLTIP, this::maybeRenderSpectatorTooltip)
            .add(EXPERIENCE_LEVEL, this::renderExperienceLevel)
            .add(EFFECTS, this::renderEffects)
            .add(BOSS_OVERLAY, (p_315814_, p_315815_) -> this.bossOverlay.render(p_315814_));
        var layereddraw1 = new net.neoforged.neoforge.client.gui.GuiLayerManager()
            .add(DEMO_OVERLAY, this::renderDemoOverlay)
            .add(DEBUG_OVERLAY, (p_315812_, p_315813_) -> {
                if (this.debugOverlay.showDebugScreen()) {
                    this.debugOverlay.render(p_315812_);
                }
            })
            .add(SCOREBOARD_SIDEBAR, this::renderScoreboardSidebar)
            .add(OVERLAY_MESSAGE, this::renderOverlayMessage)
            .add(TITLE, this::renderTitle)
            .add(CHAT, this::renderChat)
            .add(TAB_LIST, this::renderTabList)
            .add(SUBTITLE_OVERLAY, (p_315816_, p_315817_) -> this.subtitleOverlay.render(p_315816_))
            .add(SAVING_INDICATOR, this::renderSavingIndicator);
        this.layerManager.add(layereddraw, () -> !minecraft.options.hideGui).add(SLEEP_OVERLAY, this::renderSleepOverlay).add(layereddraw1, () -> !minecraft.options.hideGui);
    }

    public void resetTitleTimes() {
        this.titleFadeInTime = 10;
        this.titleStayTime = 70;
        this.titleFadeOutTime = 20;
    }

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        RenderSystem.enableDepthTest();
        leftHeight = 39;
        rightHeight = 39;
        this.layerManager.render(guiGraphics, deltaTracker);
        RenderSystem.disableDepthTest();
    }

    private void renderCameraOverlays(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (Minecraft.useFancyGraphics()) {
            this.renderVignette(guiGraphics, this.minecraft.getCameraEntity());
        }

        float f = deltaTracker.getGameTimeDeltaTicks();
        this.scopeScale = Mth.lerp(0.5F * f, this.scopeScale, 1.125F);
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            if (this.minecraft.player.isScoping()) {
                this.renderSpyglassOverlay(guiGraphics, this.scopeScale);
            } else {
                this.scopeScale = 0.5F;
                ItemStack itemstack = this.minecraft.player.getInventory().getArmor(3);
                if (itemstack.is(Blocks.CARVED_PUMPKIN.asItem())) {
                    this.renderTextureOverlay(guiGraphics, PUMPKIN_BLUR_LOCATION, 1.0F);
                } else {
                    net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemstack).renderHelmetOverlay(itemstack, this.minecraft.player, guiGraphics, deltaTracker);
                }
            }
        }

        if (this.minecraft.player.getTicksFrozen() > 0) {
            this.renderTextureOverlay(guiGraphics, POWDER_SNOW_OUTLINE_LOCATION, this.minecraft.player.getPercentFrozen());
        }

        float f1 = Mth.lerp(
            deltaTracker.getGameTimeDeltaPartialTick(false), this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity
        );
        if (f1 > 0.0F && !this.minecraft.player.hasEffect(MobEffects.CONFUSION)) {
            this.renderPortalOverlay(guiGraphics, f1);
        }
    }

    private void renderSleepOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.player.getSleepTimer() > 0) {
            this.minecraft.getProfiler().push("sleep");
            float f = (float)this.minecraft.player.getSleepTimer();
            float f1 = f / 100.0F;
            if (f1 > 1.0F) {
                f1 = 1.0F - (f - 100.0F) / 10.0F;
            }

            int i = (int)(220.0F * f1) << 24 | 1052704;
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), i);
            this.minecraft.getProfiler().pop();
        }
    }

    private void renderOverlayMessage(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Font font = this.getFont();
        if (this.overlayMessageString != null && this.overlayMessageTime > 0) {
            this.minecraft.getProfiler().push("overlayMessage");
            float f = (float)this.overlayMessageTime - deltaTracker.getGameTimeDeltaPartialTick(false);
            int i = (int)(f * 255.0F / 20.0F);
            if (i > 255) {
                i = 255;
            }

            if (i > 8) {
                //Include a shift based on the bar height plus the difference between the height that renderSelectedItemName
                // renders at (59) and the height that the overlay/status bar renders at (68) by default
                int yShift = Math.max(leftHeight, rightHeight) + (68 - 59);
                guiGraphics.pose().pushPose();
                //If y shift is smaller less than the default y level, just render it at the base y level
                guiGraphics.pose().translate((float)(guiGraphics.guiWidth() / 2), (float)(guiGraphics.guiHeight() - Math.max(yShift, 68)), 0.0F);
                int j;
                if (this.animateOverlayMessageColor) {
                    j = Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, i);
                } else {
                    j = FastColor.ARGB32.color(i, -1);
                }

                int k = font.width(this.overlayMessageString);
                guiGraphics.drawStringWithBackdrop(font, this.overlayMessageString, -k / 2, -4, k, j);
                guiGraphics.pose().popPose();
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private void renderTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.title != null && this.titleTime > 0) {
            Font font = this.getFont();
            this.minecraft.getProfiler().push("titleAndSubtitle");
            float f = (float)this.titleTime - deltaTracker.getGameTimeDeltaPartialTick(false);
            int i = 255;
            if (this.titleTime > this.titleFadeOutTime + this.titleStayTime) {
                float f1 = (float)(this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime) - f;
                i = (int)(f1 * 255.0F / (float)this.titleFadeInTime);
            }

            if (this.titleTime <= this.titleFadeOutTime) {
                i = (int)(f * 255.0F / (float)this.titleFadeOutTime);
            }

            i = Mth.clamp(i, 0, 255);
            if (i > 8) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate((float)(guiGraphics.guiWidth() / 2), (float)(guiGraphics.guiHeight() / 2), 0.0F);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(4.0F, 4.0F, 4.0F);
                int l = font.width(this.title);
                int j = FastColor.ARGB32.color(i, -1);
                guiGraphics.drawStringWithBackdrop(font, this.title, -l / 2, -10, l, j);
                guiGraphics.pose().popPose();
                if (this.subtitle != null) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
                    int k = font.width(this.subtitle);
                    guiGraphics.drawStringWithBackdrop(font, this.subtitle, -k / 2, 5, k, j);
                    guiGraphics.pose().popPose();
                }

                guiGraphics.pose().popPose();
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private void renderChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!this.chat.isChatFocused()) {
            Window window = this.minecraft.getWindow();
            // Neo: Allow customizing position of chat component
            var chatBottomMargin = 40; // See ChatComponent#BOTTOM_MARGIN (used in translate calls in ChatComponent#render)
            var event = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.Chat(window, guiGraphics, deltaTracker, 0, guiGraphics.guiHeight() - chatBottomMargin)
            );

            // The event is given the absolute Y position; account for chat component's own offsetting here
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(event.getPosX(), (event.getPosY() - guiGraphics.guiHeight() + chatBottomMargin) / this.chat.getScale(), 0.0F);
            int i = Mth.floor(this.minecraft.mouseHandler.xpos() * (double)window.getGuiScaledWidth() / (double)window.getScreenWidth());
            int j = Mth.floor(this.minecraft.mouseHandler.ypos() * (double)window.getGuiScaledHeight() / (double)window.getScreenHeight());
            this.chat.render(guiGraphics, this.tickCount, i, j, false);
            guiGraphics.pose().popPose();
        }
    }

    private void renderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Scoreboard scoreboard = this.minecraft.level.getScoreboard();
        Objective objective = null;
        PlayerTeam playerteam = scoreboard.getPlayersTeam(this.minecraft.player.getScoreboardName());
        if (playerteam != null) {
            DisplaySlot displayslot = DisplaySlot.teamColorToSlot(playerteam.getColor());
            if (displayslot != null) {
                objective = scoreboard.getDisplayObjective(displayslot);
            }
        }

        Objective objective1 = objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective1 != null) {
            this.displayScoreboardSidebar(guiGraphics, objective1);
        }
    }

    private void renderTabList(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Scoreboard scoreboard = this.minecraft.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        if (!this.minecraft.options.keyPlayerList.isDown()
            || this.minecraft.isLocalServer() && this.minecraft.player.connection.getListedOnlinePlayers().size() <= 1 && objective == null) {
            this.tabList.setVisible(false);
        } else {
            this.tabList.setVisible(true);
            this.tabList.render(guiGraphics, guiGraphics.guiWidth(), scoreboard, objective);
        }
    }

    private void renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Options options = this.minecraft.options;
        if (options.getCameraType().isFirstPerson()) {
            if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR || this.canRenderCrosshairForSpectator(this.minecraft.hitResult)) {
                RenderSystem.enableBlend();
                if (this.debugOverlay.showDebugScreen() && !this.minecraft.player.isReducedDebugInfo() && !options.reducedDebugInfo().get()) {
                    Camera camera = this.minecraft.gameRenderer.getMainCamera();
                    Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
                    matrix4fstack.pushMatrix();
                    matrix4fstack.mul(guiGraphics.pose().last().pose());
                    matrix4fstack.translate((float)(guiGraphics.guiWidth() / 2), (float)(guiGraphics.guiHeight() / 2), 0.0F);
                    matrix4fstack.rotateX(-camera.getXRot() * (float) (Math.PI / 180.0));
                    matrix4fstack.rotateY(camera.getYRot() * (float) (Math.PI / 180.0));
                    matrix4fstack.scale(-1.0F, -1.0F, -1.0F);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.renderCrosshair(10);
                    matrix4fstack.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                } else {
                    RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                    );
                    int i = 15;
                    guiGraphics.blitSprite(CROSSHAIR_SPRITE, (guiGraphics.guiWidth() - 15) / 2, (guiGraphics.guiHeight() - 15) / 2, 15, 15);
                    if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
                        float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                        boolean flag = false;
                        if (this.minecraft.crosshairPickEntity != null && this.minecraft.crosshairPickEntity instanceof LivingEntity && f >= 1.0F) {
                            flag = this.minecraft.player.getCurrentItemAttackStrengthDelay() > 5.0F;
                            flag &= this.minecraft.crosshairPickEntity.isAlive();
                        }

                        int j = guiGraphics.guiHeight() / 2 - 7 + 16;
                        int k = guiGraphics.guiWidth() / 2 - 8;
                        if (flag) {
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE, k, j, 16, 16);
                        } else if (f < 1.0F) {
                            int l = (int)(f * 17.0F);
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k, j, 16, 4);
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE, 16, 4, 0, 0, k, j, l, 4);
                        }
                    }

                    RenderSystem.defaultBlendFunc();
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Checks if the crosshair can be rendered for a spectator based on the provided {@link HitResult}.
     * <p>
     * @return {@code true} if the crosshair can be rendered for a spectator, {@code false} otherwise.
     *
     * @param rayTrace the result of a ray trace operation.
     */
    private boolean canRenderCrosshairForSpectator(@Nullable HitResult rayTrace) {
        if (rayTrace == null) {
            return false;
        } else if (rayTrace.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult)rayTrace).getEntity() instanceof MenuProvider;
        } else if (rayTrace.getType() == HitResult.Type.BLOCK) {
            BlockPos blockpos = ((BlockHitResult)rayTrace).getBlockPos();
            Level level = this.minecraft.level;
            return level.getBlockState(blockpos).getMenuProvider(level, blockpos) != null;
        } else {
            return false;
        }
    }

    private void renderEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
        if (!collection.isEmpty()) {
            if (this.minecraft.screen instanceof EffectRenderingInventoryScreen effectrenderinginventoryscreen
                && effectrenderinginventoryscreen.canSeeEffects()) {
                return;
            }

            RenderSystem.enableBlend();
            int j1 = 0;
            int k1 = 0;
            MobEffectTextureManager mobeffecttexturemanager = this.minecraft.getMobEffectTextures();
            List<Runnable> list = Lists.newArrayListWithExpectedSize(collection.size());

            for (MobEffectInstance mobeffectinstance : Ordering.natural().reverse().sortedCopy(collection)) {
                var renderer = net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions.of(mobeffectinstance);
                if (!renderer.isVisibleInGui(mobeffectinstance)) continue;
                Holder<MobEffect> holder = mobeffectinstance.getEffect();
                if (mobeffectinstance.showIcon()) {
                    int i = guiGraphics.guiWidth();
                    int j = 1;
                    if (this.minecraft.isDemo()) {
                        j += 15;
                    }

                    if (holder.value().isBeneficial()) {
                        j1++;
                        i -= 25 * j1;
                    } else {
                        k1++;
                        i -= 25 * k1;
                        j += 26;
                    }

                    float f = 1.0F;
                    if (mobeffectinstance.isAmbient()) {
                        guiGraphics.blitSprite(EFFECT_BACKGROUND_AMBIENT_SPRITE, i, j, 24, 24);
                    } else {
                        guiGraphics.blitSprite(EFFECT_BACKGROUND_SPRITE, i, j, 24, 24);
                        if (mobeffectinstance.endsWithin(200)) {
                            int k = mobeffectinstance.getDuration();
                            int l = 10 - k / 20;
                            f = Mth.clamp((float)k / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                                + Mth.cos((float)k * (float) Math.PI / 5.0F) * Mth.clamp((float)l / 10.0F * 0.25F, 0.0F, 0.25F);
                        }
                    }

                    if (renderer.renderGuiIcon(mobeffectinstance, this, guiGraphics, i, j, 0, f)) continue;
                    TextureAtlasSprite textureatlassprite = mobeffecttexturemanager.get(holder);
                    int l1 = i;
                    int i1 = j;
                    float f1 = f;
                    list.add(() -> {
                        guiGraphics.setColor(1.0F, 1.0F, 1.0F, f1);
                        guiGraphics.blit(l1 + 3, i1 + 3, 0, 18, 18, textureatlassprite);
                        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    });
                }
            }

            list.forEach(Runnable::run);
            RenderSystem.disableBlend();
        }
    }

    @Deprecated // Neo: Split up into different layers
    private void renderHotbarAndDecorations(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        renderHotbar(guiGraphics, deltaTracker);
        maybeRenderJumpMeter(guiGraphics, deltaTracker);
        maybeRenderExperienceBar(guiGraphics, deltaTracker);
        maybeRenderPlayerHealth(guiGraphics, deltaTracker);
        maybeRenderVehicleHealth(guiGraphics, deltaTracker);
        maybeRenderSelectedItemName(guiGraphics, deltaTracker);
        maybeRenderSpectatorTooltip(guiGraphics, deltaTracker);
    }

    private void renderHotbar(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            this.spectatorGui.renderHotbar(p_316628_);
        } else {
            this.renderItemHotbar(p_316628_, p_348543_);
        }
    }

    private void maybeRenderJumpMeter(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        int i = p_316628_.guiWidth() / 2 - 91;
        PlayerRideableJumping playerrideablejumping = this.minecraft.player.jumpableVehicle();
        if (playerrideablejumping != null) {
            this.renderJumpMeter(playerrideablejumping, p_316628_, i);
        }

    }

    private void maybeRenderExperienceBar(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        int i = p_316628_.guiWidth() / 2 - 91;
        if (this.minecraft.player.jumpableVehicle() == null && this.isExperienceBarVisible()) {
            this.renderExperienceBar(p_316628_, i);
        }
    }

    private void maybeRenderPlayerHealth(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        if (this.minecraft.gameMode.canHurtPlayer()) {
            this.renderPlayerHealth(p_316628_);
        }
    }

    private void maybeRenderVehicleHealth(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        this.renderVehicleHealth(p_316628_);
    }

    private void maybeRenderSelectedItemName(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            this.renderSelectedItemName(p_316628_, Math.max(this.leftHeight, this.rightHeight));
        }
    }

    private void maybeRenderSpectatorTooltip(GuiGraphics p_316628_, DeltaTracker p_348543_) {
        if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR && this.minecraft.player.isSpectator()) {
            this.spectatorGui.renderTooltip(p_316628_);
        }
    }

    private void renderItemHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            ItemStack itemstack = player.getOffhandItem();
            HumanoidArm humanoidarm = player.getMainArm().getOpposite();
            int i = guiGraphics.guiWidth() / 2;
            int j = 182;
            int k = 91;
            RenderSystem.enableBlend();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, -90.0F);
            guiGraphics.blitSprite(HOTBAR_SPRITE, i - 91, guiGraphics.guiHeight() - 22, 182, 22);
            guiGraphics.blitSprite(HOTBAR_SELECTION_SPRITE, i - 91 - 1 + player.getInventory().selected * 20, guiGraphics.guiHeight() - 22 - 1, 24, 23);
            if (!itemstack.isEmpty()) {
                if (humanoidarm == HumanoidArm.LEFT) {
                    guiGraphics.blitSprite(HOTBAR_OFFHAND_LEFT_SPRITE, i - 91 - 29, guiGraphics.guiHeight() - 23, 29, 24);
                } else {
                    guiGraphics.blitSprite(HOTBAR_OFFHAND_RIGHT_SPRITE, i + 91, guiGraphics.guiHeight() - 23, 29, 24);
                }
            }

            guiGraphics.pose().popPose();
            RenderSystem.disableBlend();
            int l = 1;

            for (int i1 = 0; i1 < 9; i1++) {
                int j1 = i - 90 + i1 * 20 + 2;
                int k1 = guiGraphics.guiHeight() - 16 - 3;
                this.renderSlot(guiGraphics, j1, k1, deltaTracker, player, player.getInventory().items.get(i1), l++);
            }

            if (!itemstack.isEmpty()) {
                int i2 = guiGraphics.guiHeight() - 16 - 3;
                if (humanoidarm == HumanoidArm.LEFT) {
                    this.renderSlot(guiGraphics, i - 91 - 26, i2, deltaTracker, player, itemstack, l++);
                } else {
                    this.renderSlot(guiGraphics, i + 91 + 10, i2, deltaTracker, player, itemstack, l++);
                }
            }

            if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                RenderSystem.enableBlend();
                float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                if (f < 1.0F) {
                    int j2 = guiGraphics.guiHeight() - 20;
                    int k2 = i + 91 + 6;
                    if (humanoidarm == HumanoidArm.RIGHT) {
                        k2 = i - 91 - 22;
                    }

                    int l1 = (int)(f * 19.0F);
                    guiGraphics.blitSprite(HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k2, j2, 18, 18);
                    guiGraphics.blitSprite(HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - l1, k2, j2 + 18 - l1, 18, l1);
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Renders the jump meter for a rideable entity on the screen using the provided rideable object, GuiGraphics object, and x-coordinate.
     *
     * @param rideable    the PlayerRideableJumping object representing the rideable
     *                    entity.
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param x           the x-coordinate for rendering the jump meter.
     */
    private void renderJumpMeter(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x) {
        this.minecraft.getProfiler().push("jumpBar");
        float f = this.minecraft.player.getJumpRidingScale();
        int i = 182;
        int j = (int)(f * 183.0F);
        int k = guiGraphics.guiHeight() - 32 + 3;
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(JUMP_BAR_BACKGROUND_SPRITE, x, k, 182, 5);
        if (rideable.getJumpCooldown() > 0) {
            guiGraphics.blitSprite(JUMP_BAR_COOLDOWN_SPRITE, x, k, 182, 5);
        } else if (j > 0) {
            guiGraphics.blitSprite(JUMP_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, k, j, 5);
        }

        RenderSystem.disableBlend();
        this.minecraft.getProfiler().pop();
    }

    /**
     * Renders the experience bar on the screen using the provided GuiGraphics object and x-coordinate.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param x           the x-coordinate for rendering the experience bar.
     */
    private void renderExperienceBar(GuiGraphics guiGraphics, int x) {
        this.minecraft.getProfiler().push("expBar");
        int i = this.minecraft.player.getXpNeededForNextLevel();
        if (i > 0) {
            int j = 182;
            int k = (int)(this.minecraft.player.experienceProgress * 183.0F);
            int l = guiGraphics.guiHeight() - 32 + 3;
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(EXPERIENCE_BAR_BACKGROUND_SPRITE, x, l, 182, 5);
            if (k > 0) {
                guiGraphics.blitSprite(EXPERIENCE_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, l, k, 5);
            }

            RenderSystem.disableBlend();
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int i = this.minecraft.player.experienceLevel;
        if (this.isExperienceBarVisible() && i > 0) {
            this.minecraft.getProfiler().push("expLevel");
            String s = i + "";
            int j = (guiGraphics.guiWidth() - this.getFont().width(s)) / 2;
            int k = guiGraphics.guiHeight() - 31 - 4;
            guiGraphics.drawString(this.getFont(), s, j + 1, k, 0, false);
            guiGraphics.drawString(this.getFont(), s, j - 1, k, 0, false);
            guiGraphics.drawString(this.getFont(), s, j, k + 1, 0, false);
            guiGraphics.drawString(this.getFont(), s, j, k - 1, 0, false);
            guiGraphics.drawString(this.getFont(), s, j, k, 8453920, false);
            this.minecraft.getProfiler().pop();
        }
    }

    private boolean isExperienceBarVisible() {
        return this.minecraft.player.jumpableVehicle() == null && this.minecraft.gameMode.hasExperience();
    }

    /**
     * Renders the name of the selected item on the screen using the provided GuiGraphics object.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     */
    private void renderSelectedItemName(GuiGraphics guiGraphics) {
        renderSelectedItemName(guiGraphics, 0);
    }

    /**
     * Renders the name of the selected item on the screen using the provided GuiGraphics object.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     */
    public void renderSelectedItemName(GuiGraphics guiGraphics, int yShift) {
        this.minecraft.getProfiler().push("selectedItemName");
        if (this.toolHighlightTimer > 0 && !this.lastToolHighlight.isEmpty()) {
            MutableComponent mutablecomponent = Component.empty()
                .append(this.lastToolHighlight.getHoverName())
                .withStyle(this.lastToolHighlight.getRarity().getStyleModifier());
            if (this.lastToolHighlight.has(DataComponents.CUSTOM_NAME)) {
                mutablecomponent.withStyle(ChatFormatting.ITALIC);
            }

            Component highlightTip = this.lastToolHighlight.getHighlightTip(mutablecomponent);
            int i = this.getFont().width(highlightTip);
            int j = (guiGraphics.guiWidth() - i) / 2;
            int k = guiGraphics.guiHeight() - Math.max(yShift, 59);
            if (!this.minecraft.gameMode.canHurtPlayer()) {
                k += 14;
            }

            int l = (int)((float)this.toolHighlightTimer * 256.0F / 10.0F);
            if (l > 255) {
                l = 255;
            }

            if (l > 0) {
                Font font = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(lastToolHighlight).getFont(lastToolHighlight, net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext.SELECTED_ITEM_NAME);
                if (font == null) {
                    guiGraphics.drawStringWithBackdrop(this.getFont(), highlightTip, j, k, i, FastColor.ARGB32.color(l, -1));
                } else {
                    j = (guiGraphics.guiWidth() - font.width(highlightTip)) / 2;
                    guiGraphics.drawStringWithBackdrop(font, highlightTip, j, k, i, FastColor.ARGB32.color(l, -1));
                }
            }
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderDemoOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.isDemo() && !this.getDebugOverlay().showDebugScreen()) { // Neo: Hide demo timer when F3 debug overlay is open; fixes MC-271166
            this.minecraft.getProfiler().push("demo");
            Component component;
            if (this.minecraft.level.getGameTime() >= 120500L) {
                component = DEMO_EXPIRED_TEXT;
            } else {
                component = Component.translatable(
                    "demo.remainingTime",
                    StringUtil.formatTickDuration((int)(120500L - this.minecraft.level.getGameTime()), this.minecraft.level.tickRateManager().tickrate())
                );
            }

            int i = this.getFont().width(component);
            int j = guiGraphics.guiWidth() - i - 10;
            int k = 5;
            guiGraphics.drawStringWithBackdrop(this.getFont(), component, j, 5, i, -1);
            this.minecraft.getProfiler().pop();
        }
    }

    /**
     * Displays the scoreboard sidebar on the screen using the provided GuiGraphics object and objective.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param objective   the objective representing the scoreboard sidebar.
     */
    private void displayScoreboardSidebar(GuiGraphics guiGraphics, Objective objective) {
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberformat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        @OnlyIn(Dist.CLIENT)
        record DisplayEntry(Component name, Component score, int scoreWidth) {
        }

        DisplayEntry[] agui$1displayentry = scoreboard.listPlayerScores(objective)
            .stream()
            .filter(p_313419_ -> !p_313419_.isHidden())
            .sorted(SCORE_DISPLAY_ORDER)
            .limit(15L)
            .map(p_313418_ -> {
                PlayerTeam playerteam = scoreboard.getPlayersTeam(p_313418_.owner());
                Component component1 = p_313418_.ownerName();
                Component component2 = PlayerTeam.formatNameForTeam(playerteam, component1);
                Component component3 = p_313418_.formatValue(numberformat);
                int i1 = this.getFont().width(component3);
                return new DisplayEntry(component2, component3, i1);
            })
            .toArray(DisplayEntry[]::new);
        Component component = objective.getDisplayName();
        int i = this.getFont().width(component);
        int j = i;
        int k = this.getFont().width(": ");

        for (DisplayEntry gui$1displayentry : agui$1displayentry) {
            j = Math.max(j, this.getFont().width(gui$1displayentry.name) + (gui$1displayentry.scoreWidth > 0 ? k + gui$1displayentry.scoreWidth : 0));
        }

        int l = j;
        guiGraphics.drawManaged(() -> {
            int i1 = agui$1displayentry.length;
            int j1 = i1 * 9;
            int k1 = guiGraphics.guiHeight() / 2 + j1 / 3;
            int l1 = 3;
            int i2 = guiGraphics.guiWidth() - l - 3;
            int j2 = guiGraphics.guiWidth() - 3 + 2;
            int k2 = this.minecraft.options.getBackgroundColor(0.3F);
            int l2 = this.minecraft.options.getBackgroundColor(0.4F);
            int i3 = k1 - i1 * 9;
            guiGraphics.fill(i2 - 2, i3 - 9 - 1, j2, i3 - 1, l2);
            guiGraphics.fill(i2 - 2, i3 - 1, j2, k1, k2);
            guiGraphics.drawString(this.getFont(), component, i2 + l / 2 - i / 2, i3 - 9, -1, false);

            for (int j3 = 0; j3 < i1; j3++) {
                DisplayEntry gui$1displayentry1 = agui$1displayentry[j3];
                int k3 = k1 - (i1 - j3) * 9;
                guiGraphics.drawString(this.getFont(), gui$1displayentry1.name, i2, k3, -1, false);
                guiGraphics.drawString(this.getFont(), gui$1displayentry1.score, j2 - gui$1displayentry1.scoreWidth, k3, -1, false);
            }
        });
    }

    @Nullable
    private Player getCameraPlayer() {
        return this.minecraft.getCameraEntity() instanceof Player player ? player : null;
    }

    @Nullable
    private LivingEntity getPlayerVehicleWithHealth() {
        Player player = this.getCameraPlayer();
        if (player != null) {
            Entity entity = player.getVehicle();
            if (entity == null) {
                return null;
            }

            if (entity instanceof LivingEntity) {
                return (LivingEntity)entity;
            }
        }

        return null;
    }

    /**
     * Retrieves the maximum number of hearts representing the vehicle's health for the given mount entity.
     * <p>
     * @return the maximum number of hearts representing the vehicle's health, or 0 if the mount entity is null or does not show vehicle health.
     *
     * @param vehicle the living entity representing the vehicle.
     */
    private int getVehicleMaxHearts(@Nullable LivingEntity vehicle) {
        if (vehicle != null && vehicle.showVehicleHealth()) {
            float f = vehicle.getMaxHealth();
            int i = (int)(f + 0.5F) / 2;
            if (i > 30) {
                i = 30;
            }

            return i;
        } else {
            return 0;
        }
    }

    /**
     * Retrieves the number of rows of visible hearts needed to represent the given mount health.
     * <p>
     * @return the number of rows of visible hearts needed to represent the mount health.
     *
     * @param vehicleHealth the health of the mount entity.
     */
    private int getVisibleVehicleHeartRows(int vehicleHealth) {
        return (int)Math.ceil((double)vehicleHealth / 10.0);
    }

    /**
     * Renders the player's health, armor, food, and air bars on the screen.
     *
     * @param guiGraphics the graphics object used for rendering.
     */
    @Deprecated // Neo: Split up into different layers
    private void renderPlayerHealth(GuiGraphics guiGraphics) {
        renderHealthLevel(guiGraphics);
        renderArmorLevel(guiGraphics);
        renderFoodLevel(guiGraphics);
        renderAirLevel(guiGraphics);
    }

    private void renderHealthLevel(GuiGraphics p_283143_) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            int i = Mth.ceil(player.getHealth());
            boolean flag = this.healthBlinkTime > (long)this.tickCount && (this.healthBlinkTime - (long)this.tickCount) / 3L % 2L == 1L;
            long j = Util.getMillis();
            if (i < this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = j;
                this.healthBlinkTime = (long)(this.tickCount + 20);
            } else if (i > this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = j;
                this.healthBlinkTime = (long)(this.tickCount + 10);
            }

            if (j - this.lastHealthTime > 1000L) {
                this.lastHealth = i;
                this.displayHealth = i;
                this.lastHealthTime = j;
            }

            this.lastHealth = i;
            int k = this.displayHealth;
            this.random.setSeed((long)(this.tickCount * 312871));
            int l = p_283143_.guiWidth() / 2 - 91;
            int i1 = p_283143_.guiWidth() / 2 + 91;
            int j1 = p_283143_.guiHeight() - leftHeight;
            float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), (float)Math.max(k, i));
            int k1 = Mth.ceil(player.getAbsorptionAmount());
            int l1 = Mth.ceil((f + (float)k1) / 2.0F / 10.0F);
            int i2 = Math.max(10 - (l1 - 2), 3);
            int j2 = j1 - 10;
            leftHeight += (l1 - 1) * i2 + 10;
            int k2 = -1;
            if (player.hasEffect(MobEffects.REGENERATION)) {
                k2 = this.tickCount % Mth.ceil(f + 5.0F);
            }
            this.minecraft.getProfiler().push("health");
            this.renderHearts(p_283143_, player, l, j1, i2, k2, f, i, k, k1, flag);
            this.minecraft.getProfiler().pop();
        }
    }

    private void renderArmorLevel(GuiGraphics p_283143_) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            int l = p_283143_.guiWidth() / 2 - 91;
            this.minecraft.getProfiler().push("armor");
            renderArmor(p_283143_, player, p_283143_.guiHeight() - leftHeight + 10, 1, 0, l);
            this.minecraft.getProfiler().pop();
            if (player.getArmorValue() > 0) {
                leftHeight += 10;
            }
        }
    }

    private void renderFoodLevel(GuiGraphics p_283143_) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            LivingEntity livingentity = this.getPlayerVehicleWithHealth();
            int l2 = this.getVehicleMaxHearts(livingentity);
            if (l2 == 0) {
                this.minecraft.getProfiler().push("food");
                int i1 = p_283143_.guiWidth() / 2 + 91;
                int j1 = p_283143_.guiHeight() - rightHeight;
                this.renderFood(p_283143_, player, j1, i1);
                rightHeight += 10;
                this.minecraft.getProfiler().pop();
            }
        }
    }

    private void renderAirLevel(GuiGraphics p_283143_) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            int i1 = p_283143_.guiWidth() / 2 + 91;

            this.minecraft.getProfiler().push("air");
            int i3 = player.getMaxAirSupply();
            int j3 = Math.min(player.getAirSupply(), i3);
            if (player.isEyeInFluid(FluidTags.WATER) || j3 < i3) {
                int j2 = p_283143_.guiHeight() - rightHeight;
                int l3 = Mth.ceil((double)(j3 - 2) * 10.0 / (double)i3);
                int i4 = Mth.ceil((double)j3 * 10.0 / (double)i3) - l3;
                RenderSystem.enableBlend();

                for (int j4 = 0; j4 < l3 + i4; j4++) {
                    if (j4 < l3) {
                        p_283143_.blitSprite(AIR_SPRITE, i1 - j4 * 8 - 9, j2, 9, 9);
                    } else {
                        p_283143_.blitSprite(AIR_BURSTING_SPRITE, i1 - j4 * 8 - 9, j2, 9, 9);
                    }
                }

                RenderSystem.disableBlend();
                rightHeight += 10;
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private static void renderArmor(GuiGraphics guiGraphics, Player player, int y, int heartRows, int height, int x) {
        int i = player.getArmorValue();
        if (i > 0) {
            RenderSystem.enableBlend();
            int j = y - (heartRows - 1) * height - 10;

            for (int k = 0; k < 10; k++) {
                int l = x + k * 8;
                if (k * 2 + 1 < i) {
                    guiGraphics.blitSprite(ARMOR_FULL_SPRITE, l, j, 9, 9);
                }

                if (k * 2 + 1 == i) {
                    guiGraphics.blitSprite(ARMOR_HALF_SPRITE, l, j, 9, 9);
                }

                if (k * 2 + 1 > i) {
                    guiGraphics.blitSprite(ARMOR_EMPTY_SPRITE, l, j, 9, 9);
                }
            }

            RenderSystem.disableBlend();
        }
    }

    /**
     * Renders the player's hearts, including health, absorption, and highlight hearts, on the screen.
     *
     * @param guiGraphics      the graphics object used for rendering.
     * @param player           the player entity.
     * @param x                the x-coordinate of the hearts' position.
     * @param y                the y-coordinate of the hearts' position.
     * @param height           the height of each heart.
     * @param offsetHeartIndex the index of the offset heart.
     * @param maxHealth        the maximum health of the player.
     * @param currentHealth    the current health of the player.
     * @param displayHealth    the displayed health of the player.
     * @param absorptionAmount the absorption amount of the player.
     * @param renderHighlight  determines whether to render the highlight hearts.
     */
    private void renderHearts(
        GuiGraphics guiGraphics,
        Player player,
        int x,
        int y,
        int height,
        int offsetHeartIndex,
        float maxHealth,
        int currentHealth,
        int displayHealth,
        int absorptionAmount,
        boolean renderHighlight
    ) {
        Gui.HeartType gui$hearttype = Gui.HeartType.forPlayer(player);
        boolean flag = player.level().getLevelData().isHardcore();
        int i = Mth.ceil((double)maxHealth / 2.0);
        int j = Mth.ceil((double)absorptionAmount / 2.0);
        int k = i * 2;

        for (int l = i + j - 1; l >= 0; l--) {
            int i1 = l / 10;
            int j1 = l % 10;
            int k1 = x + j1 * 8;
            int l1 = y - i1 * height;
            if (currentHealth + absorptionAmount <= 4) {
                l1 += this.random.nextInt(2);
            }

            if (l < i && l == offsetHeartIndex) {
                l1 -= 2;
            }

            this.renderHeart(guiGraphics, Gui.HeartType.CONTAINER, k1, l1, flag, renderHighlight, false);
            int i2 = l * 2;
            boolean flag1 = l >= i;
            if (flag1) {
                int j2 = i2 - k;
                if (j2 < absorptionAmount) {
                    boolean flag2 = j2 + 1 == absorptionAmount;
                    this.renderHeart(guiGraphics, gui$hearttype == Gui.HeartType.WITHERED ? gui$hearttype : Gui.HeartType.ABSORBING, k1, l1, flag, false, flag2);
                }
            }

            if (renderHighlight && i2 < displayHealth) {
                boolean flag3 = i2 + 1 == displayHealth;
                this.renderHeart(guiGraphics, gui$hearttype, k1, l1, flag, true, flag3);
            }

            if (i2 < currentHealth) {
                boolean flag4 = i2 + 1 == currentHealth;
                this.renderHeart(guiGraphics, gui$hearttype, k1, l1, flag, false, flag4);
            }
        }
    }

    private void renderHeart(
        GuiGraphics guiGraphics, Gui.HeartType heartType, int x, int y, boolean hardcore, boolean halfHeart, boolean blinking
    ) {
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(heartType.getSprite(hardcore, blinking, halfHeart), x, y, 9, 9);
        RenderSystem.disableBlend();
    }

    private void renderFood(GuiGraphics guiGraphics, Player player, int y, int x) {
        FoodData fooddata = player.getFoodData();
        int i = fooddata.getFoodLevel();
        RenderSystem.enableBlend();

        for (int j = 0; j < 10; j++) {
            int k = y;
            ResourceLocation resourcelocation;
            ResourceLocation resourcelocation1;
            ResourceLocation resourcelocation2;
            if (player.hasEffect(MobEffects.HUNGER)) {
                resourcelocation = FOOD_EMPTY_HUNGER_SPRITE;
                resourcelocation1 = FOOD_HALF_HUNGER_SPRITE;
                resourcelocation2 = FOOD_FULL_HUNGER_SPRITE;
            } else {
                resourcelocation = FOOD_EMPTY_SPRITE;
                resourcelocation1 = FOOD_HALF_SPRITE;
                resourcelocation2 = FOOD_FULL_SPRITE;
            }

            if (player.getFoodData().getSaturationLevel() <= 0.0F && this.tickCount % (i * 3 + 1) == 0) {
                k = y + (this.random.nextInt(3) - 1);
            }

            int l = x - j * 8 - 9;
            guiGraphics.blitSprite(resourcelocation, l, k, 9, 9);
            if (j * 2 + 1 < i) {
                guiGraphics.blitSprite(resourcelocation2, l, k, 9, 9);
            }

            if (j * 2 + 1 == i) {
                guiGraphics.blitSprite(resourcelocation1, l, k, 9, 9);
            }
        }

        RenderSystem.disableBlend();
    }

    /**
     * Renders the health of the player's vehicle on the screen.
     *
     * @param guiGraphics the graphics object used for rendering.
     */
    private void renderVehicleHealth(GuiGraphics guiGraphics) {
        LivingEntity livingentity = this.getPlayerVehicleWithHealth();
        if (livingentity != null) {
            int i = this.getVehicleMaxHearts(livingentity);
            if (i != 0) {
                int j = (int)Math.ceil((double)livingentity.getHealth());
                this.minecraft.getProfiler().popPush("mountHealth");
                int k = guiGraphics.guiHeight() - rightHeight;
                int l = guiGraphics.guiWidth() / 2 + 91;
                int i1 = k;
                int j1 = 0;
                RenderSystem.enableBlend();

                while (i > 0) {
                    int k1 = Math.min(i, 10);
                    i -= k1;

                    for (int l1 = 0; l1 < k1; l1++) {
                        int i2 = l - l1 * 8 - 9;
                        guiGraphics.blitSprite(HEART_VEHICLE_CONTAINER_SPRITE, i2, i1, 9, 9);
                        if (l1 * 2 + 1 + j1 < j) {
                            guiGraphics.blitSprite(HEART_VEHICLE_FULL_SPRITE, i2, i1, 9, 9);
                        }

                        if (l1 * 2 + 1 + j1 == j) {
                            guiGraphics.blitSprite(HEART_VEHICLE_HALF_SPRITE, i2, i1, 9, 9);
                        }
                    }

                    i1 -= 10;
                    rightHeight += 10;
                    j1 += 20;
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Renders a texture overlay on the screen with the specified shader location and alpha value.
     *
     * @param guiGraphics    the graphics object used for rendering.
     * @param shaderLocation the location of the shader texture.
     * @param alpha          the alpha value to apply to the overlay.
     */
    private void renderTextureOverlay(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(shaderLocation, 0, 0, -90, 0.0F, 0.0F, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Renders the overlay for the spyglass effect.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param scopeScale  the scale factor for the spyglass scope.
     */
    private void renderSpyglassOverlay(GuiGraphics guiGraphics, float scopeScale) {
        float f = (float)Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float f1 = Math.min((float)guiGraphics.guiWidth() / f, (float)guiGraphics.guiHeight() / f) * scopeScale;
        int i = Mth.floor(f * f1);
        int j = Mth.floor(f * f1);
        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - j) / 2;
        int i1 = k + i;
        int j1 = l + j;
        ItemStack useItem = this.minecraft.player.getUseItem();
        var clientItemExtensions = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(useItem);
        RenderSystem.enableBlend();
        guiGraphics.blit(clientItemExtensions.getScopeOverlayTexture(useItem), k, l, -90, 0.0F, 0.0F, i, j, i, j);
        RenderSystem.disableBlend();
        guiGraphics.fill(RenderType.guiOverlay(), 0, j1, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), l, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, l, k, j1, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), i1, l, guiGraphics.guiWidth(), j1, -90, -16777216);
    }

    /**
     * Updates the brightness of the vignette effect based on the brightness of the given entity's position.
     *
     * @param entity the entity used to determine the brightness.
     */
    private void updateVignetteBrightness(Entity entity) {
        BlockPos blockpos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        float f = LightTexture.getBrightness(entity.level().dimensionType(), entity.level().getMaxLocalRawBrightness(blockpos));
        float f1 = Mth.clamp(1.0F - f, 0.0F, 1.0F);
        this.vignetteBrightness = this.vignetteBrightness + (f1 - this.vignetteBrightness) * 0.01F;
    }

    /**
     * Renders the vignette effect on the screen based on the distance to the world border and the entity's position.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param entity      the entity used to determine the distance to the world
     *                    border.
     */
    private void renderVignette(GuiGraphics guiGraphics, @Nullable Entity entity) {
        WorldBorder worldborder = this.minecraft.level.getWorldBorder();
        float f = 0.0F;
        if (entity != null) {
            float f1 = (float)worldborder.getDistanceToBorder(entity);
            double d0 = Math.min(
                worldborder.getLerpSpeed() * (double)worldborder.getWarningTime() * 1000.0, Math.abs(worldborder.getLerpTarget() - worldborder.getSize())
            );
            double d1 = Math.max((double)worldborder.getWarningBlocks(), d0);
            if ((double)f1 < d1) {
                f = 1.0F - (float)((double)f1 / d1);
            }
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        if (f > 0.0F) {
            f = Mth.clamp(f, 0.0F, 1.0F);
            guiGraphics.setColor(0.0F, f, f, 1.0F);
        } else {
            float f2 = this.vignetteBrightness;
            f2 = Mth.clamp(f2, 0.0F, 1.0F);
            guiGraphics.setColor(f2, f2, f2, 1.0F);
        }

        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Renders the portal overlay effect on the screen with the specified alpha value.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param alpha       the alpha value of the overlay.
     */
    private void renderPortalOverlay(GuiGraphics guiGraphics, float alpha) {
        if (alpha < 1.0F) {
            alpha *= alpha;
            alpha *= alpha;
            alpha = alpha * 0.8F + 0.2F;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        TextureAtlasSprite textureatlassprite = this.minecraft
            .getBlockRenderer()
            .getBlockModelShaper()
            .getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
        guiGraphics.blit(0, 0, -90, guiGraphics.guiWidth(), guiGraphics.guiHeight(), textureatlassprite);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderSlot(GuiGraphics guiGraphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed) {
        if (!stack.isEmpty()) {
            float f = (float)stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
            if (f > 0.0F) {
                float f1 = 1.0F + f / 5.0F;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate((float)(x + 8), (float)(y + 12), 0.0F);
                guiGraphics.pose().scale(1.0F / f1, (f1 + 1.0F) / 2.0F, 1.0F);
                guiGraphics.pose().translate((float)(-(x + 8)), (float)(-(y + 12)), 0.0F);
            }

            guiGraphics.renderItem(player, stack, x, y, seed);
            if (f > 0.0F) {
                guiGraphics.pose().popPose();
            }

            guiGraphics.renderItemDecorations(this.minecraft.font, stack, x, y);
        }
    }

    /**
     * Advances the tick for the autosave indicator and optionally ticks the object if not paused.
     */
    public void tick(boolean pause) {
        this.tickAutosaveIndicator();
        if (!pause) {
            this.tick();
        }
    }

    private void tick() {
        if (this.overlayMessageTime > 0) {
            this.overlayMessageTime--;
        }

        if (this.titleTime > 0) {
            this.titleTime--;
            if (this.titleTime <= 0) {
                this.title = null;
                this.subtitle = null;
            }
        }

        this.tickCount++;
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
            this.updateVignetteBrightness(entity);
        }

        if (this.minecraft.player != null) {
            ItemStack itemstack = this.minecraft.player.getInventory().getSelected();
            if (itemstack.isEmpty()) {
                this.toolHighlightTimer = 0;
            } else if (this.lastToolHighlight.isEmpty()
                || !itemstack.is(this.lastToolHighlight.getItem())
                || (!itemstack.getHoverName().equals(this.lastToolHighlight.getHoverName()) || !itemstack.getHighlightTip(itemstack.getHoverName()).equals(this.lastToolHighlight.getHighlightTip(this.lastToolHighlight.getHoverName())))) {
                this.toolHighlightTimer = (int)(40.0 * this.minecraft.options.notificationDisplayTime().get());
            } else if (this.toolHighlightTimer > 0) {
                this.toolHighlightTimer--;
            }

            this.lastToolHighlight = itemstack;
        }

        this.chat.tick();
    }

    private void tickAutosaveIndicator() {
        MinecraftServer minecraftserver = this.minecraft.getSingleplayerServer();
        boolean flag = minecraftserver != null && minecraftserver.isCurrentlySaving();
        this.lastAutosaveIndicatorValue = this.autosaveIndicatorValue;
        this.autosaveIndicatorValue = Mth.lerp(0.2F, this.autosaveIndicatorValue, flag ? 1.0F : 0.0F);
    }

    /**
     * Sets the currently playing record display name and updates the overlay message.
     *
     * @param displayName the display name of the currently playing record.
     */
    public void setNowPlaying(Component displayName) {
        Component component = Component.translatable("record.nowPlaying", displayName);
        this.setOverlayMessage(component, true);
        this.minecraft.getNarrator().sayNow(component);
    }

    /**
     * Sets the overlay message to be displayed on the screen.
     *
     * @param component    the {@link Component} representing the overlay message.
     * @param animateColor a boolean indicating whether to animate the color of the
     *                     overlay message.
     */
    public void setOverlayMessage(Component component, boolean animateColor) {
        this.setChatDisabledByPlayerShown(false);
        this.overlayMessageString = component;
        this.overlayMessageTime = 60;
        this.animateOverlayMessageColor = animateColor;
    }

    /**
     * {@return {@code true} if the chat is disabled, {@code false} if chat is enabled}
     */
    public void setChatDisabledByPlayerShown(boolean chatDisabledByPlayerShown) {
        this.chatDisabledByPlayerShown = chatDisabledByPlayerShown;
    }

    public boolean isShowingChatDisabledByPlayer() {
        return this.chatDisabledByPlayerShown && this.overlayMessageTime > 0;
    }

    /**
     * Sets the fade-in, stay, and fade-out times for the title display.
     *
     * @param titleFadeInTime  the fade-in time for the title message in ticks.
     * @param titleStayTime    the stay time for the title message in ticks.
     * @param titleFadeOutTime the fade-out time for the title message in ticks.
     */
    public void setTimes(int titleFadeInTime, int titleStayTime, int titleFadeOutTime) {
        if (titleFadeInTime >= 0) {
            this.titleFadeInTime = titleFadeInTime;
        }

        if (titleStayTime >= 0) {
            this.titleStayTime = titleStayTime;
        }

        if (titleFadeOutTime >= 0) {
            this.titleFadeOutTime = titleFadeOutTime;
        }

        if (this.titleTime > 0) {
            this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
        }
    }

    /**
     * Sets the subtitle to be displayed in the title screen.
     *
     * @param subtitle the subtitle {@link Component} to be displayed.
     */
    public void setSubtitle(Component subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Sets the title to be displayed in the title screen.
     *
     * @param title the title {@link Component} to be displayed.
     */
    public void setTitle(Component title) {
        this.title = title;
        this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
    }

    public void clear() {
        this.title = null;
        this.subtitle = null;
        this.titleTime = 0;
    }

    public ChatComponent getChat() {
        return this.chat;
    }

    public int getGuiTicks() {
        return this.tickCount;
    }

    public Font getFont() {
        return this.minecraft.font;
    }

    public SpectatorGui getSpectatorGui() {
        return this.spectatorGui;
    }

    public PlayerTabOverlay getTabList() {
        return this.tabList;
    }

    public void onDisconnected() {
        this.tabList.reset();
        this.bossOverlay.reset();
        this.minecraft.getToasts().clear();
        this.debugOverlay.reset();
        this.chat.clearMessages(true);
    }

    public BossHealthOverlay getBossOverlay() {
        return this.bossOverlay;
    }

    public DebugScreenOverlay getDebugOverlay() {
        return this.debugOverlay;
    }

    public void clearCache() {
        this.debugOverlay.clearChunkCache();
    }

    public void renderSavingIndicator(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.options.showAutosaveIndicator().get() && (this.autosaveIndicatorValue > 0.0F || this.lastAutosaveIndicatorValue > 0.0F)) {
            int i = Mth.floor(
                255.0F * Mth.clamp(Mth.lerp(deltaTracker.getRealtimeDeltaTicks(), this.lastAutosaveIndicatorValue, this.autosaveIndicatorValue), 0.0F, 1.0F)
            );
            if (i > 8) {
                Font font = this.getFont();
                int j = font.width(SAVING_TEXT);
                int k = FastColor.ARGB32.color(i, -1);
                int l = guiGraphics.guiWidth() - j - 2;
                int i1 = guiGraphics.guiHeight() - 35;
                guiGraphics.drawStringWithBackdrop(font, SAVING_TEXT, l, i1, j, k);
            }
        }
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void initModdedOverlays() {
        this.layerManager.initModdedLayers();
    }

    public int getLayerCount() {
        return this.layerManager.getLayerCount();
    }

    @OnlyIn(Dist.CLIENT)
    public static enum HeartType implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
        CONTAINER(
            ResourceLocation.withDefaultNamespace("hud/heart/container"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore_blinking")
        ),
        NORMAL(
            ResourceLocation.withDefaultNamespace("hud/heart/full"),
            ResourceLocation.withDefaultNamespace("hud/heart/full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/half"),
            ResourceLocation.withDefaultNamespace("hud/heart/half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_half_blinking")
        ),
        POISIONED(
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_half_blinking")
        ),
        WITHERED(
            ResourceLocation.withDefaultNamespace("hud/heart/withered_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_half_blinking")
        ),
        ABSORBING(
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half_blinking")
        ),
        FROZEN(
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_half_blinking")
        );

        private final ResourceLocation full;
        private final ResourceLocation fullBlinking;
        private final ResourceLocation half;
        private final ResourceLocation halfBlinking;
        private final ResourceLocation hardcoreFull;
        private final ResourceLocation hardcoreFullBlinking;
        private final ResourceLocation hardcoreHalf;
        private final ResourceLocation hardcoreHalfBlinking;

        private HeartType(
            ResourceLocation full,
            ResourceLocation fullBlinking,
            ResourceLocation half,
            ResourceLocation halfBlinking,
            ResourceLocation hardcoreFull,
            ResourceLocation hardcoreBlinking,
            ResourceLocation hardcoreHalf,
            ResourceLocation hardcoreHalfBlinking
        ) {
            this.full = full;
            this.fullBlinking = fullBlinking;
            this.half = half;
            this.halfBlinking = halfBlinking;
            this.hardcoreFull = hardcoreFull;
            this.hardcoreFullBlinking = hardcoreBlinking;
            this.hardcoreHalf = hardcoreHalf;
            this.hardcoreHalfBlinking = hardcoreHalfBlinking;
        }

        public ResourceLocation getSprite(boolean hardcore, boolean halfHeart, boolean blinking) {
            if (!hardcore) {
                if (halfHeart) {
                    return blinking ? this.halfBlinking : this.half;
                } else {
                    return blinking ? this.fullBlinking : this.full;
                }
            } else if (halfHeart) {
                return blinking ? this.hardcoreHalfBlinking : this.hardcoreHalf;
            } else {
                return blinking ? this.hardcoreFullBlinking : this.hardcoreFull;
            }
        }

        /**
         * Returns the {@link HeartType} based on the player's status effects.
         * <p>
         * @return the {@link HeartType} based on the player's status effects.
         *
         * @param player the player for which to determine the HeartType.
         */
        static Gui.HeartType forPlayer(Player player) {
            Gui.HeartType gui$hearttype;
            if (player.hasEffect(MobEffects.POISON)) {
                gui$hearttype = POISIONED;
            } else if (player.hasEffect(MobEffects.WITHER)) {
                gui$hearttype = WITHERED;
            } else if (player.isFullyFrozen()) {
                gui$hearttype = FROZEN;
            } else {
                gui$hearttype = NORMAL;
            }
            gui$hearttype = net.neoforged.neoforge.event.EventHooks.firePlayerHeartTypeEvent(player, gui$hearttype);

            return gui$hearttype;
        }

        public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
            return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(Gui.HeartType.class);
        }
    }
}
