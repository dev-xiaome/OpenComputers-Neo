package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class WorldGenSettingsFix extends DataFix {
    private static final String VILLAGE = "minecraft:village";
    private static final String DESERT_PYRAMID = "minecraft:desert_pyramid";
    private static final String IGLOO = "minecraft:igloo";
    private static final String JUNGLE_TEMPLE = "minecraft:jungle_pyramid";
    private static final String SWAMP_HUT = "minecraft:swamp_hut";
    private static final String PILLAGER_OUTPOST = "minecraft:pillager_outpost";
    private static final String END_CITY = "minecraft:endcity";
    private static final String WOODLAND_MANSION = "minecraft:mansion";
    private static final String OCEAN_MONUMENT = "minecraft:monument";
    private static final ImmutableMap<String, WorldGenSettingsFix.StructureFeatureConfiguration> DEFAULTS = ImmutableMap.<String, WorldGenSettingsFix.StructureFeatureConfiguration>builder()
        .put("minecraft:village", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 10387312))
        .put("minecraft:desert_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357617))
        .put("minecraft:igloo", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357618))
        .put("minecraft:jungle_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357619))
        .put("minecraft:swamp_hut", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357620))
        .put("minecraft:pillager_outpost", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 165745296))
        .put("minecraft:monument", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 5, 10387313))
        .put("minecraft:endcity", new WorldGenSettingsFix.StructureFeatureConfiguration(20, 11, 10387313))
        .put("minecraft:mansion", new WorldGenSettingsFix.StructureFeatureConfiguration(80, 20, 10387319))
        .build();

    public WorldGenSettingsFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "WorldGenSettings building",
            this.getInputSchema().getType(References.WORLD_GEN_SETTINGS),
            p_17184_ -> p_17184_.update(DSL.remainderFinder(), WorldGenSettingsFix::fix)
        );
    }

    private static <T> Dynamic<T> noise(long seed, DynamicLike<T> data, Dynamic<T> settings, Dynamic<T> biomeNoise) {
        return data.createMap(
            ImmutableMap.of(
                data.createString("type"),
                data.createString("minecraft:noise"),
                data.createString("biome_source"),
                biomeNoise,
                data.createString("seed"),
                data.createLong(seed),
                data.createString("settings"),
                settings
            )
        );
    }

    private static <T> Dynamic<T> vanillaBiomeSource(Dynamic<T> data, long seed, boolean legacyBiomeInitLayer, boolean largeBiomes) {
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.<Dynamic<T>, Dynamic<T>>builder()
            .put(data.createString("type"), data.createString("minecraft:vanilla_layered"))
            .put(data.createString("seed"), data.createLong(seed))
            .put(data.createString("large_biomes"), data.createBoolean(largeBiomes));
        if (legacyBiomeInitLayer) {
            builder.put(data.createString("legacy_biome_init_layer"), data.createBoolean(legacyBiomeInitLayer));
        }

        return data.createMap(builder.build());
    }

    private static <T> Dynamic<T> fix(Dynamic<T> data) {
        DynamicOps<T> dynamicops = data.getOps();
        long i = data.get("RandomSeed").asLong(0L);
        Optional<String> optional = data.get("generatorName").asString().map(p_17227_ -> p_17227_.toLowerCase(Locale.ROOT)).result();
        Optional<String> optional1 = data.get("legacy_custom_options")
            .asString()
            .result()
            .map(Optional::of)
            .orElseGet(() -> optional.equals(Optional.of("customized")) ? data.get("generatorOptions").asString().result() : Optional.empty());
        boolean flag = false;
        Dynamic<T> dynamic;
        if (optional.equals(Optional.of("customized"))) {
            dynamic = defaultOverworld(data, i);
        } else if (optional.isEmpty()) {
            dynamic = defaultOverworld(data, i);
        } else {
            String $$28 = optional.get();
            switch ($$28) {
                case "flat":
                    OptionalDynamic<T> optionaldynamic = data.get("generatorOptions");
                    Map<Dynamic<T>, Dynamic<T>> map = fixFlatStructures(dynamicops, optionaldynamic);
                    dynamic = data.createMap(
                        ImmutableMap.of(
                            data.createString("type"),
                            data.createString("minecraft:flat"),
                            data.createString("settings"),
                            data.createMap(
                                ImmutableMap.of(
                                    data.createString("structures"),
                                    data.createMap(map),
                                    data.createString("layers"),
                                    optionaldynamic.get("layers")
                                        .result()
                                        .orElseGet(
                                            () -> data.createList(
                                                    Stream.of(
                                                        data.createMap(
                                                            ImmutableMap.of(
                                                                data.createString("height"),
                                                                data.createInt(1),
                                                                data.createString("block"),
                                                                data.createString("minecraft:bedrock")
                                                            )
                                                        ),
                                                        data.createMap(
                                                            ImmutableMap.of(
                                                                data.createString("height"),
                                                                data.createInt(2),
                                                                data.createString("block"),
                                                                data.createString("minecraft:dirt")
                                                            )
                                                        ),
                                                        data.createMap(
                                                            ImmutableMap.of(
                                                                data.createString("height"),
                                                                data.createInt(1),
                                                                data.createString("block"),
                                                                data.createString("minecraft:grass_block")
                                                            )
                                                        )
                                                    )
                                                )
                                        ),
                                    data.createString("biome"),
                                    data.createString(optionaldynamic.get("biome").asString("minecraft:plains"))
                                )
                            )
                        )
                    );
                    break;
                case "debug_all_block_states":
                    dynamic = data.createMap(ImmutableMap.of(data.createString("type"), data.createString("minecraft:debug")));
                    break;
                case "buffet":
                    OptionalDynamic<T> optionaldynamic1 = data.get("generatorOptions");
                    OptionalDynamic<?> optionaldynamic2 = optionaldynamic1.get("chunk_generator");
                    Optional<String> optional2 = optionaldynamic2.get("type").asString().result();
                    Dynamic<T> dynamic1;
                    if (Objects.equals(optional2, Optional.of("minecraft:caves"))) {
                        dynamic1 = data.createString("minecraft:caves");
                        flag = true;
                    } else if (Objects.equals(optional2, Optional.of("minecraft:floating_islands"))) {
                        dynamic1 = data.createString("minecraft:floating_islands");
                    } else {
                        dynamic1 = data.createString("minecraft:overworld");
                    }

                    Dynamic<T> dynamic2 = optionaldynamic1.get("biome_source")
                        .result()
                        .orElseGet(() -> data.createMap(ImmutableMap.of(data.createString("type"), data.createString("minecraft:fixed"))));
                    Dynamic<T> dynamic3;
                    if (dynamic2.get("type").asString().result().equals(Optional.of("minecraft:fixed"))) {
                        String s1 = dynamic2.get("options")
                            .get("biomes")
                            .asStream()
                            .findFirst()
                            .flatMap(p_337681_ -> p_337681_.asString().result())
                            .orElse("minecraft:ocean");
                        dynamic3 = dynamic2.remove("options").set("biome", data.createString(s1));
                    } else {
                        dynamic3 = dynamic2;
                    }

                    dynamic = noise(i, data, dynamic1, dynamic3);
                    break;
                default:
                    boolean flag1 = optional.get().equals("default");
                    boolean flag2 = optional.get().equals("default_1_1") || flag1 && data.get("generatorVersion").asInt(0) == 0;
                    boolean flag3 = optional.get().equals("amplified");
                    boolean flag4 = optional.get().equals("largebiomes");
                    dynamic = noise(
                        i,
                        data,
                        data.createString(flag3 ? "minecraft:amplified" : "minecraft:overworld"),
                        vanillaBiomeSource(data, i, flag2, flag4)
                    );
            }
        }

        boolean flag5 = data.get("MapFeatures").asBoolean(true);
        boolean flag6 = data.get("BonusChest").asBoolean(false);
        Builder<T, T> builder = ImmutableMap.builder();
        builder.put(dynamicops.createString("seed"), dynamicops.createLong(i));
        builder.put(dynamicops.createString("generate_features"), dynamicops.createBoolean(flag5));
        builder.put(dynamicops.createString("bonus_chest"), dynamicops.createBoolean(flag6));
        builder.put(dynamicops.createString("dimensions"), vanillaLevels(data, i, dynamic, flag));
        optional1.ifPresent(p_17182_ -> builder.put(dynamicops.createString("legacy_custom_options"), dynamicops.createString(p_17182_)));
        return new Dynamic<>(dynamicops, dynamicops.createMap(builder.build()));
    }

    protected static <T> Dynamic<T> defaultOverworld(Dynamic<T> data, long seed) {
        return noise(seed, data, data.createString("minecraft:overworld"), vanillaBiomeSource(data, seed, false, false));
    }

    protected static <T> T vanillaLevels(Dynamic<T> data, long seed, Dynamic<T> p_17193_, boolean caves) {
        DynamicOps<T> dynamicops = data.getOps();
        return dynamicops.createMap(
            ImmutableMap.of(
                dynamicops.createString("minecraft:overworld"),
                dynamicops.createMap(
                    ImmutableMap.of(
                        dynamicops.createString("type"),
                        dynamicops.createString("minecraft:overworld" + (caves ? "_caves" : "")),
                        dynamicops.createString("generator"),
                        p_17193_.getValue()
                    )
                ),
                dynamicops.createString("minecraft:the_nether"),
                dynamicops.createMap(
                    ImmutableMap.of(
                        dynamicops.createString("type"),
                        dynamicops.createString("minecraft:the_nether"),
                        dynamicops.createString("generator"),
                        noise(
                                seed,
                                data,
                                data.createString("minecraft:nether"),
                                data.createMap(
                                    ImmutableMap.of(
                                        data.createString("type"),
                                        data.createString("minecraft:multi_noise"),
                                        data.createString("seed"),
                                        data.createLong(seed),
                                        data.createString("preset"),
                                        data.createString("minecraft:nether")
                                    )
                                )
                            )
                            .getValue()
                    )
                ),
                dynamicops.createString("minecraft:the_end"),
                dynamicops.createMap(
                    ImmutableMap.of(
                        dynamicops.createString("type"),
                        dynamicops.createString("minecraft:the_end"),
                        dynamicops.createString("generator"),
                        noise(
                                seed,
                                data,
                                data.createString("minecraft:end"),
                                data.createMap(
                                    ImmutableMap.of(
                                        data.createString("type"),
                                        data.createString("minecraft:the_end"),
                                        data.createString("seed"),
                                        data.createLong(seed)
                                    )
                                )
                            )
                            .getValue()
                    )
                )
            )
        );
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> fixFlatStructures(DynamicOps<T> ops, OptionalDynamic<T> generatorOptions) {
        MutableInt mutableint = new MutableInt(32);
        MutableInt mutableint1 = new MutableInt(3);
        MutableInt mutableint2 = new MutableInt(128);
        MutableBoolean mutableboolean = new MutableBoolean(false);
        Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map = Maps.newHashMap();
        if (generatorOptions.result().isEmpty()) {
            mutableboolean.setTrue();
            map.put("minecraft:village", DEFAULTS.get("minecraft:village"));
        }

        generatorOptions.get("structures")
            .flatMap(Dynamic::getMapValues)
            .ifSuccess(
                p_17257_ -> p_17257_.forEach(
                        (p_337679_, p_337680_) -> p_337680_.getMapValues()
                                .result()
                                .ifPresent(
                                    p_145816_ -> p_145816_.forEach(
                                            (p_145807_, p_145808_) -> {
                                                String s = p_337679_.asString("");
                                                String s1 = p_145807_.asString("");
                                                String s2 = p_145808_.asString("");
                                                if ("stronghold".equals(s)) {
                                                    mutableboolean.setTrue();
                                                    switch (s1) {
                                                        case "distance":
                                                            mutableint.setValue(getInt(s2, mutableint.getValue(), 1));
                                                            return;
                                                        case "spread":
                                                            mutableint1.setValue(getInt(s2, mutableint1.getValue(), 1));
                                                            return;
                                                        case "count":
                                                            mutableint2.setValue(getInt(s2, mutableint2.getValue(), 1));
                                                            return;
                                                    }
                                                } else {
                                                    switch (s1) {
                                                        case "distance":
                                                            switch (s) {
                                                                case "village":
                                                                    setSpacing(map, "minecraft:village", s2, 9);
                                                                    return;
                                                                case "biome_1":
                                                                    setSpacing(map, "minecraft:desert_pyramid", s2, 9);
                                                                    setSpacing(map, "minecraft:igloo", s2, 9);
                                                                    setSpacing(map, "minecraft:jungle_pyramid", s2, 9);
                                                                    setSpacing(map, "minecraft:swamp_hut", s2, 9);
                                                                    setSpacing(map, "minecraft:pillager_outpost", s2, 9);
                                                                    return;
                                                                case "endcity":
                                                                    setSpacing(map, "minecraft:endcity", s2, 1);
                                                                    return;
                                                                case "mansion":
                                                                    setSpacing(map, "minecraft:mansion", s2, 1);
                                                                    return;
                                                                default:
                                                                    return;
                                                            }
                                                        case "separation":
                                                            if ("oceanmonument".equals(s)) {
                                                                WorldGenSettingsFix.StructureFeatureConfiguration worldgensettingsfix$structurefeatureconfiguration = map.getOrDefault(
                                                                    "minecraft:monument", DEFAULTS.get("minecraft:monument")
                                                                );
                                                                int i = getInt(s2, worldgensettingsfix$structurefeatureconfiguration.separation, 1);
                                                                map.put(
                                                                    "minecraft:monument",
                                                                    new WorldGenSettingsFix.StructureFeatureConfiguration(
                                                                        i,
                                                                        worldgensettingsfix$structurefeatureconfiguration.separation,
                                                                        worldgensettingsfix$structurefeatureconfiguration.salt
                                                                    )
                                                                );
                                                            }

                                                            return;
                                                        case "spacing":
                                                            if ("oceanmonument".equals(s)) {
                                                                setSpacing(map, "minecraft:monument", s2, 1);
                                                            }

                                                            return;
                                                    }
                                                }
                                            }
                                        )
                                )
                    )
            );
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.builder();
        builder.put(
            generatorOptions.createString("structures"),
            generatorOptions.createMap(
                map.entrySet()
                    .stream()
                    .collect(Collectors.toMap(p_17225_ -> generatorOptions.createString(p_17225_.getKey()), p_17222_ -> p_17222_.getValue().serialize(ops)))
            )
        );
        if (mutableboolean.isTrue()) {
            builder.put(
                generatorOptions.createString("stronghold"),
                generatorOptions.createMap(
                    ImmutableMap.of(
                        generatorOptions.createString("distance"),
                        generatorOptions.createInt(mutableint.getValue()),
                        generatorOptions.createString("spread"),
                        generatorOptions.createInt(mutableint1.getValue()),
                        generatorOptions.createString("count"),
                        generatorOptions.createInt(mutableint2.getValue())
                    )
                )
            );
        }

        return builder.build();
    }

    private static int getInt(String string, int defaultValue) {
        return NumberUtils.toInt(string, defaultValue);
    }

    private static int getInt(String string, int defaultValue, int minValue) {
        return Math.max(minValue, getInt(string, defaultValue));
    }

    private static void setSpacing(Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> p_17236_, String p_17237_, String spacing, int p_17239_) {
        WorldGenSettingsFix.StructureFeatureConfiguration worldgensettingsfix$structurefeatureconfiguration = p_17236_.getOrDefault(
            p_17237_, DEFAULTS.get(p_17237_)
        );
        int i = getInt(spacing, worldgensettingsfix$structurefeatureconfiguration.spacing, p_17239_);
        p_17236_.put(
            p_17237_,
            new WorldGenSettingsFix.StructureFeatureConfiguration(
                i, worldgensettingsfix$structurefeatureconfiguration.separation, worldgensettingsfix$structurefeatureconfiguration.salt
            )
        );
    }

    static final class StructureFeatureConfiguration {
        public static final Codec<WorldGenSettingsFix.StructureFeatureConfiguration> CODEC = RecordCodecBuilder.create(
            p_17279_ -> p_17279_.group(
                        Codec.INT.fieldOf("spacing").forGetter(p_145830_ -> p_145830_.spacing),
                        Codec.INT.fieldOf("separation").forGetter(p_145828_ -> p_145828_.separation),
                        Codec.INT.fieldOf("salt").forGetter(p_145826_ -> p_145826_.salt)
                    )
                    .apply(p_17279_, WorldGenSettingsFix.StructureFeatureConfiguration::new)
        );
        final int spacing;
        final int separation;
        final int salt;

        public StructureFeatureConfiguration(int spacing, int separation, int salt) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
        }

        public <T> Dynamic<T> serialize(DynamicOps<T> ops) {
            return new Dynamic<>(ops, CODEC.encodeStart(ops, this).result().orElse(ops.emptyMap()));
        }
    }
}
