package net.minecraft.util.profiling.metrics.profiling;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import org.apache.commons.lang3.tuple.Pair;

public class ProfilerSamplerAdapter {
    private final Set<String> previouslyFoundSamplerNames = new ObjectOpenHashSet<>();

    public Set<MetricSampler> newSamplersFoundInProfiler(Supplier<ProfileCollector> profiles) {
        Set<MetricSampler> set = profiles.get()
            .getChartedPaths()
            .stream()
            .filter(p_146176_ -> !this.previouslyFoundSamplerNames.contains(p_146176_.getLeft()))
            .map(p_146174_ -> samplerForProfilingPath(profiles, p_146174_.getLeft(), p_146174_.getRight()))
            .collect(Collectors.toSet());

        for (MetricSampler metricsampler : set) {
            this.previouslyFoundSamplerNames.add(metricsampler.getName());
        }

        return set;
    }

    private static MetricSampler samplerForProfilingPath(Supplier<ProfileCollector> profiles, String name, MetricCategory category) {
        return MetricSampler.create(name, category, () -> {
            ActiveProfiler.PathEntry activeprofiler$pathentry = profiles.get().getEntry(name);
            return activeprofiler$pathentry == null ? 0.0 : (double)activeprofiler$pathentry.getMaxDuration() / (double)TimeUtil.NANOSECONDS_PER_MILLISECOND;
        });
    }
}
