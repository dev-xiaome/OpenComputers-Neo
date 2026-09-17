package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class IpBanList extends StoredUserList<String, IpBanListEntry> {
    public IpBanList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<String> createEntry(JsonObject entryData) {
        return new IpBanListEntry(entryData);
    }

    public boolean isBanned(SocketAddress address) {
        String s = this.getIpFromAddress(address);
        return this.contains(s);
    }

    public boolean isBanned(String address) {
        return this.contains(address);
    }

    @Nullable
    public IpBanListEntry get(SocketAddress address) {
        String s = this.getIpFromAddress(address);
        return this.get(s);
    }

    private String getIpFromAddress(SocketAddress address) {
        String s = address.toString();
        if (s.contains("/")) {
            s = s.substring(s.indexOf(47) + 1);
        }

        if (s.contains(":")) {
            s = s.substring(0, s.indexOf(58));
        }

        return s;
    }
}
