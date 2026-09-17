package net.minecraft.util.datafix.schemas;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.types.templates.Hook.HookFunction;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;

public class V1451_6 extends NamespacedSchema {
    public static final String SPECIAL_OBJECTIVE_MARKER = "_special";
    protected static final HookFunction UNPACK_OBJECTIVE_ID = new HookFunction() {
        @Override
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic<>(ops, value);
            return DataFixUtils.orElse(
                    dynamic.get("CriteriaName")
                        .asString()
                        .result()
                        .map(p_181094_ -> {
                            int i = p_181094_.indexOf(58);
                            if (i < 0) {
                                return Pair.of("_special", p_181094_);
                            } else {
                                try {
                                    ResourceLocation resourcelocation = ResourceLocation.bySeparator(p_181094_.substring(0, i), '.');
                                    ResourceLocation resourcelocation1 = ResourceLocation.bySeparator(p_181094_.substring(i + 1), '.');
                                    return Pair.of(resourcelocation.toString(), resourcelocation1.toString());
                                } catch (Exception exception) {
                                    return Pair.of("_special", p_181094_);
                                }
                            }
                        })
                        .map(
                            p_181092_ -> dynamic.set(
                                    "CriteriaType",
                                    dynamic.createMap(
                                        ImmutableMap.of(
                                            dynamic.createString("type"),
                                            dynamic.createString(p_181092_.getFirst()),
                                            dynamic.createString("id"),
                                            dynamic.createString(p_181092_.getSecond())
                                        )
                                    )
                                )
                        ),
                    dynamic
                )
                .getValue();
        }
    };
    protected static final HookFunction REPACK_OBJECTIVE_ID = new HookFunction() {
        @Override
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic<>(ops, value);
            Optional<Dynamic<T>> optional = dynamic.get("CriteriaType")
                .get()
                .result()
                .flatMap(
                    p_293835_ -> {
                        Optional<String> optional1 = p_293835_.get("type").asString().result();
                        Optional<String> optional2 = p_293835_.get("id").asString().result();
                        if (optional1.isPresent() && optional2.isPresent()) {
                            String s = optional1.get();
                            return s.equals("_special")
                                ? Optional.of(dynamic.createString(optional2.get()))
                                : Optional.of(p_293835_.createString(V1451_6.packNamespacedWithDot(s) + ":" + V1451_6.packNamespacedWithDot(optional2.get())));
                        } else {
                            return Optional.empty();
                        }
                    }
                );
            return DataFixUtils.orElse(optional.map(p_181101_ -> dynamic.set("CriteriaName", (Dynamic<?>)p_181101_).remove("CriteriaType")), dynamic)
                .getValue();
        }
    };

    public V1451_6(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    @Override
    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        Supplier<TypeTemplate> supplier = () -> DSL.compoundList(References.ITEM_NAME.in(schema), DSL.constType(DSL.intType()));
        schema.registerType(
            false,
            References.STATS,
            () -> DSL.optionalFields(
                    "stats",
                    DSL.optionalFields(
                        Pair.of("minecraft:mined", DSL.compoundList(References.BLOCK_NAME.in(schema), DSL.constType(DSL.intType()))),
                        Pair.of("minecraft:crafted", supplier.get()),
                        Pair.of("minecraft:used", supplier.get()),
                        Pair.of("minecraft:broken", supplier.get()),
                        Pair.of("minecraft:picked_up", supplier.get()),
                        Pair.of("minecraft:dropped", supplier.get()),
                        Pair.of("minecraft:killed", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))),
                        Pair.of("minecraft:killed_by", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))),
                        Pair.of("minecraft:custom", DSL.compoundList(DSL.constType(namespacedString()), DSL.constType(DSL.intType())))
                    )
                )
        );
        Map<String, Supplier<TypeTemplate>> map = createCriterionTypes(schema);
        schema.registerType(
            false,
            References.OBJECTIVE,
            () -> DSL.hook(DSL.optionalFields("CriteriaType", DSL.taggedChoiceLazy("type", DSL.string(), map)), UNPACK_OBJECTIVE_ID, REPACK_OBJECTIVE_ID)
        );
    }

    protected static Map<String, Supplier<TypeTemplate>> createCriterionTypes(Schema schema) {
        Supplier<TypeTemplate> supplier = () -> DSL.optionalFields("id", References.ITEM_NAME.in(schema));
        Supplier<TypeTemplate> supplier1 = () -> DSL.optionalFields("id", References.BLOCK_NAME.in(schema));
        Supplier<TypeTemplate> supplier2 = () -> DSL.optionalFields("id", References.ENTITY_NAME.in(schema));
        Map<String, Supplier<TypeTemplate>> map = Maps.newHashMap();
        map.put("minecraft:mined", supplier1);
        map.put("minecraft:crafted", supplier);
        map.put("minecraft:used", supplier);
        map.put("minecraft:broken", supplier);
        map.put("minecraft:picked_up", supplier);
        map.put("minecraft:dropped", supplier);
        map.put("minecraft:killed", supplier2);
        map.put("minecraft:killed_by", supplier2);
        map.put("minecraft:custom", () -> DSL.optionalFields("id", DSL.constType(namespacedString())));
        map.put("_special", () -> DSL.optionalFields("id", DSL.constType(DSL.string())));
        return map;
    }

    public static String packNamespacedWithDot(String namespace) {
        ResourceLocation resourcelocation = ResourceLocation.tryParse(namespace);
        return resourcelocation != null ? resourcelocation.getNamespace() + "." + resourcelocation.getPath() : namespace;
    }
}
