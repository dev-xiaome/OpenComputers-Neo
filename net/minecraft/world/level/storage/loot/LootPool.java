package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool {
    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create(
        p_344669_ -> p_344669_.group(
                    LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(p_297995_ -> p_297995_.entries),
                    LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(p_297992_ -> p_297992_.conditions),
                    LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(p_297994_ -> p_297994_.functions),
                    NumberProviders.CODEC.fieldOf("rolls").forGetter(p_297993_ -> p_297993_.rolls),
                    NumberProviders.CODEC.fieldOf("bonus_rolls").orElse(ConstantValue.exactly(0.0F)).forGetter(p_297997_ -> p_297997_.bonusRolls),
                    Codec.STRING.optionalFieldOf("name").forGetter(pool -> java.util.Optional.ofNullable(pool.name).filter(name -> !name.startsWith("custom#")))
                )
                .apply(p_344669_, LootPool::new)
    );
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private NumberProvider rolls;
    private NumberProvider bonusRolls;

    LootPool(
        List<LootPoolEntryContainer> entries,
        List<LootItemCondition> conditions,
        List<LootItemFunction> functions,
        NumberProvider rolls,
        NumberProvider bonusRolls,
        java.util.Optional<String> name
    ) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
        this.name = name.orElse(null);
    }

    private void addRandomItem(Consumer<ItemStack> stackConsumer, LootContext context) {
        RandomSource randomsource = context.getRandom();
        List<LootPoolEntry> list = Lists.newArrayList();
        MutableInt mutableint = new MutableInt();

        for (LootPoolEntryContainer lootpoolentrycontainer : this.entries) {
            lootpoolentrycontainer.expand(context, p_79048_ -> {
                int k = p_79048_.getWeight(context.getLuck());
                if (k > 0) {
                    list.add(p_79048_);
                    mutableint.add(k);
                }
            });
        }

        int i = list.size();
        if (mutableint.intValue() != 0 && i != 0) {
            if (i == 1) {
                list.get(0).createItemStack(stackConsumer, context);
            } else {
                int j = randomsource.nextInt(mutableint.intValue());

                for (LootPoolEntry lootpoolentry : list) {
                    j -= lootpoolentry.getWeight(context.getLuck());
                    if (j < 0) {
                        lootpoolentry.createItemStack(stackConsumer, context);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Generate the random items from this LootPool to the given {@code stackConsumer}.
     * This first checks this pool's conditions, generating nothing if they do not match.
     * Then the random items are generated based on the {@link LootPoolEntry LootPoolEntries} in this pool according to the rolls and bonusRolls, applying any loot functions.
     */
    public void addRandomItems(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        if (this.compositeCondition.test(lootContext)) {
            Consumer<ItemStack> consumer = LootItemFunction.decorate(this.compositeFunction, stackConsumer, lootContext);
            int i = this.rolls.getInt(lootContext) + Mth.floor(this.bonusRolls.getFloat(lootContext) * lootContext.getLuck());

            for (int j = 0; j < i; j++) {
                this.addRandomItem(consumer, lootContext);
            }
        }
    }

    /**
     * Validate this LootPool according to the given context.
     */
    public void validate(ValidationContext context) {
        for (int i = 0; i < this.conditions.size(); i++) {
            this.conditions.get(i).validate(context.forChild(".condition[" + i + "]"));
        }

        for (int j = 0; j < this.functions.size(); j++) {
            this.functions.get(j).validate(context.forChild(".functions[" + j + "]"));
        }

        for (int k = 0; k < this.entries.size(); k++) {
            this.entries.get(k).validate(context.forChild(".entries[" + k + "]"));
        }

        this.rolls.validate(context.forChild(".rolls"));
        this.bonusRolls.validate(context.forChild(".bonusRolls"));
    }

    // Neo: Implement LootPool freezing to prevent manipulation outside of Neo APIs
    private boolean isFrozen = false;

    public void freeze() {
        this.isFrozen = true;
    }

    public boolean isFrozen() {
        return this.isFrozen;
    }

    private void checkFrozen() {
        if (this.isFrozen())
            throw new RuntimeException("Attempted to modify LootPool after being frozen!");
    }

    // Neo: Apply names for LootPools to allow easier targeting specific pools
    @org.jetbrains.annotations.Nullable
    private String name;

    @org.jetbrains.annotations.Nullable
    public String getName() {
        return this.name;
    }

    void setName(final String name) {
        if (this.name != null) {
            throw new UnsupportedOperationException("Cannot change the name of a pool when it has a name set!");
        }
        this.name = name;
    }

    // Neo: Add getters and settings for changing the rolls for this pool
    public NumberProvider getRolls() {
        return this.rolls;
    }

    public NumberProvider getBonusRolls() {
        return this.bonusRolls;
    }

    public void setRolls (NumberProvider v) {
        checkFrozen();
        this.rolls = v;
    }

    public void setBonusRolls (NumberProvider v) {
        checkFrozen();
        this.bonusRolls = v;
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);
        @org.jetbrains.annotations.Nullable
        private String name;

        public LootPool.Builder setRolls(NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(LootPoolEntryContainer.Builder<?> entriesBuilder) {
            this.entries.add(entriesBuilder.build());
            return this;
        }

        public LootPool.Builder when(LootItemCondition.Builder conditionBuilder) {
            this.conditions.add(conditionBuilder.build());
            return this;
        }

        public LootPool.Builder apply(LootItemFunction.Builder functionBuilder) {
            this.functions.add(functionBuilder.build());
            return this;
        }

        public LootPool.Builder name(String name) {
            this.name = name;
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls, java.util.Optional.ofNullable(this.name));
        }
    }
}
