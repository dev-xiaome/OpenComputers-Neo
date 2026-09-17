package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class MobEffectIdFix extends DataFix {
    private static final Int2ObjectMap<String> ID_MAP = Util.make(new Int2ObjectOpenHashMap<>(), p_298903_ -> {
        p_298903_.put(1, "minecraft:speed");
        p_298903_.put(2, "minecraft:slowness");
        p_298903_.put(3, "minecraft:haste");
        p_298903_.put(4, "minecraft:mining_fatigue");
        p_298903_.put(5, "minecraft:strength");
        p_298903_.put(6, "minecraft:instant_health");
        p_298903_.put(7, "minecraft:instant_damage");
        p_298903_.put(8, "minecraft:jump_boost");
        p_298903_.put(9, "minecraft:nausea");
        p_298903_.put(10, "minecraft:regeneration");
        p_298903_.put(11, "minecraft:resistance");
        p_298903_.put(12, "minecraft:fire_resistance");
        p_298903_.put(13, "minecraft:water_breathing");
        p_298903_.put(14, "minecraft:invisibility");
        p_298903_.put(15, "minecraft:blindness");
        p_298903_.put(16, "minecraft:night_vision");
        p_298903_.put(17, "minecraft:hunger");
        p_298903_.put(18, "minecraft:weakness");
        p_298903_.put(19, "minecraft:poison");
        p_298903_.put(20, "minecraft:wither");
        p_298903_.put(21, "minecraft:health_boost");
        p_298903_.put(22, "minecraft:absorption");
        p_298903_.put(23, "minecraft:saturation");
        p_298903_.put(24, "minecraft:glowing");
        p_298903_.put(25, "minecraft:levitation");
        p_298903_.put(26, "minecraft:luck");
        p_298903_.put(27, "minecraft:unluck");
        p_298903_.put(28, "minecraft:slow_falling");
        p_298903_.put(29, "minecraft:conduit_power");
        p_298903_.put(30, "minecraft:dolphins_grace");
        p_298903_.put(31, "minecraft:bad_omen");
        p_298903_.put(32, "minecraft:hero_of_the_village");
        p_298903_.put(33, "minecraft:darkness");
    });
    private static final Set<String> MOB_EFFECT_INSTANCE_CARRIER_ITEMS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );

    public MobEffectIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Optional<Dynamic<T>> getAndConvertMobEffectId(Dynamic<T> dynamic, String key) {
        return dynamic.get(key).asNumber().result().map(p_298383_ -> ID_MAP.get(p_298383_.intValue())).map(dynamic::createString);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> oldDynamic, String oldName, Dynamic<T> newDynamic, String newName) {
        Optional<Dynamic<T>> optional = getAndConvertMobEffectId(oldDynamic, oldName);
        return newDynamic.replaceField(oldName, newName, optional);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> dynamic, String oldName, String newName) {
        return updateMobEffectIdField(dynamic, oldName, dynamic, newName);
    }

    private static <T> Dynamic<T> updateMobEffectIdFieldConsideringForge(Dynamic<T> p_298948_, String p_299234_, Dynamic<T> p_298832_, String p_298487_, String forgeFieldId) {
        final var forgeField = p_298948_.get(forgeFieldId).result();
        if (forgeField.isPresent()) {
                return setFieldIfPresent((p_298948_ == p_298832_ ? p_298948_.remove(forgeFieldId) : p_298832_), p_298487_, forgeField);
            }
        return updateMobEffectIdField(p_298948_, p_299234_, p_298832_, p_298487_);
    }

    private static <T> Dynamic<T> setFieldIfPresent(Dynamic<T> dynamic, String s, Optional<Dynamic<T>> optional) {
        return optional.isEmpty() ? dynamic : dynamic.set(s, optional.get());
    }

    private static <T> Dynamic<T> updateMobEffectInstance(Dynamic<T> dynamic) {
        dynamic = updateMobEffectIdFieldConsideringForge(dynamic, "Id", dynamic, "id", "forge:id");
        dynamic = dynamic.renameField("Ambient", "ambient");
        dynamic = dynamic.renameField("Amplifier", "amplifier");
        dynamic = dynamic.renameField("Duration", "duration");
        dynamic = dynamic.renameField("ShowParticles", "show_particles");
        dynamic = dynamic.renameField("ShowIcon", "show_icon");
        Optional<Dynamic<T>> optional = dynamic.get("HiddenEffect").result().map(MobEffectIdFix::updateMobEffectInstance);
        return dynamic.replaceField("HiddenEffect", "hidden_effect", optional);
    }

    private static <T> Dynamic<T> updateMobEffectInstanceList(Dynamic<T> tag, String oldName, String newName) {
        Optional<Dynamic<T>> optional = tag.get(oldName)
            .asStreamOpt()
            .result()
            .map(p_298291_ -> tag.createList(p_298291_.map(MobEffectIdFix::updateMobEffectInstance)));
        return tag.replaceField(oldName, newName, optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> oldDynamic, Dynamic<T> newDynamic) {
        newDynamic = updateMobEffectIdFieldConsideringForge(oldDynamic, "EffectId", newDynamic, "id", "forge:effect_id");
        Optional<Dynamic<T>> optional = oldDynamic.get("EffectDuration").result();
        return newDynamic.replaceField("EffectDuration", "duration", optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> suspiciousStewEntry) {
        return updateSuspiciousStewEntry(suspiciousStewEntry, suspiciousStewEntry);
    }

    private Typed<?> updateNamedChoice(Typed<?> typed, TypeReference reference, String id, Function<Dynamic<?>, Dynamic<?>> fixer) {
        Type<?> type = this.getInputSchema().getChoiceType(reference, id);
        Type<?> type1 = this.getOutputSchema().getChoiceType(reference, id);
        return typed.updateTyped(DSL.namedChoice(id, type), type1, p_298322_ -> p_298322_.update(DSL.remainderFinder(), fixer));
    }

    private TypeRewriteRule blockEntityFixer() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BlockEntityMobEffectIdFix", type, p_299097_ -> this.updateNamedChoice(p_299097_, References.BLOCK_ENTITY, "minecraft:beacon", p_298966_ -> {
                    p_298966_ = updateMobEffectIdField(p_298966_, "Primary", "primary_effect");
                    return updateMobEffectIdField(p_298966_, "Secondary", "secondary_effect");
                })
        );
    }

    private static <T> Dynamic<T> fixMooshroomTag(Dynamic<T> mooshroomTag) {
        Dynamic<T> dynamic = mooshroomTag.emptyMap();
        Dynamic<T> dynamic1 = updateSuspiciousStewEntry(mooshroomTag, dynamic);
        if (!dynamic1.equals(dynamic)) {
            mooshroomTag = mooshroomTag.set("stew_effects", mooshroomTag.createList(Stream.of(dynamic1)));
        }

        return mooshroomTag.remove("EffectId").remove("EffectDuration");
    }

    private static <T> Dynamic<T> fixArrowTag(Dynamic<T> arrowTag) {
        return updateMobEffectInstanceList(arrowTag, "CustomPotionEffects", "custom_potion_effects");
    }

    private static <T> Dynamic<T> fixAreaEffectCloudTag(Dynamic<T> areaEffectCloudTag) {
        return updateMobEffectInstanceList(areaEffectCloudTag, "Effects", "effects");
    }

    private static Dynamic<?> updateLivingEntityTag(Dynamic<?> livingEntityTag) {
        return updateMobEffectInstanceList(livingEntityTag, "ActiveEffects", "active_effects");
    }

    private TypeRewriteRule entityFixer() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped("EntityMobEffectIdFix", type, p_298626_ -> {
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:mooshroom", MobEffectIdFix::fixMooshroomTag);
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:arrow", MobEffectIdFix::fixArrowTag);
            p_298626_ = this.updateNamedChoice(p_298626_, References.ENTITY, "minecraft:area_effect_cloud", MobEffectIdFix::fixAreaEffectCloudTag);
            return p_298626_.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
        });
    }

    private TypeRewriteRule playerFixer() {
        Type<?> type = this.getInputSchema().getType(References.PLAYER);
        return this.fixTypeEverywhereTyped(
            "PlayerMobEffectIdFix", type, p_300792_ -> p_300792_.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag)
        );
    }

    private static <T> Dynamic<T> fixSuspiciousStewTag(Dynamic<T> suspiciousStewTag) {
        Optional<Dynamic<T>> optional = suspiciousStewTag.get("Effects")
            .asStreamOpt()
            .result()
            .map(p_299036_ -> suspiciousStewTag.createList(p_299036_.map(MobEffectIdFix::updateSuspiciousStewEntry)));
        return suspiciousStewTag.replaceField("Effects", "effects", optional);
    }

    private TypeRewriteRule itemStackFixer() {
        OpticFinder<Pair<String, String>> opticfinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder1 = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemStackMobEffectIdFix",
            type,
            p_298821_ -> {
                Optional<Pair<String, String>> optional = p_298821_.getOptional(opticfinder);
                if (optional.isPresent()) {
                    String s = optional.get().getSecond();
                    if (s.equals("minecraft:suspicious_stew")) {
                        return p_298821_.updateTyped(opticfinder1, p_298520_ -> p_298520_.update(DSL.remainderFinder(), MobEffectIdFix::fixSuspiciousStewTag));
                    }

                    if (MOB_EFFECT_INSTANCE_CARRIER_ITEMS.contains(s)) {
                        return p_298821_.updateTyped(
                            opticfinder1,
                            p_298705_ -> p_298705_.update(
                                    DSL.remainderFinder(), p_298855_ -> updateMobEffectInstanceList(p_298855_, "CustomPotionEffects", "custom_potion_effects")
                                )
                        );
                    }
                }

                return p_298821_;
            }
        );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.blockEntityFixer(), this.entityFixer(), this.playerFixer(), this.itemStackFixer());
    }
}
