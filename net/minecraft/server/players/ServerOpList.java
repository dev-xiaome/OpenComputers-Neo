package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class ServerOpList extends StoredUserList<GameProfile, ServerOpListEntry> {
    public ServerOpList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject entryData) {
        return new ServerOpListEntry(entryData);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(GameProfile::getName).toArray(String[]::new);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        ServerOpListEntry serveroplistentry = this.get(profile);
        return serveroplistentry != null ? serveroplistentry.getBypassesPlayerLimit() : false;
    }

    /**
     * Gets the key value for the given object
     */
    protected String getKeyForUser(GameProfile obj) {
        return obj.getId().toString();
    }
}
