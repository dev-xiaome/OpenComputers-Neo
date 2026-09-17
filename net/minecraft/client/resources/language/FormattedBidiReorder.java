package net.minecraft.client.resources.language;

import com.google.common.collect.Lists;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BidiRun;
import java.util.List;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.SubStringSource;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FormattedBidiReorder {
    public static FormattedCharSequence reorder(FormattedText text, boolean defaultRightToLeft) {
        SubStringSource substringsource = SubStringSource.create(text, UCharacter::getMirror, FormattedBidiReorder::shape);
        Bidi bidi = new Bidi(substringsource.getPlainText(), defaultRightToLeft ? 127 : 126);
        bidi.setReorderingMode(0);
        List<FormattedCharSequence> list = Lists.newArrayList();
        int i = bidi.countRuns();

        for (int j = 0; j < i; j++) {
            BidiRun bidirun = bidi.getVisualRun(j);
            list.addAll(substringsource.substring(bidirun.getStart(), bidirun.getLength(), bidirun.isOddRun()));
        }

        return FormattedCharSequence.composite(list);
    }

    private static String shape(String text) {
        try {
            return new ArabicShaping(8).shape(text);
        } catch (Exception exception) {
            return text;
        }
    }
}
