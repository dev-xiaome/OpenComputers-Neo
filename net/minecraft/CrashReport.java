package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.util.MemoryReserve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

public class CrashReport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final Throwable exception;
    private final List<CrashReportCategory> details = Lists.newArrayList();
    @Nullable
    private Path saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];
    private final SystemReport systemReport = new SystemReport();

    public CrashReport(String title, Throwable exception) {
        this.title = title;
        this.exception = exception;
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public String getDetails() {
        StringBuilder stringbuilder = new StringBuilder();
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    /**
     * Gets the various sections of the crash report into the given StringBuilder
     */
    public void getDetails(StringBuilder builder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = ArrayUtils.subarray(this.details.get(0).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            builder.append("-- Head --\n");
            builder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            builder.append("Stacktrace:");
            builder.append(net.neoforged.neoforge.logging.CrashReportExtender.generateEnhancedStackTrace(this.uncategorizedStackTrace));
        }

        for (CrashReportCategory crashreportcategory : this.details) {
            crashreportcategory.getDetails(builder);
            builder.append("\n\n");
        }

        net.neoforged.neoforge.logging.CrashReportExtender.extendSystemReport(systemReport);
        this.systemReport.appendToCrashReportString(builder);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Throwable throwable = this.exception;
        if (throwable.getMessage() == null) {
            if (throwable instanceof NullPointerException) {
                throwable = new NullPointerException(this.title);
            } else if (throwable instanceof StackOverflowError) {
                throwable = new StackOverflowError(this.title);
            } else if (throwable instanceof OutOfMemoryError) {
                throwable = new OutOfMemoryError(this.title);
            }

            throwable.setStackTrace(this.exception.getStackTrace());
        }

        return net.neoforged.neoforge.logging.CrashReportExtender.generateEnhancedStackTrace(throwable);
    }

    public String getFriendlyReport(ReportType type, List<String> links) {
        StringBuilder stringbuilder = new StringBuilder();
        type.appendHeader(stringbuilder, links);
        stringbuilder.append("Time: ");
        stringbuilder.append(DATE_TIME_FORMATTER.format(ZonedDateTime.now()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.title);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; i++) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public String getFriendlyReport(ReportType type) {
        return this.getFriendlyReport(type, List.of());
    }

    @Nullable
    public Path getSaveFile() {
        return this.saveFile;
    }

    public boolean saveToFile(Path path, ReportType type, List<String> links) {
        if (this.saveFile != null) {
            return false;
        } else {
            try {
                if (path.getParent() != null) {
                    FileUtil.createDirectoriesSafe(path.getParent());
                }

                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write(this.getFriendlyReport(type, links));
                }

                this.saveFile = path;
                return true;
            } catch (Throwable throwable1) {
                LOGGER.error("Could not save crash report to {}", path, throwable1);
                return false;
            }
        }
    }

    public boolean saveToFile(Path path, ReportType type) {
        return this.saveToFile(path, type, List.of());
    }

    public SystemReport getSystemReport() {
        return this.systemReport;
    }

    /**
     * Creates a CrashReportCategory
     */
    public CrashReportCategory addCategory(String name) {
        return this.addCategory(name, 1);
    }

    /**
     * Creates a CrashReportCategory for the given stack trace depth
     */
    public CrashReportCategory addCategory(String categoryName, int stacktraceLength) {
        CrashReportCategory crashreportcategory = new CrashReportCategory(categoryName);
        if (this.trackingStackTrace) {
            int i = crashreportcategory.fillInStackTrace(stacktraceLength);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int j = astacktraceelement.length - i;
            if (j < 0) {
                LOGGER.error("Negative index in crash report handler ({}/{})", astacktraceelement.length, i);
            }

            if (astacktraceelement != null && 0 <= j && j < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[j];
                if (astacktraceelement.length + 1 - i < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - i];
                }
            }

            this.trackingStackTrace = crashreportcategory.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (astacktraceelement != null && astacktraceelement.length >= i && 0 <= j && j < astacktraceelement.length) {
                this.uncategorizedStackTrace = new StackTraceElement[j];
                System.arraycopy(astacktraceelement, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashreportcategory);
        return crashreportcategory;
    }

    /**
     * Creates a crash report for the exception
     */
    public static CrashReport forThrowable(Throwable cause, String description) {
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }

        CrashReport crashreport;
        if (cause instanceof ReportedException reportedexception) {
            crashreport = reportedexception.getReport();
        } else {
            crashreport = new CrashReport(description, cause);
        }

        return crashreport;
    }

    public static void preload() {
        MemoryReserve.allocate();
        new CrashReport("Don't panic!", new Throwable()).getFriendlyReport(ReportType.CRASH);
    }
}
