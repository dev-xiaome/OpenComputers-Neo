package net.minecraft.stats;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
    private static final Map<RecipeBookType, Pair<String, String>> TAG_FIELDS = net.neoforged.neoforge.common.CommonHooks.buildRecipeBookTypeTagFields(ImmutableMap.of(
        RecipeBookType.CRAFTING,
        Pair.of("isGuiOpen", "isFilteringCraftable"),
        RecipeBookType.FURNACE,
        Pair.of("isFurnaceGuiOpen", "isFurnaceFilteringCraftable"),
        RecipeBookType.BLAST_FURNACE,
        Pair.of("isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"),
        RecipeBookType.SMOKER,
        Pair.of("isSmokerGuiOpen", "isSmokerFilteringCraftable")
    ));
    private final Map<RecipeBookType, RecipeBookSettings.TypeSettings> states;

    private RecipeBookSettings(Map<RecipeBookType, RecipeBookSettings.TypeSettings> states) {
        this.states = states;
    }

    public RecipeBookSettings() {
        this(Util.make(Maps.newEnumMap(RecipeBookType.class), p_12740_ -> {
            for (RecipeBookType recipebooktype : RecipeBookType.values()) {
                p_12740_.put(recipebooktype, new RecipeBookSettings.TypeSettings(false, false));
            }
        }));
    }

    public boolean isOpen(RecipeBookType bookType) {
        return this.states.get(bookType).open;
    }

    public void setOpen(RecipeBookType bookType, boolean open) {
        this.states.get(bookType).open = open;
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.states.get(bookType).filtering;
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.states.get(bookType).filtering = filtering;
    }

    public static RecipeBookSettings read(FriendlyByteBuf buffer) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);

        // Neo: filter out modded RecipeBookTypes when connected to a vanilla server
        for (RecipeBookType recipebooktype : net.neoforged.neoforge.common.CommonHooks.getFilteredRecipeBookTypeValues()) {
            boolean flag = buffer.readBoolean();
            boolean flag1 = buffer.readBoolean();
            map.put(recipebooktype, new RecipeBookSettings.TypeSettings(flag, flag1));
        }

        return new RecipeBookSettings(map);
    }

    public void write(FriendlyByteBuf buffer) {
        for (RecipeBookType recipebooktype : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings recipebooksettings$typesettings = this.states.get(recipebooktype);
            if (recipebooksettings$typesettings == null) {
                buffer.writeBoolean(false);
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(recipebooksettings$typesettings.open);
                buffer.writeBoolean(recipebooksettings$typesettings.filtering);
            }
        }
    }

    public static RecipeBookSettings read(CompoundTag tag) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);
        TAG_FIELDS.forEach((p_12750_, p_12751_) -> {
            boolean flag = tag.getBoolean(p_12751_.getFirst());
            boolean flag1 = tag.getBoolean(p_12751_.getSecond());
            map.put(p_12750_, new RecipeBookSettings.TypeSettings(flag, flag1));
        });
        return new RecipeBookSettings(map);
    }

    public void write(CompoundTag tag) {
        TAG_FIELDS.forEach((p_12745_, p_12746_) -> {
            RecipeBookSettings.TypeSettings recipebooksettings$typesettings = this.states.get(p_12745_);
            tag.putBoolean(p_12746_.getFirst(), recipebooksettings$typesettings.open);
            tag.putBoolean(p_12746_.getSecond(), recipebooksettings$typesettings.filtering);
        });
    }

    public RecipeBookSettings copy() {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);

        for (RecipeBookType recipebooktype : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings recipebooksettings$typesettings = this.states.get(recipebooktype);
            map.put(recipebooktype, recipebooksettings$typesettings.copy());
        }

        return new RecipeBookSettings(map);
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.states.clear();

        for (RecipeBookType recipebooktype : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings recipebooksettings$typesettings = other.states.get(recipebooktype);
            this.states.put(recipebooktype, recipebooksettings$typesettings.copy());
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeBookSettings && this.states.equals(((RecipeBookSettings)other).states);
    }

    @Override
    public int hashCode() {
        return this.states.hashCode();
    }

    static final class TypeSettings {
        boolean open;
        boolean filtering;

        public TypeSettings(boolean open, boolean filtering) {
            this.open = open;
            this.filtering = filtering;
        }

        public RecipeBookSettings.TypeSettings copy() {
            return new RecipeBookSettings.TypeSettings(this.open, this.filtering);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else {
                return !(other instanceof RecipeBookSettings.TypeSettings recipebooksettings$typesettings)
                    ? false
                    : this.open == recipebooksettings$typesettings.open && this.filtering == recipebooksettings$typesettings.filtering;
            }
        }

        @Override
        public int hashCode() {
            int i = this.open ? 1 : 0;
            return 31 * i + (this.filtering ? 1 : 0);
        }

        @Override
        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }
    }
}
