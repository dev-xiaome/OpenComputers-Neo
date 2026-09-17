package net.minecraft.client.gui.screens.social;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SocialInteractionsScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.socialInteractions.title");
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("social_interactions/background");
    private static final ResourceLocation SEARCH_SPRITE = ResourceLocation.withDefaultNamespace("icon/search");
    private static final Component TAB_ALL = Component.translatable("gui.socialInteractions.tab_all");
    private static final Component TAB_HIDDEN = Component.translatable("gui.socialInteractions.tab_hidden");
    private static final Component TAB_BLOCKED = Component.translatable("gui.socialInteractions.tab_blocked");
    private static final Component TAB_ALL_SELECTED = TAB_ALL.plainCopy().withStyle(ChatFormatting.UNDERLINE);
    private static final Component TAB_HIDDEN_SELECTED = TAB_HIDDEN.plainCopy().withStyle(ChatFormatting.UNDERLINE);
    private static final Component TAB_BLOCKED_SELECTED = TAB_BLOCKED.plainCopy().withStyle(ChatFormatting.UNDERLINE);
    private static final Component SEARCH_HINT = Component.translatable("gui.socialInteractions.search_hint")
        .withStyle(ChatFormatting.ITALIC)
        .withStyle(ChatFormatting.GRAY);
    static final Component EMPTY_SEARCH = Component.translatable("gui.socialInteractions.search_empty").withStyle(ChatFormatting.GRAY);
    private static final Component EMPTY_HIDDEN = Component.translatable("gui.socialInteractions.empty_hidden").withStyle(ChatFormatting.GRAY);
    private static final Component EMPTY_BLOCKED = Component.translatable("gui.socialInteractions.empty_blocked").withStyle(ChatFormatting.GRAY);
    private static final Component BLOCKING_HINT = Component.translatable("gui.socialInteractions.blocking_hint");
    private static final int BG_BORDER_SIZE = 8;
    private static final int BG_WIDTH = 236;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MARGIN_Y = 64;
    public static final int SEARCH_START = 72;
    public static final int LIST_START = 88;
    private static final int IMAGE_WIDTH = 238;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 36;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private final Screen lastScreen;
    SocialInteractionsPlayerList socialInteractionsPlayerList;
    EditBox searchBox;
    private String lastSearch = "";
    private SocialInteractionsScreen.Page page = SocialInteractionsScreen.Page.ALL;
    private Button allButton;
    private Button hiddenButton;
    private Button blockedButton;
    private Button blockingHintButton;
    @Nullable
    private Component serverLabel;
    private int playerCount;

    public SocialInteractionsScreen() {
        this(null);
    }

    public SocialInteractionsScreen(@Nullable Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.updateServerLabel(Minecraft.getInstance());
    }

    private int windowHeight() {
        return Math.max(52, this.height - 128 - 16);
    }

    private int listEnd() {
        return 80 + this.windowHeight() - 8;
    }

    private int marginX() {
        return (this.width - 238) / 2;
    }

    @Override
    public Component getNarrationMessage() {
        return (Component)(this.serverLabel != null
            ? CommonComponents.joinForNarration(super.getNarrationMessage(), this.serverLabel)
            : super.getNarrationMessage());
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.socialInteractionsPlayerList = new SocialInteractionsPlayerList(this, this.minecraft, this.width, this.listEnd() - 88, 88, 36);
        int i = this.socialInteractionsPlayerList.getRowWidth() / 3;
        int j = this.socialInteractionsPlayerList.getRowLeft();
        int k = this.socialInteractionsPlayerList.getRowRight();
        this.allButton = this.addRenderableWidget(
            Button.builder(TAB_ALL, p_240243_ -> this.showPage(SocialInteractionsScreen.Page.ALL)).bounds(j, 45, i, 20).build()
        );
        this.hiddenButton = this.addRenderableWidget(
            Button.builder(TAB_HIDDEN, p_100791_ -> this.showPage(SocialInteractionsScreen.Page.HIDDEN)).bounds((j + k - i) / 2 + 1, 45, i, 20).build()
        );
        this.blockedButton = this.addRenderableWidget(
            Button.builder(TAB_BLOCKED, p_100785_ -> this.showPage(SocialInteractionsScreen.Page.BLOCKED)).bounds(k - i + 1, 45, i, 20).build()
        );
        String s = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(this.font, this.marginX() + 28, 74, 200, 15, SEARCH_HINT) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return !SocialInteractionsScreen.this.searchBox.getValue().isEmpty() && SocialInteractionsScreen.this.socialInteractionsPlayerList.isEmpty()
                    ? super.createNarrationMessage().append(", ").append(SocialInteractionsScreen.EMPTY_SEARCH)
                    : super.createNarrationMessage();
            }
        };
        this.searchBox.setMaxLength(16);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(-1);
        this.searchBox.setValue(s);
        this.searchBox.setHint(SEARCH_HINT);
        this.searchBox.setResponder(this::checkSearchStringUpdate);
        this.addRenderableWidget(this.searchBox);
        this.addWidget(this.socialInteractionsPlayerList);
        this.blockingHintButton = this.addRenderableWidget(
            Button.builder(BLOCKING_HINT, ConfirmLinkScreen.confirmLink(this, CommonLinks.BLOCKING_HELP))
                .bounds(this.width / 2 - 100, 64 + this.windowHeight(), 200, 20)
                .build()
        );
        this.showPage(this.page);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, p_329743_ -> this.onClose()).width(200).build());
        this.layout.visitWidgets(p_329745_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_329745_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.socialInteractionsPlayerList.updateSizeAndPosition(this.width, this.listEnd() - 88, 88);
        this.searchBox.setPosition(this.marginX() + 28, 74);
        int i = this.socialInteractionsPlayerList.getRowLeft();
        int j = this.socialInteractionsPlayerList.getRowRight();
        int k = this.socialInteractionsPlayerList.getRowWidth() / 3;
        this.allButton.setPosition(i, 45);
        this.hiddenButton.setPosition((i + j - k) / 2 + 1, 45);
        this.blockedButton.setPosition(j - k + 1, 45);
        this.blockingHintButton.setPosition(this.width / 2 - 100, 64 + this.windowHeight());
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.searchBox);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private void showPage(SocialInteractionsScreen.Page page) {
        this.page = page;
        this.allButton.setMessage(TAB_ALL);
        this.hiddenButton.setMessage(TAB_HIDDEN);
        this.blockedButton.setMessage(TAB_BLOCKED);
        boolean flag = false;
        switch (page) {
            case ALL:
                this.allButton.setMessage(TAB_ALL_SELECTED);
                Collection<UUID> collection = this.minecraft.player.connection.getOnlinePlayerIds();
                this.socialInteractionsPlayerList.updatePlayerList(collection, this.socialInteractionsPlayerList.getScrollAmount(), true);
                break;
            case HIDDEN:
                this.hiddenButton.setMessage(TAB_HIDDEN_SELECTED);
                Set<UUID> set1 = this.minecraft.getPlayerSocialManager().getHiddenPlayers();
                flag = set1.isEmpty();
                this.socialInteractionsPlayerList.updatePlayerList(set1, this.socialInteractionsPlayerList.getScrollAmount(), false);
                break;
            case BLOCKED:
                this.blockedButton.setMessage(TAB_BLOCKED_SELECTED);
                PlayerSocialManager playersocialmanager = this.minecraft.getPlayerSocialManager();
                Set<UUID> set = this.minecraft
                    .player
                    .connection
                    .getOnlinePlayerIds()
                    .stream()
                    .filter(playersocialmanager::isBlocked)
                    .collect(Collectors.toSet());
                flag = set.isEmpty();
                this.socialInteractionsPlayerList.updatePlayerList(set, this.socialInteractionsPlayerList.getScrollAmount(), false);
        }

        GameNarrator gamenarrator = this.minecraft.getNarrator();
        if (!this.searchBox.getValue().isEmpty() && this.socialInteractionsPlayerList.isEmpty() && !this.searchBox.isFocused()) {
            gamenarrator.sayNow(EMPTY_SEARCH);
        } else if (flag) {
            if (page == SocialInteractionsScreen.Page.HIDDEN) {
                gamenarrator.sayNow(EMPTY_HIDDEN);
            } else if (page == SocialInteractionsScreen.Page.BLOCKED) {
                gamenarrator.sayNow(EMPTY_BLOCKED);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int i = this.marginX() + 3;
        guiGraphics.blitSprite(BACKGROUND_SPRITE, i, 64, 236, this.windowHeight() + 16);
        guiGraphics.blitSprite(SEARCH_SPRITE, i + 10, 76, 12, 12);
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
        this.updateServerLabel(this.minecraft);
        if (this.serverLabel != null) {
            guiGraphics.drawString(this.minecraft.font, this.serverLabel, this.marginX() + 8, 35, -1);
        }

        if (!this.socialInteractionsPlayerList.isEmpty()) {
            this.socialInteractionsPlayerList.render(guiGraphics, mouseX, mouseY, partialTick);
        } else if (!this.searchBox.getValue().isEmpty()) {
            guiGraphics.drawCenteredString(this.minecraft.font, EMPTY_SEARCH, this.width / 2, (72 + this.listEnd()) / 2, -1);
        } else if (this.page == SocialInteractionsScreen.Page.HIDDEN) {
            guiGraphics.drawCenteredString(this.minecraft.font, EMPTY_HIDDEN, this.width / 2, (72 + this.listEnd()) / 2, -1);
        } else if (this.page == SocialInteractionsScreen.Page.BLOCKED) {
            guiGraphics.drawCenteredString(this.minecraft.font, EMPTY_BLOCKED, this.width / 2, (72 + this.listEnd()) / 2, -1);
        }

        this.blockingHintButton.visible = this.page == SocialInteractionsScreen.Page.BLOCKED;
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.searchBox.isFocused() && this.minecraft.options.keySocialInteractions.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void checkSearchStringUpdate(String newText) {
        newText = newText.toLowerCase(Locale.ROOT);
        if (!newText.equals(this.lastSearch)) {
            this.socialInteractionsPlayerList.setFilter(newText);
            this.lastSearch = newText;
            this.showPage(this.page);
        }
    }

    private void updateServerLabel(Minecraft minecraft) {
        int i = minecraft.getConnection().getOnlinePlayers().size();
        if (this.playerCount != i) {
            String s = "";
            ServerData serverdata = minecraft.getCurrentServer();
            if (minecraft.isLocalServer()) {
                s = minecraft.getSingleplayerServer().getMotd();
            } else if (serverdata != null) {
                s = serverdata.name;
            }

            if (i > 1) {
                this.serverLabel = Component.translatable("gui.socialInteractions.server_label.multiple", s, i);
            } else {
                this.serverLabel = Component.translatable("gui.socialInteractions.server_label.single", s, i);
            }

            this.playerCount = i;
        }
    }

    public void onAddPlayer(PlayerInfo playerInfo) {
        this.socialInteractionsPlayerList.addPlayer(playerInfo, this.page);
    }

    public void onRemovePlayer(UUID id) {
        this.socialInteractionsPlayerList.removePlayer(id);
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Page {
        ALL,
        HIDDEN,
        BLOCKED;
    }
}
