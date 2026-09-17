package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.Date;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public class UserBanListEntry extends BanListEntry<GameProfile> {
    public UserBanListEntry(@Nullable GameProfile user) {
        this(user, null, null, null, null);
    }

    public UserBanListEntry(
        @Nullable GameProfile profile, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason
    ) {
        super(profile, created, source, expires, reason);
    }

    public UserBanListEntry(JsonObject entryData) {
        super(createGameProfile(entryData), entryData);
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            data.addProperty("uuid", this.getUser().getId().toString());
            data.addProperty("name", this.getUser().getName());
            super.serialize(data);
        }
    }

    @Override
    public Component getDisplayName() {
        GameProfile gameprofile = this.getUser();
        return gameprofile != null ? Component.literal(gameprofile.getName()) : Component.translatable("commands.banlist.entry.unknown");
    }

    /**
     * Convert a {@linkplain com.google.gson.JsonObject JsonObject} into a {@linkplain com.mojang.authlib.GameProfile}. The json object must have {@code uuid} and {@code name} attributes or {@code null} will be returned.
     */
    @Nullable
    private static GameProfile createGameProfile(JsonObject json) {
        if (json.has("uuid") && json.has("name")) {
            String s = json.get("uuid").getAsString();

            UUID uuid;
            try {
                uuid = UUID.fromString(s);
            } catch (Throwable throwable) {
                return null;
            }

            return new GameProfile(uuid, json.get("name").getAsString());
        } else {
            return null;
        }
    }
}
