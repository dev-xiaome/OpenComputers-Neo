package net.minecraft.client;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.packs.PackResources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ResourceLoadStateTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private ResourceLoadStateTracker.ReloadState reloadState;
    private int reloadCount;

    public void startReload(ResourceLoadStateTracker.ReloadReason reloadReason, List<PackResources> packs) {
        this.reloadCount++;
        if (this.reloadState != null && !this.reloadState.finished) {
            LOGGER.warn("Reload already ongoing, replacing");
        }

        this.reloadState = new ResourceLoadStateTracker.ReloadState(
            reloadReason, packs.stream().map(PackResources::packId).collect(ImmutableList.toImmutableList())
        );
    }

    public void startRecovery(Throwable error) {
        if (this.reloadState == null) {
            LOGGER.warn("Trying to signal reload recovery, but nothing was started");
            this.reloadState = new ResourceLoadStateTracker.ReloadState(ResourceLoadStateTracker.ReloadReason.UNKNOWN, ImmutableList.of());
        }

        this.reloadState.recoveryReloadInfo = new ResourceLoadStateTracker.RecoveryInfo(error);
    }

    public void finishReload() {
        if (this.reloadState == null) {
            LOGGER.warn("Trying to finish reload, but nothing was started");
        } else {
            this.reloadState.finished = true;
        }
    }

    public void fillCrashReport(CrashReport report) {
        CrashReportCategory crashreportcategory = report.addCategory("Last reload");
        crashreportcategory.setDetail("Reload number", this.reloadCount);
        if (this.reloadState != null) {
            this.reloadState.fillCrashInfo(crashreportcategory);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class RecoveryInfo {
        private final Throwable error;

        RecoveryInfo(Throwable error) {
            this.error = error;
        }

        public void fillCrashInfo(CrashReportCategory crash) {
            crash.setDetail("Recovery", "Yes");
            crash.setDetail("Recovery reason", () -> {
                StringWriter stringwriter = new StringWriter();
                this.error.printStackTrace(new PrintWriter(stringwriter));
                return stringwriter.toString();
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum ReloadReason {
        INITIAL("initial"),
        MANUAL("manual"),
        UNKNOWN("unknown");

        final String name;

        private ReloadReason(String name) {
            this.name = name;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class ReloadState {
        private final ResourceLoadStateTracker.ReloadReason reloadReason;
        private final List<String> packs;
        @Nullable
        ResourceLoadStateTracker.RecoveryInfo recoveryReloadInfo;
        boolean finished;

        ReloadState(ResourceLoadStateTracker.ReloadReason reloadReason, List<String> packs) {
            this.reloadReason = reloadReason;
            this.packs = packs;
        }

        public void fillCrashInfo(CrashReportCategory crash) {
            crash.setDetail("Reload reason", this.reloadReason.name);
            crash.setDetail("Finished", this.finished ? "Yes" : "No");
            crash.setDetail("Packs", () -> String.join(", ", this.packs));
            if (this.recoveryReloadInfo != null) {
                this.recoveryReloadInfo.fillCrashInfo(crash);
            }
        }
    }
}
