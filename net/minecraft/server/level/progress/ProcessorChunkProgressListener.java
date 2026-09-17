package net.minecraft.server.level.progress;

import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ProcessorChunkProgressListener implements ChunkProgressListener {
    private final ChunkProgressListener delegate;
    private final ProcessorMailbox<Runnable> mailbox;
    private boolean started;

    private ProcessorChunkProgressListener(ChunkProgressListener delegate, Executor dispatcher) {
        this.delegate = delegate;
        this.mailbox = ProcessorMailbox.create(dispatcher, "progressListener");
    }

    public static ProcessorChunkProgressListener createStarted(ChunkProgressListener delegate, Executor dispatcher) {
        ProcessorChunkProgressListener processorchunkprogresslistener = new ProcessorChunkProgressListener(delegate, dispatcher);
        processorchunkprogresslistener.start();
        return processorchunkprogresslistener;
    }

    @Override
    public void updateSpawnPos(ChunkPos center) {
        this.mailbox.tell(() -> this.delegate.updateSpawnPos(center));
    }

    @Override
    public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        if (this.started) {
            this.mailbox.tell(() -> this.delegate.onStatusChange(chunkPos, chunkStatus));
        }
    }

    @Override
    public void start() {
        this.started = true;
        this.mailbox.tell(this.delegate::start);
    }

    @Override
    public void stop() {
        this.started = false;
        this.mailbox.tell(this.delegate::stop);
    }
}
