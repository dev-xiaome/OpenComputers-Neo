package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.function.Function;

public class ChunkRenamesFix extends DataFix {
    public ChunkRenamesFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticfinder = type.findField("Level");
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("Structures");
        Type<?> type1 = this.getOutputSchema().getType(References.CHUNK);
        Type<?> type2 = type1.findFieldType("structures");
        return this.fixTypeEverywhereTyped("Chunk Renames; purge Level-tag", type, type1, p_199427_ -> {
            Typed<?> typed = p_199427_.getTyped(opticfinder);
            Typed<?> typed1 = appendChunkName(typed);
            typed1 = typed1.set(DSL.remainderFinder(), mergeRemainders(p_199427_, typed.get(DSL.remainderFinder())));
            typed1 = renameField(typed1, "TileEntities", "block_entities");
            typed1 = renameField(typed1, "TileTicks", "block_ticks");
            typed1 = renameField(typed1, "Entities", "entities");
            typed1 = renameField(typed1, "Sections", "sections");
            typed1 = typed1.updateTyped(opticfinder1, type2, p_185128_ -> renameField(p_185128_, "Starts", "starts"));
            typed1 = renameField(typed1, "Structures", "structures");
            return typed1.update(DSL.remainderFinder(), p_199429_ -> p_199429_.remove("Level"));
        });
    }

    private static Typed<?> renameField(Typed<?> typed, String oldName, String newName) {
        return renameFieldHelper(typed, oldName, newName, typed.getType().findFieldType(oldName))
            .update(DSL.remainderFinder(), p_199439_ -> p_199439_.remove(oldName));
    }

    private static <A> Typed<?> renameFieldHelper(Typed<?> typed, String oldName, String newName, Type<A> p_type) {
        Type<Either<A, Unit>> type = DSL.optional(DSL.field(oldName, p_type));
        Type<Either<A, Unit>> type1 = DSL.optional(DSL.field(newName, p_type));
        return typed.update(type.finder(), type1, Function.identity());
    }

    private static <A> Typed<Pair<String, A>> appendChunkName(Typed<A> typed) {
        return new Typed<>(DSL.named("chunk", typed.getType()), typed.getOps(), Pair.of("chunk", typed.getValue()));
    }

    private static <T> Dynamic<T> mergeRemainders(Typed<?> typed, Dynamic<T> p_dynamic) {
        DynamicOps<T> dynamicops = p_dynamic.getOps();
        Dynamic<T> dynamic = typed.get(DSL.remainderFinder()).convert(dynamicops);
        DataResult<T> dataresult = dynamicops.getMap(p_dynamic.getValue())
            .flatMap(p_199433_ -> dynamicops.mergeToMap(dynamic.getValue(), (MapLike<T>)p_199433_));
        return dataresult.result().map(p_199436_ -> new Dynamic<>(dynamicops, (T)p_199436_)).orElse(p_dynamic);
    }
}
