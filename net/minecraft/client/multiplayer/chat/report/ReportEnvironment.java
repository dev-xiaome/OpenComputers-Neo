package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.ClientInfo;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.RealmInfo;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.ThirdPartyServerInfo;
import com.mojang.realmsclient.dto.RealmsServer;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ReportEnvironment(String clientVersion, @Nullable ReportEnvironment.Server server) {
    public static ReportEnvironment local() {
        return create(null);
    }

    public static ReportEnvironment thirdParty(String ip) {
        return create(new ReportEnvironment.Server.ThirdParty(ip));
    }

    public static ReportEnvironment realm(RealmsServer realmsServer) {
        return create(new ReportEnvironment.Server.Realm(realmsServer));
    }

    public static ReportEnvironment create(@Nullable ReportEnvironment.Server server) {
        return new ReportEnvironment(getClientVersion(), server);
    }

    public ClientInfo clientInfo() {
        return new ClientInfo(this.clientVersion, Locale.getDefault().toLanguageTag());
    }

    @Nullable
    public ThirdPartyServerInfo thirdPartyServerInfo() {
        return this.server instanceof ReportEnvironment.Server.ThirdParty reportenvironment$server$thirdparty
            ? new ThirdPartyServerInfo(reportenvironment$server$thirdparty.ip)
            : null;
    }

    @Nullable
    public RealmInfo realmInfo() {
        return this.server instanceof ReportEnvironment.Server.Realm reportenvironment$server$realm
            ? new RealmInfo(String.valueOf(reportenvironment$server$realm.realmId()), reportenvironment$server$realm.slotId())
            : null;
    }

    private static String getClientVersion() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("1.21.1");
        if (Minecraft.checkModStatus().shouldReportAsModified()) {
            stringbuilder.append(" (modded)");
        }

        return stringbuilder.toString();
    }

    @OnlyIn(Dist.CLIENT)
    public interface Server {
        @OnlyIn(Dist.CLIENT)
        public static record Realm(long realmId, int slotId) implements ReportEnvironment.Server {
            public Realm(RealmsServer p_239068_) {
                this(p_239068_.id, p_239068_.activeSlot);
            }
        }

        @OnlyIn(Dist.CLIENT)
        public static record ThirdParty(String ip) implements ReportEnvironment.Server {
        }
    }
}
