package com.mojang.realmsclient.gui.task;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.util.TimeSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class DataFetcher {
    static final Logger LOGGER = LogUtils.getLogger();
    final Executor executor;
    final TimeUnit resolution;
    final TimeSource timeSource;

    public DataFetcher(Executor executor, TimeUnit resolution, TimeSource timeSource) {
        this.executor = executor;
        this.resolution = resolution;
        this.timeSource = timeSource;
    }

    public <T> DataFetcher.Task<T> createTask(String id, Callable<T> updater, Duration period, RepeatedDelayStrategy repeatStrategy) {
        long i = this.resolution.convert(period);
        if (i == 0L) {
            throw new IllegalArgumentException("Period of " + period + " too short for selected resolution of " + this.resolution);
        } else {
            return new DataFetcher.Task<>(id, updater, i, repeatStrategy);
        }
    }

    public DataFetcher.Subscription createSubscription() {
        return new DataFetcher.Subscription();
    }

    @OnlyIn(Dist.CLIENT)
    static record ComputationResult<T>(Either<T, Exception> value, long time) {
    }

    @OnlyIn(Dist.CLIENT)
    class SubscribedTask<T> {
        private final DataFetcher.Task<T> task;
        private final Consumer<T> output;
        private long lastCheckTime = -1L;

        SubscribedTask(DataFetcher.Task<T> task, Consumer<T> output) {
            this.task = task;
            this.output = output;
        }

        void update(long time) {
            this.task.updateIfNeeded(time);
            this.runCallbackIfNeeded();
        }

        void runCallbackIfNeeded() {
            DataFetcher.SuccessfulComputationResult<T> successfulcomputationresult = this.task.lastResult;
            if (successfulcomputationresult != null && this.lastCheckTime < successfulcomputationresult.time) {
                this.output.accept(successfulcomputationresult.value);
                this.lastCheckTime = successfulcomputationresult.time;
            }
        }

        void runCallback() {
            DataFetcher.SuccessfulComputationResult<T> successfulcomputationresult = this.task.lastResult;
            if (successfulcomputationresult != null) {
                this.output.accept(successfulcomputationresult.value);
                this.lastCheckTime = successfulcomputationresult.time;
            }
        }

        void reset() {
            this.task.reset();
            this.lastCheckTime = -1L;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class Subscription {
        private final List<DataFetcher.SubscribedTask<?>> subscriptions = new ArrayList<>();

        public <T> void subscribe(DataFetcher.Task<T> task, Consumer<T> output) {
            DataFetcher.SubscribedTask<T> subscribedtask = DataFetcher.this.new SubscribedTask<>(task, output);
            this.subscriptions.add(subscribedtask);
            subscribedtask.runCallbackIfNeeded();
        }

        public void forceUpdate() {
            for (DataFetcher.SubscribedTask<?> subscribedtask : this.subscriptions) {
                subscribedtask.runCallback();
            }
        }

        public void tick() {
            for (DataFetcher.SubscribedTask<?> subscribedtask : this.subscriptions) {
                subscribedtask.update(DataFetcher.this.timeSource.get(DataFetcher.this.resolution));
            }
        }

        public void reset() {
            for (DataFetcher.SubscribedTask<?> subscribedtask : this.subscriptions) {
                subscribedtask.reset();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record SuccessfulComputationResult<T>(T value, long time) {
    }

    @OnlyIn(Dist.CLIENT)
    public class Task<T> {
        private final String id;
        private final Callable<T> updater;
        private final long period;
        private final RepeatedDelayStrategy repeatStrategy;
        @Nullable
        private CompletableFuture<DataFetcher.ComputationResult<T>> pendingTask;
        @Nullable
        DataFetcher.SuccessfulComputationResult<T> lastResult;
        private long nextUpdate = -1L;

        Task(String id, Callable<T> updater, long period, RepeatedDelayStrategy repeatStrategy) {
            this.id = id;
            this.updater = updater;
            this.period = period;
            this.repeatStrategy = repeatStrategy;
        }

        void updateIfNeeded(long time) {
            if (this.pendingTask != null) {
                DataFetcher.ComputationResult<T> computationresult = this.pendingTask.getNow(null);
                if (computationresult == null) {
                    return;
                }

                this.pendingTask = null;
                long i = computationresult.time;
                computationresult.value().ifLeft(p_239691_ -> {
                    this.lastResult = new DataFetcher.SuccessfulComputationResult<>((T)p_239691_, i);
                    this.nextUpdate = i + this.period * this.repeatStrategy.delayCyclesAfterSuccess();
                }).ifRight(p_239281_ -> {
                    long j = this.repeatStrategy.delayCyclesAfterFailure();
                    DataFetcher.LOGGER.warn("Failed to process task {}, will repeat after {} cycles", this.id, j, p_239281_);
                    this.nextUpdate = i + this.period * j;
                });
            }

            if (this.nextUpdate <= time) {
                this.pendingTask = CompletableFuture.supplyAsync(() -> {
                    try {
                        T t = this.updater.call();
                        long k = DataFetcher.this.timeSource.get(DataFetcher.this.resolution);
                        return new DataFetcher.ComputationResult<>(Either.left(t), k);
                    } catch (Exception exception) {
                        long j = DataFetcher.this.timeSource.get(DataFetcher.this.resolution);
                        return new DataFetcher.ComputationResult<>(Either.right(exception), j);
                    }
                }, DataFetcher.this.executor);
            }
        }

        public void reset() {
            this.pendingTask = null;
            this.lastResult = null;
            this.nextUpdate = -1L;
        }
    }
}
