package net.minecraft.util.profiling;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import org.slf4j.Logger;

public class SingleTickProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LongSupplier realTime;
    private final long saveThreshold;
    private int tick;
    private final File location;
    private ProfileCollector profiler = InactiveProfiler.INSTANCE;

    public SingleTickProfiler(LongSupplier realTime, String location, long saveThreshold) {
        this.realTime = realTime;
        this.location = new File("debug", location);
        this.saveThreshold = saveThreshold;
    }

    public ProfilerFiller startTick() {
        this.profiler = new ActiveProfiler(this.realTime, () -> this.tick, false);
        this.tick++;
        return this.profiler;
    }

    public void endTick() {
        if (this.profiler != InactiveProfiler.INSTANCE) {
            ProfileResults profileresults = this.profiler.getResults();
            this.profiler = InactiveProfiler.INSTANCE;
            if (profileresults.getNanoDuration() >= this.saveThreshold) {
                File file1 = new File(this.location, "tick-results-" + Util.getFilenameFormattedDateTime() + ".txt");
                profileresults.saveResults(file1.toPath());
                LOGGER.info("Recorded long tick -- wrote info to: {}", file1.getAbsolutePath());
            }
        }
    }

    @Nullable
    public static SingleTickProfiler createTickProfiler(String name) {
        return null;
    }

    public static ProfilerFiller decorateFiller(ProfilerFiller profiler, @Nullable SingleTickProfiler singleTickProfiler) {
        return singleTickProfiler != null ? ProfilerFiller.tee(singleTickProfiler.startTick(), profiler) : profiler;
    }
}
