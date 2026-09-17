package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    public static final String EXPIRES_NEVER = "forever";
    protected final Date created;
    protected final String source;
    @Nullable
    protected final Date expires;
    protected final String reason;

    public BanListEntry(@Nullable T user, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(user);
        this.created = created == null ? new Date() : created;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expires;
        this.reason = reason == null ? "Banned by an operator." : reason;
    }

    protected BanListEntry(@Nullable T user, JsonObject entryData) {
        super(user);

        Date date;
        try {
            date = entryData.has("created") ? DATE_FORMAT.parse(entryData.get("created").getAsString()) : new Date();
        } catch (ParseException parseexception1) {
            date = new Date();
        }

        this.created = date;
        this.source = entryData.has("source") ? entryData.get("source").getAsString() : "(Unknown)";

        Date date1;
        try {
            date1 = entryData.has("expires") ? DATE_FORMAT.parse(entryData.get("expires").getAsString()) : null;
        } catch (ParseException parseexception) {
            date1 = null;
        }

        this.expires = date1;
        this.reason = entryData.has("reason") ? entryData.get("reason").getAsString() : "Banned by an operator.";
    }

    public Date getCreated() {
        return this.created;
    }

    public String getSource() {
        return this.source;
    }

    @Nullable
    public Date getExpires() {
        return this.expires;
    }

    public String getReason() {
        return this.reason;
    }

    public abstract Component getDisplayName();

    @Override
    boolean hasExpired() {
        return this.expires == null ? false : this.expires.before(new Date());
    }

    @Override
    protected void serialize(JsonObject data) {
        data.addProperty("created", DATE_FORMAT.format(this.created));
        data.addProperty("source", this.source);
        data.addProperty("expires", this.expires == null ? "forever" : DATE_FORMAT.format(this.expires));
        data.addProperty("reason", this.reason);
    }
}
