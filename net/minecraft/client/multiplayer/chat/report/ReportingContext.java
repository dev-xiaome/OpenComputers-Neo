package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.minecraft.UserApiService;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ReportingContext {
    private static final int LOG_CAPACITY = 1024;
    private final AbuseReportSender sender;
    private final ReportEnvironment environment;
    private final ChatLog chatLog;
    @Nullable
    private Report draftReport;

    public ReportingContext(AbuseReportSender sender, ReportEnvironment enviroment, ChatLog chatLog) {
        this.sender = sender;
        this.environment = enviroment;
        this.chatLog = chatLog;
    }

    public static ReportingContext create(ReportEnvironment environment, UserApiService userApiService) {
        ChatLog chatlog = new ChatLog(1024);
        AbuseReportSender abusereportsender = AbuseReportSender.create(environment, userApiService);
        return new ReportingContext(abusereportsender, environment, chatlog);
    }

    public void draftReportHandled(Minecraft minecraft, Screen screen, Runnable quitter, boolean quitToTitle) {
        if (this.draftReport != null) {
            Report report = this.draftReport.copy();
            minecraft.setScreen(
                new ConfirmScreen(
                    p_299807_ -> {
                        this.setReportDraft(null);
                        if (p_299807_) {
                            minecraft.setScreen(report.createScreen(screen, this));
                        } else {
                            quitter.run();
                        }
                    },
                    Component.translatable(quitToTitle ? "gui.abuseReport.draft.quittotitle.title" : "gui.abuseReport.draft.title"),
                    Component.translatable(quitToTitle ? "gui.abuseReport.draft.quittotitle.content" : "gui.abuseReport.draft.content"),
                    Component.translatable("gui.abuseReport.draft.edit"),
                    Component.translatable("gui.abuseReport.draft.discard")
                )
            );
        } else {
            quitter.run();
        }
    }

    public AbuseReportSender sender() {
        return this.sender;
    }

    public ChatLog chatLog() {
        return this.chatLog;
    }

    public boolean matches(ReportEnvironment environment) {
        return Objects.equals(this.environment, environment);
    }

    public void setReportDraft(@Nullable Report draftReport) {
        this.draftReport = draftReport;
    }

    public boolean hasDraftReport() {
        return this.draftReport != null;
    }

    public boolean hasDraftReportFor(UUID uuid) {
        return this.hasDraftReport() && this.draftReport.isReportedPlayer(uuid);
    }
}
