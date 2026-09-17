package net.minecraft.advancements.critereon;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {
    private final ProblemReporter reporter;
    private final HolderGetter.Provider lootData;

    public CriterionValidator(ProblemReporter reporter, HolderGetter.Provider lootData) {
        this.reporter = reporter;
        this.lootData = lootData;
    }

    public void validateEntity(Optional<ContextAwarePredicate> entity, String name) {
        entity.ifPresent(p_311858_ -> this.validateEntity(p_311858_, name));
    }

    public void validateEntities(List<ContextAwarePredicate> entities, String name) {
        this.validate(entities, LootContextParamSets.ADVANCEMENT_ENTITY, name);
    }

    public void validateEntity(ContextAwarePredicate entity, String name) {
        this.validate(entity, LootContextParamSets.ADVANCEMENT_ENTITY, name);
    }

    public void validate(ContextAwarePredicate entity, LootContextParamSet contextParams, String name) {
        entity.validate(new ValidationContext(this.reporter.forChild(name), contextParams, this.lootData));
    }

    public void validate(List<ContextAwarePredicate> entities, LootContextParamSet contextParams, String name) {
        for (int i = 0; i < entities.size(); i++) {
            ContextAwarePredicate contextawarepredicate = entities.get(i);
            contextawarepredicate.validate(new ValidationContext(this.reporter.forChild(name + "[" + i + "]"), contextParams, this.lootData));
        }
    }
}
