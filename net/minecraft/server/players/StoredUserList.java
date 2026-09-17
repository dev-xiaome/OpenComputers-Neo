package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newHashMap();

    public StoredUserList(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    /**
     * Adds an entry to the list
     */
    public void add(V entry) {
        this.map.put(this.getKeyForUser(entry.getUser()), entry);

        try {
            this.save();
        } catch (IOException ioexception) {
            LOGGER.warn("Could not save the list after adding a user.", (Throwable)ioexception);
        }
    }

    @Nullable
    public V get(K obj) {
        this.removeExpired();
        return this.map.get(this.getKeyForUser(obj));
    }

    public void remove(K user) {
        this.map.remove(this.getKeyForUser(user));

        try {
            this.save();
        } catch (IOException ioexception) {
            LOGGER.warn("Could not save the list after removing a user.", (Throwable)ioexception);
        }
    }

    public void remove(StoredUserEntry<K> entry) {
        this.remove(entry.getUser());
    }

    public String[] getUserList() {
        return this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        return this.map.size() < 1;
    }

    /**
     * Gets the key value for the given object
     */
    protected String getKeyForUser(K obj) {
        return obj.toString();
    }

    protected boolean contains(K entry) {
        return this.map.containsKey(this.getKeyForUser(entry));
    }

    private void removeExpired() {
        List<K> list = Lists.newArrayList();

        for (V v : this.map.values()) {
            if (v.hasExpired()) {
                list.add(v.getUser());
            }
        }

        for (K k : list) {
            this.map.remove(this.getKeyForUser(k));
        }
    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject entryData);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        JsonArray jsonarray = new JsonArray();
        this.map.values().stream().map(p_11392_ -> Util.make(new JsonObject(), p_11392_::serialize)).forEach(jsonarray::add);

        try (BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            GSON.toJson(jsonarray, GSON.newJsonWriter(bufferedwriter));
        }
    }

    public void load() throws IOException {
        if (this.file.exists()) {
            try (BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                this.map.clear();
                JsonArray jsonarray = GSON.fromJson(bufferedreader, JsonArray.class);
                if (jsonarray == null) {
                    return;
                }

                for (JsonElement jsonelement : jsonarray) {
                    JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "entry");
                    StoredUserEntry<K> storeduserentry = this.createEntry(jsonobject);
                    if (storeduserentry.getUser() != null) {
                        this.map.put(this.getKeyForUser(storeduserentry.getUser()), (V)storeduserentry);
                    }
                }
            }
        }
    }
}
