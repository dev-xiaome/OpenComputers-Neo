package net.minecraft.util.datafix.fixes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SavedDataFeaturePoolElementFix extends DataFix {
    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(\\d+)\\]");
    private static final Set<String> PIECE_TYPE = Sets.newHashSet(
        "minecraft:jigsaw", "minecraft:nvi", "minecraft:pcp", "minecraft:bastionremnant", "minecraft:runtime"
    );
    private static final Set<String> FEATURES = Sets.newHashSet("minecraft:tree", "minecraft:flower", "minecraft:block_pile", "minecraft:random_patch");

    public SavedDataFeaturePoolElementFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "SavedDataFeaturePoolElementFix",
            this.getInputSchema().getType(References.STRUCTURE_FEATURE),
            this.getOutputSchema().getType(References.STRUCTURE_FEATURE),
            SavedDataFeaturePoolElementFix::fixTag
        );
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> tag) {
        return tag.update("Children", SavedDataFeaturePoolElementFix::updateChildren);
    }

    private static <T> Dynamic<T> updateChildren(Dynamic<T> data) {
        return data.asStreamOpt().map(SavedDataFeaturePoolElementFix::updateChildren).map(data::createList).result().orElse(data);
    }

    private static Stream<? extends Dynamic<?>> updateChildren(Stream<? extends Dynamic<?>> children) {
        return children.map(
            p_145667_ -> {
                String s = p_145667_.get("id").asString("");
                if (!PIECE_TYPE.contains(s)) {
                    return p_145667_;
                } else {
                    OptionalDynamic<?> optionaldynamic = p_145667_.get("pool_element");
                    return !optionaldynamic.get("element_type").asString("").equals("minecraft:feature_pool_element")
                        ? p_145667_
                        : p_145667_.update("pool_element", p_145669_ -> p_145669_.update("feature", SavedDataFeaturePoolElementFix::fixFeature));
                }
            }
        );
    }

    private static <T> OptionalDynamic<T> get(Dynamic<T> dynamic, String... path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("Missing path");
        } else {
            OptionalDynamic<T> optionaldynamic = dynamic.get(path[0]);

            for (int i = 1; i < path.length; i++) {
                String s = path[i];
                Matcher matcher = INDEX_PATTERN.matcher(s);
                if (matcher.matches()) {
                    int j = Integer.parseInt(matcher.group(1));
                    List<? extends Dynamic<T>> list = optionaldynamic.asList(Function.identity());
                    if (j >= 0 && j < list.size()) {
                        optionaldynamic = new OptionalDynamic<>(dynamic.getOps(), DataResult.success((Dynamic<T>)list.get(j)));
                    } else {
                        optionaldynamic = new OptionalDynamic<>(dynamic.getOps(), DataResult.error(() -> "Missing id:" + j));
                    }
                } else {
                    optionaldynamic = optionaldynamic.get(s);
                }
            }

            return optionaldynamic;
        }
    }

    @VisibleForTesting
    protected static Dynamic<?> fixFeature(Dynamic<?> dynamic) {
        Optional<String> optional = getReplacement(
            get(dynamic, "type").asString(""),
            get(dynamic, "name").asString(""),
            get(dynamic, "config", "state_provider", "type").asString(""),
            get(dynamic, "config", "state_provider", "state", "Name").asString(""),
            get(dynamic, "config", "state_provider", "entries", "[0]", "data", "Name").asString(""),
            get(dynamic, "config", "foliage_placer", "type").asString(""),
            get(dynamic, "config", "leaves_provider", "state", "Name").asString("")
        );
        return optional.isPresent() ? dynamic.createString(optional.get()) : dynamic;
    }

    private static Optional<String> getReplacement(
        String type, String name, String stateProviderType, String state, String stateProviderName, String foliagePlacerType, String leavesState
    ) {
        String s;
        if (!type.isEmpty()) {
            s = type;
        } else {
            if (name.isEmpty()) {
                return Optional.empty();
            }

            if ("minecraft:normal_tree".equals(name)) {
                s = "minecraft:tree";
            } else {
                s = name;
            }
        }

        if (FEATURES.contains(s)) {
            if ("minecraft:random_patch".equals(s)) {
                if ("minecraft:simple_state_provider".equals(stateProviderType)) {
                    if ("minecraft:sweet_berry_bush".equals(state)) {
                        return Optional.of("minecraft:patch_berry_bush");
                    }

                    if ("minecraft:cactus".equals(state)) {
                        return Optional.of("minecraft:patch_cactus");
                    }
                } else if ("minecraft:weighted_state_provider".equals(stateProviderType) && ("minecraft:grass".equals(stateProviderName) || "minecraft:fern".equals(stateProviderName))
                    )
                 {
                    return Optional.of("minecraft:patch_taiga_grass");
                }
            } else if ("minecraft:block_pile".equals(s)) {
                if (!"minecraft:simple_state_provider".equals(stateProviderType) && !"minecraft:rotated_block_provider".equals(stateProviderType)) {
                    if ("minecraft:weighted_state_provider".equals(stateProviderType)) {
                        if ("minecraft:packed_ice".equals(stateProviderName) || "minecraft:blue_ice".equals(stateProviderName)) {
                            return Optional.of("minecraft:pile_ice");
                        }

                        if ("minecraft:jack_o_lantern".equals(stateProviderName) || "minecraft:pumpkin".equals(stateProviderName)) {
                            return Optional.of("minecraft:pile_pumpkin");
                        }
                    }
                } else {
                    if ("minecraft:hay_block".equals(state)) {
                        return Optional.of("minecraft:pile_hay");
                    }

                    if ("minecraft:melon".equals(state)) {
                        return Optional.of("minecraft:pile_melon");
                    }

                    if ("minecraft:snow".equals(state)) {
                        return Optional.of("minecraft:pile_snow");
                    }
                }
            } else {
                if ("minecraft:flower".equals(s)) {
                    return Optional.of("minecraft:flower_plain");
                }

                if ("minecraft:tree".equals(s)) {
                    if ("minecraft:acacia_foliage_placer".equals(foliagePlacerType)) {
                        return Optional.of("minecraft:acacia");
                    }

                    if ("minecraft:blob_foliage_placer".equals(foliagePlacerType) && "minecraft:oak_leaves".equals(leavesState)) {
                        return Optional.of("minecraft:oak");
                    }

                    if ("minecraft:pine_foliage_placer".equals(foliagePlacerType)) {
                        return Optional.of("minecraft:pine");
                    }

                    if ("minecraft:spruce_foliage_placer".equals(foliagePlacerType)) {
                        return Optional.of("minecraft:spruce");
                    }
                }
            }
        }

        return Optional.empty();
    }
}
