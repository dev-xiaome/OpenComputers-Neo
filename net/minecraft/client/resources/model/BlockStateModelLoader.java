package net.minecraft.client.resources.model;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class BlockStateModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int SINGLETON_MODEL_GROUP = -1;
    private static final int INVISIBLE_MODEL_GROUP = 0;
    public static final FileToIdConverter BLOCKSTATE_LISTER = FileToIdConverter.json("blockstates");
    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);
    private static final StateDefinition<Block, BlockState> ITEM_FRAME_FAKE_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(BooleanProperty.create("map"))
        .create(Block::defaultBlockState, BlockState::new);
    private static final Map<ResourceLocation, StateDefinition<Block, BlockState>> STATIC_DEFINITIONS = Map.of(
        ResourceLocation.withDefaultNamespace("item_frame"),
        ITEM_FRAME_FAKE_DEFINITION,
        ResourceLocation.withDefaultNamespace("glow_item_frame"),
        ITEM_FRAME_FAKE_DEFINITION
    );
    private final Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources;
    private final ProfilerFiller profiler;
    private final BlockColors blockColors;
    private final BiConsumer<ModelResourceLocation, UnbakedModel> discoveredModelOutput;
    private int nextModelGroup = 1;
    private final Object2IntMap<BlockState> modelGroups = Util.make(new Object2IntOpenHashMap<>(), p_352094_ -> p_352094_.defaultReturnValue(-1));
    private final BlockStateModelLoader.LoadedModel missingModel;
    private final BlockModelDefinition.Context context = new BlockModelDefinition.Context();

    public BlockStateModelLoader(
        Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources,
        ProfilerFiller profiler,
        UnbakedModel missingModel,
        BlockColors blockColors,
        BiConsumer<ModelResourceLocation, UnbakedModel> discoveredModelOutput
    ) {
        this.blockStateResources = blockStateResources;
        this.profiler = profiler;
        this.blockColors = blockColors;
        this.discoveredModelOutput = discoveredModelOutput;
        BlockStateModelLoader.ModelGroupKey blockstatemodelloader$modelgroupkey = new BlockStateModelLoader.ModelGroupKey(List.of(missingModel), List.of());
        this.missingModel = new BlockStateModelLoader.LoadedModel(missingModel, () -> blockstatemodelloader$modelgroupkey);
    }

    public void loadAllBlockStates() {
        this.profiler.push("static_definitions");
        STATIC_DEFINITIONS.forEach(this::loadBlockStateDefinitions);
        this.profiler.popPush("blocks");

        for (Block block : BuiltInRegistries.BLOCK) {
            this.loadBlockStateDefinitions(block.builtInRegistryHolder().key().location(), block.getStateDefinition());
        }

        this.profiler.pop();
    }

    private void loadBlockStateDefinitions(ResourceLocation blockStateId, StateDefinition<Block, BlockState> stateDefenition) {
        this.context.setDefinition(stateDefenition);
        List<Property<?>> list = List.copyOf(this.blockColors.getColoringProperties(stateDefenition.getOwner()));
        List<BlockState> list1 = stateDefenition.getPossibleStates();
        Map<ModelResourceLocation, BlockState> map = new HashMap<>();
        list1.forEach(p_352345_ -> map.put(BlockModelShaper.stateToModelLocation(blockStateId, p_352345_), p_352345_));
        Map<BlockState, BlockStateModelLoader.LoadedModel> map1 = new HashMap<>();
        ResourceLocation resourcelocation = BLOCKSTATE_LISTER.idToFile(blockStateId);

        try {
            for (BlockStateModelLoader.LoadedJson blockstatemodelloader$loadedjson : this.blockStateResources.getOrDefault(resourcelocation, List.of())) {
                BlockModelDefinition blockmodeldefinition = blockstatemodelloader$loadedjson.parse(blockStateId, this.context);
                Map<BlockState, BlockStateModelLoader.LoadedModel> map2 = new IdentityHashMap<>();
                MultiPart multipart;
                if (blockmodeldefinition.isMultiPart()) {
                    multipart = blockmodeldefinition.getMultiPart();
                    list1.forEach(
                        p_352430_ -> map2.put(
                                p_352430_,
                                new BlockStateModelLoader.LoadedModel(multipart, () -> BlockStateModelLoader.ModelGroupKey.create(p_352430_, multipart, list))
                            )
                    );
                } else {
                    multipart = null;
                }

                blockmodeldefinition.getVariants()
                    .forEach(
                        (p_352346_, p_352105_) -> {
                            try {
                                list1.stream()
                                    .filter(predicate(stateDefenition, p_352346_))
                                    .forEach(
                                        p_352306_ -> {
                                            BlockStateModelLoader.LoadedModel blockstatemodelloader$loadedmodel = map2.put(
                                                p_352306_,
                                                new BlockStateModelLoader.LoadedModel(
                                                    p_352105_, () -> BlockStateModelLoader.ModelGroupKey.create(p_352306_, p_352105_, list)
                                                )
                                            );
                                            if (blockstatemodelloader$loadedmodel != null && blockstatemodelloader$loadedmodel.model != multipart) {
                                                map2.put(p_352306_, this.missingModel);
                                                throw new RuntimeException(
                                                    "Overlapping definition with: "
                                                        + blockmodeldefinition.getVariants()
                                                            .entrySet()
                                                            .stream()
                                                            .filter(p_352224_ -> p_352224_.getValue() == blockstatemodelloader$loadedmodel.model)
                                                            .findFirst()
                                                            .get()
                                                            .getKey()
                                                );
                                            }
                                        }
                                    );
                            } catch (Exception exception1) {
                                LOGGER.warn(
                                    "Exception loading blockstate definition: '{}' in resourcepack: '{}' for variant: '{}': {}",
                                    resourcelocation,
                                    blockstatemodelloader$loadedjson.source,
                                    p_352346_,
                                    exception1.getMessage()
                                );
                            }
                        }
                    );
                map1.putAll(map2);
            }
        } catch (BlockStateModelLoader.BlockStateDefinitionException blockstatemodelloader$blockstatedefinitionexception) {
            LOGGER.warn("{}", blockstatemodelloader$blockstatedefinitionexception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Exception loading blockstate definition: '{}'", resourcelocation, exception);
        } finally {
            Map<BlockStateModelLoader.ModelGroupKey, Set<BlockState>> map3 = new HashMap<>();
            map.forEach((p_352171_, p_352134_) -> {
                BlockStateModelLoader.LoadedModel blockstatemodelloader$loadedmodel = map1.get(p_352134_);
                if (blockstatemodelloader$loadedmodel == null) {
                    LOGGER.warn("Exception loading blockstate definition: '{}' missing model for variant: '{}'", resourcelocation, p_352171_);
                    blockstatemodelloader$loadedmodel = this.missingModel;
                }

                this.discoveredModelOutput.accept(p_352171_, blockstatemodelloader$loadedmodel.model);

                try {
                    BlockStateModelLoader.ModelGroupKey blockstatemodelloader$modelgroupkey = blockstatemodelloader$loadedmodel.key().get();
                    map3.computeIfAbsent(blockstatemodelloader$modelgroupkey, p_352112_ -> Sets.newIdentityHashSet()).add(p_352134_);
                } catch (Exception exception1) {
                    LOGGER.warn("Exception evaluating model definition: '{}'", p_352171_, exception1);
                }
            });
            map3.forEach((p_352263_, p_352463_) -> {
                Iterator<BlockState> iterator = p_352463_.iterator();

                while (iterator.hasNext()) {
                    BlockState blockstate = iterator.next();
                    if (blockstate.getRenderShape() != RenderShape.MODEL) {
                        iterator.remove();
                        this.modelGroups.put(blockstate, 0);
                    }
                }

                if (p_352463_.size() > 1) {
                    this.registerModelGroup(p_352463_);
                }
            });
        }
    }

    private static Predicate<BlockState> predicate(StateDefinition<Block, BlockState> stateDefentition, String properties) {
        Map<Property<?>, Comparable<?>> map = new HashMap<>();

        for (String s : COMMA_SPLITTER.split(properties)) {
            Iterator<String> iterator = EQUAL_SPLITTER.split(s).iterator();
            if (iterator.hasNext()) {
                String s1 = iterator.next();
                Property<?> property = stateDefentition.getProperty(s1);
                if (property != null && iterator.hasNext()) {
                    String s2 = iterator.next();
                    Comparable<?> comparable = getValueHelper(property, s2);
                    if (comparable == null) {
                        throw new RuntimeException("Unknown value: '" + s2 + "' for blockstate property: '" + s1 + "' " + property.getPossibleValues());
                    }

                    map.put(property, comparable);
                } else if (!s1.isEmpty()) {
                    throw new RuntimeException("Unknown blockstate property: '" + s1 + "'");
                }
            }
        }

        Block block = stateDefentition.getOwner();
        return p_352368_ -> {
            if (p_352368_ != null && p_352368_.is(block)) {
                for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
                    if (!Objects.equals(p_352368_.getValue(entry.getKey()), entry.getValue())) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        };
    }

    @Nullable
    static <T extends Comparable<T>> T getValueHelper(Property<T> property, String propertyName) {
        return property.getValue(propertyName).orElse(null);
    }

    private void registerModelGroup(Iterable<BlockState> models) {
        int i = this.nextModelGroup++;
        models.forEach(p_352170_ -> this.modelGroups.put(p_352170_, i));
    }

    public Object2IntMap<BlockState> getModelGroups() {
        return this.modelGroups;
    }

    @OnlyIn(Dist.CLIENT)
    static class BlockStateDefinitionException extends RuntimeException {
        public BlockStateDefinitionException(String message) {
            super(message);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record LoadedJson(String source, JsonElement data) {
        BlockModelDefinition parse(ResourceLocation blockStateId, BlockModelDefinition.Context context) {
            try {
                return BlockModelDefinition.fromJsonElement(context, this.data);
            } catch (Exception exception) {
                throw new BlockStateModelLoader.BlockStateDefinitionException(
                    String.format(
                        Locale.ROOT, "Exception loading blockstate definition: '%s' in resourcepack: '%s': %s", blockStateId, this.source, exception.getMessage()
                    )
                );
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record LoadedModel(UnbakedModel model, Supplier<BlockStateModelLoader.ModelGroupKey> key) {
    }

    @OnlyIn(Dist.CLIENT)
    static record ModelGroupKey(List<UnbakedModel> models, List<Object> coloringValues) {
        public static BlockStateModelLoader.ModelGroupKey create(BlockState state, MultiPart model, Collection<Property<?>> properties) {
            StateDefinition<Block, BlockState> statedefinition = state.getBlock().getStateDefinition();
            List<UnbakedModel> list = model.getSelectors()
                .stream()
                .filter(p_352283_ -> p_352283_.getPredicate(statedefinition).test(state))
                .map(Selector::getVariant)
                .collect(Collectors.toUnmodifiableList());
            List<Object> list1 = getColoringValues(state, properties);
            return new BlockStateModelLoader.ModelGroupKey(list, list1);
        }

        public static BlockStateModelLoader.ModelGroupKey create(BlockState state, UnbakedModel model, Collection<Property<?>> properties) {
            List<Object> list = getColoringValues(state, properties);
            return new BlockStateModelLoader.ModelGroupKey(List.of(model), list);
        }

        private static List<Object> getColoringValues(BlockState state, Collection<Property<?>> properties) {
            return properties.stream().map(state::getValue).collect(Collectors.toUnmodifiableList());
        }
    }
}
