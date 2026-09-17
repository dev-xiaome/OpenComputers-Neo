package net.minecraft.client.gui.screens.reporting;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.report.ReportReason;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ReportReasonSelectionScreen extends Screen {
    private static final Component REASON_TITLE = Component.translatable("gui.abuseReport.reason.title");
    private static final Component REASON_DESCRIPTION = Component.translatable("gui.abuseReport.reason.description");
    private static final Component READ_INFO_LABEL = Component.translatable("gui.abuseReport.read_info");
    private static final int DESCRIPTION_BOX_WIDTH = 320;
    private static final int DESCRIPTION_BOX_HEIGHT = 62;
    private static final int PADDING = 4;
    @Nullable
    private final Screen lastScreen;
    @Nullable
    private ReportReasonSelectionScreen.ReasonSelectionList reasonSelectionList;
    @Nullable
    ReportReason currentlySelectedReason;
    private final Consumer<ReportReason> onSelectedReason;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public ReportReasonSelectionScreen(@Nullable Screen lastScreen, @Nullable ReportReason currentlySelectedReason, Consumer<ReportReason> onSelectedReason) {
        super(REASON_TITLE);
        this.lastScreen = lastScreen;
        this.currentlySelectedReason = currentlySelectedReason;
        this.onSelectedReason = onSelectedReason;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(REASON_TITLE, this.font);
        LinearLayout linearlayout = this.layout.addToContents(LinearLayout.vertical().spacing(4));
        this.reasonSelectionList = linearlayout.addChild(new ReportReasonSelectionScreen.ReasonSelectionList(this.minecraft));
        ReportReasonSelectionScreen.ReasonSelectionList.Entry reportreasonselectionscreen$reasonselectionlist$entry = Optionull.map(
            this.currentlySelectedReason, this.reasonSelectionList::findEntry
        );
        this.reasonSelectionList.setSelected(reportreasonselectionscreen$reasonselectionlist$entry);
        linearlayout.addChild(SpacerElement.height(this.descriptionHeight()));
        LinearLayout linearlayout1 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout1.addChild(Button.builder(READ_INFO_LABEL, ConfirmLinkScreen.confirmLink(this, CommonLinks.REPORTING_HELP)).build());
        linearlayout1.addChild(
            Button.builder(
                    CommonComponents.GUI_DONE,
                    p_329741_ -> {
                        ReportReasonSelectionScreen.ReasonSelectionList.Entry reportreasonselectionscreen$reasonselectionlist$entry1 = this.reasonSelectionList
                            .getSelected();
                        if (reportreasonselectionscreen$reasonselectionlist$entry1 != null) {
                            this.onSelectedReason.accept(reportreasonselectionscreen$reasonselectionlist$entry1.getReason());
                        }

                        this.minecraft.setScreen(this.lastScreen);
                    }
                )
                .build()
        );
        this.layout.visitWidgets(p_329740_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_329740_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.reasonSelectionList != null) {
            this.reasonSelectionList.updateSizeAndPosition(this.width, this.listHeight(), this.layout.getHeaderHeight());
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
        guiGraphics.fill(this.descriptionLeft(), this.descriptionTop(), this.descriptionRight(), this.descriptionBottom(), -16777216);
        guiGraphics.renderOutline(this.descriptionLeft(), this.descriptionTop(), this.descriptionWidth(), this.descriptionHeight(), -1);
        guiGraphics.drawString(this.font, REASON_DESCRIPTION, this.descriptionLeft() + 4, this.descriptionTop() + 4, -1);
        ReportReasonSelectionScreen.ReasonSelectionList.Entry reportreasonselectionscreen$reasonselectionlist$entry = this.reasonSelectionList.getSelected();
        if (reportreasonselectionscreen$reasonselectionlist$entry != null) {
            int i = this.descriptionLeft() + 4 + 16;
            int j = this.descriptionRight() - 4;
            int k = this.descriptionTop() + 4 + 9 + 2;
            int l = this.descriptionBottom() - 4;
            int i1 = j - i;
            int j1 = l - k;
            int k1 = this.font.wordWrapHeight(reportreasonselectionscreen$reasonselectionlist$entry.reason.description(), i1);
            guiGraphics.drawWordWrap(this.font, reportreasonselectionscreen$reasonselectionlist$entry.reason.description(), i, k + (j1 - k1) / 2, i1, -1);
        }
    }

    private int descriptionLeft() {
        return (this.width - 320) / 2;
    }

    private int descriptionRight() {
        return (this.width + 320) / 2;
    }

    private int descriptionTop() {
        return this.descriptionBottom() - this.descriptionHeight();
    }

    private int descriptionBottom() {
        return this.height - this.layout.getFooterHeight() - 4;
    }

    private int descriptionWidth() {
        return 320;
    }

    private int descriptionHeight() {
        return 62;
    }

    int listHeight() {
        return this.layout.getContentHeight() - this.descriptionHeight() - 8;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @OnlyIn(Dist.CLIENT)
    public class ReasonSelectionList extends ObjectSelectionList<ReportReasonSelectionScreen.ReasonSelectionList.Entry> {
        public ReasonSelectionList(Minecraft minecraft) {
            super(
                minecraft,
                ReportReasonSelectionScreen.this.width,
                ReportReasonSelectionScreen.this.listHeight(),
                ReportReasonSelectionScreen.this.layout.getHeaderHeight(),
                18
            );

            for (ReportReason reportreason : ReportReason.values()) {
                this.addEntry(new ReportReasonSelectionScreen.ReasonSelectionList.Entry(reportreason));
            }
        }

        @Nullable
        public ReportReasonSelectionScreen.ReasonSelectionList.Entry findEntry(ReportReason reason) {
            return this.children().stream().filter(p_239293_ -> p_239293_.reason == reason).findFirst().orElse(null);
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        public void setSelected(@Nullable ReportReasonSelectionScreen.ReasonSelectionList.Entry selected) {
            super.setSelected(selected);
            ReportReasonSelectionScreen.this.currentlySelectedReason = selected != null ? selected.getReason() : null;
        }

        @OnlyIn(Dist.CLIENT)
        public class Entry extends ObjectSelectionList.Entry<ReportReasonSelectionScreen.ReasonSelectionList.Entry> {
            final ReportReason reason;

            public Entry(ReportReason reason) {
                this.reason = reason;
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
                int i = left + 1;
                int j = top + (height - 9) / 2 + 1;
                guiGraphics.drawString(ReportReasonSelectionScreen.this.font, this.reason.title(), i, j, -1);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("gui.abuseReport.reason.narration", this.reason.title(), this.reason.description());
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
                ReasonSelectionList.this.setSelected(this);
                return super.mouseClicked(mouseX, mouseY, button);
            }

            public ReportReason getReason() {
                return this.reason;
            }
        }
    }
}
