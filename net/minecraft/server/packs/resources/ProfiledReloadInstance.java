package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Stopwatch total = Stopwatch.createUnstarted();

    public ProfiledReloadInstance(
        ResourceManager resourceManager, List<PreparableReloadListener> listeners, Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor
    ) {
        super(
            backgroundExecutor,
            gameExecutor,
            resourceManager,
            listeners,
            (p_10668_, p_10669_, p_10670_, p_10671_, p_10672_) -> {
                AtomicLong atomiclong = new AtomicLong();
                AtomicLong atomiclong1 = new AtomicLong();
                ActiveProfiler activeprofiler = new ActiveProfiler(Util.timeSource, () -> 0, false);
                ActiveProfiler activeprofiler1 = new ActiveProfiler(Util.timeSource, () -> 0, false);
                CompletableFuture<Void> completablefuture = p_10670_.reload(
                    p_10668_, p_10669_, activeprofiler, activeprofiler1, p_143927_ -> p_10671_.execute(() -> {
                            long i = Util.getNanos();
                            p_143927_.run();
                            atomiclong.addAndGet(Util.getNanos() - i);
                        }), p_143920_ -> p_10672_.execute(() -> {
                            long i = Util.getNanos();
                            p_143920_.run();
                            atomiclong1.addAndGet(Util.getNanos() - i);
                        })
                );
                return completablefuture.thenApplyAsync(
                    p_143913_ -> {
                        LOGGER.debug("Finished reloading " + p_10670_.getName());
                        return new ProfiledReloadInstance.State(
                            p_10670_.getName(), activeprofiler.getResults(), activeprofiler1.getResults(), atomiclong, atomiclong1
                        );
                    },
                    gameExecutor
                );
            },
            alsoWaitedFor
        );
        this.total.start();
        this.allDone = this.allDone.thenApplyAsync(this::finish, gameExecutor);
    }

    private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> datapoints) {
        this.total.stop();
        long i = 0L;
        LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

        for (ProfiledReloadInstance.State profiledreloadinstance$state : datapoints) {
            ProfileResults profileresults = profiledreloadinstance$state.preparationResult;
            ProfileResults profileresults1 = profiledreloadinstance$state.reloadResult;
            long j = TimeUnit.NANOSECONDS.toMillis(profiledreloadinstance$state.preparationNanos.get());
            long k = TimeUnit.NANOSECONDS.toMillis(profiledreloadinstance$state.reloadNanos.get());
            long l = j + k;
            String s = profiledreloadinstance$state.name;
            LOGGER.info("{} took approximately {} ms ({} ms preparing, {} ms applying)", s, l, j, k);
            i += k;
        }

        LOGGER.info("Total blocking time: {} ms", i);
        return datapoints;
    }

    public static class State {
        final String name;
        final ProfileResults preparationResult;
        final ProfileResults reloadResult;
        final AtomicLong preparationNanos;
        final AtomicLong reloadNanos;

        State(String name, ProfileResults preperationResult, ProfileResults reloadResult, AtomicLong preperationNanos, AtomicLong reloadNanos) {
            this.name = name;
            this.preparationResult = preperationResult;
            this.reloadResult = reloadResult;
            this.preparationNanos = preperationNanos;
            this.reloadNanos = reloadNanos;
        }
    }
}
