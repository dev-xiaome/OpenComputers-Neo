package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

/**
 * Context for validating loot tables. Loot tables are validated recursively by checking that all functions, conditions, etc. (implementing {@link LootContextUser}) are valid according to their LootTable's {@link LootContextParamSet}.
 */
public class ValidationContext {
    private final ProblemReporter reporter;
    private final LootContextParamSet params;
    private final Optional<HolderGetter.Provider> resolver;
    private final Set<ResourceKey<?>> visitedElements;

    public ValidationContext(ProblemReporter reporter, LootContextParamSet params, HolderGetter.Provider resolver) {
        this(reporter, params, Optional.of(resolver), Set.of());
    }

    public ValidationContext(ProblemReporter reporter, LootContextParamSet params) {
        this(reporter, params, Optional.empty(), Set.of());
    }

    private ValidationContext(
        ProblemReporter reporter, LootContextParamSet params, Optional<HolderGetter.Provider> resolver, Set<ResourceKey<?>> visitedElements
    ) {
        this.reporter = reporter;
        this.params = params;
        this.resolver = resolver;
        this.visitedElements = visitedElements;
    }

    /**
     * Create a new ValidationContext with {@code childName} being added to the context.
     */
    public ValidationContext forChild(String childName) {
        return new ValidationContext(this.reporter.forChild(childName), this.params, this.resolver, this.visitedElements);
    }

    public ValidationContext enterElement(String name, ResourceKey<?> key) {
        Set<ResourceKey<?>> set = ImmutableSet.<ResourceKey<?>>builder().addAll(this.visitedElements).add(key).build();
        return new ValidationContext(this.reporter.forChild(name), this.params, this.resolver, set);
    }

    public boolean hasVisitedElement(ResourceKey<?> key) {
        return this.visitedElements.contains(key);
    }

    /**
     * Report a problem to this ValidationContext.
     */
    public void reportProblem(String problem) {
        this.reporter.report(problem);
    }

    /**
     * Validate the given LootContextUser.
     */
    public void validateUser(LootContextUser lootContextUser) {
        this.params.validateUser(this, lootContextUser);
    }

    public HolderGetter.Provider resolver() {
        return this.resolver.orElseThrow(() -> new UnsupportedOperationException("References not allowed"));
    }

    public boolean allowsReferences() {
        return this.resolver.isPresent();
    }

    /**
     * Create a new ValidationContext with the given LootContextParamSet.
     */
    public ValidationContext setParams(LootContextParamSet params) {
        return new ValidationContext(this.reporter, params, this.resolver, this.visitedElements);
    }

    public ProblemReporter reporter() {
        return this.reporter;
    }
}
