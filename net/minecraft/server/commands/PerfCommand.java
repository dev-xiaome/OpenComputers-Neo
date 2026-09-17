package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileZipper;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class PerfCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.perf.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(
        Component.translatable("commands.perf.alreadyRunning")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("perf")
                .requires(p_180462_ -> p_180462_.hasPermission(4))
                .then(Commands.literal("start").executes(p_180455_ -> startProfilingDedicatedServer(p_180455_.getSource())))
                .then(Commands.literal("stop").executes(p_180440_ -> stopProfilingDedicatedServer(p_180440_.getSource())))
        );
    }

    private static int startProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        if (minecraftserver.isRecordingMetrics()) {
            throw ERROR_ALREADY_RUNNING.create();
        } else {
            Consumer<ProfileResults> consumer = p_180460_ -> whenStopped(source, p_180460_);
            Consumer<Path> consumer1 = p_180453_ -> saveResults(source, p_180453_, minecraftserver);
            minecraftserver.startRecordingMetrics(consumer, consumer1);
            source.sendSuccess(() -> Component.translatable("commands.perf.started"), false);
            return 0;
        }
    }

    private static int stopProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        if (!minecraftserver.isRecordingMetrics()) {
            throw ERROR_NOT_RUNNING.create();
        } else {
            minecraftserver.finishRecordingMetrics();
            return 0;
        }
    }

    private static void saveResults(CommandSourceStack source, Path path, MinecraftServer server) {
        String s = String.format(
            Locale.ROOT, "%s-%s-%s", Util.getFilenameFormattedDateTime(), server.getWorldData().getLevelName(), SharedConstants.getCurrentVersion().getId()
        );

        String s1;
        try {
            s1 = FileUtil.findAvailableName(MetricsPersister.PROFILING_RESULTS_DIR, s, ".zip");
        } catch (IOException ioexception1) {
            source.sendFailure(Component.translatable("commands.perf.reportFailed"));
            LOGGER.error("Failed to create report name", (Throwable)ioexception1);
            return;
        }

        try (FileZipper filezipper = new FileZipper(MetricsPersister.PROFILING_RESULTS_DIR.resolve(s1))) {
            filezipper.add(Paths.get("system.txt"), server.fillSystemReport(new SystemReport()).toLineSeparatedString());
            filezipper.add(path);
        }

        try {
            FileUtils.forceDelete(path.toFile());
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to delete temporary profiling file {}", path, ioexception);
        }

        source.sendSuccess(() -> Component.translatable("commands.perf.reportSaved", s1), false);
    }

    private static void whenStopped(CommandSourceStack source, ProfileResults results) {
        if (results != EmptyProfileResults.EMPTY) {
            int i = results.getTickDuration();
            double d0 = (double)results.getNanoDuration() / (double)TimeUtil.NANOSECONDS_PER_SECOND;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.perf.stopped", String.format(Locale.ROOT, "%.2f", d0), i, String.format(Locale.ROOT, "%.2f", (double)i / d0)
                    ),
                false
            );
        }
    }
}
