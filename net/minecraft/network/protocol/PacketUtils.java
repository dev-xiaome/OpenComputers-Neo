package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

public class PacketUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Ensures that the given packet is handled on the main thread. If the current thread is not the main thread, this method
     * throws {@link net.minecraft.server.RunningOnDifferentThreadException}, which is caught and ignored in the outer call ({@link net.minecraft.network.Connection#channelRead0(io.netty.channel.ChannelHandlerContext, net.minecraft.network.protocol.Packet)}). Additionally, it then re-schedules the packet to be handled on the main thread,
     * which will then end up back here, but this time on the main thread.
     */
    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T processor, ServerLevel level) throws RunningOnDifferentThreadException {
        ensureRunningOnSameThread(packet, processor, level.getServer());
    }

    /**
     * Ensures that the given packet is handled on the main thread. If the current thread is not the main thread, this method
     * throws {@link net.minecraft.server.RunningOnDifferentThreadException}, which is caught and ignored in the outer call ({@link net.minecraft.network.Connection#channelRead0(io.netty.channel.ChannelHandlerContext, net.minecraft.network.protocol.Packet)}). Additionally, it then re-schedules the packet to be handled on the main thread,
     * which will then end up back here, but this time on the main thread.
     */
    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T processor, BlockableEventLoop<?> executor) throws RunningOnDifferentThreadException {
        if (!executor.isSameThread()) {
            executor.executeIfPossible(() -> {
                if (processor.shouldHandleMessage(packet)) {
                    try {
                        packet.handle(processor);
                    } catch (Exception exception) {
                        if (exception instanceof ReportedException reportedexception && reportedexception.getCause() instanceof OutOfMemoryError) {
                            throw makeReportedException(exception, packet, processor);
                        }

                        processor.onPacketError(packet, exception);
                    }
                } else {
                    LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                }
            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    public static <T extends PacketListener> ReportedException makeReportedException(Exception exception, Packet<T> packet, T packetListener) {
        if (exception instanceof ReportedException reportedexception) {
            fillCrashReport(reportedexception.getReport(), packetListener, packet);
            return reportedexception;
        } else {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Main thread packet handler");
            fillCrashReport(crashreport, packetListener, packet);
            return new ReportedException(crashreport);
        }
    }

    public static <T extends PacketListener> void fillCrashReport(CrashReport crashReport, T packetListener, @Nullable Packet<T> packet) {
        if (packet != null) {
            CrashReportCategory crashreportcategory = crashReport.addCategory("Incoming Packet");
            crashreportcategory.setDetail("Type", () -> packet.type().toString());
            crashreportcategory.setDetail("Is Terminal", () -> Boolean.toString(packet.isTerminal()));
            crashreportcategory.setDetail("Is Skippable", () -> Boolean.toString(packet.isSkippable()));
        }

        packetListener.fillCrashReport(crashReport);
    }
}
