package net.minecraft.client.gui.components;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BossHealthOverlay {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final ResourceLocation[] BAR_BACKGROUND_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("boss_bar/pink_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/blue_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/red_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/green_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/yellow_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/purple_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/white_background")
    };
    private static final ResourceLocation[] BAR_PROGRESS_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("boss_bar/pink_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/blue_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/red_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/green_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/yellow_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/purple_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/white_progress")
    };
    private static final ResourceLocation[] OVERLAY_BACKGROUND_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("boss_bar/notched_6_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_10_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_12_background"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_20_background")
    };
    private static final ResourceLocation[] OVERLAY_PROGRESS_SPRITES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("boss_bar/notched_6_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_10_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_12_progress"),
        ResourceLocation.withDefaultNamespace("boss_bar/notched_20_progress")
    };
    private final Minecraft minecraft;
    final Map<UUID, LerpingBossEvent> events = Maps.newLinkedHashMap();

    public BossHealthOverlay(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(GuiGraphics guiGraphics) {
        if (!this.events.isEmpty()) {
            this.minecraft.getProfiler().push("bossHealth");
            int i = guiGraphics.guiWidth();
            int j = 12;

            for (LerpingBossEvent lerpingbossevent : this.events.values()) {
                int k = i / 2 - 91;
                var event = net.neoforged.neoforge.client.ClientHooks.onCustomizeBossEventProgress(guiGraphics, this.minecraft.getWindow(), lerpingbossevent, k, j, 10 + this.minecraft.font.lineHeight);
                if (!event.isCanceled()) {
                this.drawBar(guiGraphics, k, j, lerpingbossevent);
                Component component = lerpingbossevent.getName();
                int l = this.minecraft.font.width(component);
                int i1 = i / 2 - l / 2;
                int j1 = j - 9;
                guiGraphics.drawString(this.minecraft.font, component, i1, j1, 16777215);
                }
                j += event.getIncrement();
                if (j >= guiGraphics.guiHeight() / 3) {
                    break;
                }
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private void drawBar(GuiGraphics guiGraphics, int x, int y, BossEvent bossEvent) {
        this.drawBar(guiGraphics, x, y, bossEvent, 182, BAR_BACKGROUND_SPRITES, OVERLAY_BACKGROUND_SPRITES);
        int i = Mth.lerpDiscrete(bossEvent.getProgress(), 0, 182);
        if (i > 0) {
            this.drawBar(guiGraphics, x, y, bossEvent, i, BAR_PROGRESS_SPRITES, OVERLAY_PROGRESS_SPRITES);
        }
    }

    private void drawBar(
        GuiGraphics guiGraphics, int x, int y, BossEvent bossEvent, int progress, ResourceLocation[] barProgressSprites, ResourceLocation[] overlayProgressSprites
    ) {
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(barProgressSprites[bossEvent.getColor().ordinal()], 182, 5, 0, 0, x, y, progress, 5);
        if (bossEvent.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
            guiGraphics.blitSprite(overlayProgressSprites[bossEvent.getOverlay().ordinal() - 1], 182, 5, 0, 0, x, y, progress, 5);
        }

        RenderSystem.disableBlend();
    }

    public void update(ClientboundBossEventPacket packet) {
        packet.dispatch(
            new ClientboundBossEventPacket.Handler() {
                @Override
                public void add(
                    UUID p_168824_,
                    Component p_168825_,
                    float p_168826_,
                    BossEvent.BossBarColor p_168827_,
                    BossEvent.BossBarOverlay p_168828_,
                    boolean p_168829_,
                    boolean p_168830_,
                    boolean p_168831_
                ) {
                    BossHealthOverlay.this.events
                        .put(p_168824_, new LerpingBossEvent(p_168824_, p_168825_, p_168826_, p_168827_, p_168828_, p_168829_, p_168830_, p_168831_));
                }

                @Override
                public void remove(UUID p_168812_) {
                    BossHealthOverlay.this.events.remove(p_168812_);
                }

                @Override
                public void updateProgress(UUID p_168814_, float p_168815_) {
                    BossHealthOverlay.this.events.get(p_168814_).setProgress(p_168815_);
                }

                @Override
                public void updateName(UUID p_168821_, Component p_168822_) {
                    BossHealthOverlay.this.events.get(p_168821_).setName(p_168822_);
                }

                @Override
                public void updateStyle(UUID p_168817_, BossEvent.BossBarColor p_168818_, BossEvent.BossBarOverlay p_168819_) {
                    LerpingBossEvent lerpingbossevent = BossHealthOverlay.this.events.get(p_168817_);
                    lerpingbossevent.setColor(p_168818_);
                    lerpingbossevent.setOverlay(p_168819_);
                }

                @Override
                public void updateProperties(UUID p_168833_, boolean p_168834_, boolean p_168835_, boolean p_168836_) {
                    LerpingBossEvent lerpingbossevent = BossHealthOverlay.this.events.get(p_168833_);
                    lerpingbossevent.setDarkenScreen(p_168834_);
                    lerpingbossevent.setPlayBossMusic(p_168835_);
                    lerpingbossevent.setCreateWorldFog(p_168836_);
                }
            }
        );
    }

    public void reset() {
        this.events.clear();
    }

    public boolean shouldPlayMusic() {
        if (!this.events.isEmpty()) {
            for (BossEvent bossevent : this.events.values()) {
                if (bossevent.shouldPlayBossMusic()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean shouldDarkenScreen() {
        if (!this.events.isEmpty()) {
            for (BossEvent bossevent : this.events.values()) {
                if (bossevent.shouldDarkenScreen()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean shouldCreateWorldFog() {
        if (!this.events.isEmpty()) {
            for (BossEvent bossevent : this.events.values()) {
                if (bossevent.shouldCreateWorldFog()) {
                    return true;
                }
            }
        }

        return false;
    }
}
