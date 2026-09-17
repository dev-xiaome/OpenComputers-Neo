package net.minecraft.client.multiplayer.chat.report;

import com.google.common.collect.Lists;
import com.mojang.authlib.minecraft.report.AbuseReport;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.authlib.minecraft.report.ReportChatMessage;
import com.mojang.authlib.minecraft.report.ReportEvidence;
import com.mojang.authlib.minecraft.report.ReportedEntity;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.reporting.ChatReportScreen;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageLink;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class ChatReport extends Report {
    final IntSet reportedMessages = new IntOpenHashSet();

    ChatReport(UUID reportId, Instant createdAt, UUID reportedProfileId) {
        super(reportId, createdAt, reportedProfileId);
    }

    public void toggleReported(int id, AbuseReportLimits limits) {
        if (this.reportedMessages.contains(id)) {
            this.reportedMessages.remove(id);
        } else if (this.reportedMessages.size() < limits.maxReportedMessageCount()) {
            this.reportedMessages.add(id);
        }
    }

    public ChatReport copy() {
        ChatReport chatreport = new ChatReport(this.reportId, this.createdAt, this.reportedProfileId);
        chatreport.reportedMessages.addAll(this.reportedMessages);
        chatreport.comments = this.comments;
        chatreport.reason = this.reason;
        chatreport.attested = this.attested;
        return chatreport;
    }

    @Override
    public Screen createScreen(Screen lastScreen, ReportingContext reportingContext) {
        return new ChatReportScreen(lastScreen, reportingContext, this);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder extends Report.Builder<ChatReport> {
        public Builder(ChatReport report, AbuseReportLimits limits) {
            super(report, limits);
        }

        public Builder(UUID reportedProfileId, AbuseReportLimits limits) {
            super(new ChatReport(UUID.randomUUID(), Instant.now(), reportedProfileId), limits);
        }

        public IntSet reportedMessages() {
            return this.report.reportedMessages;
        }

        public void toggleReported(int id) {
            this.report.toggleReported(id, this.limits);
        }

        public boolean isReported(int id) {
            return this.report.reportedMessages.contains(id);
        }

        @Override
        public boolean hasContent() {
            return StringUtils.isNotEmpty(this.comments()) || !this.reportedMessages().isEmpty() || this.reason() != null;
        }

        @Nullable
        @Override
        public Report.CannotBuildReason checkBuildable() {
            if (this.report.reportedMessages.isEmpty()) {
                return Report.CannotBuildReason.NO_REPORTED_MESSAGES;
            } else if (this.report.reportedMessages.size() > this.limits.maxReportedMessageCount()) {
                return Report.CannotBuildReason.TOO_MANY_MESSAGES;
            } else if (this.report.reason == null) {
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
                ReportEvidence reportevidence = this.buildEvidence(reportingContext);
                ReportedEntity reportedentity = new ReportedEntity(this.report.reportedProfileId);
                AbuseReport abusereport = AbuseReport.chat(this.report.comments, s, reportevidence, reportedentity, this.report.createdAt);
                return Either.left(new Report.Result(this.report.reportId, ReportType.CHAT, abusereport));
            }
        }

        private ReportEvidence buildEvidence(ReportingContext reportingContext) {
            List<ReportChatMessage> list = new ArrayList<>();
            ChatReportContextBuilder chatreportcontextbuilder = new ChatReportContextBuilder(this.limits.leadingContextMessageCount());
            chatreportcontextbuilder.collectAllContext(
                reportingContext.chatLog(),
                this.report.reportedMessages,
                (p_299903_, p_300034_) -> list.add(this.buildReportedChatMessage(p_300034_, this.isReported(p_299903_)))
            );
            return new ReportEvidence(Lists.reverse(list));
        }

        private ReportChatMessage buildReportedChatMessage(LoggedChatMessage.Player chatMessage, boolean messageReported) {
            SignedMessageLink signedmessagelink = chatMessage.message().link();
            SignedMessageBody signedmessagebody = chatMessage.message().signedBody();
            List<ByteBuffer> list = signedmessagebody.lastSeen().entries().stream().map(MessageSignature::asByteBuffer).toList();
            ByteBuffer bytebuffer = Optionull.map(chatMessage.message().signature(), MessageSignature::asByteBuffer);
            return new ReportChatMessage(
                signedmessagelink.index(),
                signedmessagelink.sender(),
                signedmessagelink.sessionId(),
                signedmessagebody.timeStamp(),
                signedmessagebody.salt(),
                list,
                signedmessagebody.content(),
                bytebuffer,
                messageReported
            );
        }

        public ChatReport.Builder copy() {
            return new ChatReport.Builder(this.report.copy(), this.limits);
        }
    }
}
