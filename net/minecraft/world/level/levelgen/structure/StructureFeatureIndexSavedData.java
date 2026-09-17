package net.minecraft.world.level.levelgen.structure;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class StructureFeatureIndexSavedData extends SavedData {
    private static final String TAG_REMAINING_INDEXES = "Remaining";
    private static final String TAG_All_INDEXES = "All";
    private final LongSet all;
    private final LongSet remaining;

    public static SavedData.Factory<StructureFeatureIndexSavedData> factory() {
        return new SavedData.Factory<>(
            StructureFeatureIndexSavedData::new, StructureFeatureIndexSavedData::load, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES
        );
    }

    private StructureFeatureIndexSavedData(LongSet all, LongSet remaining) {
        this.all = all;
        this.remaining = remaining;
    }

    public StructureFeatureIndexSavedData() {
        this(new LongOpenHashSet(), new LongOpenHashSet());
    }

    public static StructureFeatureIndexSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new StructureFeatureIndexSavedData(new LongOpenHashSet(tag.getLongArray("All")), new LongOpenHashSet(tag.getLongArray("Remaining")));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("All", this.all.toLongArray());
        tag.putLongArray("Remaining", this.remaining.toLongArray());
        return tag;
    }

    public void addIndex(long index) {
        this.all.add(index);
        this.remaining.add(index);
    }

    public boolean hasStartIndex(long index) {
        return this.all.contains(index);
    }

    public boolean hasUnhandledIndex(long index) {
        return this.remaining.contains(index);
    }

    public void removeIndex(long index) {
        this.remaining.remove(index);
    }

    public LongSet getAll() {
        return this.all;
    }
}
