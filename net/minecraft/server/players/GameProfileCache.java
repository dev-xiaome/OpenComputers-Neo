package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

public class GameProfileCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GAMEPROFILES_MRU_LIMIT = 1000;
    private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
    private static boolean usesAuthentication;
    /**
     * A map between player usernames and
     */
    private final Map<String, GameProfileCache.GameProfileInfo> profilesByName = Maps.newConcurrentMap();
    /**
     * A map between and
     */
    private final Map<UUID, GameProfileCache.GameProfileInfo> profilesByUUID = Maps.newConcurrentMap();
    private final Map<String, CompletableFuture<Optional<GameProfile>>> requests = Maps.newConcurrentMap();
    private final GameProfileRepository profileRepository;
    private final Gson gson = new GsonBuilder().create();
    private final File file;
    private final AtomicLong operationCount = new AtomicLong();
    @Nullable
    private Executor executor;

    public GameProfileCache(GameProfileRepository profileRepository, File file) {
        this.profileRepository = profileRepository;
        this.file = file;
        Lists.reverse(this.load()).forEach(this::safeAdd);
    }

    private void safeAdd(GameProfileCache.GameProfileInfo profile) {
        GameProfile gameprofile = profile.getProfile();
        profile.setLastAccess(this.getNextOperation());
        this.profilesByName.put(gameprofile.getName().toLowerCase(Locale.ROOT), profile);
        this.profilesByUUID.put(gameprofile.getId(), profile);
    }

    private static Optional<GameProfile> lookupGameProfile(GameProfileRepository profileRepo, String name) {
        if (!StringUtil.isValidPlayerName(name)) {
            return createUnknownProfile(name);
        } else {
            final AtomicReference<GameProfile> atomicreference = new AtomicReference<>();
            ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    atomicreference.set(profile);
                }

                @Override
                public void onProfileLookupFailed(String profileName, Exception exception) {
                    atomicreference.set(null);
                }
            };
            profileRepo.findProfilesByNames(new String[]{name}, profilelookupcallback);
            GameProfile gameprofile = atomicreference.get();
            return gameprofile != null ? Optional.of(gameprofile) : createUnknownProfile(name);
        }
    }

    private static Optional<GameProfile> createUnknownProfile(String profileName) {
        return usesAuthentication() ? Optional.empty() : Optional.of(UUIDUtil.createOfflineProfile(profileName));
    }

    public static void setUsesAuthentication(boolean onlineMode) {
        usesAuthentication = onlineMode;
    }

    private static boolean usesAuthentication() {
        return usesAuthentication;
    }

    /**
     * Add an entry to this cache
     */
    public void add(GameProfile gameProfile) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(2, 1);
        Date date = calendar.getTime();
        GameProfileCache.GameProfileInfo gameprofilecache$gameprofileinfo = new GameProfileCache.GameProfileInfo(gameProfile, date);
        this.safeAdd(gameprofilecache$gameprofileinfo);
        this.save();
    }

    private long getNextOperation() {
        return this.operationCount.incrementAndGet();
    }

    /**
     * Get a player's GameProfile given their username. Mojang's servers will be contacted if the entry is not cached locally.
     */
    public Optional<GameProfile> get(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        GameProfileCache.GameProfileInfo gameprofilecache$gameprofileinfo = this.profilesByName.get(s);
        boolean flag = false;
        if (gameprofilecache$gameprofileinfo != null && new Date().getTime() >= gameprofilecache$gameprofileinfo.expirationDate.getTime()) {
            this.profilesByUUID.remove(gameprofilecache$gameprofileinfo.getProfile().getId());
            this.profilesByName.remove(gameprofilecache$gameprofileinfo.getProfile().getName().toLowerCase(Locale.ROOT));
            flag = true;
            gameprofilecache$gameprofileinfo = null;
        }

        Optional<GameProfile> optional;
        if (gameprofilecache$gameprofileinfo != null) {
            gameprofilecache$gameprofileinfo.setLastAccess(this.getNextOperation());
            optional = Optional.of(gameprofilecache$gameprofileinfo.getProfile());
        } else {
            optional = lookupGameProfile(this.profileRepository, s);
            if (optional.isPresent()) {
                this.add(optional.get());
                flag = false;
            }
        }

        if (flag) {
            this.save();
        }

        return optional;
    }

    public CompletableFuture<Optional<GameProfile>> getAsync(String name) {
        if (this.executor == null) {
            throw new IllegalStateException("No executor");
        } else {
            CompletableFuture<Optional<GameProfile>> completablefuture = this.requests.get(name);
            if (completablefuture != null) {
                return completablefuture;
            } else {
                CompletableFuture<Optional<GameProfile>> completablefuture1 = CompletableFuture.<Optional<GameProfile>>supplyAsync(
                        () -> this.get(name), Util.backgroundExecutor()
                    )
                    .whenCompleteAsync((p_143965_, p_143966_) -> this.requests.remove(name), this.executor);
                this.requests.put(name, completablefuture1);
                return completablefuture1;
            }
        }
    }

    /**
     * @param uuid Get a player's {@link GameProfile} given their UUID
     */
    public Optional<GameProfile> get(UUID uuid) {
        GameProfileCache.GameProfileInfo gameprofilecache$gameprofileinfo = this.profilesByUUID.get(uuid);
        if (gameprofilecache$gameprofileinfo == null) {
            return Optional.empty();
        } else {
            gameprofilecache$gameprofileinfo.setLastAccess(this.getNextOperation());
            return Optional.of(gameprofilecache$gameprofileinfo.getProfile());
        }
    }

    public void setExecutor(Executor exectutor) {
        this.executor = exectutor;
    }

    public void clearExecutor() {
        this.executor = null;
    }

    private static DateFormat createDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    }

    public List<GameProfileCache.GameProfileInfo> load() {
        List<GameProfileCache.GameProfileInfo> list = Lists.newArrayList();

        try {
            Object object;
            try (Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                JsonArray jsonarray = this.gson.fromJson(reader, JsonArray.class);
                if (jsonarray != null) {
                    DateFormat dateformat = createDateFormat();
                    jsonarray.forEach(p_143973_ -> readGameProfile(p_143973_, dateformat).ifPresent(list::add));
                    return list;
                }

                object = list;
            }

            return (List<GameProfileCache.GameProfileInfo>)object;
        } catch (FileNotFoundException filenotfoundexception) {
        } catch (JsonParseException | IOException ioexception) {
            LOGGER.warn("Failed to load profile cache {}", this.file, ioexception);
        }

        return list;
    }

    public void save() {
        JsonArray jsonarray = new JsonArray();
        DateFormat dateformat = createDateFormat();
        this.getTopMRUProfiles(1000).forEach(p_143962_ -> jsonarray.add(writeGameProfile(p_143962_, dateformat)));
        String s = this.gson.toJson((JsonElement)jsonarray);

        try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            writer.write(s);
        } catch (IOException ioexception) {
        }
    }

    private Stream<GameProfileCache.GameProfileInfo> getTopMRUProfiles(int limit) {
        return ImmutableList.copyOf(this.profilesByUUID.values())
            .stream()
            .sorted(Comparator.comparing(GameProfileCache.GameProfileInfo::getLastAccess).reversed())
            .limit((long)limit);
    }

    private static JsonElement writeGameProfile(GameProfileCache.GameProfileInfo profileInfo, DateFormat dateFormat) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("name", profileInfo.getProfile().getName());
        jsonobject.addProperty("uuid", profileInfo.getProfile().getId().toString());
        jsonobject.addProperty("expiresOn", dateFormat.format(profileInfo.getExpirationDate()));
        return jsonobject;
    }

    private static Optional<GameProfileCache.GameProfileInfo> readGameProfile(JsonElement json, DateFormat dateFormat) {
        if (json.isJsonObject()) {
            JsonObject jsonobject = json.getAsJsonObject();
            JsonElement jsonelement = jsonobject.get("name");
            JsonElement jsonelement1 = jsonobject.get("uuid");
            JsonElement jsonelement2 = jsonobject.get("expiresOn");
            if (jsonelement != null && jsonelement1 != null) {
                String s = jsonelement1.getAsString();
                String s1 = jsonelement.getAsString();
                Date date = null;
                if (jsonelement2 != null) {
                    try {
                        date = dateFormat.parse(jsonelement2.getAsString());
                    } catch (ParseException parseexception) {
                    }
                }

                if (s1 != null && s != null && date != null) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(s);
                    } catch (Throwable throwable) {
                        return Optional.empty();
                    }

                    return Optional.of(new GameProfileCache.GameProfileInfo(new GameProfile(uuid, s1), date));
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    static class GameProfileInfo {
        /**
         * The player's GameProfile
         */
        private final GameProfile profile;
        /**
         * The date that this entry will expire
         */
        final Date expirationDate;
        private volatile long lastAccess;

        GameProfileInfo(GameProfile profile, Date expirationDate) {
            this.profile = profile;
            this.expirationDate = expirationDate;
        }

        public GameProfile getProfile() {
            return this.profile;
        }

        public Date getExpirationDate() {
            return this.expirationDate;
        }

        public void setLastAccess(long lastAccess) {
            this.lastAccess = lastAccess;
        }

        public long getLastAccess() {
            return this.lastAccess;
        }
    }
}
