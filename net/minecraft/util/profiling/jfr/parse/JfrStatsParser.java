package net.minecraft.util.profiling.jfr.parse;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;

public class JfrStatsParser {
    private Instant recordingStarted = Instant.EPOCH;
    private Instant recordingEnded = Instant.EPOCH;
    private final List<ChunkGenStat> chunkGenStats = Lists.newArrayList();
    private final List<CpuLoadStat> cpuLoadStat = Lists.newArrayList();
    private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> receivedPackets = Maps.newHashMap();
    private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> sentPackets = Maps.newHashMap();
    private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> readChunks = Maps.newHashMap();
    private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> writtenChunks = Maps.newHashMap();
    private final List<FileIOStat> fileWrites = Lists.newArrayList();
    private final List<FileIOStat> fileReads = Lists.newArrayList();
    private int garbageCollections;
    private Duration gcTotalDuration = Duration.ZERO;
    private final List<GcHeapStat> gcHeapStats = Lists.newArrayList();
    private final List<ThreadAllocationStat> threadAllocationStats = Lists.newArrayList();
    private final List<TickTimeStat> tickTimes = Lists.newArrayList();
    @Nullable
    private Duration worldCreationDuration = null;

    private JfrStatsParser(Stream<RecordedEvent> events) {
        this.capture(events);
    }

    public static JfrStatsResult parse(Path file) {
        try {
            JfrStatsResult jfrstatsresult;
            try (final RecordingFile recordingfile = new RecordingFile(file)) {
                Iterator<RecordedEvent> iterator = new Iterator<RecordedEvent>() {
                    @Override
                    public boolean hasNext() {
                        return recordingfile.hasMoreEvents();
                    }

                    public RecordedEvent next() {
                        if (!this.hasNext()) {
                            throw new NoSuchElementException();
                        } else {
                            try {
                                return recordingfile.readEvent();
                            } catch (IOException ioexception1) {
                                throw new UncheckedIOException(ioexception1);
                            }
                        }
                    }
                };
                Stream<RecordedEvent> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 1297), false);
                jfrstatsresult = new JfrStatsParser(stream).results();
            }

            return jfrstatsresult;
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    private JfrStatsResult results() {
        Duration duration = Duration.between(this.recordingStarted, this.recordingEnded);
        return new JfrStatsResult(
            this.recordingStarted,
            this.recordingEnded,
            duration,
            this.worldCreationDuration,
            this.tickTimes,
            this.cpuLoadStat,
            GcHeapStat.summary(duration, this.gcHeapStats, this.gcTotalDuration, this.garbageCollections),
            ThreadAllocationStat.summary(this.threadAllocationStats),
            collectIoStats(duration, this.receivedPackets),
            collectIoStats(duration, this.sentPackets),
            collectIoStats(duration, this.writtenChunks),
            collectIoStats(duration, this.readChunks),
            FileIOStat.summary(duration, this.fileWrites),
            FileIOStat.summary(duration, this.fileReads),
            this.chunkGenStats
        );
    }

    private void capture(Stream<RecordedEvent> events) {
        events.forEach(p_325662_ -> {
            if (p_325662_.getEndTime().isAfter(this.recordingEnded) || this.recordingEnded.equals(Instant.EPOCH)) {
                this.recordingEnded = p_325662_.getEndTime();
            }

            if (p_325662_.getStartTime().isBefore(this.recordingStarted) || this.recordingStarted.equals(Instant.EPOCH)) {
                this.recordingStarted = p_325662_.getStartTime();
            }

            String s = p_325662_.getEventType().getName();
            switch (s) {
                case "minecraft.ChunkGeneration":
                    this.chunkGenStats.add(ChunkGenStat.from(p_325662_));
                    break;
                case "minecraft.LoadWorld":
                    this.worldCreationDuration = p_325662_.getDuration();
                    break;
                case "minecraft.ServerTickTime":
                    this.tickTimes.add(TickTimeStat.from(p_325662_));
                    break;
                case "minecraft.PacketReceived":
                    this.incrementPacket(p_325662_, p_325662_.getInt("bytes"), this.receivedPackets);
                    break;
                case "minecraft.PacketSent":
                    this.incrementPacket(p_325662_, p_325662_.getInt("bytes"), this.sentPackets);
                    break;
                case "minecraft.ChunkRegionRead":
                    this.incrementChunk(p_325662_, p_325662_.getInt("bytes"), this.readChunks);
                    break;
                case "minecraft.ChunkRegionWrite":
                    this.incrementChunk(p_325662_, p_325662_.getInt("bytes"), this.writtenChunks);
                    break;
                case "jdk.ThreadAllocationStatistics":
                    this.threadAllocationStats.add(ThreadAllocationStat.from(p_325662_));
                    break;
                case "jdk.GCHeapSummary":
                    this.gcHeapStats.add(GcHeapStat.from(p_325662_));
                    break;
                case "jdk.CPULoad":
                    this.cpuLoadStat.add(CpuLoadStat.from(p_325662_));
                    break;
                case "jdk.FileWrite":
                    this.appendFileIO(p_325662_, this.fileWrites, "bytesWritten");
                    break;
                case "jdk.FileRead":
                    this.appendFileIO(p_325662_, this.fileReads, "bytesRead");
                    break;
                case "jdk.GarbageCollection":
                    this.garbageCollections++;
                    this.gcTotalDuration = this.gcTotalDuration.plus(p_325662_.getDuration());
            }
        });
    }

    private void incrementPacket(RecordedEvent event, int increment, Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> packets) {
        packets.computeIfAbsent(PacketIdentification.from(event), p_325660_ -> new JfrStatsParser.MutableCountAndSize()).increment(increment);
    }

    private void incrementChunk(RecordedEvent event, int increment, Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> chunks) {
        chunks.computeIfAbsent(ChunkIdentification.from(event), p_326333_ -> new JfrStatsParser.MutableCountAndSize()).increment(increment);
    }

    private void appendFileIO(RecordedEvent event, List<FileIOStat> stats, String id) {
        stats.add(new FileIOStat(event.getDuration(), event.getString("path"), event.getLong(id)));
    }

    private static <T> IoSummary<T> collectIoStats(Duration recordingDuration, Map<T, JfrStatsParser.MutableCountAndSize> entries) {
        List<Pair<T, IoSummary.CountAndSize>> list = entries.entrySet()
            .stream()
            .map(p_325661_ -> Pair.of(p_325661_.getKey(), p_325661_.getValue().toCountAndSize()))
            .toList();
        return new IoSummary<>(recordingDuration, list);
    }

    public static final class MutableCountAndSize {
        private long count;
        private long totalSize;

        public void increment(int increment) {
            this.totalSize += (long)increment;
            this.count++;
        }

        public IoSummary.CountAndSize toCountAndSize() {
            return new IoSummary.CountAndSize(this.count, this.totalSize);
        }
    }
}
