package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class PoolAliasBindings {
    public static MapCodec<? extends PoolAliasBinding> bootstrap(Registry<MapCodec<? extends PoolAliasBinding>> registry) {
        Registry.register(registry, "random", Random.CODEC);
        Registry.register(registry, "random_group", RandomGroup.CODEC);
        return Registry.register(registry, "direct", Direct.CODEC);
    }

    public static void registerTargetsAsPools(
        BootstrapContext<StructureTemplatePool> context, Holder<StructureTemplatePool> pool, List<PoolAliasBinding> poolAliasBindings
    ) {
        poolAliasBindings.stream()
            .flatMap(PoolAliasBinding::allTargets)
            .map(p_312156_ -> p_312156_.location().getPath())
            .forEach(
                p_321475_ -> Pools.register(
                        context,
                        p_321475_,
                        new StructureTemplatePool(
                            pool, List.of(Pair.of(StructurePoolElement.single(p_321475_), 1)), StructureTemplatePool.Projection.RIGID
                        )
                    )
            );
    }
}
