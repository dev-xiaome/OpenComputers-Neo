package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class ChunkTaskPriorityQueueSorter implements ChunkHolder.LevelChangeListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<ProcessorHandle<?>, ChunkTaskPriorityQueue<? extends Function<ProcessorHandle<Unit>, ?>>> queues;
    private final Set<ProcessorHandle<?>> sleeping;
    private final ProcessorMailbox<StrictQueue.IntRunnable> mailbox;

    public ChunkTaskPriorityQueueSorter(List<ProcessorHandle<?>> queues, Executor task, int maxTasks) {
        this.queues = queues.stream()
            .collect(Collectors.toMap(Function.identity(), p_140561_ -> new ChunkTaskPriorityQueue<>(p_140561_.name() + "_queue", maxTasks)));
        this.sleeping = Sets.newHashSet(queues);
        this.mailbox = new ProcessorMailbox<>(new StrictQueue.FixedPriorityQueue(4), task, "sorter");
    }

    public boolean hasWork() {
        return this.mailbox.hasWork() || this.queues.values().stream().anyMatch(ChunkTaskPriorityQueue::hasWork);
    }

    public static <T> ChunkTaskPriorityQueueSorter.Message<T> message(Function<ProcessorHandle<Unit>, T> task, long pos, IntSupplier level) {
        return new ChunkTaskPriorityQueueSorter.Message<>(task, pos, level);
    }

    public static ChunkTaskPriorityQueueSorter.Message<Runnable> message(Runnable task, long pos, IntSupplier level) {
        return new ChunkTaskPriorityQueueSorter.Message<>(p_140634_ -> () -> {
                task.run();
                p_140634_.tell(Unit.INSTANCE);
            }, pos, level);
    }

    public static ChunkTaskPriorityQueueSorter.Message<Runnable> message(GenerationChunkHolder chunk, Runnable task) {
        return message(task, chunk.getPos().toLong(), chunk::getQueueLevel);
    }

    public static <T> ChunkTaskPriorityQueueSorter.Message<T> message(GenerationChunkHolder chunk, Function<ProcessorHandle<Unit>, T> task) {
        return message(task, chunk.getPos().toLong(), chunk::getQueueLevel);
    }

    public static ChunkTaskPriorityQueueSorter.Release release(Runnable task, long pos, boolean clearQueue) {
        return new ChunkTaskPriorityQueueSorter.Release(task, pos, clearQueue);
    }

    public <T> ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<T>> getProcessor(ProcessorHandle<T> processor, boolean flush) {
        return this.mailbox
            .<ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<T>>>ask(
                p_140610_ -> new StrictQueue.IntRunnable(
                        0,
                        () -> {
                            this.getQueue(processor);
                            p_140610_.tell(
                                ProcessorHandle.of(
                                    "chunk priority sorter around " + processor.name(),
                                    p_143176_ -> this.submit(processor, p_143176_.task, p_143176_.pos, p_143176_.level, flush)
                                )
                            );
                        }
                    )
            )
            .join();
    }

    public ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> getReleaseProcessor(ProcessorHandle<Runnable> processor) {
        return this.mailbox
            .<ProcessorHandle<ChunkTaskPriorityQueueSorter.Release>>ask(
                p_140581_ -> new StrictQueue.IntRunnable(
                        0,
                        () -> p_140581_.tell(
                                ProcessorHandle.of(
                                    "chunk priority sorter around " + processor.name(),
                                    p_143165_ -> this.release(processor, p_143165_.pos, p_143165_.task, p_143165_.clearQueue)
                                )
                            )
                    )
            )
            .join();
    }

    @Override
    public void onLevelChange(ChunkPos chunkPos, IntSupplier queueLevelGetter, int ticketLevel, IntConsumer queueLevelSetter) {
        this.mailbox.tell(new StrictQueue.IntRunnable(0, () -> {
            int i = queueLevelGetter.getAsInt();
            this.queues.values().forEach(p_143155_ -> p_143155_.resortChunkTasks(i, chunkPos, ticketLevel));
            queueLevelSetter.accept(ticketLevel);
        }));
    }

    private <T> void release(ProcessorHandle<T> processor, long chunkPos, Runnable task, boolean clearQueue) {
        this.mailbox.tell(new StrictQueue.IntRunnable(1, () -> {
            ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> chunktaskpriorityqueue = this.getQueue(processor);
            chunktaskpriorityqueue.release(chunkPos, clearQueue);
            if (this.sleeping.remove(processor)) {
                this.pollTask(chunktaskpriorityqueue, processor);
            }

            task.run();
        }));
    }

    private <T> void submit(
        ProcessorHandle<T> processor, Function<ProcessorHandle<Unit>, T> task, long chunkPos, IntSupplier level, boolean flush
    ) {
        this.mailbox.tell(new StrictQueue.IntRunnable(2, () -> {
            ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> chunktaskpriorityqueue = this.getQueue(processor);
            int i = level.getAsInt();
            chunktaskpriorityqueue.submit(Optional.of(task), chunkPos, i);
            if (flush) {
                chunktaskpriorityqueue.submit(Optional.empty(), chunkPos, i);
            }

            if (this.sleeping.remove(processor)) {
                this.pollTask(chunktaskpriorityqueue, processor);
            }
        }));
    }

    private <T> void pollTask(ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> queue, ProcessorHandle<T> processor) {
        this.mailbox.tell(new StrictQueue.IntRunnable(3, () -> {
            Stream<Either<Function<ProcessorHandle<Unit>, T>, Runnable>> stream = queue.pop();
            if (stream == null) {
                this.sleeping.add(processor);
            } else {
                CompletableFuture.allOf(stream.map(p_143172_ -> p_143172_.map(processor::ask, p_143180_ -> {
                        p_143180_.run();
                        return CompletableFuture.completedFuture(Unit.INSTANCE);
                    })).toArray(CompletableFuture[]::new)).thenAccept(p_212894_ -> this.pollTask(queue, processor));
            }
        }));
    }

    private <T> ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>> getQueue(ProcessorHandle<T> processor) {
        ChunkTaskPriorityQueue<? extends Function<ProcessorHandle<Unit>, ?>> chunktaskpriorityqueue = this.queues.get(processor);
        if (chunktaskpriorityqueue == null) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("No queue for: " + processor));
        } else {
            return (ChunkTaskPriorityQueue<Function<ProcessorHandle<Unit>, T>>)chunktaskpriorityqueue;
        }
    }

    @VisibleForTesting
    public String getDebugStatus() {
        return this.queues
                .entrySet()
                .stream()
                .map(
                    p_212898_ -> p_212898_.getKey().name()
                            + "=["
                            + p_212898_.getValue()
                                .getAcquired()
                                .stream()
                                .map(p_339451_ -> p_339451_ + ":" + new ChunkPos(p_339451_))
                                .collect(Collectors.joining(","))
                            + "]"
                )
                .collect(Collectors.joining(","))
            + ", s="
            + this.sleeping.size();
    }

    @Override
    public void close() {
        this.queues.keySet().forEach(ProcessorHandle::close);
    }

    public static final class Message<T> {
        final Function<ProcessorHandle<Unit>, T> task;
        final long pos;
        final IntSupplier level;

        Message(Function<ProcessorHandle<Unit>, T> task, long pos, IntSupplier level) {
            this.task = task;
            this.pos = pos;
            this.level = level;
        }
    }

    public static final class Release {
        final Runnable task;
        final long pos;
        final boolean clearQueue;

        Release(Runnable task, long pos, boolean clearQueue) {
            this.task = task;
            this.pos = pos;
            this.clearQueue = clearQueue;
        }
    }
}
