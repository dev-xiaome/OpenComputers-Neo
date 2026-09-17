package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class StructuresBecomeConfiguredFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, StructuresBecomeConfiguredFix.Conversion> CONVERSION_MAP = ImmutableMap.<String, StructuresBecomeConfiguredFix.Conversion>builder()
        .put(
            "mineshaft",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"), "minecraft:mineshaft_mesa"),
                "minecraft:mineshaft"
            )
        )
        .put(
            "shipwreck",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:beach", "minecraft:snowy_beach"), "minecraft:shipwreck_beached"), "minecraft:shipwreck"
            )
        )
        .put(
            "ocean_ruin",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"), "minecraft:ocean_ruin_warm"),
                "minecraft:ocean_ruin_cold"
            )
        )
        .put(
            "village",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(
                    List.of("minecraft:desert"),
                    "minecraft:village_desert",
                    List.of("minecraft:savanna"),
                    "minecraft:village_savanna",
                    List.of("minecraft:snowy_plains"),
                    "minecraft:village_snowy",
                    List.of("minecraft:taiga"),
                    "minecraft:village_taiga"
                ),
                "minecraft:village_plains"
            )
        )
        .put(
            "ruined_portal",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(
                    List.of("minecraft:desert"),
                    "minecraft:ruined_portal_desert",
                    List.of(
                        "minecraft:badlands",
                        "minecraft:eroded_badlands",
                        "minecraft:wooded_badlands",
                        "minecraft:windswept_hills",
                        "minecraft:windswept_forest",
                        "minecraft:windswept_gravelly_hills",
                        "minecraft:savanna_plateau",
                        "minecraft:windswept_savanna",
                        "minecraft:stony_shore",
                        "minecraft:meadow",
                        "minecraft:frozen_peaks",
                        "minecraft:jagged_peaks",
                        "minecraft:stony_peaks",
                        "minecraft:snowy_slopes"
                    ),
                    "minecraft:ruined_portal_mountain",
                    List.of("minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:sparse_jungle"),
                    "minecraft:ruined_portal_jungle",
                    List.of(
                        "minecraft:deep_frozen_ocean",
                        "minecraft:deep_cold_ocean",
                        "minecraft:deep_ocean",
                        "minecraft:deep_lukewarm_ocean",
                        "minecraft:frozen_ocean",
                        "minecraft:ocean",
                        "minecraft:cold_ocean",
                        "minecraft:lukewarm_ocean",
                        "minecraft:warm_ocean"
                    ),
                    "minecraft:ruined_portal_ocean"
                ),
                "minecraft:ruined_portal"
            )
        )
        .put("pillager_outpost", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:pillager_outpost"))
        .put("mansion", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:mansion"))
        .put("jungle_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:jungle_pyramid"))
        .put("desert_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:desert_pyramid"))
        .put("igloo", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:igloo"))
        .put("swamp_hut", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:swamp_hut"))
        .put("stronghold", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:stronghold"))
        .put("monument", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:monument"))
        .put("fortress", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:fortress"))
        .put("endcity", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:end_city"))
        .put("buried_treasure", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:buried_treasure"))
        .put("nether_fossil", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:nether_fossil"))
        .put("bastion_remnant", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:bastion_remnant"))
        .build();

    public StructuresBecomeConfiguredFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = this.getInputSchema().getType(References.CHUNK);
        return this.writeFixAndRead("StucturesToConfiguredStructures", type, type1, this::fix);
    }

    private Dynamic<?> fix(Dynamic<?> data) {
        return data.update(
            "structures",
            p_207728_ -> p_207728_.update("starts", p_207734_ -> this.updateStarts(p_207734_, data))
                    .update("References", p_207731_ -> this.updateReferences(p_207731_, data))
        );
    }

    private Dynamic<?> updateStarts(Dynamic<?> starts, Dynamic<?> data) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = starts.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashmap = Maps.newHashMap();
        map.forEach((p_339494_, p_339495_) -> {
            if (!p_339495_.get("id").asString("INVALID").equals("INVALID")) {
                Dynamic<?> dynamic = this.findUpdatedStructureType((Dynamic<?>)p_339494_, data);
                if (dynamic == null) {
                    LOGGER.warn("Encountered unknown structure in datafixer: " + p_339494_.asString("<missing key>"));
                } else {
                    hashmap.computeIfAbsent(dynamic, p_339502_ -> p_339495_.set("id", dynamic));
                }
            }
        });
        return data.createMap(hashmap);
    }

    private Dynamic<?> updateReferences(Dynamic<?> references, Dynamic<?> data) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = references.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashmap = Maps.newHashMap();
        map.forEach(
            (p_339498_, p_339499_) -> {
                if (p_339499_.asLongStream().count() != 0L) {
                    Dynamic<?> dynamic = this.findUpdatedStructureType((Dynamic<?>)p_339498_, data);
                    if (dynamic == null) {
                        LOGGER.warn("Encountered unknown structure in datafixer: " + p_339498_.asString("<missing key>"));
                    } else {
                        hashmap.compute(
                            dynamic,
                            (p_339504_, p_339505_) -> p_339505_ == null
                                    ? p_339499_
                                    : p_339499_.createLongList(LongStream.concat(p_339505_.asLongStream(), p_339499_.asLongStream()))
                        );
                    }
                }
            }
        );
        return data.createMap(hashmap);
    }

    @Nullable
    private Dynamic<?> findUpdatedStructureType(Dynamic<?> p_207725_, Dynamic<?> data) {
        String s = p_207725_.asString("UNKNOWN").toLowerCase(Locale.ROOT);
        StructuresBecomeConfiguredFix.Conversion structuresbecomeconfiguredfix$conversion = CONVERSION_MAP.get(s);
        if (structuresbecomeconfiguredfix$conversion == null) structuresbecomeconfiguredfix$conversion = net.neoforged.neoforge.common.CommonHooks.getStructureConversion(s); // Neo: hook for mods to register conversions through RegisterStructureConversionsEvent
        if (structuresbecomeconfiguredfix$conversion == null) {
            // Porting 1.20.5 check if this is correct
            if (net.neoforged.neoforge.common.CommonHooks.checkStructureNamespace(s)) return data.createString(s); // Neo: pass-through structure IDs which have a non-"minecraft" namespace
            if (true) return data.createString("unknown." + s); // Neo: Pass-through with "unknown." prefix, so deserializer logs and ignores rather than fixer throwing an exception and dropping chunk data
            return null;
        } else {
            String s1 = structuresbecomeconfiguredfix$conversion.fallback;
            if (!structuresbecomeconfiguredfix$conversion.biomeMapping().isEmpty()) {
                Optional<String> optional = this.guessConfiguration(data, structuresbecomeconfiguredfix$conversion);
                if (optional.isPresent()) {
                    s1 = optional.get();
                }
            }

            return data.createString(s1);
        }
    }

    private Optional<String> guessConfiguration(Dynamic<?> data, StructuresBecomeConfiguredFix.Conversion conversion) {
        Object2IntArrayMap<String> object2intarraymap = new Object2IntArrayMap<>();
        data.get("sections")
            .asList(Function.identity())
            .forEach(p_207683_ -> p_207683_.get("biomes").get("palette").asList(Function.identity()).forEach(p_207709_ -> {
                    String s = conversion.biomeMapping().get(p_207709_.asString(""));
                    if (s != null) {
                        object2intarraymap.mergeInt(s, 1, Integer::sum);
                    }
                }));
        return object2intarraymap.object2IntEntrySet()
            .stream()
            .max(Comparator.comparingInt(it.unimi.dsi.fastutil.objects.Object2IntMap.Entry::getIntValue))
            .map(Entry::getKey);
    }

    public static record Conversion(Map<String, String> biomeMapping, String fallback) {
        public static StructuresBecomeConfiguredFix.Conversion trivial(String fallback) {
            return new StructuresBecomeConfiguredFix.Conversion(Map.of(), fallback);
        }

        public static StructuresBecomeConfiguredFix.Conversion biomeMapped(Map<List<String>, String> biomeMapping, String fallback) {
            return new StructuresBecomeConfiguredFix.Conversion(unpack(biomeMapping), fallback);
        }

        private static Map<String, String> unpack(Map<List<String>, String> mapping) {
            Builder<String, String> builder = ImmutableMap.builder();

            for (Entry<List<String>, String> entry : mapping.entrySet()) {
                entry.getKey().forEach(p_207745_ -> builder.put(p_207745_, entry.getValue()));
            }

            return builder.build();
        }
    }
}
