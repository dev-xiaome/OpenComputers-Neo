package net.minecraft.util.profiling.jfr.stats;

import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public record FileIOStat(Duration duration, @Nullable String path, long bytes) {
    public static FileIOStat.Summary summary(Duration duration, List<FileIOStat> stats) {
        long i = stats.stream().mapToLong(p_185652_ -> p_185652_.bytes).sum();
        return new FileIOStat.Summary(
            i,
            (double)i / (double)duration.getSeconds(),
            (long)stats.size(),
            (double)stats.size() / (double)duration.getSeconds(),
            stats.stream().map(FileIOStat::duration).reduce(Duration.ZERO, Duration::plus),
            stats.stream()
                .filter(p_185650_ -> p_185650_.path != null)
                .collect(Collectors.groupingBy(p_185647_ -> p_185647_.path, Collectors.summingLong(p_185639_ -> p_185639_.bytes)))
                .entrySet()
                .stream()
                .sorted(Entry.<String, Long>comparingByValue().reversed())
                .map(p_185644_ -> Pair.of(p_185644_.getKey(), p_185644_.getValue()))
                .limit(10L)
                .toList()
        );
    }

    public static record Summary(
        long totalBytes,
        double bytesPerSecond,
        long counts,
        double countsPerSecond,
        Duration timeSpentInIO,
        List<Pair<String, Long>> topTenContributorsByTotalBytes
    ) {
    }
}
