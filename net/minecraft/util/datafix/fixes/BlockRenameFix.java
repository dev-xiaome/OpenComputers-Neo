package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class BlockRenameFix extends DataFix {
    private final String name;

    public BlockRenameFix(Schema outputSchema, String name) {
        super(outputSchema, false);
        this.name = name;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_NAME);
        Type<Pair<String, String>> type1 = DSL.named(References.BLOCK_NAME.typeName(), NamespacedSchema.namespacedString());
        if (!Objects.equals(type, type1)) {
            throw new IllegalStateException("block type is not what was expected.");
        } else {
            TypeRewriteRule typerewriterule = this.fixTypeEverywhere(
                this.name + " for block", type1, p_14923_ -> p_145145_ -> p_145145_.mapSecond(this::renameBlock)
            );
            TypeRewriteRule typerewriterule1 = this.fixTypeEverywhereTyped(
                this.name + " for block_state",
                this.getInputSchema().getType(References.BLOCK_STATE),
                p_14913_ -> p_14913_.update(DSL.remainderFinder(), this::fixBlockState)
            );
            TypeRewriteRule typerewriterule2 = this.fixTypeEverywhereTyped(
                this.name + " for flat_block_state",
                this.getInputSchema().getType(References.FLAT_BLOCK_STATE),
                p_315924_ -> p_315924_.update(
                        DSL.remainderFinder(),
                        p_337603_ -> DataFixUtils.orElse(p_337603_.asString().result().map(this::fixFlatBlockState).map(p_337603_::createString), p_337603_)
                    )
            );
            return TypeRewriteRule.seq(typerewriterule, typerewriterule1, typerewriterule2);
        }
    }

    private Dynamic<?> fixBlockState(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.get("Name").asString().result();
        return optional.isPresent() ? dynamic.set("Name", dynamic.createString(this.renameBlock(optional.get()))) : dynamic;
    }

    private String fixFlatBlockState(String name) {
        int i = name.indexOf(91);
        int j = name.indexOf(123);
        int k = name.length();
        if (i > 0) {
            k = i;
        }

        if (j > 0) {
            k = Math.min(k, j);
        }

        String s = name.substring(0, k);
        String s1 = this.renameBlock(s);
        return s1 + name.substring(k);
    }

    protected abstract String renameBlock(String name);

    public static DataFix create(Schema p_outputSchema, String p_name, final Function<String, String> renamer) {
        return new BlockRenameFix(p_outputSchema, p_name) {
            @Override
            protected String renameBlock(String p_316686_) {
                return renamer.apply(p_316686_);
            }
        };
    }
}
