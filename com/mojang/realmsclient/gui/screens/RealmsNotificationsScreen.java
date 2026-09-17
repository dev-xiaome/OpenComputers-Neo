package com.mojang.realmsclient.gui.screens;

import com.mojang.realmsclient.RealmsAvailability;
import com.mojang.realmsclient.dto.RealmsNews;
import com.mojang.realmsclient.dto.RealmsNotification;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.mojang.realmsclient.gui.task.DataFetcher;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsNotificationsScreen extends RealmsScreen {
    private static final ResourceLocation UNSEEN_NOTIFICATION_SPRITE = ResourceLocation.withDefaultNamespace("icon/unseen_notification");
    private static final ResourceLocation NEWS_SPRITE = ResourceLocation.withDefaultNamespace("icon/news");
    private static final ResourceLocation INVITE_SPRITE = ResourceLocation.withDefaultNamespace("icon/invite");
    private static final ResourceLocation TRIAL_AVAILABLE_SPRITE = ResourceLocation.withDefaultNamespace("icon/trial_available");
    private final CompletableFuture<Boolean> validClient = RealmsAvailability.get().thenApply(p_293571_ -> p_293571_.type() == RealmsAvailability.Type.SUCCESS);
    @Nullable
    private DataFetcher.Subscription realmsDataSubscription;
    @Nullable
    private RealmsNotificationsScreen.DataFetcherConfiguration currentConfiguration;
    private volatile int numberOfPendingInvites;
    private static boolean trialAvailable;
    private static boolean hasUnreadNews;
    private static boolean hasUnseenNotifications;
    private final RealmsNotificationsScreen.DataFetcherConfiguration showAll = new RealmsNotificationsScreen.DataFetcherConfiguration() {
        @Override
        public DataFetcher.Subscription initDataFetcher(RealmsDataFetcher p_294752_) {
            DataFetcher.Subscription datafetcher$subscription = p_294752_.dataFetcher.createSubscription();
            RealmsNotificationsScreen.this.addNewsAndInvitesSubscriptions(p_294752_, datafetcher$subscription);
            RealmsNotificationsScreen.this.addNotificationsSubscriptions(p_294752_, datafetcher$subscription);
            return datafetcher$subscription;
        }

        @Override
        public boolean showOldNotifications() {
            return true;
        }
    };
    private final RealmsNotificationsScreen.DataFetcherConfiguration onlyNotifications = new RealmsNotificationsScreen.DataFetcherConfiguration() {
        @Override
        public DataFetcher.Subscription initDataFetcher(RealmsDataFetcher p_275318_) {
            DataFetcher.Subscription datafetcher$subscription = p_275318_.dataFetcher.createSubscription();
            RealmsNotificationsScreen.this.addNotificationsSubscriptions(p_275318_, datafetcher$subscription);
            return datafetcher$subscription;
        }

        @Override
        public boolean showOldNotifications() {
            return false;
        }
    };

    public RealmsNotificationsScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    public void init() {
        if (this.realmsDataSubscription != null) {
            this.realmsDataSubscription.forceUpdate();
        }
    }

    @Override
    public void added() {
        super.added();
        this.minecraft.realmsDataFetcher().notificationsTask.reset();
    }

    @Nullable
    private RealmsNotificationsScreen.DataFetcherConfiguration getConfiguration() {
        boolean flag = this.inTitleScreen() && this.validClient.getNow(false);
        if (!flag) {
            return null;
        } else {
            return this.getRealmsNotificationsEnabled() ? this.showAll : this.onlyNotifications;
        }
    }

    @Override
    public void tick() {
        RealmsNotificationsScreen.DataFetcherConfiguration realmsnotificationsscreen$datafetcherconfiguration = this.getConfiguration();
        if (!Objects.equals(this.currentConfiguration, realmsnotificationsscreen$datafetcherconfiguration)) {
            this.currentConfiguration = realmsnotificationsscreen$datafetcherconfiguration;
            if (this.currentConfiguration != null) {
                this.realmsDataSubscription = this.currentConfiguration.initDataFetcher(this.minecraft.realmsDataFetcher());
            } else {
                this.realmsDataSubscription = null;
            }
        }

        if (this.realmsDataSubscription != null) {
            this.realmsDataSubscription.tick();
        }
    }

    private boolean getRealmsNotificationsEnabled() {
        return this.minecraft.options.realmsNotifications().get();
    }

    private boolean inTitleScreen() {
        return this.minecraft.screen instanceof TitleScreen;
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.validClient.getNow(false)) {
            this.drawIcons(guiGraphics);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private void drawIcons(GuiGraphics guiGraphics) {
        int i = this.numberOfPendingInvites;
        int j = 24;
        int k = this.height / 4 + (inTitleScreen() ? 32 : 48);
        int l = this.width / 2 + 100;
        int i1 = k + 48 + 2;
        int j1 = l - 3;
        if (hasUnseenNotifications) {
            guiGraphics.blitSprite(UNSEEN_NOTIFICATION_SPRITE, j1 - 12, i1 + 3, 10, 10);
            j1 -= 16;
        }

        if (this.currentConfiguration != null && this.currentConfiguration.showOldNotifications()) {
            if (hasUnreadNews) {
                guiGraphics.blitSprite(NEWS_SPRITE, j1 - 14, i1 + 1, 14, 14);
                j1 -= 16;
            }

            if (i != 0) {
                guiGraphics.blitSprite(INVITE_SPRITE, j1 - 14, i1 + 1, 14, 14);
                j1 -= 16;
            }

            if (trialAvailable) {
                guiGraphics.blitSprite(TRIAL_AVAILABLE_SPRITE, j1 - 10, i1 + 4, 8, 8);
            }
        }
    }

    void addNewsAndInvitesSubscriptions(RealmsDataFetcher dataFetcher, DataFetcher.Subscription subscription) {
        subscription.subscribe(dataFetcher.pendingInvitesTask, p_239521_ -> this.numberOfPendingInvites = p_239521_);
        subscription.subscribe(dataFetcher.trialAvailabilityTask, p_239494_ -> trialAvailable = p_239494_);
        subscription.subscribe(dataFetcher.newsTask, p_238946_ -> {
            dataFetcher.newsManager.updateUnreadNews(p_238946_);
            hasUnreadNews = dataFetcher.newsManager.hasUnreadNews();
        });
    }

    void addNotificationsSubscriptions(RealmsDataFetcher dataFetcher, DataFetcher.Subscription subscription) {
        subscription.subscribe(dataFetcher.notificationsTask, p_274637_ -> {
            hasUnseenNotifications = false;

            for (RealmsNotification realmsnotification : p_274637_) {
                if (!realmsnotification.seen()) {
                    hasUnseenNotifications = true;
                    break;
                }
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    interface DataFetcherConfiguration {
        DataFetcher.Subscription initDataFetcher(RealmsDataFetcher dataFetcher);

        boolean showOldNotifications();
    }
}
