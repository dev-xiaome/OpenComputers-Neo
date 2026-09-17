package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class HorseBodyArmorItemFix extends NamedEntityWriteReadFix {
    private final String previousBodyArmorTag;
    private final boolean clearArmorItems;

    public HorseBodyArmorItemFix(Schema outputSchema, String entityName, String previousBodyArmorTag, boolean clearArmorItems) {
        super(outputSchema, true, "Horse armor fix for " + entityName, References.ENTITY, entityName);
        this.previousBodyArmorTag = previousBodyArmorTag;
        this.clearArmorItems = clearArmorItems;
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<? extends Dynamic<?>> optional = tag.get(this.previousBodyArmorTag).result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Dynamic<T> dynamic1 = tag.remove(this.previousBodyArmorTag);
            if (this.clearArmorItems) {
                dynamic1 = dynamic1.update(
                    "ArmorItems",
                    p_342004_ -> p_342004_.createList(
                            Streams.mapWithIndex(p_342004_.asStream(), (p_342005_, p_342002_) -> p_342002_ == 2L ? p_342005_.emptyMap() : p_342005_)
                        )
                );
                dynamic1 = dynamic1.update(
                    "ArmorDropChances",
                    p_342012_ -> p_342012_.createList(
                            Streams.mapWithIndex(p_342012_.asStream(), (p_342011_, p_342007_) -> p_342007_ == 2L ? p_342011_.createFloat(0.085F) : p_342011_)
                        )
                );
            }

            dynamic1 = dynamic1.set("body_armor_item", dynamic);
            return dynamic1.set("body_armor_drop_chance", tag.createFloat(2.0F));
        } else {
            return tag;
        }
    }
}
