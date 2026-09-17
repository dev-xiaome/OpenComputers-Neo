package net.minecraft.client.gui.components;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerTabOverlay {
    private static final ResourceLocation PING_UNKNOWN_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_unknown");
    private static final ResourceLocation PING_1_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_1");
    private static final ResourceLocation PING_2_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_2");
    private static final ResourceLocation PING_3_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_3");
    private static final ResourceLocation PING_4_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_4");
    private static final ResourceLocation PING_5_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_5");
    private static final ResourceLocation HEART_CONTAINER_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");
    private static final ResourceLocation HEART_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full_blinking");
    private static final ResourceLocation HEART_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half_blinking");
    private static final ResourceLocation HEART_ABSORBING_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_ABSORBING_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking");
    private static final ResourceLocation HEART_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt(
            p_253306_ -> p_253306_.getGameMode() == GameType.SPECTATOR ? 1 : 0
        )
        .thenComparing(p_269613_ -> Optionull.mapOrDefault(p_269613_.getTeam(), PlayerTeam::getName, ""))
        .thenComparing(p_253305_ -> p_253305_.getProfile().getName(), String::compareToIgnoreCase);
    public static final int MAX_ROWS_PER_COL = 20;
    private final Minecraft minecraft;
    private final Gui gui;
    @Nullable
    private Component footer;
    @Nullable
    private Component header;
    /**
     * Weither or not the playerlist is currently being rendered
     */
    private boolean visible;
    private final Map<UUID, PlayerTabOverlay.HealthState> healthStates = new Object2ObjectOpenHashMap<>();

    public PlayerTabOverlay(Minecraft minecraft, Gui gui) {
        this.minecraft = minecraft;
        this.gui = gui;
    }

    public Component getNameForDisplay(PlayerInfo playerInfo) {
        return playerInfo.getTabListDisplayName() != null
            ? this.decorateName(playerInfo, playerInfo.getTabListDisplayName().copy())
            : this.decorateName(playerInfo, PlayerTeam.formatNameForTeam(playerInfo.getTeam(), Component.literal(playerInfo.getProfile().getName())));
    }

    private Component decorateName(PlayerInfo playerInfo, MutableComponent name) {
        return playerInfo.getGameMode() == GameType.SPECTATOR ? name.withStyle(ChatFormatting.ITALIC) : name;
    }

    /**
     * Called by GuiIngame to update the information stored in the playerlist, does not actually render the list, however.
     */
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.healthStates.clear();
            this.visible = visible;
            if (visible) {
                Component component = ComponentUtils.formatList(this.getPlayerInfos(), Component.literal(", "), this::getNameForDisplay);
                this.minecraft.getNarrator().sayNow(Component.translatable("multiplayer.player.list.narration", component));
            }
        }
    }

    private List<PlayerInfo> getPlayerInfos() {
        return this.minecraft.player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
    }

    public void render(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, @Nullable Objective objective) {
        List<PlayerInfo> list = this.getPlayerInfos();
        List<PlayerTabOverlay.ScoreDisplayEntry> list1 = new ArrayList<>(list.size());
        int i = this.minecraft.font.width(" ");
        int j = 0;
        int k = 0;

        for (PlayerInfo playerinfo : list) {
            Component component = this.getNameForDisplay(playerinfo);
            j = Math.max(j, this.minecraft.font.width(component));
            int l = 0;
            Component component1 = null;
            int i1 = 0;
            if (objective != null) {
                ScoreHolder scoreholder = ScoreHolder.fromGameProfile(playerinfo.getProfile());
                ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
                if (readonlyscoreinfo != null) {
                    l = readonlyscoreinfo.value();
                }

                if (objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                    NumberFormat numberformat = objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT);
                    component1 = ReadOnlyScoreInfo.safeFormatValue(readonlyscoreinfo, numberformat);
                    i1 = this.minecraft.font.width(component1);
                    k = Math.max(k, i1 > 0 ? i + i1 : 0);
                }
            }

            list1.add(new PlayerTabOverlay.ScoreDisplayEntry(component, l, component1, i1));
        }

        if (!this.healthStates.isEmpty()) {
            Set<UUID> set = list.stream().map(p_250472_ -> p_250472_.getProfile().getId()).collect(Collectors.toSet());
            this.healthStates.keySet().removeIf(p_248583_ -> !set.contains(p_248583_));
        }

        int j2 = list.size();
        int k2 = j2;

        int l2;
        for (l2 = 1; k2 > 20; k2 = (j2 + l2 - 1) / l2) {
            l2++;
        }

        boolean flag2 = this.minecraft.isLocalServer() || this.minecraft.getConnection().getConnection().isEncrypted();
        int i3;
        if (objective != null) {
            if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                i3 = 90;
            } else {
                i3 = k;
            }
        } else {
            i3 = 0;
        }

        int j3 = Math.min(l2 * ((flag2 ? 9 : 0) + j + i3 + 13), width - 50) / l2;
        int k3 = width / 2 - (j3 * l2 + (l2 - 1) * 5) / 2;
        int l3 = 10;
        int i4 = j3 * l2 + (l2 - 1) * 5;
        List<FormattedCharSequence> list2 = null;
        if (this.header != null) {
            list2 = this.minecraft.font.split(this.header, width - 50);

            for (FormattedCharSequence formattedcharsequence : list2) {
                i4 = Math.max(i4, this.minecraft.font.width(formattedcharsequence));
            }
        }

        List<FormattedCharSequence> list3 = null;
        if (this.footer != null) {
            list3 = this.minecraft.font.split(this.footer, width - 50);

            for (FormattedCharSequence formattedcharsequence1 : list3) {
                i4 = Math.max(i4, this.minecraft.font.width(formattedcharsequence1));
            }
        }

        if (list2 != null) {
            guiGraphics.fill(width / 2 - i4 / 2 - 1, l3 - 1, width / 2 + i4 / 2 + 1, l3 + list2.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence formattedcharsequence2 : list2) {
                int j1 = this.minecraft.font.width(formattedcharsequence2);
                guiGraphics.drawString(this.minecraft.font, formattedcharsequence2, width / 2 - j1 / 2, l3, -1);
                l3 += 9;
            }

            l3++;
        }

        guiGraphics.fill(width / 2 - i4 / 2 - 1, l3 - 1, width / 2 + i4 / 2 + 1, l3 + k2 * 9, Integer.MIN_VALUE);
        int j4 = this.minecraft.options.getBackgroundColor(553648127);

        for (int k4 = 0; k4 < j2; k4++) {
            int l4 = k4 / k2;
            int k1 = k4 % k2;
            int l1 = k3 + l4 * j3 + l4 * 5;
            int i2 = l3 + k1 * 9;
            guiGraphics.fill(l1, i2, l1 + j3, i2 + 8, j4);
            RenderSystem.enableBlend();
            if (k4 < list.size()) {
                PlayerInfo playerinfo1 = list.get(k4);
                PlayerTabOverlay.ScoreDisplayEntry playertaboverlay$scoredisplayentry = list1.get(k4);
                GameProfile gameprofile = playerinfo1.getProfile();
                if (flag2) {
                    Player player = this.minecraft.level.getPlayerByUUID(gameprofile.getId());
                    boolean flag = player != null && LivingEntityRenderer.isEntityUpsideDown(player);
                    boolean flag1 = player != null && player.isModelPartShown(PlayerModelPart.HAT);
                    PlayerFaceRenderer.draw(guiGraphics, playerinfo1.getSkin().texture(), l1, i2, 8, flag1, flag);
                    l1 += 9;
                }

                guiGraphics.drawString(
                    this.minecraft.font, playertaboverlay$scoredisplayentry.name, l1, i2, playerinfo1.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1
                );
                if (objective != null && playerinfo1.getGameMode() != GameType.SPECTATOR) {
                    int j5 = l1 + j + 1;
                    int k5 = j5 + i3;
                    if (k5 - j5 > 5) {
                        this.renderTablistScore(objective, i2, playertaboverlay$scoredisplayentry, j5, k5, gameprofile.getId(), guiGraphics);
                    }
                }

                this.renderPingIcon(guiGraphics, j3, l1 - (flag2 ? 9 : 0), i2, playerinfo1);
            }
        }

        if (list3 != null) {
            l3 += k2 * 9 + 1;
            guiGraphics.fill(width / 2 - i4 / 2 - 1, l3 - 1, width / 2 + i4 / 2 + 1, l3 + list3.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence formattedcharsequence3 : list3) {
                int i5 = this.minecraft.font.width(formattedcharsequence3);
                guiGraphics.drawString(this.minecraft.font, formattedcharsequence3, width / 2 - i5 / 2, l3, -1);
                l3 += 9;
            }
        }
    }

    protected void renderPingIcon(GuiGraphics guiGraphics, int width, int x, int y, PlayerInfo playerInfo) {
        ResourceLocation resourcelocation;
        if (playerInfo.getLatency() < 0) {
            resourcelocation = PING_UNKNOWN_SPRITE;
        } else if (playerInfo.getLatency() < 150) {
            resourcelocation = PING_5_SPRITE;
        } else if (playerInfo.getLatency() < 300) {
            resourcelocation = PING_4_SPRITE;
        } else if (playerInfo.getLatency() < 600) {
            resourcelocation = PING_3_SPRITE;
        } else if (playerInfo.getLatency() < 1000) {
            resourcelocation = PING_2_SPRITE;
        } else {
            resourcelocation = PING_1_SPRITE;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.blitSprite(resourcelocation, x + width - 11, y, 10, 8);
        guiGraphics.pose().popPose();
    }

    private void renderTablistScore(
        Objective objective, int y, PlayerTabOverlay.ScoreDisplayEntry displayEntry, int minX, int maxX, UUID playerUuid, GuiGraphics guiGraphics
    ) {
        if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
            this.renderTablistHearts(y, minX, maxX, playerUuid, guiGraphics, displayEntry.score);
        } else if (displayEntry.formattedScore != null) {
            guiGraphics.drawString(this.minecraft.font, displayEntry.formattedScore, maxX - displayEntry.scoreWidth, y, 16777215);
        }
    }

    private void renderTablistHearts(int y, int minX, int maxX, UUID playerUuid, GuiGraphics guiGraphics, int health) {
        PlayerTabOverlay.HealthState playertaboverlay$healthstate = this.healthStates
            .computeIfAbsent(playerUuid, p_249546_ -> new PlayerTabOverlay.HealthState(health));
        playertaboverlay$healthstate.update(health, (long)this.gui.getGuiTicks());
        int i = Mth.positiveCeilDiv(Math.max(health, playertaboverlay$healthstate.displayedValue()), 2);
        int j = Math.max(health, Math.max(playertaboverlay$healthstate.displayedValue(), 20)) / 2;
        boolean flag = playertaboverlay$healthstate.isBlinking((long)this.gui.getGuiTicks());
        if (i > 0) {
            int k = Mth.floor(Math.min((float)(maxX - minX - 4) / (float)j, 9.0F));
            if (k <= 3) {
                float f1 = Mth.clamp((float)health / 20.0F, 0.0F, 1.0F);
                int j1 = (int)((1.0F - f1) * 255.0F) << 16 | (int)(f1 * 255.0F) << 8;
                float f = (float)health / 2.0F;
                Component component = Component.translatable("multiplayer.player.list.hp", f);
                Component component1;
                if (maxX - this.minecraft.font.width(component) >= minX) {
                    component1 = component;
                } else {
                    component1 = Component.literal(Float.toString(f));
                }

                guiGraphics.drawString(this.minecraft.font, component1, (maxX + minX - this.minecraft.font.width(component1)) / 2, y, j1);
            } else {
                ResourceLocation resourcelocation = flag ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE;

                for (int l = i; l < j; l++) {
                    guiGraphics.blitSprite(resourcelocation, minX + l * k, y, 9, 9);
                }

                for (int i1 = 0; i1 < i; i1++) {
                    guiGraphics.blitSprite(resourcelocation, minX + i1 * k, y, 9, 9);
                    if (flag) {
                        if (i1 * 2 + 1 < playertaboverlay$healthstate.displayedValue()) {
                            guiGraphics.blitSprite(HEART_FULL_BLINKING_SPRITE, minX + i1 * k, y, 9, 9);
                        }

                        if (i1 * 2 + 1 == playertaboverlay$healthstate.displayedValue()) {
                            guiGraphics.blitSprite(HEART_HALF_BLINKING_SPRITE, minX + i1 * k, y, 9, 9);
                        }
                    }

                    if (i1 * 2 + 1 < health) {
                        guiGraphics.blitSprite(i1 >= 10 ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE, minX + i1 * k, y, 9, 9);
                    }

                    if (i1 * 2 + 1 == health) {
                        guiGraphics.blitSprite(i1 >= 10 ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE, minX + i1 * k, y, 9, 9);
                    }
                }
            }
        }
    }

    public void setFooter(@Nullable Component footer) {
        this.footer = footer;
    }

    public void setHeader(@Nullable Component header) {
        this.header = header;
    }

    public void reset() {
        this.header = null;
        this.footer = null;
    }

    @OnlyIn(Dist.CLIENT)
    static class HealthState {
        private static final long DISPLAY_UPDATE_DELAY = 20L;
        private static final long DECREASE_BLINK_DURATION = 20L;
        private static final long INCREASE_BLINK_DURATION = 10L;
        private int lastValue;
        private int displayedValue;
        private long lastUpdateTick;
        private long blinkUntilTick;

        public HealthState(int displayedValue) {
            this.displayedValue = displayedValue;
            this.lastValue = displayedValue;
        }

        public void update(int value, long guiTicks) {
            if (value != this.lastValue) {
                long i = value < this.lastValue ? 20L : 10L;
                this.blinkUntilTick = guiTicks + i;
                this.lastValue = value;
                this.lastUpdateTick = guiTicks;
            }

            if (guiTicks - this.lastUpdateTick > 20L) {
                this.displayedValue = value;
            }
        }

        public int displayedValue() {
            return this.displayedValue;
        }

        public boolean isBlinking(long guiTicks) {
            return this.blinkUntilTick > guiTicks && (this.blinkUntilTick - guiTicks) % 6L >= 3L;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record ScoreDisplayEntry(Component name, int score, @Nullable Component formattedScore, int scoreWidth) {
    }
}
