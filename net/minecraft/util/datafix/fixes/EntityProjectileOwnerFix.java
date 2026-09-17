package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Arrays;
import java.util.function.Function;

public class EntityProjectileOwnerFix extends DataFix {
    public EntityProjectileOwnerFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        return this.fixTypeEverywhereTyped("EntityProjectileOwner", schema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> typed) {
        typed = this.updateEntity(typed, "minecraft:egg", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:ender_pearl", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:experience_bottle", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:snowball", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:potion", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:potion", this::updateItemPotion);
        typed = this.updateEntity(typed, "minecraft:llama_spit", this::updateOwnerLlamaSpit);
        typed = this.updateEntity(typed, "minecraft:arrow", this::updateOwnerArrow);
        typed = this.updateEntity(typed, "minecraft:spectral_arrow", this::updateOwnerArrow);
        return this.updateEntity(typed, "minecraft:trident", this::updateOwnerArrow);
    }

    private Dynamic<?> updateOwnerArrow(Dynamic<?> arrowTag) {
        long i = arrowTag.get("OwnerUUIDMost").asLong(0L);
        long j = arrowTag.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(arrowTag, i, j).remove("OwnerUUIDMost").remove("OwnerUUIDLeast");
    }

    private Dynamic<?> updateOwnerLlamaSpit(Dynamic<?> llamaSpitTag) {
        OptionalDynamic<?> optionaldynamic = llamaSpitTag.get("Owner");
        long i = optionaldynamic.get("OwnerUUIDMost").asLong(0L);
        long j = optionaldynamic.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(llamaSpitTag, i, j).remove("Owner");
    }

    private Dynamic<?> updateItemPotion(Dynamic<?> itemPotionTag) {
        OptionalDynamic<?> optionaldynamic = itemPotionTag.get("Potion");
        return itemPotionTag.set("Item", optionaldynamic.orElseEmptyMap()).remove("Potion");
    }

    private Dynamic<?> updateOwnerThrowable(Dynamic<?> throwableTag) {
        String s = "owner";
        OptionalDynamic<?> optionaldynamic = throwableTag.get("owner");
        long i = optionaldynamic.get("M").asLong(0L);
        long j = optionaldynamic.get("L").asLong(0L);
        return this.setUUID(throwableTag, i, j).remove("owner");
    }

    private Dynamic<?> setUUID(Dynamic<?> dynamic, long uuidMost, long uuidLeast) {
        String s = "OwnerUUID";
        return uuidMost != 0L && uuidLeast != 0L
            ? dynamic.set("OwnerUUID", dynamic.createIntList(Arrays.stream(createUUIDArray(uuidMost, uuidLeast))))
            : dynamic;
    }

    private static int[] createUUIDArray(long uuidMost, long uuidLeast) {
        return new int[]{(int)(uuidMost >> 32), (int)uuidMost, (int)(uuidLeast >> 32), (int)uuidLeast};
    }

    private Typed<?> updateEntity(Typed<?> typed, String choiceName, Function<Dynamic<?>, Dynamic<?>> updater) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, choiceName);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, choiceName);
        return typed.updateTyped(DSL.namedChoice(choiceName, type), type1, p_15576_ -> p_15576_.update(DSL.remainderFinder(), updater));
    }
}
