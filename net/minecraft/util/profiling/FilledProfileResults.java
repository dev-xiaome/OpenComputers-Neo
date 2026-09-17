package net.minecraft.util.profiling;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ReportType;
import net.minecraft.SharedConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;

public class FilledProfileResults implements ProfileResults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ProfilerPathEntry EMPTY = new ProfilerPathEntry() {
        @Override
        public long getDuration() {
            return 0L;
        }

        @Override
        public long getMaxDuration() {
            return 0L;
        }

        @Override
        public long getCount() {
            return 0L;
        }

        @Override
        public Object2LongMap<String> getCounters() {
            return Object2LongMaps.emptyMap();
        }
    };
    private static final Splitter SPLITTER = Splitter.on('\u001e');
    private static final Comparator<Entry<String, FilledProfileResults.CounterCollector>> COUNTER_ENTRY_COMPARATOR = Entry.<String, FilledProfileResults.CounterCollector>comparingByValue(
            Comparator.comparingLong(p_18489_ -> p_18489_.totalValue)
        )
        .reversed();
    private final Map<String, ? extends ProfilerPathEntry> entries;
    private final long startTimeNano;
    private final int startTimeTicks;
    private final long endTimeNano;
    private final int endTimeTicks;
    private final int tickDuration;

    public FilledProfileResults(Map<String, ? extends ProfilerPathEntry> entries, long startTimeNano, int startTimeTicks, long endTimeNano, int endTimeTicks) {
        this.entries = entries;
        this.startTimeNano = startTimeNano;
        this.startTimeTicks = startTimeTicks;
        this.endTimeNano = endTimeNano;
        this.endTimeTicks = endTimeTicks;
        this.tickDuration = endTimeTicks - startTimeTicks;
    }

    private ProfilerPathEntry getEntry(String key) {
        ProfilerPathEntry profilerpathentry = this.entries.get(key);
        return profilerpathentry != null ? profilerpathentry : EMPTY;
    }

    @Override
    public List<ResultField> getTimes(String sectionPath) {
        String s = sectionPath;
        ProfilerPathEntry profilerpathentry = this.getEntry("root");
        long i = profilerpathentry.getDuration();
        ProfilerPathEntry profilerpathentry1 = this.getEntry(sectionPath);
        long j = profilerpathentry1.getDuration();
        long k = profilerpathentry1.getCount();
        List<ResultField> list = Lists.newArrayList();
        if (!sectionPath.isEmpty()) {
            sectionPath = sectionPath + "\u001e";
        }

        long l = 0L;

        for (String s1 : this.entries.keySet()) {
            if (isDirectChild(sectionPath, s1)) {
                l += this.getEntry(s1).getDuration();
            }
        }

        float f = (float)l;
        if (l < j) {
            l = j;
        }

        if (i < l) {
            i = l;
        }

        for (String s2 : this.entries.keySet()) {
            if (isDirectChild(sectionPath, s2)) {
                ProfilerPathEntry profilerpathentry2 = this.getEntry(s2);
                long i1 = profilerpathentry2.getDuration();
                double d0 = (double)i1 * 100.0 / (double)l;
                double d1 = (double)i1 * 100.0 / (double)i;
                String s3 = s2.substring(sectionPath.length());
                list.add(new ResultField(s3, d0, d1, profilerpathentry2.getCount()));
            }
        }

        if ((float)l > f) {
            list.add(new ResultField("unspecified", (double)((float)l - f) * 100.0 / (double)l, (double)((float)l - f) * 100.0 / (double)i, k));
        }

        Collections.sort(list);
        list.add(0, new ResultField(s, 100.0, (double)l * 100.0 / (double)i, k));
        return list;
    }

    private static boolean isDirectChild(String sectionPath, String entry) {
        return entry.length() > sectionPath.length() && entry.startsWith(sectionPath) && entry.indexOf(30, sectionPath.length() + 1) < 0;
    }

    private Map<String, FilledProfileResults.CounterCollector> getCounterValues() {
        Map<String, FilledProfileResults.CounterCollector> map = Maps.newTreeMap();
        this.entries
            .forEach(
                (p_18512_, p_18513_) -> {
                    Object2LongMap<String> object2longmap = p_18513_.getCounters();
                    if (!object2longmap.isEmpty()) {
                        List<String> list = SPLITTER.splitToList(p_18512_);
                        object2longmap.forEach(
                            (p_145944_, p_145945_) -> map.computeIfAbsent(p_145944_, p_145947_ -> new FilledProfileResults.CounterCollector())
                                    .addValue(list.iterator(), p_145945_)
                        );
                    }
                }
            );
        return map;
    }

    @Override
    public long getStartTimeNano() {
        return this.startTimeNano;
    }

    @Override
    public int getStartTimeTicks() {
        return this.startTimeTicks;
    }

    @Override
    public long getEndTimeNano() {
        return this.endTimeNano;
    }

    @Override
    public int getEndTimeTicks() {
        return this.endTimeTicks;
    }

    @Override
    public boolean saveResults(Path path) {
        Writer writer = null;

        boolean flag;
        try {
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            writer.write(this.getProfilerResults(this.getNanoDuration(), this.getTickDuration()));
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("Could not save profiler results to {}", path, throwable);
            flag = false;
        } finally {
            IOUtils.closeQuietly(writer);
        }

        return flag;
    }

    protected String getProfilerResults(long timeSpan, int tickSpan) {
        StringBuilder stringbuilder = new StringBuilder();
        ReportType.PROFILE.appendHeader(stringbuilder, List.of());
        stringbuilder.append("Version: ").append(SharedConstants.getCurrentVersion().getId()).append('\n');
        stringbuilder.append("Time span: ").append(timeSpan / 1000000L).append(" ms\n");
        stringbuilder.append("Tick span: ").append(tickSpan).append(" ticks\n");
        stringbuilder.append("// This is approximately ")
            .append(String.format(Locale.ROOT, "%.2f", (float)tickSpan / ((float)timeSpan / 1.0E9F)))
            .append(" ticks per second. It should be ")
            .append(20)
            .append(" ticks per second\n\n");
        stringbuilder.append("--- BEGIN PROFILE DUMP ---\n\n");
        this.appendProfilerResults(0, "root", stringbuilder);
        stringbuilder.append("--- END PROFILE DUMP ---\n\n");
        Map<String, FilledProfileResults.CounterCollector> map = this.getCounterValues();
        if (!map.isEmpty()) {
            stringbuilder.append("--- BEGIN COUNTER DUMP ---\n\n");
            this.appendCounters(map, stringbuilder, tickSpan);
            stringbuilder.append("--- END COUNTER DUMP ---\n\n");
        }

        return stringbuilder.toString();
    }

    @Override
    public String getProfilerResults() {
        StringBuilder stringbuilder = new StringBuilder();
        this.appendProfilerResults(0, "root", stringbuilder);
        return stringbuilder.toString();
    }

    private static StringBuilder indentLine(StringBuilder builder, int indents) {
        builder.append(String.format(Locale.ROOT, "[%02d] ", indents));

        for (int i = 0; i < indents; i++) {
            builder.append("|   ");
        }

        return builder;
    }

    private void appendProfilerResults(int depth, String sectionPath, StringBuilder builder) {
        List<ResultField> list = this.getTimes(sectionPath);
        Object2LongMap<String> object2longmap = ObjectUtils.firstNonNull(this.entries.get(sectionPath), EMPTY).getCounters();
        object2longmap.forEach(
            (p_18508_, p_18509_) -> indentLine(builder, depth)
                    .append('#')
                    .append(p_18508_)
                    .append(' ')
                    .append(p_18509_)
                    .append('/')
                    .append(p_18509_ / (long)this.tickDuration)
                    .append('\n')
        );
        if (list.size() >= 3) {
            for (int i = 1; i < list.size(); i++) {
                ResultField resultfield = list.get(i);
                indentLine(builder, depth)
                    .append(resultfield.name)
                    .append('(')
                    .append(resultfield.count)
                    .append('/')
                    .append(String.format(Locale.ROOT, "%.0f", (float)resultfield.count / (float)this.tickDuration))
                    .append(')')
                    .append(" - ")
                    .append(String.format(Locale.ROOT, "%.2f", resultfield.percentage))
                    .append("%/")
                    .append(String.format(Locale.ROOT, "%.2f", resultfield.globalPercentage))
                    .append("%\n");
                if (!"unspecified".equals(resultfield.name)) {
                    try {
                        this.appendProfilerResults(depth + 1, sectionPath + "\u001e" + resultfield.name, builder);
                    } catch (Exception exception) {
                        builder.append("[[ EXCEPTION ").append(exception).append(" ]]");
                    }
                }
            }
        }
    }

    private void appendCounterResults(int indents, String name, FilledProfileResults.CounterCollector collector, int tickSpan, StringBuilder builder) {
        indentLine(builder, indents)
            .append(name)
            .append(" total:")
            .append(collector.selfValue)
            .append('/')
            .append(collector.totalValue)
            .append(" average: ")
            .append(collector.selfValue / (long)tickSpan)
            .append('/')
            .append(collector.totalValue / (long)tickSpan)
            .append('\n');
        collector.children
            .entrySet()
            .stream()
            .sorted(COUNTER_ENTRY_COMPARATOR)
            .forEach(p_18474_ -> this.appendCounterResults(indents + 1, p_18474_.getKey(), p_18474_.getValue(), tickSpan, builder));
    }

    private void appendCounters(Map<String, FilledProfileResults.CounterCollector> counters, StringBuilder builder, int tickSpan) {
        counters.forEach((p_18503_, p_18504_) -> {
            builder.append("-- Counter: ").append(p_18503_).append(" --\n");
            this.appendCounterResults(0, "root", p_18504_.children.get("root"), tickSpan, builder);
            builder.append("\n\n");
        });
    }

    @Override
    public int getTickDuration() {
        return this.tickDuration;
    }

    static class CounterCollector {
        long selfValue;
        long totalValue;
        final Map<String, FilledProfileResults.CounterCollector> children = Maps.newHashMap();

        public void addValue(Iterator<String> counters, long value) {
            this.totalValue += value;
            if (!counters.hasNext()) {
                this.selfValue += value;
            } else {
                this.children.computeIfAbsent(counters.next(), p_18546_ -> new FilledProfileResults.CounterCollector()).addValue(counters, value);
            }
        }
    }
}
