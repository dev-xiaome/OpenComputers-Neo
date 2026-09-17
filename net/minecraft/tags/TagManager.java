package net.minecraft.tags;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class TagManager implements PreparableReloadListener {
    private final RegistryAccess registryAccess;
    private List<TagManager.LoadResult<?>> results = List.of();

    public TagManager(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    public List<TagManager.LoadResult<?>> getResult() {
        return this.results;
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        List<? extends CompletableFuture<? extends TagManager.LoadResult<?>>> list = this.registryAccess
            .registries()
            .map(p_203927_ -> this.createLoader(resourceManager, backgroundExecutor, (RegistryAccess.RegistryEntry<?>)p_203927_))
            .toList();
        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new))
            .thenCompose(stage::wait)
            .thenAcceptAsync(p_203917_ -> this.results = list.stream().map(CompletableFuture::join).collect(Collectors.toUnmodifiableList()), gameExecutor);
    }

    private <T> CompletableFuture<TagManager.LoadResult<T>> createLoader(
        ResourceManager resourceManager, Executor backgroundExecutor, RegistryAccess.RegistryEntry<T> entry
    ) {
        ResourceKey<? extends Registry<T>> resourcekey = entry.key();
        Registry<T> registry = entry.value();
        TagLoader<Holder<T>> tagloader = new TagLoader<>(registry::getHolder, Registries.tagsDirPath(resourcekey));
        return CompletableFuture.supplyAsync(() -> new TagManager.LoadResult<>(resourcekey, tagloader.loadAndBuild(resourceManager)), backgroundExecutor);
    }

    public static record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<ResourceLocation, Collection<Holder<T>>> tags) {
    }
}
