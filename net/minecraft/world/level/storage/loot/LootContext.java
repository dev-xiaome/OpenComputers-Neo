package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * LootContext stores various context information for loot generation.
 * This includes the Level as well as any known {@link LootContextParam}s.
 */
public class LootContext {
    private final LootParams params;
    private final RandomSource random;
    private final HolderGetter.Provider lootDataResolver;
    private final Set<LootContext.VisitedEntry<?>> visitedElements = Sets.newLinkedHashSet();

    LootContext(LootParams params, RandomSource random, HolderGetter.Provider lootDataResolver) {
        this.params = params;
        this.random = random;
        this.lootDataResolver = lootDataResolver;
    }

    /**
     * Check whether the given parameter is present in this context.
     */
    public boolean hasParam(LootContextParam<?> parameter) {
        return this.params.hasParam(parameter);
    }

    /**
     * Get the value of the given parameter.
     *
     * @throws NoSuchElementException if the parameter is not present in this context
     */
    public <T> T getParam(LootContextParam<T> param) {
        return this.params.getParameter(param);
    }

    /**
     * Add the dynamic drops for the given dynamic drops name to the given consumer.
     * If no dynamic drops provider for the given name has been registered to this LootContext, nothing is generated.
     *
     * @see DynamicDrops
     */
    public void addDynamicDrops(ResourceLocation name, Consumer<ItemStack> consumer) {
        this.params.addDynamicDrops(name, consumer);
    }

    /**
     * Get the value of the given parameter if it is present in this context, null otherwise.
     */
    @Nullable
    public <T> T getParamOrNull(LootContextParam<T> parameter) {
        return this.params.getParamOrNull(parameter);
    }

    public boolean hasVisitedElement(LootContext.VisitedEntry<?> element) {
        return this.visitedElements.contains(element);
    }

    public boolean pushVisitedElement(LootContext.VisitedEntry<?> element) {
        return this.visitedElements.add(element);
    }

    public void popVisitedElement(LootContext.VisitedEntry<?> element) {
        this.visitedElements.remove(element);
    }

    public HolderGetter.Provider getResolver() {
        return this.lootDataResolver;
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public float getLuck() {
        return this.params.getLuck();
    }

    public ServerLevel getLevel() {
        return this.params.getLevel();
    }

    public static LootContext.VisitedEntry<LootTable> createVisitedEntry(LootTable lootTable) {
        return new LootContext.VisitedEntry<>(LootDataType.TABLE, lootTable);
    }

    public static LootContext.VisitedEntry<LootItemCondition> createVisitedEntry(LootItemCondition predicate) {
        return new LootContext.VisitedEntry<>(LootDataType.PREDICATE, predicate);
    }

    public static LootContext.VisitedEntry<LootItemFunction> createVisitedEntry(LootItemFunction modifier) {
        return new LootContext.VisitedEntry<>(LootDataType.MODIFIER, modifier);
    }

    // Neo: Keep track of the original loot table ID through modifications
    @org.jetbrains.annotations.Nullable
    private ResourceLocation queriedLootTableId;

    private LootContext(LootParams params, RandomSource random, HolderGetter.Provider lootDataResolver, ResourceLocation queriedLootTableId) {
        this(params, random, lootDataResolver);
        this.queriedLootTableId = queriedLootTableId;
    }

    public void setQueriedLootTableId(@org.jetbrains.annotations.Nullable ResourceLocation queriedLootTableId) {
        if (this.queriedLootTableId == null && queriedLootTableId != null) this.queriedLootTableId = queriedLootTableId;
    }

    public ResourceLocation getQueriedLootTableId() {
        return this.queriedLootTableId == null ? net.neoforged.neoforge.common.loot.LootTableIdCondition.UNKNOWN_LOOT_TABLE : this.queriedLootTableId;
    }

    public static class Builder {
        private final LootParams params;
        @Nullable
        private RandomSource random;
        @Nullable
        private ResourceLocation queriedLootTableId; // Forge: correctly pass around loot table ID with copy constructor

        public Builder(LootParams params) {
            this.params = params;
        }

        public Builder(LootContext context) {
            this.params = context.params;
            this.random = context.random;
            this.queriedLootTableId = context.queriedLootTableId;
        }

        public LootContext.Builder withOptionalRandomSeed(long seed) {
            if (seed != 0L) {
                this.random = RandomSource.create(seed);
            }

            return this;
        }

        public LootContext.Builder withOptionalRandomSource(RandomSource random) {
            this.random = random;
            return this;
        }

        public LootContext.Builder withQueriedLootTableId(ResourceLocation queriedLootTableId) {
            this.queriedLootTableId = queriedLootTableId;
            return this;
        }

        public ServerLevel getLevel() {
            return this.params.getLevel();
        }

        public LootContext create(Optional<ResourceLocation> sequence) {
            ServerLevel serverlevel = this.getLevel();
            MinecraftServer minecraftserver = serverlevel.getServer();
            RandomSource randomsource = Optional.ofNullable(this.random)
                .or(() -> sequence.map(serverlevel::getRandomSequence))
                .orElseGet(serverlevel::getRandom);
            return new LootContext(this.params, randomsource, minecraftserver.reloadableRegistries().lookup(), queriedLootTableId);
        }
    }

    /**
     * Represents a type of entity that can be looked up in a {@link LootContext} using a {@link LootContextParam}.
     */
    public static enum EntityTarget implements StringRepresentable {
        /**
         * Looks up {@link LootContextParams#THIS_ENTITY}.
         */
        THIS("this", LootContextParams.THIS_ENTITY),
        ATTACKER("attacker", LootContextParams.ATTACKING_ENTITY),
        DIRECT_ATTACKER("direct_attacker", LootContextParams.DIRECT_ATTACKING_ENTITY),
        ATTACKING_PLAYER("attacking_player", LootContextParams.LAST_DAMAGE_PLAYER);

        public static final StringRepresentable.EnumCodec<LootContext.EntityTarget> CODEC = StringRepresentable.fromEnum(LootContext.EntityTarget::values);
        private final String name;
        private final LootContextParam<? extends Entity> param;

        private EntityTarget(String name, LootContextParam<? extends Entity> param) {
            this.name = name;
            this.param = param;
        }

        public LootContextParam<? extends Entity> getParam() {
            return this.param;
        }

        // Forge: This method is patched in to expose the same name used in getByName so that ContextNbtProvider#forEntity serializes it properly
        public String getName() {
            return this.name;
        }

        public static LootContext.EntityTarget getByName(String name) {
            LootContext.EntityTarget lootcontext$entitytarget = CODEC.byName(name);
            if (lootcontext$entitytarget != null) {
                return lootcontext$entitytarget;
            } else {
                throw new IllegalArgumentException("Invalid entity target " + name);
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static record VisitedEntry<T>(LootDataType<T> type, T value) {
    }
}
