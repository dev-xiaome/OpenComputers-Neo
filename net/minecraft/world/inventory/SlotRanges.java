package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public class SlotRanges {
    private static final List<SlotRange> SLOTS = Util.make(new ArrayList<>(), p_333664_ -> {
        addSingleSlot(p_333664_, "contents", 0);
        addSlotRange(p_333664_, "container.", 0, 54);
        addSlotRange(p_333664_, "hotbar.", 0, 9);
        addSlotRange(p_333664_, "inventory.", 9, 27);
        addSlotRange(p_333664_, "enderchest.", 200, 27);
        addSlotRange(p_333664_, "villager.", 300, 8);
        addSlotRange(p_333664_, "horse.", 500, 15);
        int i = EquipmentSlot.MAINHAND.getIndex(98);
        int j = EquipmentSlot.OFFHAND.getIndex(98);
        addSingleSlot(p_333664_, "weapon", i);
        addSingleSlot(p_333664_, "weapon.mainhand", i);
        addSingleSlot(p_333664_, "weapon.offhand", j);
        addSlots(p_333664_, "weapon.*", i, j);
        i = EquipmentSlot.HEAD.getIndex(100);
        j = EquipmentSlot.CHEST.getIndex(100);
        int k = EquipmentSlot.LEGS.getIndex(100);
        int l = EquipmentSlot.FEET.getIndex(100);
        int i1 = EquipmentSlot.BODY.getIndex(105);
        addSingleSlot(p_333664_, "armor.head", i);
        addSingleSlot(p_333664_, "armor.chest", j);
        addSingleSlot(p_333664_, "armor.legs", k);
        addSingleSlot(p_333664_, "armor.feet", l);
        addSingleSlot(p_333664_, "armor.body", i1);
        addSlots(p_333664_, "armor.*", i, j, k, l, i1);
        addSingleSlot(p_333664_, "horse.saddle", 400);
        addSingleSlot(p_333664_, "horse.chest", 499);
        addSingleSlot(p_333664_, "player.cursor", 499);
        addSlotRange(p_333664_, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.fromValues(() -> SLOTS.toArray(new SlotRange[0]));
    private static final Function<String, SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup(SLOTS.toArray(new SlotRange[0]), p_332645_ -> p_332645_);

    private static SlotRange create(String name, int value) {
        return SlotRange.of(name, IntLists.singleton(value));
    }

    private static SlotRange create(String name, IntList values) {
        return SlotRange.of(name, IntLists.unmodifiable(values));
    }

    private static SlotRange create(String name, int... values) {
        return SlotRange.of(name, IntList.of(values));
    }

    private static void addSingleSlot(List<SlotRange> list, String name, int value) {
        list.add(create(name, value));
    }

    private static void addSlotRange(List<SlotRange> list, String prefix, int startValue, int size) {
        IntList intlist = new IntArrayList(size);

        for (int i = 0; i < size; i++) {
            int j = startValue + i;
            list.add(create(prefix + i, j));
            intlist.add(j);
        }

        list.add(create(prefix + "*", intlist));
    }

    private static void addSlots(List<SlotRange> list, String name, int... values) {
        list.add(create(name, values));
    }

    @Nullable
    public static SlotRange nameToIds(String name) {
        return NAME_LOOKUP.apply(name);
    }

    public static Stream<String> allNames() {
        return SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SLOTS.stream().filter(p_332670_ -> p_332670_.size() == 1).map(StringRepresentable::getSerializedName);
    }
}
