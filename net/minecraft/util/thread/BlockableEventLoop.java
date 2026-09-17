package net.minecraft.util.thread;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.ProfilerMeasured;
import org.slf4j.Logger;

public abstract class BlockableEventLoop<R extends Runnable> implements ProfilerMeasured, ProcessorHandle<R>, Executor {
    private final String name;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Queue<R> pendingRunnables = Queues.newConcurrentLinkedQueue();
    private int blockingCount;

    protected BlockableEventLoop(String name) {
        this.name = name;
        MetricsRegistry.INSTANCE.add(this);
    }

    protected abstract R wrapRunnable(Runnable runnable);

    protected abstract boolean shouldRun(R runnable);

    public boolean isSameThread() {
        return Thread.currentThread() == this.getRunningThread();
    }

    protected abstract Thread getRunningThread();

    protected boolean scheduleExecutables() {
        return !this.isSameThread();
    }

    public int getPendingTasksCount() {
        return this.pendingRunnables.size();
    }

    @Override
    public String name() {
        return this.name;
    }

    public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
        return this.scheduleExecutables() ? CompletableFuture.supplyAsync(supplier, this) : CompletableFuture.completedFuture(supplier.get());
    }

    public CompletableFuture<Void> submitAsync(Runnable task) {
        return CompletableFuture.<Void>supplyAsync(() -> {
            task.run();
            return null;
        }, this)
        .exceptionallyCompose(ex -> {
            // Neo: Log since this is usually swallowed
            LOGGER.error(LogUtils.FATAL_MARKER, "Error executing task on {}", name(), ex);
            return CompletableFuture.failedStage(ex);
        });
    }

    @CheckReturnValue
    public CompletableFuture<Void> submit(Runnable task) {
        if (this.scheduleExecutables()) {
            return this.submitAsync(task);
        } else {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    public void executeBlocking(Runnable task) {
        if (!this.isSameThread()) {
            this.submitAsync(task).join();
        } else {
            task.run();
        }
    }

    public void tell(R task) {
        this.pendingRunnables.add(task);
        LockSupport.unpark(this.getRunningThread());
    }

    @Override
    public void execute(Runnable task) {
        if (this.scheduleExecutables()) {
            this.tell(this.wrapRunnable(task));
        } else {
            task.run();
        }
    }

    public void executeIfPossible(Runnable task) {
        this.execute(task);
    }

    protected void dropAllTasks() {
        this.pendingRunnables.clear();
    }

    protected void runAllTasks() {
        while (this.pollTask()) {
        }
    }

    public boolean pollTask() {
        R r = this.pendingRunnables.peek();
        if (r == null) {
            return false;
        } else if (this.blockingCount == 0 && !this.shouldRun(r)) {
            return false;
        } else {
            this.doRunTask(this.pendingRunnables.remove());
            return true;
        }
    }

    /**
     * Drive the executor until the given BooleanSupplier returns true
     */
    public void managedBlock(BooleanSupplier isDone) {
        this.blockingCount++;

        try {
            while (!isDone.getAsBoolean()) {
                if (!this.pollTask()) {
                    this.waitForTasks();
                }
            }
        } finally {
            this.blockingCount--;
        }
    }

    public void waitForTasks() {
        Thread.yield();
        LockSupport.parkNanos("waiting for tasks", 100000L);
    }

    protected void doRunTask(R task) {
        try {
            task.run();
        } catch (Exception exception) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Error executing task on {}", this.name(), exception);
        }
    }

    @Override
    public List<MetricSampler> profiledMetrics() {
        return ImmutableList.of(MetricSampler.create(this.name + "-pending-tasks", MetricCategory.EVENT_LOOPS, this::getPendingTasksCount));
    }
}
