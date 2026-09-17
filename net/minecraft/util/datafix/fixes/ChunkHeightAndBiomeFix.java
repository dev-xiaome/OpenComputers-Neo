package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;

public class ChunkHeightAndBiomeFix extends DataFix {
    public static final String DATAFIXER_CONTEXT_TAG = "__context";
    private static final String NAME = "ChunkHeightAndBiomeFix";
    private static final int OLD_SECTION_COUNT = 16;
    private static final int NEW_SECTION_COUNT = 24;
    private static final int NEW_MIN_SECTION_Y = -4;
    public static final int BLOCKS_PER_SECTION = 4096;
    private static final int LONGS_PER_SECTION = 64;
    private static final int HEIGHTMAP_BITS = 9;
    private static final long HEIGHTMAP_MASK = 511L;
    private static final int HEIGHTMAP_OFFSET = 64;
    private static final String[] HEIGHTMAP_TYPES = new String[]{
        "WORLD_SURFACE_WG", "WORLD_SURFACE", "WORLD_SURFACE_IGNORE_SNOW", "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"
    };
    private static final Set<String> STATUS_IS_OR_AFTER_SURFACE = Set.of(
        "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> STATUS_IS_OR_AFTER_NOISE = Set.of(
        "noise", "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> BLOCKS_BEFORE_FEATURE_STATUS = Set.of(
        "minecraft:air",
        "minecraft:basalt",
        "minecraft:bedrock",
        "minecraft:blackstone",
        "minecraft:calcite",
        "minecraft:cave_air",
        "minecraft:coarse_dirt",
        "minecraft:crimson_nylium",
        "minecraft:dirt",
        "minecraft:end_stone",
        "minecraft:grass_block",
        "minecraft:gravel",
        "minecraft:ice",
        "minecraft:lava",
        "minecraft:mycelium",
        "minecraft:nether_wart_block",
        "minecraft:netherrack",
        "minecraft:orange_terracotta",
        "minecraft:packed_ice",
        "minecraft:podzol",
        "minecraft:powder_snow",
        "minecraft:red_sand",
        "minecraft:red_sandstone",
        "minecraft:sand",
        "minecraft:sandstone",
        "minecraft:snow_block",
        "minecraft:soul_sand",
        "minecraft:soul_soil",
        "minecraft:stone",
        "minecraft:terracotta",
        "minecraft:warped_nylium",
        "minecraft:warped_wart_block",
        "minecraft:water",
        "minecraft:white_terracotta"
    );
    private static final int BIOME_CONTAINER_LAYER_SIZE = 16;
    private static final int BIOME_CONTAINER_SIZE = 64;
    private static final int BIOME_CONTAINER_TOP_LAYER_OFFSET = 1008;
    public static final String DEFAULT_BIOME = "minecraft:plains";
    private static final Int2ObjectMap<String> BIOMES_BY_ID = new Int2ObjectOpenHashMap<>();

    public ChunkHeightAndBiomeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticfinder = type.findField("Level");
        OpticFinder<?> opticfinder1 = opticfinder.type().findField("Sections");
        Schema schema = this.getOutputSchema();
        Type<?> type1 = schema.getType(References.CHUNK);
        Type<?> type2 = type1.findField("Level").type();
        Type<?> type3 = type2.findField("Sections").type();
        return this.fixTypeEverywhereTyped(
            "ChunkHeightAndBiomeFix",
            type,
            type1,
            p_184879_ -> p_184879_.updateTyped(
                    opticfinder,
                    type2,
                    p_184884_ -> {
                        Dynamic<?> dynamic = p_184884_.get(DSL.remainderFinder());
                        OptionalDynamic<?> optionaldynamic = p_184879_.get(DSL.remainderFinder()).get("__context");
                        String s = optionaldynamic.get("dimension").asString().result().orElse("");
                        String s1 = optionaldynamic.get("generator").asString().result().orElse("");
                        boolean flag = "minecraft:overworld".equals(s);
                        MutableBoolean mutableboolean = new MutableBoolean();
                        int i = flag ? -4 : 0;
                        Dynamic<?>[] dynamic1 = getBiomeContainers(dynamic, flag, i, mutableboolean);
                        Dynamic<?> dynamic2 = makePalettedContainer(
                            dynamic.createList(
                                Stream.of(dynamic.createMap(ImmutableMap.of(dynamic.createString("Name"), dynamic.createString("minecraft:air"))))
                            )
                        );
                        Set<String> set = Sets.newHashSet();
                        MutableObject<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> mutableobject = new MutableObject<>(() -> null);
                        p_184884_ = p_184884_.updateTyped(
                            opticfinder1,
                            type3,
                            p_184936_ -> {
                                IntSet intset = new IntOpenHashSet();
                                Dynamic<?> dynamic3 = (Dynamic<?>)p_184936_.write()
                                    .result()
                                    .orElseThrow(() -> new IllegalStateException("Malformed Chunk.Level.Sections"));
                                List<Dynamic<?>> list = dynamic3.asStream().map(p_184927_ -> {
                                    int l = p_184927_.get("Y").asInt(0);
                                    Dynamic<?> dynamic5 = DataFixUtils.orElse(p_184927_.get("Palette").result().flatMap(p_184940_ -> {
                                        p_184940_.asStream().map(p_184982_ -> p_184982_.get("Name").asString("minecraft:air")).forEach(set::add);
                                        return p_184927_.get("BlockStates")
                                            .result()
                                            .map(p_184973_ -> makeOptimizedPalettedContainer(p_184940_, (Dynamic<?>)p_184973_));
                                    }), dynamic2);
                                    Dynamic<?> dynamic6 = (Dynamic<?>)p_184927_;
                                    int i1 = l - i;
                                    if (i1 >= 0 && i1 < dynamic1.length) {
                                        dynamic6 = p_184927_.set("biomes", dynamic1[i1]);
                                    }

                                    intset.add(l);
                                    if (p_184927_.get("Y").asInt(Integer.MAX_VALUE) == 0) {
                                        mutableobject.setValue(() -> {
                                            List<? extends Dynamic<?>> list1 = dynamic5.get("palette").asList(Function.identity());
                                            long[] along = dynamic5.get("data").asLongStream().toArray();
                                            return new ChunkProtoTickListFix.PoorMansPalettedContainer(list1, along);
                                        });
                                    }

                                    return dynamic6.set("block_states", dynamic5).remove("Palette").remove("BlockStates");
                                }).collect(Collectors.toCollection(ArrayList::new));

                                for (int j = 0; j < dynamic1.length; j++) {
                                    int k = j + i;
                                    if (intset.add(k)) {
                                        Dynamic<?> dynamic4 = dynamic.createMap(Map.of(dynamic.createString("Y"), dynamic.createInt(k)));
                                        dynamic4 = dynamic4.set("block_states", dynamic2);
                                        dynamic4 = dynamic4.set("biomes", dynamic1[j]);
                                        list.add(dynamic4);
                                    }
                                }

                                return Util.readTypedOrThrow(type3, dynamic.createList(list.stream()));
                            }
                        );
                        return p_184884_.update(DSL.remainderFinder(), p_184947_ -> {
                            if (flag) {
                                p_184947_ = this.predictChunkStatusBeforeSurface(p_184947_, set);
                            }

                            return updateChunkTag(p_184947_, flag, mutableboolean.booleanValue(), "minecraft:noise".equals(s1), mutableobject.getValue());
                        });
                    }
                )
        );
    }

    private Dynamic<?> predictChunkStatusBeforeSurface(Dynamic<?> data, Set<String> blockPalette) {
        return data.update("Status", p_184919_ -> {
            String s = p_184919_.asString("empty");
            if (STATUS_IS_OR_AFTER_SURFACE.contains(s)) {
                return p_184919_;
            } else {
                blockPalette.remove("minecraft:air");
                boolean flag = !blockPalette.isEmpty();
                blockPalette.removeAll(BLOCKS_BEFORE_FEATURE_STATUS);
                boolean flag1 = !blockPalette.isEmpty();
                if (flag1) {
                    return p_184919_.createString("liquid_carvers");
                } else if ("noise".equals(s) || flag) {
                    return p_184919_.createString("noise");
                } else {
                    return "biomes".equals(s) ? p_184919_.createString("structure_references") : p_184919_;
                }
            }
        });
    }

    private static Dynamic<?>[] getBiomeContainers(Dynamic<?> data, boolean overworld, int lowestY, MutableBoolean isTallChunk) {
        Dynamic<?>[] dynamic = new Dynamic[overworld ? 24 : 16];
        int[] aint = data.get("Biomes").asIntStreamOpt().result().map(IntStream::toArray).orElse(null);
        if (aint != null && aint.length == 1536) {
            isTallChunk.setValue(true);

            for (int l = 0; l < 24; l++) {
                int i1 = l;
                dynamic[l] = makeBiomeContainer(data, p_184967_ -> getOldBiome(aint, i1 * 64 + p_184967_));
            }
        } else if (aint != null && aint.length == 1024) {
            for (int i = 0; i < 16; i++) {
                int j = i - lowestY;
                int i_f = i;
                dynamic[j] = makeBiomeContainer(data, p_184954_ -> getOldBiome(aint, i_f * 64 + p_184954_));
            }

            if (overworld) {
                Dynamic<?> dynamic1 = makeBiomeContainer(data, p_184976_ -> getOldBiome(aint, p_184976_ % 16));
                Dynamic<?> dynamic2 = makeBiomeContainer(data, p_184963_ -> getOldBiome(aint, p_184963_ % 16 + 1008));

                for (int k = 0; k < 4; k++) {
                    dynamic[k] = dynamic1;
                }

                for (int j1 = 20; j1 < 24; j1++) {
                    dynamic[j1] = dynamic2;
                }
            }
        } else {
            Arrays.fill(dynamic, makePalettedContainer(data.createList(Stream.of(data.createString("minecraft:plains")))));
        }

        return dynamic;
    }

    private static int getOldBiome(int[] biomes, int index) {
        return biomes[index] & 0xFF;
    }

    private static Dynamic<?> updateChunkTag(
        Dynamic<?> chunkTag, boolean overworld, boolean isTallChunk, boolean isNoiseGenerator, Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> paletteSupplier
    ) {
        chunkTag = chunkTag.remove("Biomes");
        if (!overworld) {
            return updateCarvingMasks(chunkTag, 16, 0);
        } else if (isTallChunk) {
            return updateCarvingMasks(chunkTag, 24, 0);
        } else {
            chunkTag = updateHeightmaps(chunkTag);
            chunkTag = addPaddingEntries(chunkTag, "LiquidsToBeTicked");
            chunkTag = addPaddingEntries(chunkTag, "PostProcessing");
            chunkTag = addPaddingEntries(chunkTag, "ToBeTicked");
            chunkTag = updateCarvingMasks(chunkTag, 24, 4);
            chunkTag = chunkTag.update("UpgradeData", ChunkHeightAndBiomeFix::shiftUpgradeData);
            if (!isNoiseGenerator) {
                return chunkTag;
            } else {
                Optional<? extends Dynamic<?>> optional = chunkTag.get("Status").result();
                if (optional.isPresent()) {
                    Dynamic<?> dynamic = (Dynamic<?>)optional.get();
                    String s = dynamic.asString("");
                    if (!"empty".equals(s)) {
                        chunkTag = chunkTag.set(
                            "blending_data",
                            chunkTag.createMap(
                                ImmutableMap.of(chunkTag.createString("old_noise"), chunkTag.createBoolean(STATUS_IS_OR_AFTER_NOISE.contains(s)))
                            )
                        );
                        ChunkProtoTickListFix.PoorMansPalettedContainer chunkprototicklistfix$poormanspalettedcontainer = paletteSupplier.get();
                        if (chunkprototicklistfix$poormanspalettedcontainer != null) {
                            BitSet bitset = new BitSet(256);
                            boolean flag = s.equals("noise");

                            for (int i = 0; i < 16; i++) {
                                for (int j = 0; j < 16; j++) {
                                    Dynamic<?> dynamic1 = chunkprototicklistfix$poormanspalettedcontainer.get(j, 0, i);
                                    boolean flag1 = dynamic1 != null && "minecraft:bedrock".equals(dynamic1.get("Name").asString(""));
                                    boolean flag2 = dynamic1 != null && "minecraft:air".equals(dynamic1.get("Name").asString(""));
                                    if (flag2) {
                                        bitset.set(i * 16 + j);
                                    }

                                    flag |= flag1;
                                }
                            }

                            if (flag && bitset.cardinality() != bitset.size()) {
                                Dynamic<?> dynamic2 = "full".equals(s) ? chunkTag.createString("heightmaps") : dynamic;
                                chunkTag = chunkTag.set(
                                    "below_zero_retrogen",
                                    chunkTag.createMap(
                                        ImmutableMap.of(
                                            chunkTag.createString("target_status"),
                                            dynamic2,
                                            chunkTag.createString("missing_bedrock"),
                                            chunkTag.createLongList(LongStream.of(bitset.toLongArray()))
                                        )
                                    )
                                );
                                chunkTag = chunkTag.set("Status", chunkTag.createString("empty"));
                            }

                            chunkTag = chunkTag.set("isLightOn", chunkTag.createBoolean(false));
                        }
                    }
                }

                return chunkTag;
            }
        }
    }

    private static <T> Dynamic<T> shiftUpgradeData(Dynamic<T> data) {
        return data.update("Indices", p_337611_ -> {
            Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
            p_337611_.getMapValues().ifSuccess(p_196610_ -> p_196610_.forEach((p_337609_, p_337610_) -> {
                    try {
                        p_337609_.asString().result().map(Integer::parseInt).ifPresent(p_196607_ -> {
                            int i = p_196607_ - -4;
                            map.put(p_337609_.createString(Integer.toString(i)), (Dynamic<?>)p_337610_);
                        });
                    } catch (NumberFormatException numberformatexception) {
                    }
                }));
            return p_337611_.createMap(map);
        });
    }

    private static Dynamic<?> updateCarvingMasks(Dynamic<?> data, int sectionCount, int offset) {
        Dynamic<?> dynamic = data.get("CarvingMasks").orElseEmptyMap();
        dynamic = dynamic.updateMapValues(p_196587_ -> {
            long[] along = BitSet.valueOf(p_196587_.getSecond().asByteBuffer().array()).toLongArray();
            long[] along1 = new long[64 * sectionCount];
            System.arraycopy(along, 0, along1, 64 * offset, along.length);
            return Pair.of(p_196587_.getFirst(), data.createLongList(LongStream.of(along1)));
        });
        return data.set("CarvingMasks", dynamic);
    }

    private static Dynamic<?> addPaddingEntries(Dynamic<?> data, String key) {
        List<Dynamic<?>> list = data.get(key).orElseEmptyList().asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 24) {
            return data;
        } else {
            Dynamic<?> dynamic = data.emptyList();

            for (int i = 0; i < 4; i++) {
                list.add(0, dynamic);
                list.add(dynamic);
            }

            return data.set(key, data.createList(list.stream()));
        }
    }

    private static Dynamic<?> updateHeightmaps(Dynamic<?> data) {
        return data.update("Heightmaps", p_196612_ -> {
            for (String s : HEIGHTMAP_TYPES) {
                p_196612_ = p_196612_.update(s, ChunkHeightAndBiomeFix::getFixedHeightmap);
            }

            return p_196612_;
        });
    }

    private static Dynamic<?> getFixedHeightmap(Dynamic<?> dynamic) {
        return dynamic.createLongList(dynamic.asLongStream().map(p_196589_ -> {
            long i = 0L;

            for (int j = 0; j + 9 <= 64; j += 9) {
                long k = p_196589_ >> j & 511L;
                long l;
                if (k == 0L) {
                    l = 0L;
                } else {
                    l = Math.min(k + 64L, 511L);
                }

                i |= l << j;
            }

            return i;
        }));
    }

    private static Dynamic<?> makeBiomeContainer(Dynamic<?> data, Int2IntFunction oldBiomeGetter) {
        Int2IntMap int2intmap = new Int2IntLinkedOpenHashMap();

        for (int i = 0; i < 64; i++) {
            int j = oldBiomeGetter.applyAsInt(i);
            if (!int2intmap.containsKey(j)) {
                int2intmap.put(j, int2intmap.size());
            }
        }

        Dynamic<?> dynamic = data.createList(
            int2intmap.keySet().stream().map(p_196598_ -> data.createString(BIOMES_BY_ID.getOrDefault(p_196598_.intValue(), "minecraft:plains")))
        );
        int i2 = ceillog2(int2intmap.size());
        if (i2 == 0) {
            return makePalettedContainer(dynamic);
        } else {
            int k = 64 / i2;
            int l = (64 + k - 1) / k;
            long[] along = new long[l];
            int i1 = 0;
            int j1 = 0;

            for (int k1 = 0; k1 < 64; k1++) {
                int l1 = oldBiomeGetter.applyAsInt(k1);
                along[i1] |= (long)int2intmap.get(l1) << j1;
                j1 += i2;
                if (j1 + i2 > 64) {
                    i1++;
                    j1 = 0;
                }
            }

            Dynamic<?> dynamic1 = data.createLongList(Arrays.stream(along));
            return makePalettedContainer(dynamic, dynamic1);
        }
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette));
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette, palette.createString("data"), blockStates));
    }

    private static Dynamic<?> makeOptimizedPalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        List<Dynamic<?>> list = palette.asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 1) {
            return makePalettedContainer(palette);
        } else {
            palette = padPaletteEntries(palette, blockStates, list);
            return makePalettedContainer(palette, blockStates);
        }
    }

    private static Dynamic<?> padPaletteEntries(Dynamic<?> palette, Dynamic<?> blockStates, List<Dynamic<?>> paletteEntries) {
        long i = blockStates.asLongStream().count() * 64L;
        long j = i / 4096L;
        int k = paletteEntries.size();
        int l = ceillog2(k);
        if (j <= (long)l) {
            return palette;
        } else {
            Dynamic<?> dynamic = palette.createMap(ImmutableMap.of(palette.createString("Name"), palette.createString("minecraft:air")));
            int i1 = (1 << (int)(j - 1L)) + 1;
            int j1 = i1 - k;

            for (int k1 = 0; k1 < j1; k1++) {
                paletteEntries.add(dynamic);
            }

            return palette.createList(paletteEntries.stream());
        }
    }

    public static int ceillog2(int value) {
        return value == 0 ? 0 : (int)Math.ceil(Math.log((double)value) / Math.log(2.0));
    }

    static {
        BIOMES_BY_ID.put(0, "minecraft:ocean");
        BIOMES_BY_ID.put(1, "minecraft:plains");
        BIOMES_BY_ID.put(2, "minecraft:desert");
        BIOMES_BY_ID.put(3, "minecraft:mountains");
        BIOMES_BY_ID.put(4, "minecraft:forest");
        BIOMES_BY_ID.put(5, "minecraft:taiga");
        BIOMES_BY_ID.put(6, "minecraft:swamp");
        BIOMES_BY_ID.put(7, "minecraft:river");
        BIOMES_BY_ID.put(8, "minecraft:nether_wastes");
        BIOMES_BY_ID.put(9, "minecraft:the_end");
        BIOMES_BY_ID.put(10, "minecraft:frozen_ocean");
        BIOMES_BY_ID.put(11, "minecraft:frozen_river");
        BIOMES_BY_ID.put(12, "minecraft:snowy_tundra");
        BIOMES_BY_ID.put(13, "minecraft:snowy_mountains");
        BIOMES_BY_ID.put(14, "minecraft:mushroom_fields");
        BIOMES_BY_ID.put(15, "minecraft:mushroom_field_shore");
        BIOMES_BY_ID.put(16, "minecraft:beach");
        BIOMES_BY_ID.put(17, "minecraft:desert_hills");
        BIOMES_BY_ID.put(18, "minecraft:wooded_hills");
        BIOMES_BY_ID.put(19, "minecraft:taiga_hills");
        BIOMES_BY_ID.put(20, "minecraft:mountain_edge");
        BIOMES_BY_ID.put(21, "minecraft:jungle");
        BIOMES_BY_ID.put(22, "minecraft:jungle_hills");
        BIOMES_BY_ID.put(23, "minecraft:jungle_edge");
        BIOMES_BY_ID.put(24, "minecraft:deep_ocean");
        BIOMES_BY_ID.put(25, "minecraft:stone_shore");
        BIOMES_BY_ID.put(26, "minecraft:snowy_beach");
        BIOMES_BY_ID.put(27, "minecraft:birch_forest");
        BIOMES_BY_ID.put(28, "minecraft:birch_forest_hills");
        BIOMES_BY_ID.put(29, "minecraft:dark_forest");
        BIOMES_BY_ID.put(30, "minecraft:snowy_taiga");
        BIOMES_BY_ID.put(31, "minecraft:snowy_taiga_hills");
        BIOMES_BY_ID.put(32, "minecraft:giant_tree_taiga");
        BIOMES_BY_ID.put(33, "minecraft:giant_tree_taiga_hills");
        BIOMES_BY_ID.put(34, "minecraft:wooded_mountains");
        BIOMES_BY_ID.put(35, "minecraft:savanna");
        BIOMES_BY_ID.put(36, "minecraft:savanna_plateau");
        BIOMES_BY_ID.put(37, "minecraft:badlands");
        BIOMES_BY_ID.put(38, "minecraft:wooded_badlands_plateau");
        BIOMES_BY_ID.put(39, "minecraft:badlands_plateau");
        BIOMES_BY_ID.put(40, "minecraft:small_end_islands");
        BIOMES_BY_ID.put(41, "minecraft:end_midlands");
        BIOMES_BY_ID.put(42, "minecraft:end_highlands");
        BIOMES_BY_ID.put(43, "minecraft:end_barrens");
        BIOMES_BY_ID.put(44, "minecraft:warm_ocean");
        BIOMES_BY_ID.put(45, "minecraft:lukewarm_ocean");
        BIOMES_BY_ID.put(46, "minecraft:cold_ocean");
        BIOMES_BY_ID.put(47, "minecraft:deep_warm_ocean");
        BIOMES_BY_ID.put(48, "minecraft:deep_lukewarm_ocean");
        BIOMES_BY_ID.put(49, "minecraft:deep_cold_ocean");
        BIOMES_BY_ID.put(50, "minecraft:deep_frozen_ocean");
        BIOMES_BY_ID.put(127, "minecraft:the_void");
        BIOMES_BY_ID.put(129, "minecraft:sunflower_plains");
        BIOMES_BY_ID.put(130, "minecraft:desert_lakes");
        BIOMES_BY_ID.put(131, "minecraft:gravelly_mountains");
        BIOMES_BY_ID.put(132, "minecraft:flower_forest");
        BIOMES_BY_ID.put(133, "minecraft:taiga_mountains");
        BIOMES_BY_ID.put(134, "minecraft:swamp_hills");
        BIOMES_BY_ID.put(140, "minecraft:ice_spikes");
        BIOMES_BY_ID.put(149, "minecraft:modified_jungle");
        BIOMES_BY_ID.put(151, "minecraft:modified_jungle_edge");
        BIOMES_BY_ID.put(155, "minecraft:tall_birch_forest");
        BIOMES_BY_ID.put(156, "minecraft:tall_birch_hills");
        BIOMES_BY_ID.put(157, "minecraft:dark_forest_hills");
        BIOMES_BY_ID.put(158, "minecraft:snowy_taiga_mountains");
        BIOMES_BY_ID.put(160, "minecraft:giant_spruce_taiga");
        BIOMES_BY_ID.put(161, "minecraft:giant_spruce_taiga_hills");
        BIOMES_BY_ID.put(162, "minecraft:modified_gravelly_mountains");
        BIOMES_BY_ID.put(163, "minecraft:shattered_savanna");
        BIOMES_BY_ID.put(164, "minecraft:shattered_savanna_plateau");
        BIOMES_BY_ID.put(165, "minecraft:eroded_badlands");
        BIOMES_BY_ID.put(166, "minecraft:modified_wooded_badlands_plateau");
        BIOMES_BY_ID.put(167, "minecraft:modified_badlands_plateau");
        BIOMES_BY_ID.put(168, "minecraft:bamboo_jungle");
        BIOMES_BY_ID.put(169, "minecraft:bamboo_jungle_hills");
        BIOMES_BY_ID.put(170, "minecraft:soul_sand_valley");
        BIOMES_BY_ID.put(171, "minecraft:crimson_forest");
        BIOMES_BY_ID.put(172, "minecraft:warped_forest");
        BIOMES_BY_ID.put(173, "minecraft:basalt_deltas");
        BIOMES_BY_ID.put(174, "minecraft:dripstone_caves");
        BIOMES_BY_ID.put(175, "minecraft:lush_caves");
        BIOMES_BY_ID.put(177, "minecraft:meadow");
        BIOMES_BY_ID.put(178, "minecraft:grove");
        BIOMES_BY_ID.put(179, "minecraft:snowy_slopes");
        BIOMES_BY_ID.put(180, "minecraft:snowcapped_peaks");
        BIOMES_BY_ID.put(181, "minecraft:lofty_peaks");
        BIOMES_BY_ID.put(182, "minecraft:stony_peaks");
    }
}
