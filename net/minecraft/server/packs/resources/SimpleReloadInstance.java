package net.minecraft.server.packs.resources;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.InactiveProfiler;

public class SimpleReloadInstance<S> implements ReloadInstance {
    private static final int PREPARATION_PROGRESS_WEIGHT = 2;
    private static final int EXTRA_RELOAD_PROGRESS_WEIGHT = 2;
    private static final int LISTENER_PROGRESS_WEIGHT = 1;
    protected final CompletableFuture<Unit> allPreparations = new CompletableFuture<>();
    protected CompletableFuture<List<S>> allDone;
    final Set<PreparableReloadListener> preparingListeners;
    private final int listenerCount;
    private int startedReloads;
    private int finishedReloads;
    private final AtomicInteger startedTaskCounter = new AtomicInteger();
    private final AtomicInteger doneTaskCounter = new AtomicInteger();

    public static SimpleReloadInstance<Void> of(
        ResourceManager resourceManager, List<PreparableReloadListener> listeners, Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor
    ) {
        return new SimpleReloadInstance<>(
            backgroundExecutor,
            gameExecutor,
            resourceManager,
            listeners,
            (p_10829_, p_10830_, p_10831_, p_10832_, p_10833_) -> p_10831_.reload(
                    p_10829_, p_10830_, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, backgroundExecutor, p_10833_
                ),
            alsoWaitedFor
        );
    }

    protected SimpleReloadInstance(
        Executor backgroundExecutor,
        final Executor gameExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        this.listenerCount = listeners.size();
        this.startedTaskCounter.incrementAndGet();
        alsoWaitedFor.thenRun(this.doneTaskCounter::incrementAndGet);
        List<CompletableFuture<S>> list = Lists.newArrayList();
        CompletableFuture<?> completablefuture = alsoWaitedFor;
        this.preparingListeners = Sets.newHashSet(listeners);

        for (final PreparableReloadListener preparablereloadlistener : listeners) {
            final CompletableFuture<?> completablefuture1 = completablefuture;
            CompletableFuture<S> completablefuture2 = stateFactory.create(
                new PreparableReloadListener.PreparationBarrier() {
                    @Override
                    public <T> CompletableFuture<T> wait(T p_10858_) {
                        gameExecutor.execute(() -> {
                            SimpleReloadInstance.this.preparingListeners.remove(preparablereloadlistener);
                            if (SimpleReloadInstance.this.preparingListeners.isEmpty()) {
                                SimpleReloadInstance.this.allPreparations.complete(Unit.INSTANCE);
                            }
                        });
                        return SimpleReloadInstance.this.allPreparations
                            .thenCombine((CompletionStage<? extends T>)completablefuture1, (p_10861_, p_10862_) -> p_10858_);
                    }
                },
                resourceManager,
                preparablereloadlistener,
                p_10842_ -> {
                    this.startedTaskCounter.incrementAndGet();
                    backgroundExecutor.execute(() -> {
                        p_10842_.run();
                        this.doneTaskCounter.incrementAndGet();
                    });
                },
                p_10836_ -> {
                    this.startedReloads++;
                    gameExecutor.execute(() -> {
                        p_10836_.run();
                        this.finishedReloads++;
                    });
                }
            );
            list.add(completablefuture2);
            completablefuture = completablefuture2;
        }

        this.allDone = Util.sequenceFailFast(list);
    }

    @Override
    public CompletableFuture<?> done() {
        return this.allDone;
    }

    @Override
    public float getActualProgress() {
        int i = this.listenerCount - this.preparingListeners.size();
        float f = (float)(this.doneTaskCounter.get() * 2 + this.finishedReloads * 2 + i * 1);
        float f1 = (float)(this.startedTaskCounter.get() * 2 + this.startedReloads * 2 + this.listenerCount * 1);
        return f / f1;
    }

    public static ReloadInstance create(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor,
        boolean profiled
    ) {
        return (ReloadInstance)(profiled
            ? new ProfiledReloadInstance(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor)
            : of(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor));
    }

    protected interface StateFactory<S> {
        CompletableFuture<S> create(
            PreparableReloadListener.PreparationBarrier preperationBarrier,
            ResourceManager resourceManager,
            PreparableReloadListener listener,
            Executor backgroundExecutor,
            Executor gameExecutor
        );
    }
}
