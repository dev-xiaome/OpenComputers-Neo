package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.BitSet;
import net.minecraft.Util;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String entityName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = entityName;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> type1 = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type2 = this.getOutputSchema().getType(this.type);
        Type<?> type3 = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        OpticFinder<?> opticfinder = DSL.namedChoice(this.entityName, type1);
        Type<?> type4 = type1.all(typePatcher(type, type2), true, false).view().newType();
        return this.fix(type, type2, opticfinder, type3, type4);
    }

    private <S, T, A, B> TypeRewriteRule fix(Type<S> inputType, Type<T> outputType, OpticFinder<A> finder, Type<B> outputChoiceType, Type<?> newType) {
        return this.fixTypeEverywhere(this.name, inputType, outputType, p_323223_ -> p_323218_ -> {
                Typed<S> typed = new Typed<>(inputType, p_323223_, p_323218_);
                return (T)typed.update(finder, outputChoiceType, p_323212_ -> {
                    Typed<A> typed1 = new Typed<>((Type<A>)newType, p_323223_, p_323212_);
                    return Util.<A, B>writeAndReadTypedOrThrow(typed1, outputChoiceType, this::fix).getValue();
                }).getValue();
            });
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> type, Type<B> newType) {
        RewriteResult<A, B> rewriteresult = RewriteResult.create(View.create("Patcher", type, newType, p_323208_ -> p_323224_ -> {
                throw new UnsupportedOperationException();
            }), new BitSet());
        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(type, rewriteresult), PointFreeRule.nop(), true, true);
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> tag);
}
