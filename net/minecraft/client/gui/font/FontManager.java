package net.minecraft.client.gui.font;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.DependencySorter;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class FontManager implements PreparableReloadListener, AutoCloseable {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String FONTS_PATH = "fonts.json";
    public static final ResourceLocation MISSING_FONT = ResourceLocation.withDefaultNamespace("missing");
    private static final FileToIdConverter FONT_DEFINITIONS = FileToIdConverter.json("font");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final FontSet missingFontSet;
    private final List<GlyphProvider> providersToClose = new ArrayList<>();
    private final Map<ResourceLocation, FontSet> fontSets = new HashMap<>();
    private final TextureManager textureManager;
    @Nullable
    private volatile FontSet lastFontSetCache;

    public FontManager(TextureManager textureManager) {
        this.textureManager = textureManager;
        this.missingFontSet = Util.make(new FontSet(textureManager, MISSING_FONT), p_325488_ -> p_325488_.reload(List.of(createFallbackProvider()), Set.of()));
    }

    private static GlyphProvider.Conditional createFallbackProvider() {
        return new GlyphProvider.Conditional(new AllMissingGlyphProvider(), FontOption.Filter.ALWAYS_PASS);
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        preparationsProfiler.startTick();
        preparationsProfiler.endTick();
        return this.prepare(resourceManager, backgroundExecutor).thenCompose(preparationBarrier::wait).thenAcceptAsync(p_284609_ -> this.apply(p_284609_, reloadProfiler), gameExecutor);
    }

    private CompletableFuture<FontManager.Preparation> prepare(ResourceManager resourceManager, Executor executor) {
        List<CompletableFuture<FontManager.UnresolvedBuilderBundle>> list = new ArrayList<>();

        for (Entry<ResourceLocation, List<Resource>> entry : FONT_DEFINITIONS.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation resourcelocation = FONT_DEFINITIONS.fileToId(entry.getKey());
            list.add(CompletableFuture.supplyAsync(() -> {
                List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> list1 = loadResourceStack(entry.getValue(), resourcelocation);
                FontManager.UnresolvedBuilderBundle fontmanager$unresolvedbuilderbundle = new FontManager.UnresolvedBuilderBundle(resourcelocation);

                for (Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional> pair : list1) {
                    FontManager.BuilderId fontmanager$builderid = pair.getFirst();
                    FontOption.Filter fontoption$filter = pair.getSecond().filter();
                    pair.getSecond().definition().unpack().ifLeft(p_325476_ -> {
                        CompletableFuture<Optional<GlyphProvider>> completablefuture = this.safeLoad(fontmanager$builderid, p_325476_, resourceManager, executor);
                        fontmanager$unresolvedbuilderbundle.add(fontmanager$builderid, fontoption$filter, completablefuture);
                    }).ifRight(p_325470_ -> fontmanager$unresolvedbuilderbundle.add(fontmanager$builderid, fontoption$filter, p_325470_));
                }

                return fontmanager$unresolvedbuilderbundle;
            }, executor));
        }

        return Util.sequence(list)
            .thenCompose(
                p_341556_ -> {
                    List<CompletableFuture<Optional<GlyphProvider>>> list1 = p_341556_.stream()
                        .flatMap(FontManager.UnresolvedBuilderBundle::listBuilders)
                        .collect(Util.toMutableList());
                    GlyphProvider.Conditional glyphprovider$conditional = createFallbackProvider();
                    list1.add(CompletableFuture.completedFuture(Optional.of(glyphprovider$conditional.provider())));
                    return Util.sequence(list1)
                        .thenCompose(
                            p_284618_ -> {
                                Map<ResourceLocation, List<GlyphProvider.Conditional>> map = this.resolveProviders(p_341556_);
                                CompletableFuture<?>[] completablefuture = map.values()
                                    .stream()
                                    .map(
                                        p_284585_ -> CompletableFuture.runAsync(
                                                () -> this.finalizeProviderLoading(p_284585_, glyphprovider$conditional), executor
                                            )
                                    )
                                    .toArray(CompletableFuture[]::new);
                                return CompletableFuture.allOf(completablefuture).thenApply(p_284595_ -> {
                                    List<GlyphProvider> list2 = p_284618_.stream().flatMap(Optional::stream).toList();
                                    return new FontManager.Preparation(map, list2);
                                });
                            }
                        );
                }
            );
    }

    private CompletableFuture<Optional<GlyphProvider>> safeLoad(
        FontManager.BuilderId builderId, GlyphProviderDefinition.Loader loader, ResourceManager resourceManager, Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Optional.of(loader.load(resourceManager));
            } catch (Exception exception) {
                LOGGER.warn("Failed to load builder {}, rejecting", builderId, exception);
                return Optional.empty();
            }
        }, executor);
    }

    private Map<ResourceLocation, List<GlyphProvider.Conditional>> resolveProviders(List<FontManager.UnresolvedBuilderBundle> unresolvedBuilderBundles) {
        Map<ResourceLocation, List<GlyphProvider.Conditional>> map = new HashMap<>();
        DependencySorter<ResourceLocation, FontManager.UnresolvedBuilderBundle> dependencysorter = new DependencySorter<>();
        unresolvedBuilderBundles.forEach(p_284626_ -> dependencysorter.addEntry(p_284626_.fontId, p_284626_));
        dependencysorter.orderByDependencies(
            (p_284620_, p_284621_) -> p_284621_.resolve(map::get).ifPresent(p_284590_ -> map.put(p_284620_, (List<GlyphProvider.Conditional>)p_284590_))
        );
        return map;
    }

    private void finalizeProviderLoading(List<GlyphProvider.Conditional> providers, GlyphProvider.Conditional fallbackProvider) {
        providers.add(0, fallbackProvider);
        IntSet intset = new IntOpenHashSet();

        for (GlyphProvider.Conditional glyphprovider$conditional : providers) {
            intset.addAll(glyphprovider$conditional.provider().getSupportedGlyphs());
        }

        intset.forEach(p_325466_ -> {
            if (p_325466_ != 32) {
                for (GlyphProvider.Conditional glyphprovider$conditional1 : Lists.reverse(providers)) {
                    if (glyphprovider$conditional1.provider().getGlyph(p_325466_) != null) {
                        break;
                    }
                }
            }
        });
    }

    private static Set<FontOption> getFontOptions(Options options) {
        Set<FontOption> set = EnumSet.noneOf(FontOption.class);
        if (options.forceUnicodeFont().get()) {
            set.add(FontOption.UNIFORM);
        }

        if (options.japaneseGlyphVariants().get()) {
            set.add(FontOption.JAPANESE_VARIANTS);
        }

        return set;
    }

    private void apply(FontManager.Preparation preperation, ProfilerFiller profiler) {
        profiler.startTick();
        profiler.push("closing");
        this.lastFontSetCache = null;
        this.fontSets.values().forEach(FontSet::close);
        this.fontSets.clear();
        this.providersToClose.forEach(GlyphProvider::close);
        this.providersToClose.clear();
        Set<FontOption> set = getFontOptions(Minecraft.getInstance().options);
        profiler.popPush("reloading");
        preperation.fontSets().forEach((p_325478_, p_325479_) -> {
            FontSet fontset = new FontSet(this.textureManager, p_325478_);
            fontset.reload(Lists.reverse((List<GlyphProvider.Conditional>)p_325479_), set);
            this.fontSets.put(p_325478_, fontset);
        });
        this.providersToClose.addAll(preperation.allProviders);
        profiler.pop();
        profiler.endTick();
        if (!this.fontSets.containsKey(Minecraft.DEFAULT_FONT)) {
            throw new IllegalStateException("Default font failed to load");
        }
    }

    public void updateOptions(Options options) {
        Set<FontOption> set = getFontOptions(options);

        for (FontSet fontset : this.fontSets.values()) {
            fontset.reload(set);
        }
    }

    private static List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> loadResourceStack(
        List<Resource> resources, ResourceLocation fontId
    ) {
        List<Pair<FontManager.BuilderId, GlyphProviderDefinition.Conditional>> list = new ArrayList<>();

        for (Resource resource : resources) {
            try (Reader reader = resource.openAsReader()) {
                JsonElement jsonelement = GSON.fromJson(reader, JsonElement.class);
                FontManager.FontDefinitionFile fontmanager$fontdefinitionfile = FontManager.FontDefinitionFile.CODEC
                    .parse(JsonOps.INSTANCE, jsonelement)
                    .getOrThrow(JsonParseException::new);
                List<GlyphProviderDefinition.Conditional> list1 = fontmanager$fontdefinitionfile.providers;

                for (int i = list1.size() - 1; i >= 0; i--) {
                    FontManager.BuilderId fontmanager$builderid = new FontManager.BuilderId(fontId, resource.sourcePackId(), i);
                    list.add(Pair.of(fontmanager$builderid, list1.get(i)));
                }
            } catch (Exception exception) {
                LOGGER.warn("Unable to load font '{}' in {} in resourcepack: '{}'", fontId, "fonts.json", resource.sourcePackId(), exception);
            }
        }

        return list;
    }

    public Font createFont() {
        return new Font(this::getFontSetCached, false);
    }

    public Font createFontFilterFishy() {
        return new Font(this::getFontSetCached, true);
    }

    private FontSet getFontSetRaw(ResourceLocation fontSet) {
        return this.fontSets.getOrDefault(fontSet, this.missingFontSet);
    }

    private FontSet getFontSetCached(ResourceLocation fontSet) {
        FontSet fontset = this.lastFontSetCache;
        if (fontset != null && fontSet.equals(fontset.name())) {
            return fontset;
        } else {
            FontSet fontset1 = this.getFontSetRaw(fontSet);
            this.lastFontSetCache = fontset1;
            return fontset1;
        }
    }

    @Override
    public void close() {
        this.fontSets.values().forEach(FontSet::close);
        this.providersToClose.forEach(GlyphProvider::close);
        this.missingFontSet.close();
    }

    @OnlyIn(Dist.CLIENT)
    static record BuilderId(ResourceLocation fontId, String pack, int index) {
        @Override
        public String toString() {
            return "(" + this.fontId + ": builder #" + this.index + " from pack " + this.pack + ")";
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record BuilderResult(FontManager.BuilderId id, FontOption.Filter filter, Either<CompletableFuture<Optional<GlyphProvider>>, ResourceLocation> result) {
        public Optional<List<GlyphProvider.Conditional>> resolve(Function<ResourceLocation, List<GlyphProvider.Conditional>> providerResolver) {
            return this.result
                .map(
                    p_325492_ -> p_325492_.join().map(p_325491_ -> List.of(new GlyphProvider.Conditional(p_325491_, this.filter))),
                    p_325490_ -> {
                        List<GlyphProvider.Conditional> list = providerResolver.apply(p_325490_);
                        if (list == null) {
                            FontManager.LOGGER
                                .warn(
                                    "Can't find font {} referenced by builder {}, either because it's missing, failed to load or is part of loading cycle",
                                    p_325490_,
                                    this.id
                                );
                            return Optional.empty();
                        } else {
                            return Optional.of(list.stream().map(this::mergeFilters).toList());
                        }
                    }
                );
        }

        private GlyphProvider.Conditional mergeFilters(GlyphProvider.Conditional conditional) {
            return new GlyphProvider.Conditional(conditional.provider(), this.filter.merge(conditional.filter()));
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record FontDefinitionFile(List<GlyphProviderDefinition.Conditional> providers) {
        public static final Codec<FontManager.FontDefinitionFile> CODEC = RecordCodecBuilder.create(
            p_325493_ -> p_325493_.group(
                        GlyphProviderDefinition.Conditional.CODEC.listOf().fieldOf("providers").forGetter(FontManager.FontDefinitionFile::providers)
                    )
                    .apply(p_325493_, FontManager.FontDefinitionFile::new)
        );
    }

    @OnlyIn(Dist.CLIENT)
    static record Preparation(Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets, List<GlyphProvider> allProviders) {
    }

    @OnlyIn(Dist.CLIENT)
    static record UnresolvedBuilderBundle(ResourceLocation fontId, List<FontManager.BuilderResult> builders, Set<ResourceLocation> dependencies)
        implements DependencySorter.Entry<ResourceLocation> {
        public UnresolvedBuilderBundle(ResourceLocation p_284984_) {
            this(p_284984_, new ArrayList<>(), new HashSet<>());
        }

        public void add(FontManager.BuilderId builderId, FontOption.Filter filter, GlyphProviderDefinition.Reference glyphProvider) {
            this.builders.add(new FontManager.BuilderResult(builderId, filter, Either.right(glyphProvider.id())));
            this.dependencies.add(glyphProvider.id());
        }

        public void add(FontManager.BuilderId builderId, FontOption.Filter filter, CompletableFuture<Optional<GlyphProvider>> glyphProvider) {
            this.builders.add(new FontManager.BuilderResult(builderId, filter, Either.left(glyphProvider)));
        }

        private Stream<CompletableFuture<Optional<GlyphProvider>>> listBuilders() {
            return this.builders.stream().flatMap(p_285041_ -> p_285041_.result.left().stream());
        }

        public Optional<List<GlyphProvider.Conditional>> resolve(Function<ResourceLocation, List<GlyphProvider.Conditional>> providerResolver) {
            List<GlyphProvider.Conditional> list = new ArrayList<>();

            for (FontManager.BuilderResult fontmanager$builderresult : this.builders) {
                Optional<List<GlyphProvider.Conditional>> optional = fontmanager$builderresult.resolve(providerResolver);
                if (!optional.isPresent()) {
                    return Optional.empty();
                }

                list.addAll(optional.get());
            }

            return Optional.of(list);
        }

        @Override
        public void visitRequiredDependencies(Consumer<ResourceLocation> visitor) {
            this.dependencies.forEach(visitor);
        }

        @Override
        public void visitOptionalDependencies(Consumer<ResourceLocation> visitor) {
        }
    }
}
