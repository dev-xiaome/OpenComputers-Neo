package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EditGameRulesScreen extends Screen {
    private static final Component TITLE = Component.translatable("editGamerule.title");
    private static final int SPACING = 8;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Consumer<Optional<GameRules>> exitCallback;
    private final Set<EditGameRulesScreen.RuleEntry> invalidEntries = Sets.newHashSet();
    private final GameRules gameRules;
    @Nullable
    private EditGameRulesScreen.RuleList ruleList;
    @Nullable
    private Button doneButton;

    public EditGameRulesScreen(GameRules gameRules, Consumer<Optional<GameRules>> exitCallback) {
        super(TITLE);
        this.gameRules = gameRules;
        this.exitCallback = exitCallback;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.ruleList = this.layout.addToContents(new EditGameRulesScreen.RuleList(this.gameRules));
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.doneButton = linearlayout.addChild(
            Button.builder(CommonComponents.GUI_DONE, p_101059_ -> this.exitCallback.accept(Optional.of(this.gameRules))).build()
        );
        linearlayout.addChild(Button.builder(CommonComponents.GUI_CANCEL, p_329749_ -> this.onClose()).build());
        this.layout.visitWidgets(p_321377_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_321377_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.ruleList != null) {
            this.ruleList.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.exitCallback.accept(Optional.empty());
    }

    private void updateDoneButton() {
        if (this.doneButton != null) {
            this.doneButton.active = this.invalidEntries.isEmpty();
        }
    }

    void markInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
        this.invalidEntries.add(ruleEntry);
        this.updateDoneButton();
    }

    void clearInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
        this.invalidEntries.remove(ruleEntry);
        this.updateDoneButton();
    }

    @OnlyIn(Dist.CLIENT)
    public class BooleanRuleEntry extends EditGameRulesScreen.GameRuleEntry {
        private final CycleButton<Boolean> checkbox;

        public BooleanRuleEntry(Component label, List<FormattedCharSequence> tooltip, String p_101103_, GameRules.BooleanValue value) {
            super(tooltip, label);
            this.checkbox = CycleButton.onOffBuilder(value.get())
                .displayOnlyValue()
                .withCustomNarration(p_170219_ -> p_170219_.createDefaultNarrationMessage().append("\n").append(p_101103_))
                .create(10, 5, 44, 20, label, (p_170215_, p_170216_) -> value.set(p_170216_, null));
            this.children.add(this.checkbox);
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
            this.renderLabel(guiGraphics, top, left);
            this.checkbox.setX(left + width - 45);
            this.checkbox.setY(top);
            this.checkbox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class CategoryRuleEntry extends EditGameRulesScreen.RuleEntry {
        final Component label;

        public CategoryRuleEntry(Component label) {
            super(null);
            this.label = label;
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
            guiGraphics.drawCenteredString(EditGameRulesScreen.this.minecraft.font, this.label, left + width / 2, top + 5, -1);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput p_170225_) {
                    p_170225_.add(NarratedElementType.TITLE, CategoryRuleEntry.this.label);
                }
            });
        }
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    interface EntryFactory<T extends GameRules.Value<T>> {
        EditGameRulesScreen.RuleEntry create(Component label, List<FormattedCharSequence> tooltip, String p_101157_, T p_101158_);
    }

    @OnlyIn(Dist.CLIENT)
    public abstract class GameRuleEntry extends EditGameRulesScreen.RuleEntry {
        private final List<FormattedCharSequence> label;
        protected final List<AbstractWidget> children = Lists.newArrayList();

        public GameRuleEntry(@Nullable List<FormattedCharSequence> tooltip, Component label) {
            super(tooltip);
            this.label = EditGameRulesScreen.this.minecraft.font.split(label, 175);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }

        protected void renderLabel(GuiGraphics guiGraphics, int x, int y) {
            if (this.label.size() == 1) {
                guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), y, x + 5, -1, false);
            } else if (this.label.size() >= 2) {
                guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), y, x, -1, false);
                guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(1), y, x + 10, -1, false);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class IntegerRuleEntry extends EditGameRulesScreen.GameRuleEntry {
        private final EditBox input;

        public IntegerRuleEntry(Component label, List<FormattedCharSequence> tooltip, String p_101177_, GameRules.IntegerValue value) {
            super(tooltip, label);
            this.input = new EditBox(EditGameRulesScreen.this.minecraft.font, 10, 5, 44, 20, label.copy().append("\n").append(p_101177_).append("\n"));
            this.input.setValue(Integer.toString(value.get()));
            this.input.setResponder(p_101181_ -> {
                if (value.tryDeserialize(p_101181_)) {
                    this.input.setTextColor(14737632);
                    EditGameRulesScreen.this.clearInvalid(this);
                } else {
                    this.input.setTextColor(-65536);
                    EditGameRulesScreen.this.markInvalid(this);
                }
            });
            this.children.add(this.input);
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
            this.renderLabel(guiGraphics, top, left);
            this.input.setX(left + width - 45);
            this.input.setY(top);
            this.input.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public abstract static class RuleEntry extends ContainerObjectSelectionList.Entry<EditGameRulesScreen.RuleEntry> {
        @Nullable
        final List<FormattedCharSequence> tooltip;

        public RuleEntry(@Nullable List<FormattedCharSequence> tooltip) {
            this.tooltip = tooltip;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class RuleList extends ContainerObjectSelectionList<EditGameRulesScreen.RuleEntry> {
        private static final int ITEM_HEIGHT = 24;

        public RuleList(GameRules gameRules) {
            super(
                Minecraft.getInstance(),
                EditGameRulesScreen.this.width,
                EditGameRulesScreen.this.layout.getContentHeight(),
                EditGameRulesScreen.this.layout.getHeaderHeight(),
                24
            );
            final Map<GameRules.Category, Map<GameRules.Key<?>, EditGameRulesScreen.RuleEntry>> map = Maps.newHashMap();
            GameRules.visitGameRuleTypes(
                new GameRules.GameRuleTypeVisitor() {
                    @Override
                    public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                        this.addEntry(
                            key,
                            (p_101228_, p_101229_, p_101230_, p_101231_) -> EditGameRulesScreen.this.new BooleanRuleEntry(
                                    p_101228_, p_101229_, p_101230_, p_101231_
                                )
                        );
                    }

                    @Override
                    public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                        this.addEntry(
                            key,
                            (p_101233_, p_101234_, p_101235_, p_101236_) -> EditGameRulesScreen.this.new IntegerRuleEntry(
                                    p_101233_, p_101234_, p_101235_, p_101236_
                                )
                        );
                    }

                    private <T extends GameRules.Value<T>> void addEntry(GameRules.Key<T> key, EditGameRulesScreen.EntryFactory<T> factory) {
                        Component component = Component.translatable(key.getDescriptionId());
                        Component component1 = Component.literal(key.getId()).withStyle(ChatFormatting.YELLOW);
                        T t = gameRules.getRule(key);
                        String s = t.serialize();
                        Component component2 = Component.translatable("editGamerule.default", Component.literal(s)).withStyle(ChatFormatting.GRAY);
                        String s1 = key.getDescriptionId() + ".description";
                        List<FormattedCharSequence> list;
                        String s2;
                        if (I18n.exists(s1)) {
                            Builder<FormattedCharSequence> builder = ImmutableList.<FormattedCharSequence>builder().add(component1.getVisualOrderText());
                            Component component3 = Component.translatable(s1);
                            EditGameRulesScreen.this.font.split(component3, 150).forEach(builder::add);
                            list = builder.add(component2.getVisualOrderText()).build();
                            s2 = component3.getString() + "\n" + component2.getString();
                        } else {
                            list = ImmutableList.of(component1.getVisualOrderText(), component2.getVisualOrderText());
                            s2 = component2.getString();
                        }

                        map.computeIfAbsent(key.getCategory(), p_101223_ -> Maps.newHashMap()).put(key, factory.create(component, list, s2, t));
                    }
                }
            );
            map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                    p_101210_ -> {
                        this.addEntry(
                            EditGameRulesScreen.this.new CategoryRuleEntry(
                                Component.translatable(p_101210_.getKey().getDescriptionId()).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)
                            )
                        );
                        p_101210_.getValue()
                            .entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRules.Key::getId)))
                            .forEach(p_170229_ -> this.addEntry(p_170229_.getValue()));
                    }
                );
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            EditGameRulesScreen.RuleEntry editgamerulesscreen$ruleentry = this.getHovered();
            if (editgamerulesscreen$ruleentry != null && editgamerulesscreen$ruleentry.tooltip != null) {
                EditGameRulesScreen.this.setTooltipForNextRenderPass(editgamerulesscreen$ruleentry.tooltip);
            }
        }
    }
}
