package net.minecraft.world.level.storage.loot.parameters;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContextUser;
import net.minecraft.world.level.storage.loot.ValidationContext;

/**
 * A LootContextParamSet defines a set of required and optional {@link LootContextParam}s.
 * This is used to validate that conditions, functions and other {@link LootContextUser}s only use those parameters that are present for the given loot table.
 *
 * @see LootContextParamSets
 * @see ValidationContext
 */
public class LootContextParamSet {
    private final Set<LootContextParam<?>> required;
    private final Set<LootContextParam<?>> all;

    LootContextParamSet(Set<LootContextParam<?>> required, Set<LootContextParam<?>> optional) {
        this.required = ImmutableSet.copyOf(required);
        this.all = ImmutableSet.copyOf(Sets.union(required, optional));
    }

    /**
     * Whether the given parameter is allowed in this set.
     */
    public boolean isAllowed(LootContextParam<?> param) {
        return this.all.contains(param);
    }

    public Set<LootContextParam<?>> getRequired() {
        return this.required;
    }

    public Set<LootContextParam<?>> getAllowed() {
        return this.all;
    }

    @Override
    public String toString() {
        return "["
            + Joiner.on(", ").join(this.all.stream().map(p_339580_ -> (this.required.contains(p_339580_) ? "!" : "") + p_339580_.getName()).iterator())
            + "]";
    }

    /**
     * Validate that all parameters referenced by the given LootContextUser are present in this set.
     */
    public void validateUser(ValidationContext validationContext, LootContextUser lootContextUser) {
        this.validateUser(validationContext.reporter(), lootContextUser);
    }

    public void validateUser(ProblemReporter problemReporter, LootContextUser lootContextUser) {
        Set<LootContextParam<?>> set = lootContextUser.getReferencedContextParams();
        Set<LootContextParam<?>> set1 = Sets.difference(set, this.all);
        if (!set1.isEmpty()) {
            problemReporter.report("Parameters " + set1 + " are not provided in this context");
        }
    }

    public static LootContextParamSet.Builder builder() {
        return new LootContextParamSet.Builder();
    }

    public static class Builder {
        private final Set<LootContextParam<?>> required = Sets.newIdentityHashSet();
        private final Set<LootContextParam<?>> optional = Sets.newIdentityHashSet();

        public LootContextParamSet.Builder required(LootContextParam<?> parameter) {
            if (this.optional.contains(parameter)) {
                throw new IllegalArgumentException("Parameter " + parameter.getName() + " is already optional");
            } else {
                this.required.add(parameter);
                return this;
            }
        }

        public LootContextParamSet.Builder optional(LootContextParam<?> parameter) {
            if (this.required.contains(parameter)) {
                throw new IllegalArgumentException("Parameter " + parameter.getName() + " is already required");
            } else {
                this.optional.add(parameter);
                return this;
            }
        }

        public LootContextParamSet build() {
            return new LootContextParamSet(this.required, this.optional);
        }
    }
}
