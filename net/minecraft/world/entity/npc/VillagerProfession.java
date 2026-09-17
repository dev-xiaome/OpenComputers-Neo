package net.minecraft.world.entity.npc;

import com.google.common.collect.ImmutableSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * @param requestedItems Defines items villagers of this profession can pick up and use.
 * @param secondaryPoi World blocks this profession interacts with.
 */
public record VillagerProfession(
    String name,
    Predicate<Holder<PoiType>> heldJobSite,
    Predicate<Holder<PoiType>> acquirableJobSite,
    ImmutableSet<Item> requestedItems,
    ImmutableSet<Block> secondaryPoi,
    @Nullable SoundEvent workSound
) {
    public static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS = p_238239_ -> p_238239_.is(PoiTypeTags.ACQUIRABLE_JOB_SITE);
    public static final VillagerProfession NONE = register("none", PoiType.NONE, ALL_ACQUIRABLE_JOBS, null);
    public static final VillagerProfession ARMORER = register("armorer", PoiTypes.ARMORER, SoundEvents.VILLAGER_WORK_ARMORER);
    public static final VillagerProfession BUTCHER = register("butcher", PoiTypes.BUTCHER, SoundEvents.VILLAGER_WORK_BUTCHER);
    public static final VillagerProfession CARTOGRAPHER = register("cartographer", PoiTypes.CARTOGRAPHER, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
    public static final VillagerProfession CLERIC = register("cleric", PoiTypes.CLERIC, SoundEvents.VILLAGER_WORK_CLERIC);
    public static final VillagerProfession FARMER = register(
        "farmer",
        PoiTypes.FARMER,
        ImmutableSet.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.BONE_MEAL),
        ImmutableSet.of(Blocks.FARMLAND),
        SoundEvents.VILLAGER_WORK_FARMER
    );
    public static final VillagerProfession FISHERMAN = register("fisherman", PoiTypes.FISHERMAN, SoundEvents.VILLAGER_WORK_FISHERMAN);
    public static final VillagerProfession FLETCHER = register("fletcher", PoiTypes.FLETCHER, SoundEvents.VILLAGER_WORK_FLETCHER);
    public static final VillagerProfession LEATHERWORKER = register("leatherworker", PoiTypes.LEATHERWORKER, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
    public static final VillagerProfession LIBRARIAN = register("librarian", PoiTypes.LIBRARIAN, SoundEvents.VILLAGER_WORK_LIBRARIAN);
    public static final VillagerProfession MASON = register("mason", PoiTypes.MASON, SoundEvents.VILLAGER_WORK_MASON);
    public static final VillagerProfession NITWIT = register("nitwit", PoiType.NONE, PoiType.NONE, null);
    public static final VillagerProfession SHEPHERD = register("shepherd", PoiTypes.SHEPHERD, SoundEvents.VILLAGER_WORK_SHEPHERD);
    public static final VillagerProfession TOOLSMITH = register("toolsmith", PoiTypes.TOOLSMITH, SoundEvents.VILLAGER_WORK_TOOLSMITH);
    public static final VillagerProfession WEAPONSMITH = register("weaponsmith", PoiTypes.WEAPONSMITH, SoundEvents.VILLAGER_WORK_WEAPONSMITH);

    @Override
    public String toString() {
        return this.name;
    }

    private static VillagerProfession register(String name, ResourceKey<PoiType> jobSite, @Nullable SoundEvent workSound) {
        return register(name, p_219668_ -> p_219668_.is(jobSite), p_219640_ -> p_219640_.is(jobSite), workSound);
    }

    private static VillagerProfession register(
        String name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSites, @Nullable SoundEvent workSound
    ) {
        return register(name, heldJobSite, acquirableJobSites, ImmutableSet.of(), ImmutableSet.of(), workSound);
    }

    private static VillagerProfession register(
        String name, ResourceKey<PoiType> jobSite, ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, @Nullable SoundEvent workSound
    ) {
        return register(name, p_238234_ -> p_238234_.is(jobSite), p_238237_ -> p_238237_.is(jobSite), requestedItems, secondaryPoi, workSound);
    }

    private static VillagerProfession register(
        String name,
        Predicate<Holder<PoiType>> heldJobSite,
        Predicate<Holder<PoiType>> acquirableJobSites,
        ImmutableSet<Item> requestedItems,
        ImmutableSet<Block> secondaryPoi,
        @Nullable SoundEvent workSound
    ) {
        return Registry.register(
            BuiltInRegistries.VILLAGER_PROFESSION,
            ResourceLocation.withDefaultNamespace(name),
            new VillagerProfession(name, heldJobSite, acquirableJobSites, requestedItems, secondaryPoi, workSound)
        );
    }
}
