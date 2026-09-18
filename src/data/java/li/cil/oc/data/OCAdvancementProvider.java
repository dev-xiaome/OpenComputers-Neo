package li.cil.oc.data;

import li.cil.oc.common.Advancements;
import li.cil.oc.common.init.OCItems;
import net.minecraft.advancements.*;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class OCAdvancementProvider implements AdvancementProvider.AdvancementGenerator {
    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {
        Map<String, AdvancementHolder> advancements = new HashMap<>();
        Advancements.Definitions().foreach(definition -> {
            var advancement = generateDefinition(definition, advancements);
            saver.accept(advancement);
            advancements.put(definition.name(), advancement);
            return null;
        });
    }

    private AdvancementHolder generateDefinition(Advancements.Definition definition, Map<String, AdvancementHolder> advancements) {
        var advancement = new Advancement.Builder();
        if (definition.parent().isDefined()) {
            advancement.parent(advancements.get(definition.parent().get()));
        }

        advancement.display(
            OCItems.get(definition.icon()).createItemStack(1),
            Component.translatable("achievement.oc." + definition.name()),
            Component.translatable("achievement.oc." + definition.name() + ".desc"),
            // The root advancements must have a background defined, child ones will inherit from it.
            definition.parent().isEmpty()
                ? ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png")
                : null,
            AdvancementType.TASK,
            true,
            true,
            false
        );

        addManualCriteria(advancement, "crafting", definition.crafting());
        addManualCriteria(advancement, "assembling", definition.assembling());
        advancement.requirements(AdvancementRequirements.Strategy.OR);

        return advancement.build(definition.location());
    }

    private void addManualCriteria(Advancement.Builder advancement, String prefix, scala.collection.Seq<String> items) {
        items.foreach(item -> {
            advancement.addCriterion(prefix + "_" + item, CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()));
            return null;
        });
    }
}
