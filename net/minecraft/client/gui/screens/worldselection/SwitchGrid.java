package net.minecraft.client.gui.screens.worldselection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
class SwitchGrid {
    private static final int DEFAULT_SWITCH_BUTTON_WIDTH = 44;
    private final List<SwitchGrid.LabeledSwitch> switches;

    SwitchGrid(List<SwitchGrid.LabeledSwitch> switches) {
        this.switches = switches;
    }

    public void refreshStates() {
        this.switches.forEach(SwitchGrid.LabeledSwitch::refreshState);
    }

    public static SwitchGrid.Builder builder(int width) {
        return new SwitchGrid.Builder(width);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder {
        final int width;
        private final List<SwitchGrid.SwitchBuilder> switchBuilders = new ArrayList<>();
        int paddingLeft;
        int rowSpacing = 4;
        int rowCount;
        Optional<SwitchGrid.InfoUnderneathSettings> infoUnderneath = Optional.empty();

        public Builder(int width) {
            this.width = width;
        }

        void increaseRow() {
            this.rowCount++;
        }

        public SwitchGrid.SwitchBuilder addSwitch(Component label, BooleanSupplier stateSupplier, Consumer<Boolean> onClicked) {
            SwitchGrid.SwitchBuilder switchgrid$switchbuilder = new SwitchGrid.SwitchBuilder(label, stateSupplier, onClicked, 44);
            this.switchBuilders.add(switchgrid$switchbuilder);
            return switchgrid$switchbuilder;
        }

        public SwitchGrid.Builder withPaddingLeft(int paddingLeft) {
            this.paddingLeft = paddingLeft;
            return this;
        }

        public SwitchGrid.Builder withRowSpacing(int rowSpacing) {
            this.rowSpacing = rowSpacing;
            return this;
        }

        public SwitchGrid build(Consumer<LayoutElement> consumer) {
            GridLayout gridlayout = new GridLayout().rowSpacing(this.rowSpacing);
            gridlayout.addChild(SpacerElement.width(this.width - 44), 0, 0);
            gridlayout.addChild(SpacerElement.width(44), 0, 1);
            List<SwitchGrid.LabeledSwitch> list = new ArrayList<>();
            this.rowCount = 0;

            for (SwitchGrid.SwitchBuilder switchgrid$switchbuilder : this.switchBuilders) {
                list.add(switchgrid$switchbuilder.build(this, gridlayout, 0));
            }

            gridlayout.arrangeElements();
            consumer.accept(gridlayout);
            SwitchGrid switchgrid = new SwitchGrid(list);
            switchgrid.refreshStates();
            return switchgrid;
        }

        public SwitchGrid.Builder withInfoUnderneath(int maxInfoRows, boolean alwaysMaxHeight) {
            this.infoUnderneath = Optional.of(new SwitchGrid.InfoUnderneathSettings(maxInfoRows, alwaysMaxHeight));
            return this;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record InfoUnderneathSettings(int maxInfoRows, boolean alwaysMaxHeight) {
    }

    @OnlyIn(Dist.CLIENT)
    static record LabeledSwitch(CycleButton<Boolean> button, BooleanSupplier stateSupplier, @Nullable BooleanSupplier isActiveCondition) {
        public void refreshState() {
            this.button.setValue(this.stateSupplier.getAsBoolean());
            if (this.isActiveCondition != null) {
                this.button.active = this.isActiveCondition.getAsBoolean();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SwitchBuilder {
        private final Component label;
        private final BooleanSupplier stateSupplier;
        private final Consumer<Boolean> onClicked;
        @Nullable
        private Component info;
        @Nullable
        private BooleanSupplier isActiveCondition;
        private final int buttonWidth;

        SwitchBuilder(Component label, BooleanSupplier stateSupplier, Consumer<Boolean> onClicked, int buttonWidth) {
            this.label = label;
            this.stateSupplier = stateSupplier;
            this.onClicked = onClicked;
            this.buttonWidth = buttonWidth;
        }

        public SwitchGrid.SwitchBuilder withIsActiveCondition(BooleanSupplier isActiveCondition) {
            this.isActiveCondition = isActiveCondition;
            return this;
        }

        public SwitchGrid.SwitchBuilder withInfo(Component info) {
            this.info = info;
            return this;
        }

        SwitchGrid.LabeledSwitch build(SwitchGrid.Builder p_builder, GridLayout gridLayout, int column) {
            p_builder.increaseRow();
            StringWidget stringwidget = new StringWidget(this.label, Minecraft.getInstance().font).alignLeft();
            gridLayout.addChild(stringwidget, p_builder.rowCount, column, gridLayout.newCellSettings().align(0.0F, 0.5F).paddingLeft(p_builder.paddingLeft));
            Optional<SwitchGrid.InfoUnderneathSettings> optional = p_builder.infoUnderneath;
            CycleButton.Builder<Boolean> builder = CycleButton.onOffBuilder(this.stateSupplier.getAsBoolean());
            builder.displayOnlyValue();
            boolean flag = this.info != null && optional.isEmpty();
            if (flag) {
                Tooltip tooltip = Tooltip.create(this.info);
                builder.withTooltip(p_269644_ -> tooltip);
            }

            if (this.info != null && !flag) {
                builder.withCustomNarration(p_269645_ -> CommonComponents.joinForNarration(this.label, p_269645_.createDefaultNarrationMessage(), this.info));
            } else {
                builder.withCustomNarration(p_268230_ -> CommonComponents.joinForNarration(this.label, p_268230_.createDefaultNarrationMessage()));
            }

            CycleButton<Boolean> cyclebutton = builder.create(
                0, 0, this.buttonWidth, 20, Component.empty(), (p_267942_, p_268251_) -> this.onClicked.accept(p_268251_)
            );
            if (this.isActiveCondition != null) {
                cyclebutton.active = this.isActiveCondition.getAsBoolean();
            }

            gridLayout.addChild(cyclebutton, p_builder.rowCount, column + 1, gridLayout.newCellSettings().alignHorizontallyRight());
            if (this.info != null) {
                optional.ifPresent(
                    p_269649_ -> {
                        Component component = this.info.copy().withStyle(ChatFormatting.GRAY);
                        Font font = Minecraft.getInstance().font;
                        MultiLineTextWidget multilinetextwidget = new MultiLineTextWidget(component, font);
                        multilinetextwidget.setMaxWidth(p_builder.width - p_builder.paddingLeft - this.buttonWidth);
                        multilinetextwidget.setMaxRows(p_269649_.maxInfoRows());
                        p_builder.increaseRow();
                        int i = p_269649_.alwaysMaxHeight ? 9 * p_269649_.maxInfoRows - multilinetextwidget.getHeight() : 0;
                        gridLayout.addChild(
                            multilinetextwidget, p_builder.rowCount, column, gridLayout.newCellSettings().paddingTop(-p_builder.rowSpacing).paddingBottom(i)
                        );
                    }
                );
            }

            return new SwitchGrid.LabeledSwitch(cyclebutton, this.stateSupplier, this.isActiveCondition);
        }
    }
}
