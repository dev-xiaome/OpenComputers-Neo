package net.minecraft.world.entity.animal;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class WolfVariants {
    public static final ResourceKey<WolfVariant> PALE = createKey("pale");
    public static final ResourceKey<WolfVariant> SPOTTED = createKey("spotted");
    public static final ResourceKey<WolfVariant> SNOWY = createKey("snowy");
    public static final ResourceKey<WolfVariant> BLACK = createKey("black");
    public static final ResourceKey<WolfVariant> ASHEN = createKey("ashen");
    public static final ResourceKey<WolfVariant> RUSTY = createKey("rusty");
    public static final ResourceKey<WolfVariant> WOODS = createKey("woods");
    public static final ResourceKey<WolfVariant> CHESTNUT = createKey("chestnut");
    public static final ResourceKey<WolfVariant> STRIPED = createKey("striped");
    public static final ResourceKey<WolfVariant> DEFAULT = PALE;

    private static ResourceKey<WolfVariant> createKey(String name) {
        return ResourceKey.create(Registries.WOLF_VARIANT, ResourceLocation.withDefaultNamespace(name));
    }

    static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, ResourceKey<Biome> spawnBiome) {
        register(context, key, name, HolderSet.direct(context.lookup(Registries.BIOME).getOrThrow(spawnBiome)));
    }

    static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, TagKey<Biome> spawnBiomes) {
        register(context, key, name, context.lookup(Registries.BIOME).getOrThrow(spawnBiomes));
    }

    static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, HolderSet<Biome> spawnBiomes) {
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("entity/wolf/" + name);
        ResourceLocation resourcelocation1 = ResourceLocation.withDefaultNamespace("entity/wolf/" + name + "_tame");
        ResourceLocation resourcelocation2 = ResourceLocation.withDefaultNamespace("entity/wolf/" + name + "_angry");
        context.register(key, new WolfVariant(resourcelocation, resourcelocation1, resourcelocation2, spawnBiomes));
    }

    public static Holder<WolfVariant> getSpawnVariant(RegistryAccess registryAccess, Holder<Biome> biome) {
        Registry<WolfVariant> registry = registryAccess.registryOrThrow(Registries.WOLF_VARIANT);
        return registry.holders()
            .filter(p_332674_ -> p_332674_.value().biomes().contains(biome))
            .findFirst()
            .or(() -> registry.getHolder(DEFAULT))
            .or(registry::getAny)
            .orElseThrow();
    }

    public static void bootstrap(BootstrapContext<WolfVariant> context) {
        register(context, PALE, "wolf", Biomes.TAIGA);
        register(context, SPOTTED, "wolf_spotted", BiomeTags.IS_SAVANNA);
        register(context, SNOWY, "wolf_snowy", Biomes.GROVE);
        register(context, BLACK, "wolf_black", Biomes.OLD_GROWTH_PINE_TAIGA);
        register(context, ASHEN, "wolf_ashen", Biomes.SNOWY_TAIGA);
        register(context, RUSTY, "wolf_rusty", BiomeTags.IS_JUNGLE);
        register(context, WOODS, "wolf_woods", Biomes.FOREST);
        register(context, CHESTNUT, "wolf_chestnut", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        register(context, STRIPED, "wolf_striped", BiomeTags.IS_BADLANDS);
    }
}
