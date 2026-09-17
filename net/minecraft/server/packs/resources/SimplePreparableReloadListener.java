package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> extends net.neoforged.neoforge.resource.ContextAwareReloadListener implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(resourceManager, preparationsProfiler), backgroundExecutor)
            .thenCompose(stage::wait)
            .thenAcceptAsync(p_10792_ -> this.apply((T)p_10792_, resourceManager, reloadProfiler), gameExecutor);
    }

    /**
     * Performs any reloading that can be done off-thread, such as file IO
     */
    protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profiler);

    protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profiler);
}
