package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.PendingInvite;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.mojang.realmsclient.gui.RowButton;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsObjectSelectionList;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsPendingInvitesScreen extends RealmsScreen {
    static final ResourceLocation ACCEPT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/accept_highlighted");
    static final ResourceLocation ACCEPT_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/accept");
    static final ResourceLocation REJECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/reject_highlighted");
    static final ResourceLocation REJECT_SPRITE = ResourceLocation.withDefaultNamespace("pending_invite/reject");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component NO_PENDING_INVITES_TEXT = Component.translatable("mco.invites.nopending");
    static final Component ACCEPT_INVITE = Component.translatable("mco.invites.button.accept");
    static final Component REJECT_INVITE = Component.translatable("mco.invites.button.reject");
    private final Screen lastScreen;
    private final CompletableFuture<List<PendingInvite>> pendingInvites = CompletableFuture.supplyAsync(() -> {
        try {
            return RealmsClient.create().pendingInvites().pendingInvites;
        } catch (RealmsServiceException realmsserviceexception) {
            LOGGER.error("Couldn't list invites", (Throwable)realmsserviceexception);
            return List.of();
        }
    }, Util.ioPool());
    @Nullable
    Component toolTip;
    RealmsPendingInvitesScreen.PendingInvitationSelectionList pendingInvitationSelectionList;
    int selectedInvite = -1;
    private Button acceptButton;
    private Button rejectButton;

    public RealmsPendingInvitesScreen(Screen lastScreen, Component title) {
        super(title);
        this.lastScreen = lastScreen;
    }

    @Override
    public void init() {
        RealmsMainScreen.refreshPendingInvites();
        this.pendingInvitationSelectionList = new RealmsPendingInvitesScreen.PendingInvitationSelectionList();
        this.pendingInvites.thenAcceptAsync(p_308444_ -> {
            List<RealmsPendingInvitesScreen.Entry> list = p_308444_.stream().map(p_293579_ -> new RealmsPendingInvitesScreen.Entry(p_293579_)).toList();
            this.pendingInvitationSelectionList.replaceEntries(list);
            if (list.isEmpty()) {
                this.minecraft.getNarrator().say(NO_PENDING_INVITES_TEXT);
            }
        }, this.screenExecutor);
        this.addRenderableWidget(this.pendingInvitationSelectionList);
        this.acceptButton = this.addRenderableWidget(Button.builder(ACCEPT_INVITE, p_293576_ -> {
            this.handleInvitation(this.selectedInvite, true);
            this.selectedInvite = -1;
            this.updateButtonStates();
        }).bounds(this.width / 2 - 174, this.height - 32, 100, 20).build());
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, p_293580_ -> this.onClose()).bounds(this.width / 2 - 50, this.height - 32, 100, 20).build()
        );
        this.rejectButton = this.addRenderableWidget(Button.builder(REJECT_INVITE, p_293581_ -> {
            this.handleInvitation(this.selectedInvite, false);
            this.selectedInvite = -1;
            this.updateButtonStates();
        }).bounds(this.width / 2 + 74, this.height - 32, 100, 20).build());
        this.updateButtonStates();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    void handleInvitation(int index, boolean shouldAccept) {
        if (index < this.pendingInvitationSelectionList.getItemCount()) {
            String s = this.pendingInvitationSelectionList.children().get(index).pendingInvite.invitationId;
            CompletableFuture.<Boolean>supplyAsync(() -> {
                try {
                    RealmsClient realmsclient = RealmsClient.create();
                    if (shouldAccept) {
                        realmsclient.acceptInvitation(s);
                    } else {
                        realmsclient.rejectInvitation(s);
                    }

                    return true;
                } catch (RealmsServiceException realmsserviceexception) {
                    LOGGER.error("Couldn't handle invite", (Throwable)realmsserviceexception);
                    return false;
                }
            }, Util.ioPool()).thenAcceptAsync(p_293575_ -> {
                if (p_293575_) {
                    this.pendingInvitationSelectionList.removeAtIndex(index);
                    RealmsDataFetcher realmsdatafetcher = this.minecraft.realmsDataFetcher();
                    if (shouldAccept) {
                        realmsdatafetcher.serverListUpdateTask.reset();
                    }

                    realmsdatafetcher.pendingInvitesTask.reset();
                }
            }, this.screenExecutor);
        }
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
        this.toolTip = null;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, -1);
        if (this.toolTip != null) {
            guiGraphics.renderTooltip(this.font, this.toolTip, mouseX, mouseY);
        }

        if (this.pendingInvites.isDone() && this.pendingInvitationSelectionList.getItemCount() == 0) {
            guiGraphics.drawCenteredString(this.font, NO_PENDING_INVITES_TEXT, this.width / 2, this.height / 2 - 20, -1);
        }
    }

    void updateButtonStates() {
        this.acceptButton.visible = this.shouldAcceptAndRejectButtonBeVisible(this.selectedInvite);
        this.rejectButton.visible = this.shouldAcceptAndRejectButtonBeVisible(this.selectedInvite);
    }

    private boolean shouldAcceptAndRejectButtonBeVisible(int selectedInvite) {
        return selectedInvite != -1;
    }

    @OnlyIn(Dist.CLIENT)
    class Entry extends ObjectSelectionList.Entry<RealmsPendingInvitesScreen.Entry> {
        private static final int TEXT_LEFT = 38;
        final PendingInvite pendingInvite;
        private final List<RowButton> rowButtons;

        Entry(PendingInvite pendingInvite) {
            this.pendingInvite = pendingInvite;
            this.rowButtons = Arrays.asList(new RealmsPendingInvitesScreen.Entry.AcceptRowButton(), new RealmsPendingInvitesScreen.Entry.RejectRowButton());
        }

        @Override
        public void render(
            GuiGraphics guiGraphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovering,
            float partialTick
        ) {
            this.renderPendingInvitationItem(guiGraphics, this.pendingInvite, left, top, mouseX, mouseY);
        }

        /**
         * Called when a mouse button is clicked within the GUI element.
         * <p>
         * @return {@code true} if the event is consumed, {@code false} otherwise.
         *
         * @param mouseX the X coordinate of the mouse.
         * @param mouseY the Y coordinate of the mouse.
         * @param button the button that was clicked.
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            RowButton.rowButtonMouseClicked(RealmsPendingInvitesScreen.this.pendingInvitationSelectionList, this, this.rowButtons, button, mouseX, mouseY);
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void renderPendingInvitationItem(GuiGraphics guiGraphics, PendingInvite pendingInvite, int x, int y, int mouseX, int mouseY) {
            guiGraphics.drawString(RealmsPendingInvitesScreen.this.font, pendingInvite.realmName, x + 38, y + 1, -1, false);
            guiGraphics.drawString(RealmsPendingInvitesScreen.this.font, pendingInvite.realmOwnerName, x + 38, y + 12, 7105644, false);
            guiGraphics.drawString(
                RealmsPendingInvitesScreen.this.font,
                RealmsUtil.convertToAgePresentationFromInstant(pendingInvite.date),
                x + 38,
                y + 24,
                7105644,
                false
            );
            RowButton.drawButtonsInRow(
                guiGraphics, this.rowButtons, RealmsPendingInvitesScreen.this.pendingInvitationSelectionList, x, y, mouseX, mouseY
            );
            RealmsUtil.renderPlayerFace(guiGraphics, x, y, 32, pendingInvite.realmOwnerUuid);
        }

        @Override
        public Component getNarration() {
            Component component = CommonComponents.joinLines(
                Component.literal(this.pendingInvite.realmName),
                Component.literal(this.pendingInvite.realmOwnerName),
                RealmsUtil.convertToAgePresentationFromInstant(this.pendingInvite.date)
            );
            return Component.translatable("narrator.select", component);
        }

        @OnlyIn(Dist.CLIENT)
        class AcceptRowButton extends RowButton {
            AcceptRowButton() {
                super(15, 15, 215, 5);
            }

            @Override
            protected void draw(GuiGraphics p_282151_, int p_283695_, int p_282436_, boolean p_282168_) {
                p_282151_.blitSprite(
                    p_282168_ ? RealmsPendingInvitesScreen.ACCEPT_HIGHLIGHTED_SPRITE : RealmsPendingInvitesScreen.ACCEPT_SPRITE, p_283695_, p_282436_, 18, 18
                );
                if (p_282168_) {
                    RealmsPendingInvitesScreen.this.toolTip = RealmsPendingInvitesScreen.ACCEPT_INVITE;
                }
            }

            @Override
            public void onClick(int p_89029_) {
                RealmsPendingInvitesScreen.this.handleInvitation(p_89029_, true);
            }
        }

        @OnlyIn(Dist.CLIENT)
        class RejectRowButton extends RowButton {
            RejectRowButton() {
                super(15, 15, 235, 5);
            }

            @Override
            protected void draw(GuiGraphics p_282457_, int p_281421_, int p_281260_, boolean p_281476_) {
                p_282457_.blitSprite(
                    p_281476_ ? RealmsPendingInvitesScreen.REJECT_HIGHLIGHTED_SPRITE : RealmsPendingInvitesScreen.REJECT_SPRITE, p_281421_, p_281260_, 18, 18
                );
                if (p_281476_) {
                    RealmsPendingInvitesScreen.this.toolTip = RealmsPendingInvitesScreen.REJECT_INVITE;
                }
            }

            @Override
            public void onClick(int p_89039_) {
                RealmsPendingInvitesScreen.this.handleInvitation(p_89039_, false);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    class PendingInvitationSelectionList extends RealmsObjectSelectionList<RealmsPendingInvitesScreen.Entry> {
        public PendingInvitationSelectionList() {
            super(RealmsPendingInvitesScreen.this.width, RealmsPendingInvitesScreen.this.height - 72, 32, 36);
        }

        public void removeAtIndex(int index) {
            this.remove(index);
        }

        @Override
        public int getMaxPosition() {
            return this.getItemCount() * 36;
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        public void selectItem(int index) {
            super.selectItem(index);
            this.selectInviteListItem(index);
        }

        public void selectInviteListItem(int index) {
            RealmsPendingInvitesScreen.this.selectedInvite = index;
            RealmsPendingInvitesScreen.this.updateButtonStates();
        }

        public void setSelected(@Nullable RealmsPendingInvitesScreen.Entry selected) {
            super.setSelected(selected);
            RealmsPendingInvitesScreen.this.selectedInvite = this.children().indexOf(selected);
            RealmsPendingInvitesScreen.this.updateButtonStates();
        }
    }
}
