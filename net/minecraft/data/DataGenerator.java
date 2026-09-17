package net.minecraft.data;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.WorldVersion;
import net.minecraft.server.Bootstrap;
import org.slf4j.Logger;

public class DataGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path rootOutputFolder;
    private final PackOutput vanillaPackOutput;
    final Set<String> allProviderIds = new HashSet<>();
    final Map<String, DataProvider> providersToRun = new LinkedHashMap<>();
    private final WorldVersion version;
    private final boolean alwaysGenerate;
    private final Map<String, DataProvider> providersView = java.util.Collections.unmodifiableMap(this.providersToRun);

    public DataGenerator(Path rootOutputFolder, WorldVersion version, boolean alwaysGenerate) {
        this.rootOutputFolder = rootOutputFolder;
        this.vanillaPackOutput = new PackOutput(this.rootOutputFolder);
        this.version = version;
        this.alwaysGenerate = alwaysGenerate;
    }

    public void run() throws IOException {
        HashCache hashcache = new HashCache(this.rootOutputFolder, this.allProviderIds, this.version);
        Stopwatch stopwatch = Stopwatch.createStarted();
        Stopwatch stopwatch1 = Stopwatch.createUnstarted();
        this.providersToRun.forEach((p_254418_, p_253750_) -> {
            if (!this.alwaysGenerate && !hashcache.shouldRunInThisVersion(p_254418_)) {
                LOGGER.debug("Generator {} already run for version {}", p_254418_, this.version.getName());
            } else {
                LOGGER.info("Starting provider: {}", p_254418_);
                net.neoforged.fml.loading.progress.StartupNotificationManager.addModMessage("Generating: " + p_254418_);
                stopwatch1.start();
                hashcache.applyUpdate(hashcache.generateUpdate(p_254418_, p_253750_::run).join());
                stopwatch1.stop();
                LOGGER.info("{} finished after {} ms", p_254418_, stopwatch1.elapsed(TimeUnit.MILLISECONDS));
                stopwatch1.reset();
            }
        });
        LOGGER.info("All providers took: {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        hashcache.purgeStaleAndWrite();
    }

    public DataGenerator.PackGenerator getVanillaPack(boolean toRun) {
        return new DataGenerator.PackGenerator(toRun, "vanilla", this.vanillaPackOutput);
    }

    public DataGenerator.PackGenerator getBuiltinDatapack(boolean toRun, String providerPrefix) {
        Path path = this.vanillaPackOutput.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("minecraft").resolve("datapacks").resolve(providerPrefix);
        return new DataGenerator.PackGenerator(toRun, providerPrefix, new PackOutput(path));
    }

    public PackGenerator getBuiltinDatapack(boolean toRun, String providerPrefix, String path) {
        var packPath = vanillaPackOutput.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(providerPrefix).resolve("datapacks").resolve(path);
        return new PackGenerator(toRun, providerPrefix + '_' + path, new PackOutput(packPath));
    }

    public Map<String, DataProvider> getProvidersView() {
         return this.providersView;
    }

    public PackOutput getPackOutput() {
        return this.vanillaPackOutput;
    }

    public PackOutput getPackOutput(String path) {
        return new PackOutput(rootOutputFolder.resolve(path));
    }

    public PackGenerator getPackGenerator(boolean run, String providerPrefix, String path) {
        return new PackGenerator(run, providerPrefix, getPackOutput(path));
    }

    public <T extends DataProvider> T addProvider(boolean run, DataProvider.Factory<T> factory) {
        return addProvider(run, factory.create(this.vanillaPackOutput));
    }

    public <T extends DataProvider> T addProvider(boolean run, T provider) {
        String id = provider.getName();

        if (!DataGenerator.this.allProviderIds.add(id))
            throw new IllegalStateException("Duplicate provider: " + id);

        if (run)
            DataGenerator.this.providersToRun.put(id, provider);

        return provider;
    }

    public void merge(DataGenerator other) {
        other.providersToRun.forEach((id, provider) -> {
            if(!allProviderIds.add(id))
                throw new IllegalStateException("Duplicate provider: " + id);

            providersToRun.put(id, provider);
        });

        other.providersToRun.clear();
        other.allProviderIds.clear();
    }

    static {
        Bootstrap.bootStrap();
    }

    public class PackGenerator {
        private final boolean toRun;
        private final String providerPrefix;
        private final PackOutput output;

        PackGenerator(boolean toRun, String providerPrefix, PackOutput output) {
            this.toRun = toRun;
            this.providerPrefix = providerPrefix;
            this.output = output;
        }

        public <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory) {
            T t = factory.create(this.output);
            String s = this.providerPrefix + "/" + t.getName();
            if (!DataGenerator.this.allProviderIds.add(s)) {
                throw new IllegalStateException("Duplicate provider: " + s);
            } else {
                if (this.toRun) {
                    DataGenerator.this.providersToRun.put(s, t);
                }

                return t;
            }
        }
    }
}
