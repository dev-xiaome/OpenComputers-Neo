package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractUUIDFix extends DataFix {
    protected TypeReference typeReference;

    public AbstractUUIDFix(Schema outputSchema, TypeReference typeReference) {
        super(outputSchema, false);
        this.typeReference = typeReference;
    }

    protected Typed<?> updateNamedChoice(Typed<?> typed, String choiceName, Function<Dynamic<?>, Dynamic<?>> updater) {
        Type<?> type = this.getInputSchema().getChoiceType(this.typeReference, choiceName);
        Type<?> type1 = this.getOutputSchema().getChoiceType(this.typeReference, choiceName);
        return typed.updateTyped(DSL.namedChoice(choiceName, type), type1, p_14607_ -> p_14607_.update(DSL.remainderFinder(), updater));
    }

    protected static Optional<Dynamic<?>> replaceUUIDString(Dynamic<?> dynamic, String oldKey, String newKey) {
        return createUUIDFromString(dynamic, oldKey).map(p_14616_ -> dynamic.remove(oldKey).set(newKey, (Dynamic<?>)p_14616_));
    }

    protected static Optional<Dynamic<?>> replaceUUIDMLTag(Dynamic<?> dynamic, String oldKey, String newKey) {
        return dynamic.get(oldKey)
            .result()
            .flatMap(AbstractUUIDFix::createUUIDFromML)
            .map(p_14598_ -> dynamic.remove(oldKey).set(newKey, (Dynamic<?>)p_14598_));
    }

    protected static Optional<Dynamic<?>> replaceUUIDLeastMost(Dynamic<?> dynamic, String oldKey, String newKey) {
        String s = oldKey + "Most";
        String s1 = oldKey + "Least";
        return createUUIDFromLongs(dynamic, s, s1).map(p_14604_ -> dynamic.remove(s).remove(s1).set(newKey, (Dynamic<?>)p_14604_));
    }

    protected static Optional<Dynamic<?>> createUUIDFromString(Dynamic<?> dynamic, String uuidKey) {
        return dynamic.get(uuidKey).result().flatMap(p_14586_ -> {
            String s = p_14586_.asString(null);
            if (s != null) {
                try {
                    UUID uuid = UUID.fromString(s);
                    return createUUIDTag(dynamic, uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                } catch (IllegalArgumentException illegalargumentexception) {
                }
            }

            return Optional.empty();
        });
    }

    protected static Optional<Dynamic<?>> createUUIDFromML(Dynamic<?> dynamic) {
        return createUUIDFromLongs(dynamic, "M", "L");
    }

    protected static Optional<Dynamic<?>> createUUIDFromLongs(Dynamic<?> dynamic, String mostKey, String leastKey) {
        long i = dynamic.get(mostKey).asLong(0L);
        long j = dynamic.get(leastKey).asLong(0L);
        return i != 0L && j != 0L ? createUUIDTag(dynamic, i, j) : Optional.empty();
    }

    protected static Optional<Dynamic<?>> createUUIDTag(Dynamic<?> dynamic, long most, long least) {
        return Optional.of(dynamic.createIntList(Arrays.stream(new int[]{(int)(most >> 32), (int)most, (int)(least >> 32), (int)least})));
    }
}
