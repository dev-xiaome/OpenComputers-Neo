package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.types.templates.Hook.HookFunction;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V102 extends Schema {
    public V102(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    @Override
    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        schema.registerType(
            true,
            References.ITEM_STACK,
            () -> DSL.hook(
                    DSL.optionalFields(
                        "id",
                        References.ITEM_NAME.in(schema),
                        "tag",
                        DSL.optionalFields(
                            Pair.of("EntityTag", References.ENTITY_TREE.in(schema)),
                            Pair.of("BlockEntityTag", References.BLOCK_ENTITY.in(schema)),
                            Pair.of("CanDestroy", DSL.list(References.BLOCK_NAME.in(schema))),
                            Pair.of("CanPlaceOn", DSL.list(References.BLOCK_NAME.in(schema))),
                            Pair.of("Items", DSL.list(References.ITEM_STACK.in(schema))),
                            Pair.of("ChargedProjectiles", DSL.list(References.ITEM_STACK.in(schema)))
                        )
                    ),
                    V99.ADD_NAMES,
                    HookFunction.IDENTITY
                )
        );
    }
}
