package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSequence {
    FormattedCharSequence EMPTY = p_13704_ -> true;

    boolean accept(FormattedCharSink sink);

    static FormattedCharSequence codepoint(int codePoint, Style style) {
        return p_13730_ -> p_13730_.accept(0, style, codePoint);
    }

    static FormattedCharSequence forward(String text, Style style) {
        return text.isEmpty() ? EMPTY : p_13739_ -> StringDecomposer.iterate(text, style, p_13739_);
    }

    static FormattedCharSequence forward(String text, Style style, Int2IntFunction codePointMapper) {
        return text.isEmpty() ? EMPTY : p_144730_ -> StringDecomposer.iterate(text, style, decorateOutput(p_144730_, codePointMapper));
    }

    static FormattedCharSequence backward(String text, Style style) {
        return text.isEmpty() ? EMPTY : p_144716_ -> StringDecomposer.iterateBackwards(text, style, p_144716_);
    }

    static FormattedCharSequence backward(String text, Style style, Int2IntFunction codePointMapper) {
        return text.isEmpty() ? EMPTY : p_13721_ -> StringDecomposer.iterateBackwards(text, style, decorateOutput(p_13721_, codePointMapper));
    }

    static FormattedCharSink decorateOutput(FormattedCharSink sink, Int2IntFunction codePointMapper) {
        return (p_13711_, p_13712_, p_13713_) -> sink.accept(p_13711_, p_13712_, codePointMapper.apply(Integer.valueOf(p_13713_)));
    }

    static FormattedCharSequence composite() {
        return EMPTY;
    }

    static FormattedCharSequence composite(FormattedCharSequence sequence) {
        return sequence;
    }

    static FormattedCharSequence composite(FormattedCharSequence first, FormattedCharSequence second) {
        return fromPair(first, second);
    }

    static FormattedCharSequence composite(FormattedCharSequence... parts) {
        return fromList(ImmutableList.copyOf(parts));
    }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        int i = parts.size();
        switch (i) {
            case 0:
                return EMPTY;
            case 1:
                return parts.get(0);
            case 2:
                return fromPair(parts.get(0), parts.get(1));
            default:
                return fromList(ImmutableList.copyOf(parts));
        }
    }

    static FormattedCharSequence fromPair(FormattedCharSequence first, FormattedCharSequence second) {
        return p_13702_ -> first.accept(p_13702_) && second.accept(p_13702_);
    }

    static FormattedCharSequence fromList(List<FormattedCharSequence> parts) {
        return p_13726_ -> {
            for (FormattedCharSequence formattedcharsequence : parts) {
                if (!formattedcharsequence.accept(p_13726_)) {
                    return false;
                }
            }

            return true;
        };
    }
}
