package net.minecraft.nbt.visitors;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.TagType;

public record FieldTree(int depth, Map<String, TagType<?>> selectedFields, Map<String, FieldTree> fieldsToRecurse) {
    private FieldTree(int p_202527_) {
        this(p_202527_, new HashMap<>(), new HashMap<>());
    }

    public static FieldTree createRoot() {
        return new FieldTree(1);
    }

    public void addEntry(FieldSelector selector) {
        if (this.depth <= selector.path().size()) {
            this.fieldsToRecurse.computeIfAbsent(selector.path().get(this.depth - 1), p_202534_ -> new FieldTree(this.depth + 1)).addEntry(selector);
        } else {
            this.selectedFields.put(selector.name(), selector.type());
        }
    }

    public boolean isSelected(TagType<?> type, String name) {
        return type.equals(this.selectedFields().get(name));
    }
}
