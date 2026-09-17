package net.minecraft.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.WorldVersion;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class HashCache {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String HEADER_MARKER = "// ";
    private final Path rootDir;
    private final Path cacheDir;
    private final String versionId;
    private final Map<String, HashCache.ProviderCache> caches;
    private final Map<String, HashCache.ProviderCache> originalCaches;
    private final Set<String> cachesToWrite = new HashSet<>();
    final Set<Path> cachePaths = new HashSet<>();
    private final int initialCount;
    private int writes;

    private Path getProviderCachePath(String provider) {
        return this.cacheDir.resolve(Hashing.sha1().hashString(provider, StandardCharsets.UTF_8).toString());
    }

    public HashCache(Path rootDir, Collection<String> providers, WorldVersion version) throws IOException {
        this.versionId = version.getName();
        this.rootDir = rootDir;
        this.cacheDir = rootDir.resolve(".cache");
        Files.createDirectories(this.cacheDir);
        Map<String, HashCache.ProviderCache> map = new HashMap<>();
        int i = 0;

        for (String s : providers) {
            Path path = this.getProviderCachePath(s);
            this.cachePaths.add(path);
            HashCache.ProviderCache hashcache$providercache = readCache(rootDir, path);
            map.put(s, hashcache$providercache);
            i += hashcache$providercache.count();
        }

        this.caches = map;
        this.originalCaches = Map.copyOf(this.caches);
        this.initialCount = i;
    }

    private static HashCache.ProviderCache readCache(Path rootDir, Path cachePath) {
        if (Files.isReadable(cachePath)) {
            try {
                return HashCache.ProviderCache.load(rootDir, cachePath);
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse cache {}, discarding", cachePath, exception);
            }
        }

        return new HashCache.ProviderCache("unknown", ImmutableMap.of());
    }

    public boolean shouldRunInThisVersion(String provider) {
        HashCache.ProviderCache hashcache$providercache = this.caches.get(provider);
        return hashcache$providercache == null || !hashcache$providercache.version.equals(this.versionId);
    }

    public CompletableFuture<HashCache.UpdateResult> generateUpdate(String provider, HashCache.UpdateFunction updateFunction) {
        HashCache.ProviderCache hashcache$providercache = this.caches.get(provider);
        if (hashcache$providercache == null) {
            throw new IllegalStateException("Provider not registered: " + provider);
        } else {
            HashCache.CacheUpdater hashcache$cacheupdater = new HashCache.CacheUpdater(provider, this.versionId, hashcache$providercache);
            return updateFunction.update(hashcache$cacheupdater).thenApply(p_253376_ -> hashcache$cacheupdater.close());
        }
    }

    public void applyUpdate(HashCache.UpdateResult updateResult) {
        this.caches.put(updateResult.providerId(), updateResult.cache());
        this.cachesToWrite.add(updateResult.providerId());
        this.writes = this.writes + updateResult.writes();
    }

    public void purgeStaleAndWrite() throws IOException {
        final Set<Path> set = new HashSet<>();
        this.caches.forEach((p_253378_, p_253379_) -> {
            if (this.cachesToWrite.contains(p_253378_)) {
                Path path = this.getProviderCachePath(p_253378_);
                // Forge: Only rewrite the cache file if it changed or is missing
                if (!p_253379_.equals(this.originalCaches.get(p_253378_)) || !Files.exists(path))
                p_253379_.save(this.rootDir, path, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + "\t" + p_253378_);
            }

            set.addAll(p_253379_.data().keySet());
        });
        set.add(this.rootDir.resolve("version.json"));
        final MutableInt mutableint = new MutableInt();
        final MutableInt mutableint1 = new MutableInt();
        Files.walkFileTree(this.rootDir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path p_320355_, BasicFileAttributes p_320633_) {
                if (HashCache.this.cachePaths.contains(p_320355_)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    mutableint.increment();
                    if (set.contains(p_320355_)) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        try {
                            Files.delete(p_320355_);
                        } catch (IOException ioexception) {
                            HashCache.LOGGER.warn("Failed to delete file {}", p_320355_, ioexception);
                        }

                        mutableint1.increment();
                        return FileVisitResult.CONTINUE;
                    }
                }
            }
        });
        LOGGER.info(
            "Caching: total files: {}, old count: {}, new count: {}, removed stale: {}, written: {}",
            mutableint,
            this.initialCount,
            set.size(),
            mutableint1,
            this.writes
        );
    }

    class CacheUpdater implements CachedOutput {
        private final String provider;
        private final HashCache.ProviderCache oldCache;
        private final HashCache.ProviderCacheBuilder newCache;
        private final AtomicInteger writes = new AtomicInteger();
        private volatile boolean closed;

        CacheUpdater(String provider, String version, HashCache.ProviderCache oldCache) {
            this.provider = provider;
            this.oldCache = oldCache;
            this.newCache = new HashCache.ProviderCacheBuilder(version);
        }

        private boolean shouldWrite(Path key, HashCode value) {
            return !Objects.equals(this.oldCache.get(key), value) || !Files.exists(key);
        }

        @Override
        public void writeIfNeeded(Path filePath, byte[] data, HashCode hashCode) throws IOException {
            if (this.closed) {
                throw new IllegalStateException("Cannot write to cache as it has already been closed");
            } else {
                if (this.shouldWrite(filePath, hashCode)) {
                    this.writes.incrementAndGet();
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, data);
                }

                this.newCache.put(filePath, hashCode);
            }
        }

        public HashCache.UpdateResult close() {
            this.closed = true;
            return new HashCache.UpdateResult(this.provider, this.newCache.build(), this.writes.get());
        }
    }

    static record ProviderCache(String version, ImmutableMap<Path, HashCode> data) {
        @Nullable
        public HashCode get(Path path) {
            return this.data.get(path);
        }

        public int count() {
            return this.data.size();
        }

        public static HashCache.ProviderCache load(Path rootDir, Path cachePath) throws IOException {
            HashCache.ProviderCache hashcache$providercache;
            try (BufferedReader bufferedreader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
                String s = bufferedreader.readLine();
                if (!s.startsWith("// ")) {
                    throw new IllegalStateException("Missing cache file header");
                }

                String[] astring = s.substring("// ".length()).split("\t", 2);
                String s1 = astring[0];
                Builder<Path, HashCode> builder = ImmutableMap.builder();
                bufferedreader.lines().forEach(p_253382_ -> {
                    int i = p_253382_.indexOf(32);
                    builder.put(rootDir.resolve(p_253382_.substring(i + 1)), HashCode.fromString(p_253382_.substring(0, i)));
                });
                hashcache$providercache = new HashCache.ProviderCache(s1, builder.build());
            }

            return hashcache$providercache;
        }

        public void save(Path rootDir, Path cachePath, String date) {
            try (BufferedWriter bufferedwriter = Files.newBufferedWriter(cachePath, StandardCharsets.UTF_8)) {
                bufferedwriter.write("// ");
                bufferedwriter.write(this.version);
                bufferedwriter.write(9);
                bufferedwriter.write(date);
                bufferedwriter.newLine();

                // Forge: Standardize order of entries
                for(Map.Entry<Path, HashCode> entry : this.data.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    bufferedwriter.write(entry.getValue().toString());
                    bufferedwriter.write(32);
                    bufferedwriter.write(rootDir.relativize(entry.getKey()).toString().replace("\\", "/")); // Forge: Standardize file paths.
                    bufferedwriter.newLine();
                }
            } catch (IOException ioexception) {
                HashCache.LOGGER.warn("Unable write cachefile {}: {}", cachePath, ioexception);
            }
        }
    }

    static record ProviderCacheBuilder(String version, ConcurrentMap<Path, HashCode> data) {
        ProviderCacheBuilder(String p_254186_) {
            this(p_254186_, new ConcurrentHashMap<>());
        }

        public void put(Path key, HashCode value) {
            this.data.put(key, value);
        }

        public HashCache.ProviderCache build() {
            return new HashCache.ProviderCache(this.version, ImmutableMap.copyOf(this.data));
        }
    }

    @FunctionalInterface
    public interface UpdateFunction {
        CompletableFuture<?> update(CachedOutput output);
    }

    public static record UpdateResult(String providerId, HashCache.ProviderCache cache, int writes) {
    }
}
