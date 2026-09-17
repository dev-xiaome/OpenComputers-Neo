package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public interface PoolAliasBinding {
    Codec<PoolAliasBinding> CODEC = BuiltInRegistries.POOL_ALIAS_BINDING_TYPE.byNameCodec().dispatch(PoolAliasBinding::codec, Function.identity());

    void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> stucturePoolKey);

    Stream<ResourceKey<StructureTemplatePool>> allTargets();

    static Direct direct(String alias, String target) {
        return direct(Pools.createKey(alias), Pools.createKey(target));
    }

    static Direct direct(ResourceKey<StructureTemplatePool> alias, ResourceKey<StructureTemplatePool> target) {
        return new Direct(alias, target);
    }

    static Random random(String alias, SimpleWeightedRandomList<String> targets) {
        SimpleWeightedRandomList.Builder<ResourceKey<StructureTemplatePool>> builder = SimpleWeightedRandomList.builder();
        targets.unwrap().forEach(p_338103_ -> builder.add(Pools.createKey(p_338103_.data()), p_338103_.getWeight().asInt()));
        return random(Pools.createKey(alias), builder.build());
    }

    static Random random(ResourceKey<StructureTemplatePool> alias, SimpleWeightedRandomList<ResourceKey<StructureTemplatePool>> targets) {
        return new Random(alias, targets);
    }

    static RandomGroup randomGroup(SimpleWeightedRandomList<List<PoolAliasBinding>> groups) {
        return new RandomGroup(groups);
    }

    MapCodec<? extends PoolAliasBinding> codec();
}
