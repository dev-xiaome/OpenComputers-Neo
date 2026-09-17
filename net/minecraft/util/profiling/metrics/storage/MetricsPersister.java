package net.minecraft.util.profiling.metrics.storage;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class MetricsPersister {
    public static final Path PROFILING_RESULTS_DIR = Paths.get("debug/profiling");
    public static final String METRICS_DIR_NAME = "metrics";
    public static final String DEVIATIONS_DIR_NAME = "deviations";
    public static final String PROFILING_RESULT_FILENAME = "profiling.txt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String rootFolderName;

    public MetricsPersister(String rootFolderName) {
        this.rootFolderName = rootFolderName;
    }

    public Path saveReports(Set<MetricSampler> samplers, Map<MetricSampler, List<RecordedDeviation>> deviations, ProfileResults results) {
        try {
            Files.createDirectories(PROFILING_RESULTS_DIR);
        } catch (IOException ioexception1) {
            throw new UncheckedIOException(ioexception1);
        }

        try {
            Path path = Files.createTempDirectory("minecraft-profiling");
            path.toFile().deleteOnExit();
            Files.createDirectories(PROFILING_RESULTS_DIR);
            Path path1 = path.resolve(this.rootFolderName);
            Path path2 = path1.resolve("metrics");
            this.saveMetrics(samplers, path2);
            if (!deviations.isEmpty()) {
                this.saveDeviations(deviations, path1.resolve("deviations"));
            }

            this.saveProfilingTaskExecutionResult(results, path1);
            return path;
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    private void saveMetrics(Set<MetricSampler> samplers, Path path) {
        if (samplers.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one sampler to persist");
        } else {
            Map<MetricCategory, List<MetricSampler>> map = samplers.stream().collect(Collectors.groupingBy(MetricSampler::getCategory));
            map.forEach((p_146232_, p_146233_) -> this.saveCategory(p_146232_, (List<MetricSampler>)p_146233_, path));
        }
    }

    private void saveCategory(MetricCategory category, List<MetricSampler> samplers, Path p_path) {
        Path path = p_path.resolve(Util.sanitizeName(category.getDescription(), ResourceLocation::validPathChar) + ".csv");
        Writer writer = null;

        try {
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            CsvOutput.Builder csvoutput$builder = CsvOutput.builder();
            csvoutput$builder.addColumn("@tick");

            for (MetricSampler metricsampler : samplers) {
                csvoutput$builder.addColumn(metricsampler.getName());
            }

            CsvOutput csvoutput = csvoutput$builder.build(writer);
            List<MetricSampler.SamplerResult> list = samplers.stream().map(MetricSampler::result).collect(Collectors.toList());
            int i = list.stream().mapToInt(MetricSampler.SamplerResult::getFirstTick).summaryStatistics().getMin();
            int j = list.stream().mapToInt(MetricSampler.SamplerResult::getLastTick).summaryStatistics().getMax();

            for (int k = i; k <= j; k++) {
                int l = k;
                Stream<String> stream = list.stream().map(p_146222_ -> String.valueOf(p_146222_.valueAtTick(l)));
                Object[] aobject = Stream.concat(Stream.of(String.valueOf(k)), stream).toArray(String[]::new);
                csvoutput.writeRow(aobject);
            }

            LOGGER.info("Flushed metrics to {}", path);
        } catch (Exception exception) {
            LOGGER.error("Could not save profiler results to {}", path, exception);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private void saveDeviations(Map<MetricSampler, List<RecordedDeviation>> deviations, Path p_path) {
        DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS", Locale.UK).withZone(ZoneId.systemDefault());
        deviations.forEach(
            (p_146242_, p_146243_) -> p_146243_.forEach(
                    p_146238_ -> {
                        String s = datetimeformatter.format(p_146238_.timestamp);
                        Path path = p_path.resolve(Util.sanitizeName(p_146242_.getName(), ResourceLocation::validPathChar))
                            .resolve(String.format(Locale.ROOT, "%d@%s.txt", p_146238_.tick, s));
                        p_146238_.profilerResultAtTick.saveResults(path);
                    }
                )
        );
    }

    private void saveProfilingTaskExecutionResult(ProfileResults results, Path outputPath) {
        results.saveResults(outputPath.resolve("profiling.txt"));
    }
}
