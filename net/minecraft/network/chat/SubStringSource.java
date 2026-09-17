package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public class SubStringSource {
    private final String plainText;
    private final List<Style> charStyles;
    private final Int2IntFunction reverseCharModifier;

    private SubStringSource(String plainText, List<Style> charStyles, Int2IntFunction reverseCharModifier) {
        this.plainText = plainText;
        this.charStyles = ImmutableList.copyOf(charStyles);
        this.reverseCharModifier = reverseCharModifier;
    }

    public String getPlainText() {
        return this.plainText;
    }

    public List<FormattedCharSequence> substring(int fromIndex, int toIndex, boolean reversed) {
        if (toIndex == 0) {
            return ImmutableList.of();
        } else {
            List<FormattedCharSequence> list = Lists.newArrayList();
            Style style = this.charStyles.get(fromIndex);
            int i = fromIndex;

            for (int j = 1; j < toIndex; j++) {
                int k = fromIndex + j;
                Style style1 = this.charStyles.get(k);
                if (!style1.equals(style)) {
                    String s = this.plainText.substring(i, k);
                    list.add(reversed ? FormattedCharSequence.backward(s, style, this.reverseCharModifier) : FormattedCharSequence.forward(s, style));
                    style = style1;
                    i = k;
                }
            }

            if (i < fromIndex + toIndex) {
                String s1 = this.plainText.substring(i, fromIndex + toIndex);
                list.add(reversed ? FormattedCharSequence.backward(s1, style, this.reverseCharModifier) : FormattedCharSequence.forward(s1, style));
            }

            return reversed ? Lists.reverse(list) : list;
        }
    }

    public static SubStringSource create(FormattedText formattedText) {
        return create(formattedText, p_178527_ -> p_178527_, p_178529_ -> p_178529_);
    }

    public static SubStringSource create(FormattedText formattedText, Int2IntFunction reverseCharModifier, UnaryOperator<String> textTransformer) {
        StringBuilder stringbuilder = new StringBuilder();
        List<Style> list = Lists.newArrayList();
        formattedText.visit((p_131249_, p_131250_) -> {
            StringDecomposer.iterateFormatted(p_131250_, p_131249_, (p_178533_, p_178534_, p_178535_) -> {
                stringbuilder.appendCodePoint(p_178535_);
                int i = Character.charCount(p_178535_);

                for (int j = 0; j < i; j++) {
                    list.add(p_178534_);
                }

                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return new SubStringSource(textTransformer.apply(stringbuilder.toString()), list, reverseCharModifier);
    }
}
