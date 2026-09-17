package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class DropInvalidSignDataFix extends NamedEntityFix {
    private static final String[] FIELDS_TO_DROP = new String[]{
        "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText"
    };

    public DropInvalidSignDataFix(Schema outputSchema, String name, String entityName) {
        super(outputSchema, false, name, References.BLOCK_ENTITY, entityName);
    }

    private static <T> Dynamic<T> fix(Dynamic<T> dynamic) {
        dynamic = dynamic.update("front_text", DropInvalidSignDataFix::fixText);
        dynamic = dynamic.update("back_text", DropInvalidSignDataFix::fixText);

        for (String s : FIELDS_TO_DROP) {
            dynamic = dynamic.remove(s);
        }

        return dynamic;
    }

    private static <T> Dynamic<T> fixText(Dynamic<T> textDynamic) {
        boolean flag = textDynamic.get("_filtered_correct").asBoolean(false);
        if (flag) {
            return textDynamic.remove("_filtered_correct");
        } else {
            Optional<Stream<Dynamic<T>>> optional = textDynamic.get("filtered_messages").asStreamOpt().result();
            if (optional.isEmpty()) {
                return textDynamic;
            } else {
                Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(textDynamic.getOps());
                List<Dynamic<T>> list = textDynamic.get("messages").asStreamOpt().result().orElse(Stream.of()).toList();
                List<Dynamic<T>> list1 = Streams.mapWithIndex(optional.get(), (p_294909_, p_296017_) -> {
                    Dynamic<T> dynamic1 = p_296017_ < (long)list.size() ? list.get((int)p_296017_) : dynamic;
                    return p_294909_.equals(dynamic) ? dynamic1 : p_294909_;
                }).toList();
                return list1.stream().allMatch(p_296400_ -> p_296400_.equals(dynamic))
                    ? textDynamic.remove("filtered_messages")
                    : textDynamic.set("filtered_messages", textDynamic.createList(list1.stream()));
            }
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), DropInvalidSignDataFix::fix);
    }
}
