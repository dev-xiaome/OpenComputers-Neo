package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.util.Mth;

public class BitStorageAlignFix extends DataFix {
    private static final int BIT_TO_LONG_SHIFT = 6;
    private static final int SECTION_WIDTH = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_SIZE = 4096;
    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_SIZE = 256;

    public BitStorageAlignFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        OpticFinder<?> opticfinder = DSL.fieldFinder("Level", type1);
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("Sections");
        Type<?> type2 = ((ListType)opticfinder1.type()).getElement();
        OpticFinder<?> opticfinder2 = DSL.typeFinder(type2);
        Type<Pair<String, Dynamic<?>>> type3 = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        OpticFinder<List<Pair<String, Dynamic<?>>>> opticfinder3 = DSL.fieldFinder("Palette", DSL.list(type3));
        return this.fixTypeEverywhereTyped(
            "BitStorageAlignFix",
            type,
            this.getOutputSchema().getType(References.CHUNK),
            p_14749_ -> p_14749_.updateTyped(
                    opticfinder, p_145120_ -> this.updateHeightmaps(updateSections(opticfinder1, opticfinder2, opticfinder3, p_145120_))
                )
        );
    }

    private Typed<?> updateHeightmaps(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            p_14765_ -> p_14765_.update(
                    "Heightmaps",
                    p_145113_ -> p_145113_.updateMapValues(
                            p_145110_ -> p_145110_.mapSecond(p_145123_ -> updateBitStorage(p_14765_, (Dynamic<?>)p_145123_, 256, 9))
                        )
                )
        );
    }

    private static Typed<?> updateSections(
        OpticFinder<?> sections, OpticFinder<?> p_14752_, OpticFinder<List<Pair<String, Dynamic<?>>>> palette, Typed<?> data
    ) {
        return data.updateTyped(
            sections,
            p_14758_ -> p_14758_.updateTyped(
                    p_14752_,
                    p_145103_ -> {
                        int i = p_145103_.getOptional(palette).map(p_145115_ -> Math.max(4, DataFixUtils.ceillog2(p_145115_.size()))).orElse(0);
                        return i != 0 && !Mth.isPowerOfTwo(i)
                            ? p_145103_.update(
                                DSL.remainderFinder(),
                                p_145100_ -> p_145100_.update("BlockStates", p_145107_ -> updateBitStorage(p_145100_, p_145107_, 4096, i))
                            )
                            : p_145103_;
                    }
                )
        );
    }

    private static Dynamic<?> updateBitStorage(Dynamic<?> output, Dynamic<?> data, int numBits, int bitWidth) {
        long[] along = data.asLongStream().toArray();
        long[] along1 = addPadding(numBits, bitWidth, along);
        return output.createLongList(LongStream.of(along1));
    }

    public static long[] addPadding(int numBits, int bitWidth, long[] inputData) {
        int i = inputData.length;
        if (i == 0) {
            return inputData;
        } else {
            long j = (1L << bitWidth) - 1L;
            int k = 64 / bitWidth;
            int l = (numBits + k - 1) / k;
            long[] along = new long[l];
            int i1 = 0;
            int j1 = 0;
            long k1 = 0L;
            int l1 = 0;
            long i2 = inputData[0];
            long j2 = i > 1 ? inputData[1] : 0L;

            for (int k2 = 0; k2 < numBits; k2++) {
                int l2 = k2 * bitWidth;
                int i3 = l2 >> 6;
                int j3 = (k2 + 1) * bitWidth - 1 >> 6;
                int k3 = l2 ^ i3 << 6;
                if (i3 != l1) {
                    i2 = j2;
                    j2 = i3 + 1 < i ? inputData[i3 + 1] : 0L;
                    l1 = i3;
                }

                long l3;
                if (i3 == j3) {
                    l3 = i2 >>> k3 & j;
                } else {
                    int i4 = 64 - k3;
                    l3 = (i2 >>> k3 | j2 << i4) & j;
                }

                int j4 = j1 + bitWidth;
                if (j4 >= 64) {
                    along[i1++] = k1;
                    k1 = l3;
                    j1 = bitWidth;
                } else {
                    k1 |= l3 << j1;
                    j1 = j4;
                }
            }

            if (k1 != 0L) {
                along[i1] = k1;
            }

            return along;
        }
    }
}
