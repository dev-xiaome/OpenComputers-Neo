package net.minecraft.util.datafix.fixes;

import com.google.common.base.Splitter;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.OptionalDynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackComponentizationFix extends DataFix {
    private static final int HIDE_ENCHANTMENTS = 1;
    private static final int HIDE_MODIFIERS = 2;
    private static final int HIDE_UNBREAKABLE = 4;
    private static final int HIDE_CAN_DESTROY = 8;
    private static final int HIDE_CAN_PLACE = 16;
    private static final int HIDE_ADDITIONAL = 32;
    private static final int HIDE_DYE = 64;
    private static final int HIDE_UPGRADES = 128;
    private static final Set<String> POTION_HOLDER_IDS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );
    private static final Set<String> BUCKETED_MOB_IDS = Set.of(
        "minecraft:pufferfish_bucket",
        "minecraft:salmon_bucket",
        "minecraft:cod_bucket",
        "minecraft:tropical_fish_bucket",
        "minecraft:axolotl_bucket",
        "minecraft:tadpole_bucket"
    );
    private static final List<String> BUCKETED_MOB_TAGS = List.of(
        "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health", "Age", "Variant", "HuntingCooldown", "BucketVariantTag"
    );
    private static final Set<String> BOOLEAN_BLOCK_STATE_PROPERTIES = Set.of(
        "attached",
        "bottom",
        "conditional",
        "disarmed",
        "drag",
        "enabled",
        "extended",
        "eye",
        "falling",
        "hanging",
        "has_bottle_0",
        "has_bottle_1",
        "has_bottle_2",
        "has_record",
        "has_book",
        "inverted",
        "in_wall",
        "lit",
        "locked",
        "occupied",
        "open",
        "persistent",
        "powered",
        "short",
        "signal_fire",
        "snowy",
        "triggered",
        "unstable",
        "waterlogged",
        "berries",
        "bloom",
        "shrieking",
        "can_summon",
        "up",
        "down",
        "north",
        "east",
        "south",
        "west",
        "slot_0_occupied",
        "slot_1_occupied",
        "slot_2_occupied",
        "slot_3_occupied",
        "slot_4_occupied",
        "slot_5_occupied",
        "cracked",
        "crafting"
    );
    private static final Splitter PROPERTY_SPLITTER = Splitter.on(',');

    public ItemStackComponentizationFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private static void fixItemStack(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        int i = itemStackData.removeTag("HideFlags").asInt(0);
        itemStackData.moveTagToComponent("Damage", "minecraft:damage", tag.createInt(0));
        itemStackData.moveTagToComponent("RepairCost", "minecraft:repair_cost", tag.createInt(0));
        itemStackData.moveTagToComponent("CustomModelData", "minecraft:custom_model_data");
        itemStackData.removeTag("BlockStateTag")
            .result()
            .ifPresent(p_332594_ -> itemStackData.setComponent("minecraft:block_state", fixBlockStateTag((Dynamic<?>)p_332594_)));
        itemStackData.moveTagToComponent("EntityTag", "minecraft:entity_data");
        itemStackData.fixSubTag("BlockEntityTag", false, p_330655_ -> {
            String s = NamespacedSchema.ensureNamespaced(p_330655_.get("id").asString(""));
            p_330655_ = fixBlockEntityTag(itemStackData, p_330655_, s);
            Dynamic<?> dynamic2 = p_330655_.remove("id");
            return dynamic2.equals(p_330655_.emptyMap()) ? dynamic2 : p_330655_;
        });
        itemStackData.moveTagToComponent("BlockEntityTag", "minecraft:block_entity_data");
        if (itemStackData.removeTag("Unbreakable").asBoolean(false)) {
            Dynamic<?> dynamic = tag.emptyMap();
            if ((i & 4) != 0) {
                dynamic = dynamic.set("show_in_tooltip", tag.createBoolean(false));
            }

            itemStackData.setComponent("minecraft:unbreakable", dynamic);
        }

        fixEnchantments(itemStackData, tag, "Enchantments", "minecraft:enchantments", (i & 1) != 0);
        if (itemStackData.is("minecraft:enchanted_book")) {
            fixEnchantments(itemStackData, tag, "StoredEnchantments", "minecraft:stored_enchantments", (i & 32) != 0);
        }

        itemStackData.fixSubTag("display", false, p_331784_ -> fixDisplay(itemStackData, p_331784_, i));
        fixAdventureModeChecks(itemStackData, tag, i);
        fixAttributeModifiers(itemStackData, tag, i);
        Optional<? extends Dynamic<?>> optional = itemStackData.removeTag("Trim").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic1 = (Dynamic<?>)optional.get();
            if ((i & 128) != 0) {
                dynamic1 = dynamic1.set("show_in_tooltip", dynamic1.createBoolean(false));
            }

            itemStackData.setComponent("minecraft:trim", dynamic1);
        }

        if ((i & 32) != 0) {
            itemStackData.setComponent("minecraft:hide_additional_tooltip", tag.emptyMap());
        }

        if (itemStackData.is("minecraft:crossbow")) {
            itemStackData.removeTag("Charged");
            itemStackData.moveTagToComponent("ChargedProjectiles", "minecraft:charged_projectiles", tag.createList(Stream.empty()));
        }

        if (itemStackData.is("minecraft:bundle")) {
            itemStackData.moveTagToComponent("Items", "minecraft:bundle_contents", tag.createList(Stream.empty()));
        }

        if (itemStackData.is("minecraft:filled_map")) {
            itemStackData.moveTagToComponent("map", "minecraft:map_id");
            Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = itemStackData.removeTag("Decorations")
                .asStream()
                .map(ItemStackComponentizationFix::fixMapDecoration)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (p_332591_, p_339605_) -> p_332591_));
            if (!map.isEmpty()) {
                itemStackData.setComponent("minecraft:map_decorations", tag.createMap(map));
            }
        }

        if (itemStackData.is(POTION_HOLDER_IDS)) {
            fixPotionContents(itemStackData, tag);
        }

        if (itemStackData.is("minecraft:writable_book")) {
            fixWritableBook(itemStackData, tag);
        }

        if (itemStackData.is("minecraft:written_book")) {
            fixWrittenBook(itemStackData, tag);
        }

        if (itemStackData.is("minecraft:suspicious_stew")) {
            itemStackData.moveTagToComponent("effects", "minecraft:suspicious_stew_effects");
        }

        if (itemStackData.is("minecraft:debug_stick")) {
            itemStackData.moveTagToComponent("DebugProperty", "minecraft:debug_stick_state");
        }

        if (itemStackData.is(BUCKETED_MOB_IDS)) {
            fixBucketedMobData(itemStackData, tag);
        }

        if (itemStackData.is("minecraft:goat_horn")) {
            itemStackData.moveTagToComponent("instrument", "minecraft:instrument");
        }

        if (itemStackData.is("minecraft:knowledge_book")) {
            itemStackData.moveTagToComponent("Recipes", "minecraft:recipes");
        }

        if (itemStackData.is("minecraft:compass")) {
            fixLodestoneTracker(itemStackData, tag);
        }

        if (itemStackData.is("minecraft:firework_rocket")) {
            fixFireworkRocket(itemStackData);
        }

        if (itemStackData.is("minecraft:firework_star")) {
            fixFireworkStar(itemStackData);
        }

        if (itemStackData.is("minecraft:player_head")) {
            itemStackData.removeTag("SkullOwner").result().ifPresent(p_330565_ -> itemStackData.setComponent("minecraft:profile", fixProfile((Dynamic<?>)p_330565_)));
        }
    }

    private static Dynamic<?> fixBlockStateTag(Dynamic<?> tag) {
        return DataFixUtils.orElse(tag.asMapOpt().result().map(p_339491_ -> p_339491_.collect(Collectors.toMap(Pair::getFirst, p_339490_ -> {
                String s = ((Dynamic)p_339490_.getFirst()).asString("");
                Dynamic<?> dynamic = (Dynamic<?>)p_339490_.getSecond();
                if (BOOLEAN_BLOCK_STATE_PROPERTIES.contains(s)) {
                    Optional<Boolean> optional = dynamic.asBoolean().result();
                    if (optional.isPresent()) {
                        return dynamic.createString(String.valueOf(optional.get()));
                    }
                }

                Optional<Number> optional1 = dynamic.asNumber().result();
                return optional1.isPresent() ? dynamic.createString(optional1.get().toString()) : dynamic;
            }))).map(tag::createMap), tag);
    }

    private static Dynamic<?> fixDisplay(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag, int hideFlags) {
        itemStackData.setComponent("minecraft:custom_name", tag.get("Name"));
        itemStackData.setComponent("minecraft:lore", tag.get("Lore"));
        Optional<Integer> optional = tag.get("color").asNumber().result().map(Number::intValue);
        boolean flag = (hideFlags & 64) != 0;
        if (optional.isPresent() || flag) {
            Dynamic<?> dynamic = tag.emptyMap().set("rgb", tag.createInt(optional.orElse(10511680)));
            if (flag) {
                dynamic = dynamic.set("show_in_tooltip", tag.createBoolean(false));
            }

            itemStackData.setComponent("minecraft:dyed_color", dynamic);
        }

        Optional<String> optional1 = tag.get("LocName").asString().result();
        if (optional1.isPresent()) {
            itemStackData.setComponent("minecraft:item_name", ComponentDataFixUtils.createTranslatableComponent(tag.getOps(), optional1.get()));
        }

        if (itemStackData.is("minecraft:filled_map")) {
            itemStackData.setComponent("minecraft:map_color", tag.get("MapColor"));
            tag = tag.remove("MapColor");
        }

        return tag.remove("Name").remove("Lore").remove("color").remove("LocName");
    }

    private static <T> Dynamic<T> fixBlockEntityTag(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<T> tag, String entityId) {
        itemStackData.setComponent("minecraft:lock", tag.get("Lock"));
        tag = tag.remove("Lock");
        Optional<Dynamic<T>> optional = tag.get("LootTable").result();
        if (optional.isPresent()) {
            Dynamic<T> dynamic = tag.emptyMap().set("loot_table", optional.get());
            long i = tag.get("LootTableSeed").asLong(0L);
            if (i != 0L) {
                dynamic = dynamic.set("seed", tag.createLong(i));
            }

            itemStackData.setComponent("minecraft:container_loot", dynamic);
            tag = tag.remove("LootTable").remove("LootTableSeed");
        }
        return switch (entityId) {
            case "minecraft:skull" -> {
                itemStackData.setComponent("minecraft:note_block_sound", tag.get("note_block_sound"));
                yield tag.remove("note_block_sound");
            }
            case "minecraft:decorated_pot" -> {
                itemStackData.setComponent("minecraft:pot_decorations", tag.get("sherds"));
                Optional<Dynamic<T>> optional2 = tag.get("item").result();
                if (optional2.isPresent()) {
                    itemStackData.setComponent(
                        "minecraft:container",
                        tag.createList(Stream.of(tag.emptyMap().set("slot", tag.createInt(0)).set("item", optional2.get())))
                    );
                }

                yield tag.remove("sherds").remove("item");
            }
            case "minecraft:banner" -> {
                itemStackData.setComponent("minecraft:banner_patterns", tag.get("patterns"));
                Optional<Number> optional1 = tag.get("Base").asNumber().result();
                if (optional1.isPresent()) {
                    itemStackData.setComponent("minecraft:base_color", tag.createString(BannerPatternFormatFix.fixColor(optional1.get().intValue())));
                }

                yield tag.remove("patterns").remove("Base");
            }
            case "minecraft:shulker_box", "minecraft:chest", "minecraft:trapped_chest", "minecraft:furnace", "minecraft:ender_chest", "minecraft:dispenser", "minecraft:dropper", "minecraft:brewing_stand", "minecraft:hopper", "minecraft:barrel", "minecraft:smoker", "minecraft:blast_furnace", "minecraft:campfire", "minecraft:chiseled_bookshelf", "minecraft:crafter" -> {
                List<Dynamic<T>> list = tag.get("Items")
                    .asList(
                        p_332590_ -> p_332590_.emptyMap()
                                .set("slot", p_332590_.createInt(p_332590_.get("Slot").asByte((byte)0) & 255))
                                .set("item", p_332590_.remove("Slot"))
                    );
                if (!list.isEmpty()) {
                    itemStackData.setComponent("minecraft:container", tag.createList(list.stream()));
                }

                yield tag.remove("Items");
            }
            case "minecraft:beehive" -> {
                itemStackData.setComponent("minecraft:bees", tag.get("bees"));
                yield tag.remove("bees");
            }
            default -> tag;
        };
    }

    private static void fixEnchantments(
        ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag, String key, String component, boolean hideEnchantments
    ) {
        OptionalDynamic<?> optionaldynamic = itemStackData.removeTag(key);
        List<Pair<String, Integer>> list = optionaldynamic.asList(Function.identity())
            .stream()
            .flatMap(p_330659_ -> parseEnchantment((Dynamic<?>)p_330659_).stream())
            .toList();
        if (!list.isEmpty() || hideEnchantments) {
            Dynamic<?> dynamic = tag.emptyMap();
            Dynamic<?> dynamic1 = tag.emptyMap();

            for (Pair<String, Integer> pair : list) {
                dynamic1 = dynamic1.set(pair.getFirst(), tag.createInt(pair.getSecond()));
            }

            dynamic = dynamic.set("levels", dynamic1);
            if (hideEnchantments) {
                dynamic = dynamic.set("show_in_tooltip", tag.createBoolean(false));
            }

            itemStackData.setComponent(component, dynamic);
        }

        if (optionaldynamic.result().isPresent() && list.isEmpty()) {
            itemStackData.setComponent("minecraft:enchantment_glint_override", tag.createBoolean(true));
        }
    }

    private static Optional<Pair<String, Integer>> parseEnchantment(Dynamic<?> enchantmentTag) {
        return enchantmentTag.get("id")
            .asString()
            .apply2stable((p_331946_, p_330581_) -> Pair.of(p_331946_, Mth.clamp(p_330581_.intValue(), 0, 255)), enchantmentTag.get("lvl").asNumber())
            .result();
    }

    private static void fixAdventureModeChecks(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag, int hideFlags) {
        fixBlockStatePredicates(itemStackData, tag, "CanDestroy", "minecraft:can_break", (hideFlags & 8) != 0);
        fixBlockStatePredicates(itemStackData, tag, "CanPlaceOn", "minecraft:can_place_on", (hideFlags & 16) != 0);
    }

    private static void fixBlockStatePredicates(
        ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag, String key, String component, boolean hide
    ) {
        Optional<? extends Dynamic<?>> optional = itemStackData.removeTag(key).result();
        if (!optional.isEmpty()) {
            Dynamic<?> dynamic = tag.emptyMap()
                .set(
                    "predicates",
                    tag.createList(
                        optional.get()
                            .asStream()
                            .map(
                                p_337638_ -> DataFixUtils.orElse(
                                        p_337638_.asString().map(p_330959_ -> fixBlockStatePredicate((Dynamic<?>)p_337638_, p_330959_)).result(), p_337638_
                                    )
                            )
                    )
                );
            if (hide) {
                dynamic = dynamic.set("show_in_tooltip", tag.createBoolean(false));
            }

            itemStackData.setComponent(component, dynamic);
        }
    }

    private static Dynamic<?> fixBlockStatePredicate(Dynamic<?> tag, String blockId) {
        int i = blockId.indexOf(91);
        int j = blockId.indexOf(123);
        int k = blockId.length();
        if (i != -1) {
            k = i;
        }

        if (j != -1) {
            k = Math.min(k, j);
        }

        String s = blockId.substring(0, k);
        Dynamic<?> dynamic = tag.emptyMap().set("blocks", tag.createString(s.trim()));
        int l = blockId.indexOf(93);
        if (i != -1 && l != -1) {
            Dynamic<?> dynamic1 = tag.emptyMap();

            for (String s1 : PROPERTY_SPLITTER.split(blockId.substring(i + 1, l))) {
                int i1 = s1.indexOf(61);
                if (i1 != -1) {
                    String s2 = s1.substring(0, i1).trim();
                    String s3 = s1.substring(i1 + 1).trim();
                    dynamic1 = dynamic1.set(s2, tag.createString(s3));
                }
            }

            dynamic = dynamic.set("state", dynamic1);
        }

        int j1 = blockId.indexOf(125);
        if (j != -1 && j1 != -1) {
            dynamic = dynamic.set("nbt", tag.createString(blockId.substring(j, j1 + 1)));
        }

        return dynamic;
    }

    private static void fixAttributeModifiers(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag, int hideFlags) {
        OptionalDynamic<?> optionaldynamic = itemStackData.removeTag("AttributeModifiers");
        if (!optionaldynamic.result().isEmpty()) {
            boolean flag = (hideFlags & 2) != 0;
            List<? extends Dynamic<?>> list = optionaldynamic.asList(ItemStackComponentizationFix::fixAttributeModifier);
            Dynamic<?> dynamic = tag.emptyMap().set("modifiers", tag.createList(list.stream()));
            if (flag) {
                dynamic = dynamic.set("show_in_tooltip", tag.createBoolean(false));
            }

            itemStackData.setComponent("minecraft:attribute_modifiers", dynamic);
        }
    }

    private static Dynamic<?> fixAttributeModifier(Dynamic<?> tag) {
        Dynamic<?> dynamic = tag.emptyMap()
            .set("name", tag.createString(""))
            .set("amount", tag.createDouble(0.0))
            .set("operation", tag.createString("add_value"));
        dynamic = Dynamic.copyField(tag, "AttributeName", dynamic, "type");
        dynamic = Dynamic.copyField(tag, "Slot", dynamic, "slot");
        dynamic = Dynamic.copyField(tag, "UUID", dynamic, "uuid");
        dynamic = Dynamic.copyField(tag, "Name", dynamic, "name");
        dynamic = Dynamic.copyField(tag, "Amount", dynamic, "amount");
        return Dynamic.copyAndFixField(tag, "Operation", dynamic, "operation", p_330453_ -> {
            return p_330453_.createString(switch (p_330453_.asInt(0)) {
                case 1 -> "add_multiplied_base";
                case 2 -> "add_multiplied_total";
                default -> "add_value";
            });
        });
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixMapDecoration(Dynamic<?> tag) {
        Dynamic<?> dynamic = DataFixUtils.orElseGet(tag.get("id").result(), () -> tag.createString(""));
        Dynamic<?> dynamic1 = tag.emptyMap()
            .set("type", tag.createString(fixMapDecorationType(tag.get("type").asInt(0))))
            .set("x", tag.createDouble(tag.get("x").asDouble(0.0)))
            .set("z", tag.createDouble(tag.get("z").asDouble(0.0)))
            .set("rotation", tag.createFloat((float)tag.get("rot").asDouble(0.0)));
        return Pair.of(dynamic, dynamic1);
    }

    private static String fixMapDecorationType(int decorationType) {
        return switch (decorationType) {
            case 1 -> "frame";
            case 2 -> "red_marker";
            case 3 -> "blue_marker";
            case 4 -> "target_x";
            case 5 -> "target_point";
            case 6 -> "player_off_map";
            case 7 -> "player_off_limits";
            case 8 -> "mansion";
            case 9 -> "monument";
            case 10 -> "banner_white";
            case 11 -> "banner_orange";
            case 12 -> "banner_magenta";
            case 13 -> "banner_light_blue";
            case 14 -> "banner_yellow";
            case 15 -> "banner_lime";
            case 16 -> "banner_pink";
            case 17 -> "banner_gray";
            case 18 -> "banner_light_gray";
            case 19 -> "banner_cyan";
            case 20 -> "banner_purple";
            case 21 -> "banner_blue";
            case 22 -> "banner_brown";
            case 23 -> "banner_green";
            case 24 -> "banner_red";
            case 25 -> "banner_black";
            case 26 -> "red_x";
            case 27 -> "village_desert";
            case 28 -> "village_plains";
            case 29 -> "village_savanna";
            case 30 -> "village_snowy";
            case 31 -> "village_taiga";
            case 32 -> "jungle_temple";
            case 33 -> "swamp_hut";
            default -> "player";
        };
    }

    private static void fixPotionContents(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        Dynamic<?> dynamic = tag.emptyMap();
        Optional<String> optional = itemStackData.removeTag("Potion").asString().result().filter(p_330426_ -> !p_330426_.equals("minecraft:empty"));
        if (optional.isPresent()) {
            dynamic = dynamic.set("potion", tag.createString(optional.get()));
        }

        dynamic = itemStackData.moveTagInto("CustomPotionColor", dynamic, "custom_color");
        dynamic = itemStackData.moveTagInto("custom_potion_effects", dynamic, "custom_effects");
        if (!dynamic.equals(tag.emptyMap())) {
            itemStackData.setComponent("minecraft:potion_contents", dynamic);
        }
    }

    private static void fixWritableBook(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        Dynamic<?> dynamic = fixBookPages(itemStackData, tag);
        if (dynamic != null) {
            itemStackData.setComponent("minecraft:writable_book_content", tag.emptyMap().set("pages", dynamic));
        }
    }

    private static void fixWrittenBook(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        Dynamic<?> dynamic = fixBookPages(itemStackData, tag);
        String s = itemStackData.removeTag("title").asString("");
        Optional<String> optional = itemStackData.removeTag("filtered_title").asString().result();
        Dynamic<?> dynamic1 = tag.emptyMap();
        dynamic1 = dynamic1.set("title", createFilteredText(tag, s, optional));
        dynamic1 = itemStackData.moveTagInto("author", dynamic1, "author");
        dynamic1 = itemStackData.moveTagInto("resolved", dynamic1, "resolved");
        dynamic1 = itemStackData.moveTagInto("generation", dynamic1, "generation");
        if (dynamic != null) {
            dynamic1 = dynamic1.set("pages", dynamic);
        }

        itemStackData.setComponent("minecraft:written_book_content", dynamic1);
    }

    @Nullable
    private static Dynamic<?> fixBookPages(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        List<String> list = itemStackData.removeTag("pages").asList(p_331677_ -> p_331677_.asString(""));
        Map<String, String> map = itemStackData.removeTag("filtered_pages").asMap(p_332151_ -> p_332151_.asString("0"), p_330471_ -> p_330471_.asString(""));
        if (list.isEmpty()) {
            return null;
        } else {
            List<Dynamic<?>> list1 = new ArrayList<>(list.size());

            for (int i = 0; i < list.size(); i++) {
                String s = list.get(i);
                String s1 = map.get(String.valueOf(i));
                list1.add(createFilteredText(tag, s, Optional.ofNullable(s1)));
            }

            return tag.createList(list1.stream());
        }
    }

    private static Dynamic<?> createFilteredText(Dynamic<?> tag, String unfilteredText, Optional<String> filteredText) {
        Dynamic<?> dynamic = tag.emptyMap().set("raw", tag.createString(unfilteredText));
        if (filteredText.isPresent()) {
            dynamic = dynamic.set("filtered", tag.createString(filteredText.get()));
        }

        return dynamic;
    }

    private static void fixBucketedMobData(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        Dynamic<?> dynamic = tag.emptyMap();

        for (String s : BUCKETED_MOB_TAGS) {
            dynamic = itemStackData.moveTagInto(s, dynamic, s);
        }

        if (!dynamic.equals(tag.emptyMap())) {
            itemStackData.setComponent("minecraft:bucket_entity_data", dynamic);
        }
    }

    private static void fixLodestoneTracker(ItemStackComponentizationFix.ItemStackData itemStackData, Dynamic<?> tag) {
        Optional<? extends Dynamic<?>> optional = itemStackData.removeTag("LodestonePos").result();
        Optional<? extends Dynamic<?>> optional1 = itemStackData.removeTag("LodestoneDimension").result();
        if (!optional.isEmpty() || !optional1.isEmpty()) {
            boolean flag = itemStackData.removeTag("LodestoneTracked").asBoolean(true);
            Dynamic<?> dynamic = tag.emptyMap();
            if (optional.isPresent() && optional1.isPresent()) {
                dynamic = dynamic.set("target", tag.emptyMap().set("pos", (Dynamic<?>)optional.get()).set("dimension", (Dynamic<?>)optional1.get()));
            }

            if (!flag) {
                dynamic = dynamic.set("tracked", tag.createBoolean(false));
            }

            itemStackData.setComponent("minecraft:lodestone_tracker", dynamic);
        }
    }

    private static void fixFireworkStar(ItemStackComponentizationFix.ItemStackData itemStackData) {
        itemStackData.fixSubTag("Explosion", true, p_331995_ -> {
            itemStackData.setComponent("minecraft:firework_explosion", fixFireworkExplosion(p_331995_));
            return p_331995_.remove("Type").remove("Colors").remove("FadeColors").remove("Trail").remove("Flicker");
        });
    }

    private static void fixFireworkRocket(ItemStackComponentizationFix.ItemStackData itemStackData) {
        itemStackData.fixSubTag(
            "Fireworks",
            true,
            p_331577_ -> {
                Stream<? extends Dynamic<?>> stream = p_331577_.get("Explosions").asStream().map(ItemStackComponentizationFix::fixFireworkExplosion);
                int i = p_331577_.get("Flight").asInt(0);
                itemStackData.setComponent(
                    "minecraft:fireworks",
                    p_331577_.emptyMap().set("explosions", p_331577_.createList(stream)).set("flight_duration", p_331577_.createByte((byte)i))
                );
                return p_331577_.remove("Explosions").remove("Flight");
            }
        );
    }

    private static Dynamic<?> fixFireworkExplosion(Dynamic<?> tag) {
        tag = tag.set("shape", tag.createString(switch (tag.get("Type").asInt(0)) {
            case 1 -> "large_ball";
            case 2 -> "star";
            case 3 -> "creeper";
            case 4 -> "burst";
            default -> "small_ball";
        })).remove("Type");
        tag = tag.renameField("Colors", "colors");
        tag = tag.renameField("FadeColors", "fade_colors");
        tag = tag.renameField("Trail", "has_trail");
        return tag.renameField("Flicker", "has_twinkle");
    }

    public static Dynamic<?> fixProfile(Dynamic<?> tag) {
        Optional<String> optional = tag.asString().result();
        if (optional.isPresent()) {
            return isValidPlayerName(optional.get()) ? tag.emptyMap().set("name", tag.createString(optional.get())) : tag.emptyMap();
        } else {
            String s = tag.get("Name").asString("");
            Optional<? extends Dynamic<?>> optional1 = tag.get("Id").result();
            Dynamic<?> dynamic = fixProfileProperties(tag.get("Properties"));
            Dynamic<?> dynamic1 = tag.emptyMap();
            if (isValidPlayerName(s)) {
                dynamic1 = dynamic1.set("name", tag.createString(s));
            }

            if (optional1.isPresent()) {
                dynamic1 = dynamic1.set("id", (Dynamic<?>)optional1.get());
            }

            if (dynamic != null) {
                dynamic1 = dynamic1.set("properties", dynamic);
            }

            return dynamic1;
        }
    }

    private static boolean isValidPlayerName(String name) {
        return name.length() > 16 ? false : name.chars().filter(p_332597_ -> p_332597_ <= 32 || p_332597_ >= 127).findAny().isEmpty();
    }

    @Nullable
    private static Dynamic<?> fixProfileProperties(OptionalDynamic<?> tag) {
        Map<String, List<Pair<String, Optional<String>>>> map = tag.asMap(
            p_331855_ -> p_331855_.asString(""), p_331384_ -> p_331384_.asList(p_337640_ -> {
                    String s = p_337640_.get("Value").asString("");
                    Optional<String> optional = p_337640_.get("Signature").asString().result();
                    return Pair.of(s, optional);
                })
        );
        return map.isEmpty()
            ? null
            : tag.createList(
                map.entrySet()
                    .stream()
                    .flatMap(
                        p_331925_ -> p_331925_.getValue()
                                .stream()
                                .map(
                                    p_331949_ -> {
                                        Dynamic<?> dynamic = tag.emptyMap()
                                            .set("name", tag.createString(p_331925_.getKey()))
                                            .set("value", tag.createString(p_331949_.getFirst()));
                                        Optional<String> optional = p_331949_.getSecond();
                                        return optional.isPresent() ? dynamic.set("signature", tag.createString(optional.get())) : dynamic;
                                    }
                                )
                    )
            );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ItemStack componentization",
            this.getInputSchema().getType(References.ITEM_STACK),
            this.getOutputSchema().getType(References.ITEM_STACK),
            p_331180_ -> {
                Optional<? extends Dynamic<?>> optional = ItemStackComponentizationFix.ItemStackData.read(p_331180_).map(p_330696_ -> {
                    fixItemStack(p_330696_, p_330696_.tag);
                    return p_330696_.write();
                });
                return DataFixUtils.orElse(optional, p_331180_);
            }
        );
    }

    static class ItemStackData {
        private final String item;
        private final int count;
        private Dynamic<?> components;
        private final Dynamic<?> remainder;
        Dynamic<?> tag;

        private ItemStackData(String item, int count, Dynamic<?> nbt) {
            this.item = NamespacedSchema.ensureNamespaced(item);
            this.count = count;
            this.components = nbt.emptyMap();
            this.tag = nbt.get("tag").orElseEmptyMap();
            this.remainder = nbt.remove("tag");
        }

        public static Optional<ItemStackComponentizationFix.ItemStackData> read(Dynamic<?> tag) {
            return tag.get("id")
                .asString()
                .apply2stable(
                    (p_331191_, p_330701_) -> new ItemStackComponentizationFix.ItemStackData(
                            p_331191_, p_330701_.intValue(), tag.remove("id").remove("Count")
                        ),
                    tag.get("Count").asNumber()
                )
                .result();
        }

        public OptionalDynamic<?> removeTag(String key) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(key);
            this.tag = this.tag.remove(key);
            return optionaldynamic;
        }

        public void setComponent(String component, Dynamic<?> value) {
            this.components = this.components.set(component, value);
        }

        public void setComponent(String component, OptionalDynamic<?> value) {
            value.result().ifPresent(p_332105_ -> this.components = this.components.set(component, (Dynamic<?>)p_332105_));
        }

        public Dynamic<?> moveTagInto(String oldKey, Dynamic<?> tag, String newKey) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(oldKey).result();
            return optional.isPresent() ? tag.set(newKey, (Dynamic<?>)optional.get()) : tag;
        }

        public void moveTagToComponent(String key, String component, Dynamic<?> tag) {
            Optional<? extends Dynamic<?>> optional = this.removeTag(key).result();
            if (optional.isPresent() && !optional.get().equals(tag)) {
                this.setComponent(component, (Dynamic<?>)optional.get());
            }
        }

        public void moveTagToComponent(String key, String component) {
            this.removeTag(key).result().ifPresent(p_330514_ -> this.setComponent(component, (Dynamic<?>)p_330514_));
        }

        public void fixSubTag(String key, boolean skipIfEmpty, UnaryOperator<Dynamic<?>> fixer) {
            OptionalDynamic<?> optionaldynamic = this.tag.get(key);
            if (!skipIfEmpty || !optionaldynamic.result().isEmpty()) {
                Dynamic<?> dynamic = optionaldynamic.orElseEmptyMap();
                dynamic = fixer.apply(dynamic);
                if (dynamic.equals(dynamic.emptyMap())) {
                    this.tag = this.tag.remove(key);
                } else {
                    this.tag = this.tag.set(key, dynamic);
                }
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.tag.emptyMap().set("id", this.tag.createString(this.item)).set("count", this.tag.createInt(this.count));
            if (!this.tag.equals(this.tag.emptyMap())) {
                this.components = this.components.set("minecraft:custom_data", this.tag);
            }

            if (!this.components.equals(this.tag.emptyMap())) {
                dynamic = dynamic.set("components", this.components);
            }

            return mergeRemainder(dynamic, this.remainder);
        }

        private static <T> Dynamic<T> mergeRemainder(Dynamic<T> tag, Dynamic<?> remainder) {
            DynamicOps<T> dynamicops = tag.getOps();
            return dynamicops.getMap(tag.getValue())
                .flatMap(p_330670_ -> dynamicops.mergeToMap(remainder.convert(dynamicops).getValue(), (MapLike<T>)p_330670_))
                .map(p_331482_ -> new Dynamic<>(dynamicops, (T)p_331482_))
                .result()
                .orElse(tag);
        }

        public boolean is(String item) {
            return this.item.equals(item);
        }

        public boolean is(Set<String> items) {
            return items.contains(this.item);
        }

        public boolean hasComponent(String component) {
            return this.components.get(component).result().isPresent();
        }
    }
}
