package net.minecraft;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class CrashReportCategory {
    private final String title;
    private final List<CrashReportCategory.Entry> entries = Lists.newArrayList();
    private StackTraceElement[] stackTrace = new StackTraceElement[0];

    public CrashReportCategory(String title) {
        this.title = title;
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccess, double x, double y, double z) {
        return String.format(
            Locale.ROOT,
            "%.2f,%.2f,%.2f - %s",
            x,
            y,
            z,
            formatLocation(levelHeightAccess, BlockPos.containing(x, y, z))
        );
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccess, BlockPos pos) {
        return formatLocation(levelHeightAccess, pos.getX(), pos.getY(), pos.getZ());
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccess, int x, int y, int z) {
        StringBuilder stringbuilder = new StringBuilder();

        try {
            stringbuilder.append(String.format(Locale.ROOT, "World: (%d,%d,%d)", x, y, z));
        } catch (Throwable throwable2) {
            stringbuilder.append("(Error finding world loc)");
        }

        stringbuilder.append(", ");

        try {
            int i = SectionPos.blockToSectionCoord(x);
            int j = SectionPos.blockToSectionCoord(y);
            int k = SectionPos.blockToSectionCoord(z);
            int l = x & 15;
            int i1 = y & 15;
            int j1 = z & 15;
            int k1 = SectionPos.sectionToBlockCoord(i);
            int l1 = levelHeightAccess.getMinBuildHeight();
            int i2 = SectionPos.sectionToBlockCoord(k);
            int j2 = SectionPos.sectionToBlockCoord(i + 1) - 1;
            int k2 = levelHeightAccess.getMaxBuildHeight() - 1;
            int l2 = SectionPos.sectionToBlockCoord(k + 1) - 1;
            stringbuilder.append(
                String.format(
                    Locale.ROOT, "Section: (at %d,%d,%d in %d,%d,%d; chunk contains blocks %d,%d,%d to %d,%d,%d)", l, i1, j1, i, j, k, k1, l1, i2, j2, k2, l2
                )
            );
        } catch (Throwable throwable1) {
            stringbuilder.append("(Error finding chunk loc)");
        }

        stringbuilder.append(", ");

        try {
            int i3 = x >> 9;
            int j3 = z >> 9;
            int k3 = i3 << 5;
            int l3 = j3 << 5;
            int i4 = (i3 + 1 << 5) - 1;
            int j4 = (j3 + 1 << 5) - 1;
            int k4 = i3 << 9;
            int l4 = levelHeightAccess.getMinBuildHeight();
            int i5 = j3 << 9;
            int j5 = (i3 + 1 << 9) - 1;
            int k5 = levelHeightAccess.getMaxBuildHeight() - 1;
            int l5 = (j3 + 1 << 9) - 1;
            stringbuilder.append(
                String.format(
                    Locale.ROOT, "Region: (%d,%d; contains chunks %d,%d to %d,%d, blocks %d,%d,%d to %d,%d,%d)", i3, j3, k3, l3, i4, j4, k4, l4, i5, j5, k5, l5
                )
            );
        } catch (Throwable throwable) {
            stringbuilder.append("(Error finding world loc)");
        }

        return stringbuilder.toString();
    }

    /**
     * Adds a section to this crash report category, resolved by calling the given callable.
     *
     * If the given callable throws an exception, a detail containing that exception will be created instead.
     */
    public CrashReportCategory setDetail(String name, CrashReportDetail<String> detail) {
        try {
            this.setDetail(name, detail.call());
        } catch (Throwable throwable) {
            this.setDetailError(name, throwable);
        }

        return this;
    }

    /**
     * Adds a Crashreport section with the given name with the given value (converted {@code .toString()})
     */
    public CrashReportCategory setDetail(String sectionName, Object value) {
        this.entries.add(new CrashReportCategory.Entry(sectionName, value));
        return this;
    }

    /**
     * Adds a Crashreport section with the given name with the given Throwable
     */
    public void setDetailError(String sectionName, Throwable throwable) {
        this.setDetail(sectionName, throwable);
    }

    /**
     * Resets our stack trace according to the current trace, pruning the deepest 3 entries.  The parameter indicates how many additional deepest entries to prune.  Returns the number of entries in the resulting pruned stack trace.
     */
    public int fillInStackTrace(int size) {
        StackTraceElement[] astacktraceelement = Thread.currentThread().getStackTrace();
        if (astacktraceelement.length <= 0) {
            return 0;
        } else {
            int len = astacktraceelement.length - 3 - size;
            if (len <= 0) len = astacktraceelement.length;
            this.stackTrace = new StackTraceElement[len];
            System.arraycopy(astacktraceelement, astacktraceelement.length - len, this.stackTrace, 0, this.stackTrace.length);
            return this.stackTrace.length;
        }
    }

    /**
     * Do the deepest two elements of our saved stack trace match the given elements, in order from the deepest?
     */
    public boolean validateStackTrace(StackTraceElement s1, StackTraceElement s2) {
        if (this.stackTrace.length != 0 && s1 != null) {
            StackTraceElement stacktraceelement = this.stackTrace[0];
            if (stacktraceelement.isNativeMethod() == s1.isNativeMethod()
                && stacktraceelement.getClassName().equals(s1.getClassName())
                && stacktraceelement.getFileName().equals(s1.getFileName())
                && stacktraceelement.getMethodName().equals(s1.getMethodName())) {
                if (s2 != null != this.stackTrace.length > 1) {
                    return false;
                } else if (s2 != null && !this.stackTrace[1].equals(s2)) {
                    return false;
                } else {
                    this.stackTrace[0] = s1;
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Removes the given number entries from the bottom of the stack trace.
     */
    public void trimStacktrace(int amount) {
        StackTraceElement[] astacktraceelement = new StackTraceElement[this.stackTrace.length - amount];
        System.arraycopy(this.stackTrace, 0, astacktraceelement, 0, astacktraceelement.length);
        this.stackTrace = astacktraceelement;
    }

    public void getDetails(StringBuilder builder) {
        builder.append("-- ").append(this.title).append(" --\n");
        builder.append("Details:");

        for (CrashReportCategory.Entry crashreportcategory$entry : this.entries) {
            builder.append("\n\t");
            builder.append(crashreportcategory$entry.getKey());
            builder.append(": ");
            builder.append(crashreportcategory$entry.getValue());
        }

        if (this.stackTrace != null && this.stackTrace.length > 0) {
            builder.append("\nStacktrace:");
            builder.append(net.neoforged.neoforge.logging.CrashReportExtender.generateEnhancedStackTrace(this.stackTrace));
        }
    }

    public StackTraceElement[] getStacktrace() {
        return this.stackTrace;
    }

    /** @deprecated Neo: Use {@link #setStackTrace(StackTraceElement[])} instead. */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public void applyStackTrace(Throwable t) {
        setStackTrace(t.getStackTrace());
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        this.stackTrace = stackTrace;
    }

    public static void populateBlockDetails(CrashReportCategory category, LevelHeightAccessor levelHeightAccessor, BlockPos pos, @Nullable BlockState state) {
        if (state != null) {
            category.setDetail("Block", state::toString);
        }

        category.setDetail("Block location", () -> formatLocation(levelHeightAccessor, pos));
    }

    static class Entry {
        private final String key;
        private final String value;

        public Entry(String key, @Nullable Object value) {
            this.key = key;
            if (value == null) {
                this.value = "~~NULL~~";
            } else if (value instanceof Throwable throwable) {
                this.value = "~~ERROR~~ " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            } else {
                this.value = value.toString();
            }
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }
}
