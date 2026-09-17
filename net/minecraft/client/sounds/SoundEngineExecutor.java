package net.minecraft.client.sounds;

import java.util.concurrent.locks.LockSupport;
import net.minecraft.util.thread.BlockableEventLoop;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The SoundEngineExecutor class is responsible for executing sound-related tasks in a separate thread.
 * <p>
 * It extends the BlockableEventLoop class, providing an event loop for managing and executing tasks.
 */
@OnlyIn(Dist.CLIENT)
public class SoundEngineExecutor extends BlockableEventLoop<Runnable> {
    private Thread thread = this.createThread();
    private volatile boolean shutdown;

    public SoundEngineExecutor() {
        super("Sound executor");
    }

    private Thread createThread() {
        Thread thread = new Thread(this::run);
        thread.setDaemon(true);
        thread.setName("Sound engine");
        thread.start();
        return thread;
    }

    /**
     * Wraps the given runnable task. In this case, the original runnable is returned as-is.
     * <p>
     * @return The wrapped runnable task
     *
     * @param runnable The original runnable task
     */
    @Override
    protected Runnable wrapRunnable(Runnable runnable) {
        return runnable;
    }

    /**
     * Determines whether the given runnable task should be run or not.
     * It depends on the shutdown state of the SoundEngineExecutor.
     * <p>
     * @return true if the task should run, false otherwise
     *
     * @param runnable The runnable task
     */
    @Override
    protected boolean shouldRun(Runnable runnable) {
        return !this.shutdown;
    }

    @Override
    protected Thread getRunningThread() {
        return this.thread;
    }

    private void run() {
        while (!this.shutdown) {
            this.managedBlock(() -> this.shutdown);
        }
    }

    @Override
    public void waitForTasks() {
        LockSupport.park("waiting for tasks");
    }

    public void flush() {
        this.shutdown = true;
        this.thread.interrupt();

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
            Thread.currentThread().interrupt();
        }

        this.dropAllTasks();
        this.shutdown = false;
        this.thread = this.createThread();
    }
}
