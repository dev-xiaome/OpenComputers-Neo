package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.jfr.parse.JfrStatsParser;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class SummaryReporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Runnable onDeregistration;

    protected SummaryReporter(Runnable onDeregistration) {
        this.onDeregistration = onDeregistration;
    }

    public void recordingStopped(@Nullable Path outputPath) {
        if (outputPath != null) {
            this.onDeregistration.run();
            infoWithFallback(() -> "Dumped flight recorder profiling to " + outputPath);

            JfrStatsResult jfrstatsresult;
            try {
                jfrstatsresult = JfrStatsParser.parse(outputPath);
            } catch (Throwable throwable1) {
                warnWithFallback(() -> "Failed to parse JFR recording", throwable1);
                return;
            }

            try {
                infoWithFallback(jfrstatsresult::asJson);
                Path path = outputPath.resolveSibling("jfr-report-" + StringUtils.substringBefore(outputPath.getFileName().toString(), ".jfr") + ".json");
                Files.writeString(path, jfrstatsresult.asJson(), StandardOpenOption.CREATE);
                infoWithFallback(() -> "Dumped recording summary to " + path);
            } catch (Throwable throwable) {
                warnWithFallback(() -> "Failed to output JFR report", throwable);
            }
        }
    }

    private static void infoWithFallback(Supplier<String> message) {
        if (LogUtils.isLoggerActive()) {
            LOGGER.info(message.get());
        } else {
            Bootstrap.realStdoutPrintln(message.get());
        }
    }

    private static void warnWithFallback(Supplier<String> message, Throwable throwable) {
        if (LogUtils.isLoggerActive()) {
            LOGGER.warn(message.get(), throwable);
        } else {
            Bootstrap.realStdoutPrintln(message.get());
            throwable.printStackTrace(Bootstrap.STDOUT);
        }
    }
}
