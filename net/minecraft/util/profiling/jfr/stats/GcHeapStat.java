package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;

public record GcHeapStat(Instant timestamp, long heapUsed, GcHeapStat.Timing timing) {
    public static GcHeapStat from(RecordedEvent event) {
        return new GcHeapStat(
            event.getStartTime(),
            event.getLong("heapUsed"),
            event.getString("when").equalsIgnoreCase("before gc") ? GcHeapStat.Timing.BEFORE_GC : GcHeapStat.Timing.AFTER_GC
        );
    }

    public static GcHeapStat.Summary summary(Duration duration, List<GcHeapStat> stats, Duration gcTotalDuration, int totalGCs) {
        return new GcHeapStat.Summary(duration, gcTotalDuration, totalGCs, calculateAllocationRatePerSecond(stats));
    }

    private static double calculateAllocationRatePerSecond(List<GcHeapStat> stats) {
        long i = 0L;
        Map<GcHeapStat.Timing, List<GcHeapStat>> map = stats.stream().collect(Collectors.groupingBy(p_185689_ -> p_185689_.timing));
        List<GcHeapStat> list = map.get(GcHeapStat.Timing.BEFORE_GC);
        List<GcHeapStat> list1 = map.get(GcHeapStat.Timing.AFTER_GC);

        for (int j = 1; j < list.size(); j++) {
            GcHeapStat gcheapstat = list.get(j);
            GcHeapStat gcheapstat1 = list1.get(j - 1);
            i += gcheapstat.heapUsed - gcheapstat1.heapUsed;
        }

        Duration duration = Duration.between(stats.get(1).timestamp, stats.get(stats.size() - 1).timestamp);
        return (double)i / (double)duration.getSeconds();
    }

    public static record Summary(Duration duration, Duration gcTotalDuration, int totalGCs, double allocationRateBytesPerSecond) {
        public float gcOverHead() {
            return (float)this.gcTotalDuration.toMillis() / (float)this.duration.toMillis();
        }
    }

    static enum Timing {
        BEFORE_GC,
        AFTER_GC;
    }
}
