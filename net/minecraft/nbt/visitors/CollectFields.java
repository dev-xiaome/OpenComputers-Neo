package net.minecraft.nbt.visitors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public class CollectFields extends CollectToTag {
    private int fieldsToGetCount;
    private final Set<TagType<?>> wantedTypes;
    private final Deque<FieldTree> stack = new ArrayDeque<>();

    public CollectFields(FieldSelector... selectors) {
        this.fieldsToGetCount = selectors.length;
        Builder<TagType<?>> builder = ImmutableSet.builder();
        FieldTree fieldtree = FieldTree.createRoot();

        for (FieldSelector fieldselector : selectors) {
            fieldtree.addEntry(fieldselector);
            builder.add(fieldselector.type());
        }

        this.stack.push(fieldtree);
        builder.add(CompoundTag.TYPE);
        this.wantedTypes = builder.build();
    }

    @Override
    public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        return type != CompoundTag.TYPE ? StreamTagVisitor.ValueResult.HALT : super.visitRootEntry(type);
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        FieldTree fieldtree = this.stack.element();
        if (this.depth() > fieldtree.depth()) {
            return super.visitEntry(type);
        } else if (this.fieldsToGetCount <= 0) {
            return StreamTagVisitor.EntryResult.HALT;
        } else {
            return !this.wantedTypes.contains(type) ? StreamTagVisitor.EntryResult.SKIP : super.visitEntry(type);
        }
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        FieldTree fieldtree = this.stack.element();
        if (this.depth() > fieldtree.depth()) {
            return super.visitEntry(type, id);
        } else if (fieldtree.selectedFields().remove(id, type)) {
            this.fieldsToGetCount--;
            return super.visitEntry(type, id);
        } else {
            if (type == CompoundTag.TYPE) {
                FieldTree fieldtree1 = fieldtree.fieldsToRecurse().get(id);
                if (fieldtree1 != null) {
                    this.stack.push(fieldtree1);
                    return super.visitEntry(type, id);
                }
            }

            return StreamTagVisitor.EntryResult.SKIP;
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        if (this.depth() == this.stack.element().depth()) {
            this.stack.pop();
        }

        return super.visitContainerEnd();
    }

    public int getMissingFieldCount() {
        return this.fieldsToGetCount;
    }
}
