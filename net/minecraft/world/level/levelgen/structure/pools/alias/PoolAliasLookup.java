package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = p_307289_ -> p_307289_;

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> poolKey);

    static PoolAliasLookup create(List<PoolAliasBinding> aliases, BlockPos pos, long seed) {
        if (aliases.isEmpty()) {
            return EMPTY;
        } else {
            RandomSource randomsource = RandomSource.create(seed).forkPositional().at(pos);
            Builder<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> builder = ImmutableMap.builder();
            aliases.forEach(p_307533_ -> p_307533_.forEachResolved(randomsource, builder::put));
            Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = builder.build();
            return p_307442_ -> Objects.requireNonNull(map.getOrDefault(p_307442_, p_307442_), () -> "alias " + p_307442_ + " was mapped to null value");
        }
    }
}
