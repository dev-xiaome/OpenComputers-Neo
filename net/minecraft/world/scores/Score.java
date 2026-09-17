package net.minecraft.world.scores;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;

public class Score implements ReadOnlyScoreInfo {
    private static final String TAG_SCORE = "Score";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_DISPLAY = "display";
    private static final String TAG_FORMAT = "format";
    private int value;
    private boolean locked = true;
    @Nullable
    private Component display;
    @Nullable
    private NumberFormat numberFormat;

    @Override
    public int value() {
        return this.value;
    }

    public void value(int value) {
        this.value = value;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Nullable
    public Component display() {
        return this.display;
    }

    public void display(@Nullable Component display) {
        this.display = display;
    }

    @Nullable
    @Override
    public NumberFormat numberFormat() {
        return this.numberFormat;
    }

    public void numberFormat(@Nullable NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    public CompoundTag write(HolderLookup.Provider levelRegistry) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putInt("Score", this.value);
        compoundtag.putBoolean("Locked", this.locked);
        if (this.display != null) {
            compoundtag.putString("display", Component.Serializer.toJson(this.display, levelRegistry));
        }

        if (this.numberFormat != null) {
            NumberFormatTypes.CODEC
                .encodeStart(levelRegistry.createSerializationContext(NbtOps.INSTANCE), this.numberFormat)
                .ifSuccess(p_313666_ -> compoundtag.put("format", p_313666_));
        }

        return compoundtag;
    }

    public static Score read(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        Score score = new Score();
        score.value = tag.getInt("Score");
        score.locked = tag.getBoolean("Locked");
        if (tag.contains("display", 8)) {
            score.display = Component.Serializer.fromJson(tag.getString("display"), levelRegistry);
        }

        if (tag.contains("format", 10)) {
            NumberFormatTypes.CODEC
                .parse(levelRegistry.createSerializationContext(NbtOps.INSTANCE), tag.get("format"))
                .ifSuccess(p_313664_ -> score.numberFormat = p_313664_);
        }

        return score;
    }
}
