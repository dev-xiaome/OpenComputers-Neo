package net.minecraft;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;
import oshi.hardware.VirtualMemory;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;

public class SystemReport {
    public static final long BYTES_PER_MEBIBYTE = 1048576L;
    private static final long ONE_GIGA = 1000000000L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OPERATING_SYSTEM = System.getProperty("os.name")
        + " ("
        + System.getProperty("os.arch")
        + ") version "
        + System.getProperty("os.version");
    private static final String JAVA_VERSION = System.getProperty("java.version") + ", " + System.getProperty("java.vendor");
    private static final String JAVA_VM_VERSION = System.getProperty("java.vm.name")
        + " ("
        + System.getProperty("java.vm.info")
        + "), "
        + System.getProperty("java.vm.vendor");
    private final Map<String, String> entries = Maps.newLinkedHashMap();

    public SystemReport() {
        this.setDetail("Minecraft Version", SharedConstants.getCurrentVersion().getName());
        this.setDetail("Minecraft Version ID", SharedConstants.getCurrentVersion().getId());
        this.setDetail("Operating System", OPERATING_SYSTEM);
        this.setDetail("Java Version", JAVA_VERSION);
        this.setDetail("Java VM Version", JAVA_VM_VERSION);
        this.setDetail("Memory", () -> {
            Runtime runtime = Runtime.getRuntime();
            long i = runtime.maxMemory();
            long j = runtime.totalMemory();
            long k = runtime.freeMemory();
            long l = i / 1048576L;
            long i1 = j / 1048576L;
            long j1 = k / 1048576L;
            return k + " bytes (" + j1 + " MiB) / " + j + " bytes (" + i1 + " MiB) up to " + i + " bytes (" + l + " MiB)";
        });
        this.setDetail("CPUs", () -> String.valueOf(Runtime.getRuntime().availableProcessors()));
        this.ignoreErrors("hardware", () -> this.putHardware(new SystemInfo()));
        this.setDetail("JVM Flags", () -> {
            List<String> list = Util.getVmArguments().collect(Collectors.toList());
            return String.format(Locale.ROOT, "%d total; %s", list.size(), String.join(" ", list));
        });
    }

    public void setDetail(String identifier, String value) {
        this.entries.put(identifier, value);
    }

    public void setDetail(String identifier, Supplier<String> valueSupplier) {
        try {
            this.setDetail(identifier, valueSupplier.get());
        } catch (Exception exception) {
            LOGGER.warn("Failed to get system info for {}", identifier, exception);
            this.setDetail(identifier, "ERR");
        }
    }

    private void putHardware(SystemInfo info) {
        HardwareAbstractionLayer hardwareabstractionlayer = info.getHardware();
        this.ignoreErrors("processor", () -> this.putProcessor(hardwareabstractionlayer.getProcessor()));
        this.ignoreErrors("graphics", () -> this.putGraphics(hardwareabstractionlayer.getGraphicsCards()));
        this.ignoreErrors("memory", () -> this.putMemory(hardwareabstractionlayer.getMemory()));
        this.ignoreErrors("storage", this::putStorage);
    }

    private void ignoreErrors(String groupIdentifier, Runnable executor) {
        try {
            executor.run();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed retrieving info for group {}", groupIdentifier, throwable);
        }
    }

    public static float sizeInMiB(long bytes) {
        return (float)bytes / 1048576.0F;
    }

    private void putPhysicalMemory(List<PhysicalMemory> memorySlots) {
        int i = 0;

        for (PhysicalMemory physicalmemory : memorySlots) {
            String s = String.format(Locale.ROOT, "Memory slot #%d ", i++);
            this.setDetail(s + "capacity (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(physicalmemory.getCapacity())));
            this.setDetail(s + "clockSpeed (GHz)", () -> String.format(Locale.ROOT, "%.2f", (float)physicalmemory.getClockSpeed() / 1.0E9F));
            this.setDetail(s + "type", physicalmemory::getMemoryType);
        }
    }

    private void putVirtualMemory(VirtualMemory memory) {
        this.setDetail("Virtual memory max (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getVirtualMax())));
        this.setDetail("Virtual memory used (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getVirtualInUse())));
        this.setDetail("Swap memory total (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getSwapTotal())));
        this.setDetail("Swap memory used (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getSwapUsed())));
    }

    private void putMemory(GlobalMemory memory) {
        this.ignoreErrors("physical memory", () -> this.putPhysicalMemory(memory.getPhysicalMemory()));
        this.ignoreErrors("virtual memory", () -> this.putVirtualMemory(memory.getVirtualMemory()));
    }

    private void putGraphics(List<GraphicsCard> gpus) {
        int i = 0;

        for (GraphicsCard graphicscard : gpus) {
            String s = String.format(Locale.ROOT, "Graphics card #%d ", i++);
            this.setDetail(s + "name", graphicscard::getName);
            this.setDetail(s + "vendor", graphicscard::getVendor);
            this.setDetail(s + "VRAM (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(graphicscard.getVRam())));
            this.setDetail(s + "deviceId", graphicscard::getDeviceId);
            this.setDetail(s + "versionInfo", graphicscard::getVersionInfo);
        }
    }

    private void putProcessor(CentralProcessor cpu) {
        ProcessorIdentifier processoridentifier = cpu.getProcessorIdentifier();
        this.setDetail("Processor Vendor", processoridentifier::getVendor);
        this.setDetail("Processor Name", processoridentifier::getName);
        this.setDetail("Identifier", processoridentifier::getIdentifier);
        this.setDetail("Microarchitecture", processoridentifier::getMicroarchitecture);
        this.setDetail("Frequency (GHz)", () -> String.format(Locale.ROOT, "%.2f", (float)processoridentifier.getVendorFreq() / 1.0E9F));
        this.setDetail("Number of physical packages", () -> String.valueOf(cpu.getPhysicalPackageCount()));
        this.setDetail("Number of physical CPUs", () -> String.valueOf(cpu.getPhysicalProcessorCount()));
        this.setDetail("Number of logical CPUs", () -> String.valueOf(cpu.getLogicalProcessorCount()));
    }

    private void putStorage() {
        this.putSpaceForProperty("jna.tmpdir");
        this.putSpaceForProperty("org.lwjgl.system.SharedLibraryExtractPath");
        this.putSpaceForProperty("io.netty.native.workdir");
        this.putSpaceForProperty("java.io.tmpdir");
        this.putSpaceForPath("workdir", () -> "");
    }

    private void putSpaceForProperty(String property) {
        this.putSpaceForPath(property, () -> System.getProperty(property));
    }

    private void putSpaceForPath(String property, Supplier<String> valueSupplier) {
        String s = "Space in storage for " + property + " (MiB)";

        try {
            String s1 = valueSupplier.get();
            if (s1 == null) {
                this.setDetail(s, "<path not set>");
                return;
            }

            FileStore filestore = Files.getFileStore(Path.of(s1));
            this.setDetail(
                s, String.format(Locale.ROOT, "available: %.2f, total: %.2f", sizeInMiB(filestore.getUsableSpace()), sizeInMiB(filestore.getTotalSpace()))
            );
        } catch (InvalidPathException invalidpathexception) {
            LOGGER.warn("{} is not a path", property, invalidpathexception);
            this.setDetail(s, "<invalid path>");
        } catch (Exception exception) {
            LOGGER.warn("Failed retrieving storage space for {}", property, exception);
            this.setDetail(s, "ERR");
        }
    }

    public void appendToCrashReportString(StringBuilder reportAppender) {
        reportAppender.append("-- ").append("System Details").append(" --\n");
        reportAppender.append("Details:");
        this.entries.forEach((p_143529_, p_143530_) -> {
            reportAppender.append("\n\t");
            reportAppender.append(p_143529_);
            reportAppender.append(": ");
            reportAppender.append(p_143530_);
        });
    }

    public String toLineSeparatedString() {
        return this.entries
            .entrySet()
            .stream()
            .map(p_143534_ -> p_143534_.getKey() + ": " + p_143534_.getValue())
            .collect(Collectors.joining(System.lineSeparator()));
    }
}
