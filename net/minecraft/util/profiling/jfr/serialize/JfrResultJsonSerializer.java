package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.DoubleStream;
import net.minecraft.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {
    private static final String BYTES_PER_SECOND = "bytesPerSecond";
    private static final String COUNT = "count";
    private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
    private static final String TOTAL_BYTES = "totalBytes";
    private static final String COUNT_PER_SECOND = "countPerSecond";
    final Gson gson = new GsonBuilder().setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();

    private static void serializePacketId(PacketIdentification packetIdentification, JsonObject json) {
        json.addProperty("protocolId", packetIdentification.protocolId());
        json.addProperty("packetId", packetIdentification.packetId());
    }

    private static void serializeChunkId(ChunkIdentification chunkIndentification, JsonObject json) {
        json.addProperty("level", chunkIndentification.level());
        json.addProperty("dimension", chunkIndentification.dimension());
        json.addProperty("x", chunkIndentification.x());
        json.addProperty("z", chunkIndentification.z());
    }

    public String format(JfrStatsResult result) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("startedEpoch", result.recordingStarted().toEpochMilli());
        jsonobject.addProperty("endedEpoch", result.recordingEnded().toEpochMilli());
        jsonobject.addProperty("durationMs", result.recordingDuration().toMillis());
        Duration duration = result.worldCreationDuration();
        if (duration != null) {
            jsonobject.addProperty("worldGenDurationMs", duration.toMillis());
        }

        jsonobject.add("heap", this.heap(result.heapSummary()));
        jsonobject.add("cpuPercent", this.cpu(result.cpuLoadStats()));
        jsonobject.add("network", this.network(result));
        jsonobject.add("fileIO", this.fileIO(result));
        jsonobject.add("serverTick", this.serverTicks(result.tickTimes()));
        jsonobject.add("threadAllocation", this.threadAllocations(result.threadAllocationSummary()));
        jsonobject.add("chunkGen", this.chunkGen(result.chunkGenSummary()));
        return this.gson.toJson((JsonElement)jsonobject);
    }

    private JsonElement heap(GcHeapStat.Summary summary) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("allocationRateBytesPerSecond", summary.allocationRateBytesPerSecond());
        jsonobject.addProperty("gcCount", summary.totalGCs());
        jsonobject.addProperty("gcOverHeadPercent", summary.gcOverHead());
        jsonobject.addProperty("gcTotalDurationMs", summary.gcTotalDuration().toMillis());
        return jsonobject;
    }

    private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> summary) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("durationNanosTotal", summary.stream().mapToDouble(p_185567_ -> (double)p_185567_.getSecond().totalDuration().toNanos()).sum());
        JsonArray jsonarray = Util.make(new JsonArray(), p_185558_ -> jsonobject.add("status", p_185558_));

        for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : summary) {
            TimedStatSummary<ChunkGenStat> timedstatsummary = pair.getSecond();
            JsonObject jsonobject1 = Util.make(new JsonObject(), jsonarray::add);
            jsonobject1.addProperty("state", pair.getFirst().toString());
            jsonobject1.addProperty("count", timedstatsummary.count());
            jsonobject1.addProperty("durationNanosTotal", timedstatsummary.totalDuration().toNanos());
            jsonobject1.addProperty("durationNanosAvg", timedstatsummary.totalDuration().toNanos() / (long)timedstatsummary.count());
            JsonObject jsonobject2 = Util.make(new JsonObject(), p_185561_ -> jsonobject1.add("durationNanosPercentiles", p_185561_));
            timedstatsummary.percentilesNanos().forEach((p_185584_, p_185585_) -> jsonobject2.addProperty("p" + p_185584_, p_185585_));
            Function<ChunkGenStat, JsonElement> function = p_185538_ -> {
                JsonObject jsonobject3 = new JsonObject();
                jsonobject3.addProperty("durationNanos", p_185538_.duration().toNanos());
                jsonobject3.addProperty("level", p_185538_.level());
                jsonobject3.addProperty("chunkPosX", p_185538_.chunkPos().x);
                jsonobject3.addProperty("chunkPosZ", p_185538_.chunkPos().z);
                jsonobject3.addProperty("worldPosX", p_185538_.worldPos().x());
                jsonobject3.addProperty("worldPosZ", p_185538_.worldPos().z());
                return jsonobject3;
            };
            jsonobject1.add("fastest", function.apply(timedstatsummary.fastest()));
            jsonobject1.add("slowest", function.apply(timedstatsummary.slowest()));
            jsonobject1.add(
                "secondSlowest", (JsonElement)(timedstatsummary.secondSlowest() != null ? function.apply(timedstatsummary.secondSlowest()) : JsonNull.INSTANCE)
            );
        }

        return jsonobject;
    }

    private JsonElement threadAllocations(ThreadAllocationStat.Summary summary) {
        JsonArray jsonarray = new JsonArray();
        summary.allocationsPerSecondByThread().forEach((p_185554_, p_185555_) -> jsonarray.add(Util.make(new JsonObject(), p_185571_ -> {
                p_185571_.addProperty("thread", p_185554_);
                p_185571_.addProperty("bytesPerSecond", p_185555_);
            })));
        return jsonarray;
    }

    private JsonElement serverTicks(List<TickTimeStat> stats) {
        if (stats.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonobject = new JsonObject();
            double[] adouble = stats.stream().mapToDouble(p_185548_ -> (double)p_185548_.currentAverage().toNanos() / 1000000.0).toArray();
            DoubleSummaryStatistics doublesummarystatistics = DoubleStream.of(adouble).summaryStatistics();
            jsonobject.addProperty("minMs", doublesummarystatistics.getMin());
            jsonobject.addProperty("averageMs", doublesummarystatistics.getAverage());
            jsonobject.addProperty("maxMs", doublesummarystatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(adouble);
            map.forEach((p_185564_, p_185565_) -> jsonobject.addProperty("p" + p_185564_, p_185565_));
            return jsonobject;
        }
    }

    private JsonElement fileIO(JfrStatsResult result) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.add("write", this.fileIoSummary(result.fileWrites()));
        jsonobject.add("read", this.fileIoSummary(result.fileReads()));
        jsonobject.add("chunksRead", this.ioSummary(result.readChunks(), JfrResultJsonSerializer::serializeChunkId));
        jsonobject.add("chunksWritten", this.ioSummary(result.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
        return jsonobject;
    }

    private JsonElement fileIoSummary(FileIOStat.Summary summary) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("totalBytes", summary.totalBytes());
        jsonobject.addProperty("count", summary.counts());
        jsonobject.addProperty("bytesPerSecond", summary.bytesPerSecond());
        jsonobject.addProperty("countPerSecond", summary.countsPerSecond());
        JsonArray jsonarray = new JsonArray();
        jsonobject.add("topContributors", jsonarray);
        summary.topTenContributorsByTotalBytes().forEach(p_185581_ -> {
            JsonObject jsonobject1 = new JsonObject();
            jsonarray.add(jsonobject1);
            jsonobject1.addProperty("path", p_185581_.getFirst());
            jsonobject1.addProperty("totalBytes", p_185581_.getSecond());
        });
        return jsonobject;
    }

    private JsonElement network(JfrStatsResult result) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.add("sent", this.ioSummary(result.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        jsonobject.add("received", this.ioSummary(result.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        return jsonobject;
    }

    private <T> JsonElement ioSummary(IoSummary<T> ioSummary, BiConsumer<T, JsonObject> serializer) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("totalBytes", ioSummary.getTotalSize());
        jsonobject.addProperty("count", ioSummary.getTotalCount());
        jsonobject.addProperty("bytesPerSecond", ioSummary.getSizePerSecond());
        jsonobject.addProperty("countPerSecond", ioSummary.getCountsPerSecond());
        JsonArray jsonarray = new JsonArray();
        jsonobject.add("topContributors", jsonarray);
        ioSummary.largestSizeContributors().forEach(p_325665_ -> {
            JsonObject jsonobject1 = new JsonObject();
            jsonarray.add(jsonobject1);
            T t = p_325665_.getFirst();
            IoSummary.CountAndSize iosummary$countandsize = p_325665_.getSecond();
            serializer.accept(t, jsonobject1);
            jsonobject1.addProperty("totalBytes", iosummary$countandsize.totalSize());
            jsonobject1.addProperty("count", iosummary$countandsize.totalCount());
            jsonobject1.addProperty("averageSize", iosummary$countandsize.averageSize());
        });
        return jsonobject;
    }

    private JsonElement cpu(List<CpuLoadStat> stats) {
        JsonObject jsonobject = new JsonObject();
        BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> bifunction = (p_185575_, p_185576_) -> {
            JsonObject jsonobject1 = new JsonObject();
            DoubleSummaryStatistics doublesummarystatistics = p_185575_.stream().mapToDouble(p_185576_).summaryStatistics();
            jsonobject1.addProperty("min", doublesummarystatistics.getMin());
            jsonobject1.addProperty("average", doublesummarystatistics.getAverage());
            jsonobject1.addProperty("max", doublesummarystatistics.getMax());
            return jsonobject1;
        };
        jsonobject.add("jvm", bifunction.apply(stats, CpuLoadStat::jvm));
        jsonobject.add("userJvm", bifunction.apply(stats, CpuLoadStat::userJvm));
        jsonobject.add("system", bifunction.apply(stats, CpuLoadStat::system));
        return jsonobject;
    }
}
