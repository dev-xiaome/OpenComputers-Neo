package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class UserWhiteList extends StoredUserList<GameProfile, UserWhiteListEntry> {
    public UserWhiteList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject entryData) {
        return new UserWhiteListEntry(entryData);
    }

    /**
     * Returns {@code true} if the profile is in the whitelist.
     */
    public boolean isWhiteListed(GameProfile profile) {
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
