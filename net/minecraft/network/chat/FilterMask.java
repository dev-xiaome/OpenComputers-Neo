package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.BitSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.StringUtils;

public class FilterMask {
    public static final Codec<FilterMask> CODEC = StringRepresentable.fromEnum(FilterMask.Type::values).dispatch(FilterMask::type, FilterMask.Type::codec);
    public static final FilterMask FULLY_FILTERED = new FilterMask(new BitSet(0), FilterMask.Type.FULLY_FILTERED);
    public static final FilterMask PASS_THROUGH = new FilterMask(new BitSet(0), FilterMask.Type.PASS_THROUGH);
    public static final Style FILTERED_STYLE = Style.EMPTY
        .withColor(ChatFormatting.DARK_GRAY)
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.filtered")));
    static final MapCodec<FilterMask> PASS_THROUGH_CODEC = MapCodec.unit(PASS_THROUGH);
    static final MapCodec<FilterMask> FULLY_FILTERED_CODEC = MapCodec.unit(FULLY_FILTERED);
    static final MapCodec<FilterMask> PARTIALLY_FILTERED_CODEC = ExtraCodecs.BIT_SET.xmap(FilterMask::new, FilterMask::mask).fieldOf("value");
    private static final char HASH = '#';
    private final BitSet mask;
    private final FilterMask.Type type;

    private FilterMask(BitSet mask, FilterMask.Type type) {
        this.mask = mask;
        this.type = type;
    }

    private FilterMask(BitSet mask) {
        this.mask = mask;
        this.type = FilterMask.Type.PARTIALLY_FILTERED;
    }

    public FilterMask(int size) {
        this(new BitSet(size), FilterMask.Type.PARTIALLY_FILTERED);
    }

    private FilterMask.Type type() {
        return this.type;
    }

    private BitSet mask() {
        return this.mask;
    }

    public static FilterMask read(FriendlyByteBuf buffer) {
        FilterMask.Type filtermask$type = buffer.readEnum(FilterMask.Type.class);

        return switch (filtermask$type) {
            case PASS_THROUGH -> PASS_THROUGH;
            case FULLY_FILTERED -> FULLY_FILTERED;
            case PARTIALLY_FILTERED -> new FilterMask(buffer.readBitSet(), FilterMask.Type.PARTIALLY_FILTERED);
        };
    }

    public static void write(FriendlyByteBuf buffer, FilterMask mask) {
        buffer.writeEnum(mask.type);
        if (mask.type == FilterMask.Type.PARTIALLY_FILTERED) {
            buffer.writeBitSet(mask.mask);
        }
    }

    public void setFiltered(int bitIndex) {
        this.mask.set(bitIndex);
    }

    @Nullable
    public String apply(String text) {
        return switch (this.type) {
            case PASS_THROUGH -> text;
            case FULLY_FILTERED -> null;
            case PARTIALLY_FILTERED -> {
                char[] achar = text.toCharArray();

                for (int i = 0; i < achar.length && i < this.mask.length(); i++) {
                    if (this.mask.get(i)) {
                        achar[i] = '#';
                    }
                }

                yield new String(achar);
            }
        };
    }

    @Nullable
    public Component applyWithFormatting(String text) {
        return switch (this.type) {
            case PASS_THROUGH -> Component.literal(text);
            case FULLY_FILTERED -> null;
            case PARTIALLY_FILTERED -> {
                MutableComponent mutablecomponent = Component.empty();
                int i = 0;
                boolean flag = this.mask.get(0);

                while (true) {
                    int j = flag ? this.mask.nextClearBit(i) : this.mask.nextSetBit(i);
                    j = j < 0 ? text.length() : j;
                    if (j == i) {
                        yield mutablecomponent;
                    }

                    if (flag) {
                        mutablecomponent.append(Component.literal(StringUtils.repeat('#', j - i)).withStyle(FILTERED_STYLE));
                    } else {
                        mutablecomponent.append(text.substring(i, j));
                    }

                    flag = !flag;
                    i = j;
                }
            }
        };
    }

    public boolean isEmpty() {
        return this.type == FilterMask.Type.PASS_THROUGH;
    }

    public boolean isFullyFiltered() {
        return this.type == FilterMask.Type.FULLY_FILTERED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            FilterMask filtermask = (FilterMask)other;
            return this.mask.equals(filtermask.mask) && this.type == filtermask.type;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.mask.hashCode();
        return 31 * i + this.type.hashCode();
    }

    static enum Type implements StringRepresentable {
        PASS_THROUGH("pass_through", () -> FilterMask.PASS_THROUGH_CODEC),
        FULLY_FILTERED("fully_filtered", () -> FilterMask.FULLY_FILTERED_CODEC),
        PARTIALLY_FILTERED("partially_filtered", () -> FilterMask.PARTIALLY_FILTERED_CODEC);

        private final String serializedName;
        private final Supplier<MapCodec<FilterMask>> codec;

        private Type(String serializedName, Supplier<MapCodec<FilterMask>> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        private MapCodec<FilterMask> codec() {
            return this.codec.get();
        }
    }
}
