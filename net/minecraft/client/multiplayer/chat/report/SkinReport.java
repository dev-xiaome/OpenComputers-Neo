package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.minecraft.report.AbuseReport;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.authlib.minecraft.report.ReportedEntity;
import com.mojang.datafixers.util.Either;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.reporting.SkinReportScreen;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class SkinReport extends Report {
    final Supplier<PlayerSkin> skinGetter;

    SkinReport(UUID reportId, Instant created, UUID reportedProfileId, Supplier<PlayerSkin> skinGetter) {
        super(reportId, created, reportedProfileId);
        this.skinGetter = skinGetter;
    }

    public Supplier<PlayerSkin> getSkinGetter() {
        return this.skinGetter;
    }

    public SkinReport copy() {
        SkinReport skinreport = new SkinReport(this.reportId, this.createdAt, this.reportedProfileId, this.skinGetter);
        skinreport.comments = this.comments;
        skinreport.reason = this.reason;
        skinreport.attested = this.attested;
        return skinreport;
    }

    @Override
    public Screen createScreen(Screen lastScreen, ReportingContext reportingContext) {
        return new SkinReportScreen(lastScreen, reportingContext, this);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder extends Report.Builder<SkinReport> {
        public Builder(SkinReport report, AbuseReportLimits limits) {
            super(report, limits);
        }

        public Builder(UUID reportedPlayerId, Supplier<PlayerSkin> skinGetter, AbuseReportLimits limits) {
            super(new SkinReport(UUID.randomUUID(), Instant.now(), reportedPlayerId, skinGetter), limits);
        }

        @Override
        public boolean hasContent() {
            return StringUtils.isNotEmpty(this.comments()) || this.reason() != null;
        }

        @Nullable
        @Override
        public Report.CannotBuildReason checkBuildable() {
            if (this.report.reason == null) {
                return Report.CannotBuildReason.NO_REASON;
            } else {
                return this.report.comments.length() > this.limits.maxOpinionCommentsLength()
                    ? Report.CannotBuildReason.COMMENT_TOO_LONG
                    : super.checkBuildable();
            }
        }

        @Override
        public Either<Report.Result, Report.CannotBuildReason> build(ReportingContext reportingContext) {
            Report.CannotBuildReason report$cannotbuildreason = this.checkBuildable();
            if (report$cannotbuildreason != null) {
                return Either.right(report$cannotbuildreason);
            } else {
                String s = Objects.requireNonNull(this.report.reason).backendName();
                ReportedEntity reportedentity = new ReportedEntity(this.report.reportedProfileId);
                PlayerSkin playerskin = this.report.skinGetter.get();
                String s1 = playerskin.textureUrl();
                AbuseReport abusereport = AbuseReport.skin(this.report.comments, s, s1, reportedentity, this.report.createdAt);
                return Either.left(new Report.Result(this.report.reportId, ReportType.SKIN, abusereport));
            }
        }
    }
}
