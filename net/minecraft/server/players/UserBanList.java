package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class UserBanList extends StoredUserList<GameProfile, UserBanListEntry> {
    public UserBanList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject entryData) {
        return new UserBanListEntry(entryData);
    }

    public boolean isBanned(GameProfile profile) {
        return this.contains(profile);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(GameProfile::getName).toArray(String[]::new);
    }

    /**
     * Gets the key value for the given object
     */
    protected String getKeyForUser(GameProfile obj) {
        return obj.getId().toString();
    }
}
