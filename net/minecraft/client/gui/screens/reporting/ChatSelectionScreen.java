package net.minecraft.client.gui.screens.reporting;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.client.multiplayer.chat.report.ChatReport;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChatSelectionScreen extends Screen {
    static final ResourceLocation CHECKMARK_SPRITE = ResourceLocation.withDefaultNamespace("icon/checkmark");
    private static final Component TITLE = Component.translatable("gui.chatSelection.title");
    private static final Component CONTEXT_INFO = Component.translatable("gui.chatSelection.context");
    @Nullable
    private final Screen lastScreen;
    private final ReportingContext reportingContext;
    private Button confirmSelectedButton;
    private MultiLineLabel contextInfoLabel;
    @Nullable
    private ChatSelectionScreen.ChatSelectionList chatSelectionList;
    final ChatReport.Builder report;
    private final Consumer<ChatReport.Builder> onSelected;
    private ChatSelectionLogFiller chatLogFiller;

    public ChatSelectionScreen(@Nullable Screen lastScreen, ReportingContext reportingContext, ChatReport.Builder report, Consumer<ChatReport.Builder> onSelected) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.reportingContext = reportingContext;
        this.report = report.copy();
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        this.chatLogFiller = new ChatSelectionLogFiller(this.reportingContext, this::canReport);
        this.contextInfoLabel = MultiLineLabel.create(this.font, CONTEXT_INFO, this.width - 16);
        this.chatSelectionList = this.addRenderableWidget(
            new ChatSelectionScreen.ChatSelectionList(this.minecraft, (this.contextInfoLabel.getLineCount() + 1) * 9)
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK, p_239860_ -> this.onClose()).bounds(this.width / 2 - 155, this.height - 32, 150, 20).build()
        );
        this.confirmSelectedButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, p_299799_ -> {
            this.onSelected.accept(this.report);
            this.onClose();
        }).bounds(this.width / 2 - 155 + 160, this.height - 32, 150, 20).build());
        this.updateConfirmSelectedButton();
        this.extendLog();
        this.chatSelectionList.setScrollAmount((double)this.chatSelectionList.getMaxScroll());
    }

    private boolean canReport(LoggedChatMessage message) {
        return message.canReport(this.report.reportedProfileId());
    }

    private void extendLog() {
        int i = this.chatSelectionList.getMaxVisibleEntries();
        this.chatLogFiller.fillNextPage(i, this.chatSelectionList);
    }

    void onReachedScrollTop() {
        this.extendLog();
    }

    void updateConfirmSelectedButton() {
        this.confirmSelectedButton.active = !this.report.reportedMessages().isEmpty();
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 16777215);
        AbuseReportLimits abusereportlimits = this.reportingContext.sender().reportLimits();
        int i = this.report.reportedMessages().size();
        int j = abusereportlimits.maxReportedMessageCount();
        Component component = Component.translatable("gui.chatSelection.selected", i, j);
        guiGraphics.drawCenteredString(this.font, component, this.width / 2, 16 + 9 * 3 / 2, -1);
        this.contextInfoLabel.renderCentered(guiGraphics, this.width / 2, this.chatSelectionList.getFooterTop());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), CONTEXT_INFO);
    }

    @OnlyIn(Dist.CLIENT)
    public class ChatSelectionList extends ObjectSelectionList<ChatSelectionScreen.ChatSelectionList.Entry> implements ChatSelectionLogFiller.Output {
        @Nullable
        private ChatSelectionScreen.ChatSelectionList.Heading previousHeading;

        public ChatSelectionList(Minecraft minecraft, int height) {
            super(minecraft, ChatSelectionScreen.this.width, ChatSelectionScreen.this.height - height - 80, 40, 16);
        }

        @Override
        public void setScrollAmount(double scroll) {
            double d0 = this.getScrollAmount();
            super.setScrollAmount(scroll);
            if ((float)this.getMaxScroll() > 1.0E-5F && scroll <= 1.0E-5F && !Mth.equal(scroll, d0)) {
                ChatSelectionScreen.this.onReachedScrollTop();
            }
        }

        @Override
        public void acceptMessage(int chatId, LoggedChatMessage.Player playerMessage) {
            boolean flag = playerMessage.canReport(ChatSelectionScreen.this.report.reportedProfileId());
            ChatTrustLevel chattrustlevel = playerMessage.trustLevel();
            GuiMessageTag guimessagetag = chattrustlevel.createTag(playerMessage.message());
            ChatSelectionScreen.ChatSelectionList.Entry chatselectionscreen$chatselectionlist$entry = new ChatSelectionScreen.ChatSelectionList.MessageEntry(
                chatId, playerMessage.toContentComponent(), playerMessage.toNarrationComponent(), guimessagetag, flag, true
            );
            this.addEntryToTop(chatselectionscreen$chatselectionlist$entry);
            this.updateHeading(playerMessage, flag);
        }

        private void updateHeading(LoggedChatMessage.Player loggedPlayerChatMessage, boolean canReport) {
            ChatSelectionScreen.ChatSelectionList.Entry chatselectionscreen$chatselectionlist$entry = new ChatSelectionScreen.ChatSelectionList.MessageHeadingEntry(
                loggedPlayerChatMessage.profile(), loggedPlayerChatMessage.toHeadingComponent(), canReport
            );
            this.addEntryToTop(chatselectionscreen$chatselectionlist$entry);
            ChatSelectionScreen.ChatSelectionList.Heading chatselectionscreen$chatselectionlist$heading = new ChatSelectionScreen.ChatSelectionList.Heading(
                loggedPlayerChatMessage.profileId(), chatselectionscreen$chatselectionlist$entry
            );
            if (this.previousHeading != null && this.previousHeading.canCombine(chatselectionscreen$chatselectionlist$heading)) {
                this.removeEntryFromTop(this.previousHeading.entry());
            }

            this.previousHeading = chatselectionscreen$chatselectionlist$heading;
        }

        @Override
        public void acceptDivider(Component text) {
            this.addEntryToTop(new ChatSelectionScreen.ChatSelectionList.PaddingEntry());
            this.addEntryToTop(new ChatSelectionScreen.ChatSelectionList.DividerEntry(text));
            this.addEntryToTop(new ChatSelectionScreen.ChatSelectionList.PaddingEntry());
            this.previousHeading = null;
        }

        @Override
        public int getRowWidth() {
            return Math.min(350, this.width - 50);
        }

        public int getMaxVisibleEntries() {
            return Mth.positiveCeilDiv(this.height, this.itemHeight);
        }

        @Override
        protected void renderItem(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, int left, int top, int width, int height
        ) {
            ChatSelectionScreen.ChatSelectionList.Entry chatselectionscreen$chatselectionlist$entry = this.getEntry(index);
            if (this.shouldHighlightEntry(chatselectionscreen$chatselectionlist$entry)) {
                boolean flag = this.getSelected() == chatselectionscreen$chatselectionlist$entry;
                int i = this.isFocused() && flag ? -1 : -8355712;
                this.renderSelection(guiGraphics, top, width, height, i, -16777216);
            }

            chatselectionscreen$chatselectionlist$entry.render(
                guiGraphics,
                index,
                top,
                left,
                width,
                height,
                mouseX,
                mouseY,
                this.getHovered() == chatselectionscreen$chatselectionlist$entry,
                partialTick
            );
        }

        private boolean shouldHighlightEntry(ChatSelectionScreen.ChatSelectionList.Entry entry) {
            if (entry.canSelect()) {
                boolean flag = this.getSelected() == entry;
                boolean flag1 = this.getSelected() == null;
                boolean flag2 = this.getHovered() == entry;
                return flag || flag1 && flag2 && entry.canReport();
            } else {
                return false;
            }
        }

        @Nullable
        protected ChatSelectionScreen.ChatSelectionList.Entry nextEntry(ScreenDirection direction) {
            return this.nextEntry(direction, ChatSelectionScreen.ChatSelectionList.Entry::canSelect);
        }

        public void setSelected(@Nullable ChatSelectionScreen.ChatSelectionList.Entry selected) {
            super.setSelected(selected);
            ChatSelectionScreen.ChatSelectionList.Entry chatselectionscreen$chatselectionlist$entry = this.nextEntry(ScreenDirection.UP);
            if (chatselectionscreen$chatselectionlist$entry == null) {
                ChatSelectionScreen.this.onReachedScrollTop();
            }
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
            ChatSelectionScreen.ChatSelectionList.Entry chatselectionscreen$chatselectionlist$entry = this.getSelected();
            return chatselectionscreen$chatselectionlist$entry != null
                    && chatselectionscreen$chatselectionlist$entry.keyPressed(keyCode, scanCode, modifiers)
                ? true
                : super.keyPressed(keyCode, scanCode, modifiers);
        }

        public int getFooterTop() {
            return this.getBottom() + 9;
        }

        @OnlyIn(Dist.CLIENT)
        public class DividerEntry extends ChatSelectionScreen.ChatSelectionList.Entry {
            private static final int COLOR = -6250336;
            private final Component text;

            public DividerEntry(Component text) {
                this.text = text;
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
                int i = top + height / 2;
                int j = left + width - 8;
                int k = ChatSelectionScreen.this.font.width(this.text);
                int l = (left + j - k) / 2;
                int i1 = i - 9 / 2;
                guiGraphics.drawString(ChatSelectionScreen.this.font, this.text, l, i1, -6250336);
            }

            @Override
            public Component getNarration() {
                return this.text;
            }
        }

        @OnlyIn(Dist.CLIENT)
        public abstract class Entry extends ObjectSelectionList.Entry<ChatSelectionScreen.ChatSelectionList.Entry> {
            @Override
            public Component getNarration() {
                return CommonComponents.EMPTY;
            }

            public boolean isSelected() {
                return false;
            }

            public boolean canSelect() {
                return false;
            }

            public boolean canReport() {
                return this.canSelect();
            }
        }

        @OnlyIn(Dist.CLIENT)
        static record Heading(UUID sender, ChatSelectionScreen.ChatSelectionList.Entry entry) {
            public boolean canCombine(ChatSelectionScreen.ChatSelectionList.Heading other) {
                return other.sender.equals(this.sender);
            }
        }

        @OnlyIn(Dist.CLIENT)
        public class MessageEntry extends ChatSelectionScreen.ChatSelectionList.Entry {
            private static final int CHECKMARK_WIDTH = 9;
            private static final int CHECKMARK_HEIGHT = 8;
            private static final int INDENT_AMOUNT = 11;
            private static final int TAG_MARGIN_LEFT = 4;
            private final int chatId;
            private final FormattedText text;
            private final Component narration;
            @Nullable
            private final List<FormattedCharSequence> hoverText;
            @Nullable
            private final GuiMessageTag.Icon tagIcon;
            @Nullable
            private final List<FormattedCharSequence> tagHoverText;
            private final boolean canReport;
            private final boolean playerMessage;

            public MessageEntry(
                int chatId, Component text, Component narration, @Nullable GuiMessageTag tagIcon, boolean canReport, boolean playerMessage
            ) {
                this.chatId = chatId;
                this.tagIcon = Optionull.map(tagIcon, GuiMessageTag::icon);
                this.tagHoverText = tagIcon != null && tagIcon.text() != null
                    ? ChatSelectionScreen.this.font.split(tagIcon.text(), ChatSelectionList.this.getRowWidth())
                    : null;
                this.canReport = canReport;
                this.playerMessage = playerMessage;
                FormattedText formattedtext = ChatSelectionScreen.this.font
                    .substrByWidth(text, this.getMaximumTextWidth() - ChatSelectionScreen.this.font.width(CommonComponents.ELLIPSIS));
                if (text != formattedtext) {
                    this.text = FormattedText.composite(formattedtext, CommonComponents.ELLIPSIS);
                    this.hoverText = ChatSelectionScreen.this.font.split(text, ChatSelectionList.this.getRowWidth());
                } else {
                    this.text = text;
                    this.hoverText = null;
                }

                this.narration = narration;
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
                if (this.isSelected() && this.canReport) {
                    this.renderSelectedCheckmark(guiGraphics, top, left, height);
                }

                int i = left + this.getTextIndent();
                int j = top + 1 + (height - 9) / 2;
                guiGraphics.drawString(ChatSelectionScreen.this.font, Language.getInstance().getVisualOrder(this.text), i, j, this.canReport ? -1 : -1593835521);
                if (this.hoverText != null && hovering) {
                    ChatSelectionScreen.this.setTooltipForNextRenderPass(this.hoverText);
                }

                int k = ChatSelectionScreen.this.font.width(this.text);
                this.renderTag(guiGraphics, i + k + 4, top, height, mouseX, mouseY);
            }

            private void renderTag(GuiGraphics guiGraphics, int x, int y, int height, int mouseX, int mouseY) {
                if (this.tagIcon != null) {
                    int i = y + (height - this.tagIcon.height) / 2;
                    this.tagIcon.draw(guiGraphics, x, i);
                    if (this.tagHoverText != null
                        && mouseX >= x
                        && mouseX <= x + this.tagIcon.width
                        && mouseY >= i
                        && mouseY <= i + this.tagIcon.height) {
                        ChatSelectionScreen.this.setTooltipForNextRenderPass(this.tagHoverText);
                    }
                }
            }

            private void renderSelectedCheckmark(GuiGraphics guiGraphics, int top, int left, int height) {
                int i = top + (height - 8) / 2;
                RenderSystem.enableBlend();
                guiGraphics.blitSprite(ChatSelectionScreen.CHECKMARK_SPRITE, left, i, 9, 8);
                RenderSystem.disableBlend();
            }

            private int getMaximumTextWidth() {
                int i = this.tagIcon != null ? this.tagIcon.width + 4 : 0;
                return ChatSelectionList.this.getRowWidth() - this.getTextIndent() - 4 - i;
            }

            private int getTextIndent() {
                return this.playerMessage ? 11 : 0;
            }

            @Override
            public Component getNarration() {
                return (Component)(this.isSelected() ? Component.translatable("narrator.select", this.narration) : this.narration);
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
                ChatSelectionList.this.setSelected(null);
                return this.toggleReport();
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
                return CommonInputs.selected(keyCode) ? this.toggleReport() : false;
            }

            @Override
            public boolean isSelected() {
                return ChatSelectionScreen.this.report.isReported(this.chatId);
            }

            @Override
            public boolean canSelect() {
                return true;
            }

            @Override
            public boolean canReport() {
                return this.canReport;
            }

            private boolean toggleReport() {
                if (this.canReport) {
                    ChatSelectionScreen.this.report.toggleReported(this.chatId);
                    ChatSelectionScreen.this.updateConfirmSelectedButton();
                    return true;
                } else {
                    return false;
                }
            }
        }

        @OnlyIn(Dist.CLIENT)
        public class MessageHeadingEntry extends ChatSelectionScreen.ChatSelectionList.Entry {
            private static final int FACE_SIZE = 12;
            private static final int PADDING = 4;
            private final Component heading;
            private final Supplier<PlayerSkin> skin;
            private final boolean canReport;

            public MessageHeadingEntry(GameProfile profile, Component heading, boolean canReport) {
                this.heading = heading;
                this.canReport = canReport;
                this.skin = ChatSelectionList.this.minecraft.getSkinManager().lookupInsecure(profile);
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
                int i = left - 12 + 4;
                int j = top + (height - 12) / 2;
                PlayerFaceRenderer.draw(guiGraphics, this.skin.get(), i, j, 12);
                int k = top + 1 + (height - 9) / 2;
                guiGraphics.drawString(ChatSelectionScreen.this.font, this.heading, i + 12 + 4, k, this.canReport ? -1 : -1593835521);
            }
        }

        @OnlyIn(Dist.CLIENT)
        public class PaddingEntry extends ChatSelectionScreen.ChatSelectionList.Entry {
            @Override
            public void render(
                GuiGraphics p_282007_,
                int p_240110_,
                int p_240111_,
                int p_240112_,
                int p_240113_,
                int p_240114_,
                int p_240115_,
                int p_240116_,
                boolean p_240117_,
                float p_240118_
            ) {
            }
        }
    }
}
