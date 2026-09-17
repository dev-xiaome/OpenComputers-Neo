package net.minecraft.client.gui.components;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CycleButton<T> extends AbstractButton {
    public static final BooleanSupplier DEFAULT_ALT_LIST_SELECTOR = Screen::hasAltDown;
    private static final List<Boolean> BOOLEAN_OPTIONS = ImmutableList.of(Boolean.TRUE, Boolean.FALSE);
    private final Component name;
    private int index;
    private T value;
    private final CycleButton.ValueListSupplier<T> values;
    private final Function<T, Component> valueStringifier;
    private final Function<CycleButton<T>, MutableComponent> narrationProvider;
    private final CycleButton.OnValueChange<T> onValueChange;
    private final boolean displayOnlyValue;
    private final OptionInstance.TooltipSupplier<T> tooltipSupplier;

    CycleButton(
        int x,
        int y,
        int width,
        int height,
        Component message,
        Component name,
        int index,
        T value,
        CycleButton.ValueListSupplier<T> values,
        Function<T, Component> valueStringifier,
        Function<CycleButton<T>, MutableComponent> narrationProvider,
        CycleButton.OnValueChange<T> onValueChange,
        OptionInstance.TooltipSupplier<T> tooltipSupplier,
        boolean displayOnlyValue
    ) {
        super(x, y, width, height, message);
        this.name = name;
        this.index = index;
        this.value = value;
        this.values = values;
        this.valueStringifier = valueStringifier;
        this.narrationProvider = narrationProvider;
        this.onValueChange = onValueChange;
        this.displayOnlyValue = displayOnlyValue;
        this.tooltipSupplier = tooltipSupplier;
        this.updateTooltip();
    }

    private void updateTooltip() {
        this.setTooltip(this.tooltipSupplier.apply(this.value));
    }

    @Override
    public void onPress() {
        if (Screen.hasShiftDown()) {
            this.cycleValue(-1);
        } else {
            this.cycleValue(1);
        }
    }

    private void cycleValue(int delta) {
        List<T> list = this.values.getSelectedList();
        this.index = Mth.positiveModulo(this.index + delta, list.size());
        T t = list.get(this.index);
        this.updateValue(t);
        this.onValueChange.onValueChange(this, t);
    }

    private T getCycledValue(int delta) {
        List<T> list = this.values.getSelectedList();
        return list.get(Mth.positiveModulo(this.index + delta, list.size()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0.0) {
            this.cycleValue(-1);
        } else if (scrollY < 0.0) {
            this.cycleValue(1);
        }

        return true;
    }

    public void setValue(T value) {
        List<T> list = this.values.getSelectedList();
        int i = list.indexOf(value);
        if (i != -1) {
            this.index = i;
        }

        this.updateValue(value);
    }

    private void updateValue(T value) {
        Component component = this.createLabelForValue(value);
        this.setMessage(component);
        this.value = value;
        this.updateTooltip();
    }

    private Component createLabelForValue(T value) {
        return (Component)(this.displayOnlyValue ? this.valueStringifier.apply(value) : this.createFullName(value));
    }

    private MutableComponent createFullName(T value) {
        return CommonComponents.optionNameValue(this.name, this.valueStringifier.apply(value));
    }

    public T getValue() {
        return this.value;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return this.narrationProvider.apply(this);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            T t = this.getCycledValue(1);
            Component component = this.createLabelForValue(t);
            if (this.isFocused()) {
                narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.cycle_button.usage.focused", component));
            } else {
                narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.cycle_button.usage.hovered", component));
            }
        }
    }

    public MutableComponent createDefaultNarrationMessage() {
        return wrapDefaultNarrationMessage((Component)(this.displayOnlyValue ? this.createFullName(this.value) : this.getMessage()));
    }

    public static <T> CycleButton.Builder<T> builder(Function<T, Component> valueStringifier) {
        return new CycleButton.Builder<>(valueStringifier);
    }

    public static CycleButton.Builder<Boolean> booleanBuilder(Component componentOn, Component componentOff) {
        return new CycleButton.Builder<Boolean>(p_168902_ -> p_168902_ ? componentOn : componentOff).withValues(BOOLEAN_OPTIONS);
    }

    public static CycleButton.Builder<Boolean> onOffBuilder() {
        return new CycleButton.Builder<Boolean>(p_168891_ -> p_168891_ ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).withValues(BOOLEAN_OPTIONS);
    }

    public static CycleButton.Builder<Boolean> onOffBuilder(boolean initialValue) {
        return onOffBuilder().withInitialValue(initialValue);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder<T> {
        private int initialIndex;
        @Nullable
        private T initialValue;
        private final Function<T, Component> valueStringifier;
        private OptionInstance.TooltipSupplier<T> tooltipSupplier = p_168964_ -> null;
        private Function<CycleButton<T>, MutableComponent> narrationProvider = CycleButton::createDefaultNarrationMessage;
        private CycleButton.ValueListSupplier<T> values = CycleButton.ValueListSupplier.create(ImmutableList.of());
        private boolean displayOnlyValue;

        public Builder(Function<T, Component> valueStringifier) {
            this.valueStringifier = valueStringifier;
        }

        public CycleButton.Builder<T> withValues(Collection<T> values) {
            return this.withValues(CycleButton.ValueListSupplier.create(values));
        }

        @SafeVarargs
        public final CycleButton.Builder<T> withValues(T... values) {
            return this.withValues(ImmutableList.copyOf(values));
        }

        public CycleButton.Builder<T> withValues(List<T> defaultList, List<T> selectedList) {
            return this.withValues(CycleButton.ValueListSupplier.create(CycleButton.DEFAULT_ALT_LIST_SELECTOR, defaultList, selectedList));
        }

        public CycleButton.Builder<T> withValues(BooleanSupplier altListSelector, List<T> defaultList, List<T> selectedList) {
            return this.withValues(CycleButton.ValueListSupplier.create(altListSelector, defaultList, selectedList));
        }

        public CycleButton.Builder<T> withValues(CycleButton.ValueListSupplier<T> values) {
            this.values = values;
            return this;
        }

        public CycleButton.Builder<T> withTooltip(OptionInstance.TooltipSupplier<T> tooltipSupplier) {
            this.tooltipSupplier = tooltipSupplier;
            return this;
        }

        public CycleButton.Builder<T> withInitialValue(T initialValue) {
            this.initialValue = initialValue;
            int i = this.values.getDefaultList().indexOf(initialValue);
            if (i != -1) {
                this.initialIndex = i;
            }

            return this;
        }

        public CycleButton.Builder<T> withCustomNarration(Function<CycleButton<T>, MutableComponent> narrationProvider) {
            this.narrationProvider = narrationProvider;
            return this;
        }

        public CycleButton.Builder<T> displayOnlyValue() {
            this.displayOnlyValue = true;
            return this;
        }

        public CycleButton<T> create(Component message, CycleButton.OnValueChange<T> onValueChange) {
            return this.create(0, 0, 150, 20, message, onValueChange);
        }

        public CycleButton<T> create(int x, int y, int width, int height, Component name) {
            return this.create(x, y, width, height, name, (p_168946_, p_168947_) -> {
            });
        }

        public CycleButton<T> create(int x, int y, int width, int height, Component name, CycleButton.OnValueChange<T> onValueChange) {
            List<T> list = this.values.getDefaultList();
            if (list.isEmpty()) {
                throw new IllegalStateException("No values for cycle button");
            } else {
                T t = this.initialValue != null ? this.initialValue : list.get(this.initialIndex);
                Component component = this.valueStringifier.apply(t);
                Component component1 = (Component)(this.displayOnlyValue ? component : CommonComponents.optionNameValue(name, component));
                return new CycleButton<>(
                    x,
                    y,
                    width,
                    height,
                    component1,
                    name,
                    this.initialIndex,
                    t,
                    this.values,
                    this.valueStringifier,
                    this.narrationProvider,
                    onValueChange,
                    this.tooltipSupplier,
                    this.displayOnlyValue
                );
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface OnValueChange<T> {
        void onValueChange(CycleButton<T> cycleButton, T value);
    }

    @OnlyIn(Dist.CLIENT)
    public interface ValueListSupplier<T> {
        List<T> getSelectedList();

        List<T> getDefaultList();

        static <T> CycleButton.ValueListSupplier<T> create(Collection<T> values) {
            final List<T> list = ImmutableList.copyOf(values);
            return new CycleButton.ValueListSupplier<T>() {
                @Override
                public List<T> getSelectedList() {
                    return list;
                }

                @Override
                public List<T> getDefaultList() {
                    return list;
                }
            };
        }

        static <T> CycleButton.ValueListSupplier<T> create(final BooleanSupplier altListSelector, List<T> defaultList, List<T> selectedList) {
            final List<T> list = ImmutableList.copyOf(defaultList);
            final List<T> list1 = ImmutableList.copyOf(selectedList);
            return new CycleButton.ValueListSupplier<T>() {
                @Override
                public List<T> getSelectedList() {
                    return altListSelector.getAsBoolean() ? list1 : list;
                }

                @Override
                public List<T> getDefaultList() {
                    return list;
                }
            };
        }
    }
}
