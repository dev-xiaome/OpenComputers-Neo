package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class TagBuilder implements net.neoforged.neoforge.common.extensions.ITagBuilderExtension {
    // FORGE: Remove entries are used for datagen.
    private final List<TagEntry> removeEntries = new ArrayList<>();
    public java.util.stream.Stream<TagEntry> getRemoveEntries() { return this.removeEntries.stream(); }
    // FORGE: Add an entry to be removed from this tag in datagen.
    public TagBuilder remove(final TagEntry entry) {
        this.removeEntries.add(entry);
        return this;
    }
    // FORGE: is this tag set to replace or not?
    private boolean replace = false;
    private final List<TagEntry> entries = new ArrayList<>();

    public static TagBuilder create() {
        return new TagBuilder();
    }

    public List<TagEntry> build() {
        return List.copyOf(this.entries);
    }

    public TagBuilder add(TagEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public TagBuilder addElement(ResourceLocation elementLocation) {
        return this.add(TagEntry.element(elementLocation));
    }

    public TagBuilder addOptionalElement(ResourceLocation elementLocation) {
        return this.add(TagEntry.optionalElement(elementLocation));
    }

    public TagBuilder addTag(ResourceLocation tagLocation) {
        return this.add(TagEntry.tag(tagLocation));
    }

    public TagBuilder addOptionalTag(ResourceLocation tagLocation) {
        return this.add(TagEntry.optionalTag(tagLocation));
    }

    // FORGE: Set the replace property of this tag.
    public TagBuilder replace(boolean value) {
        this.replace = value;
        return this;
    }

    // FORGE: Shorthand version of replace(true)
    public TagBuilder replace() {
        return replace(true);
    }

    // FORGE: Is this tag set to replace or not?
    public boolean isReplace() {
        return this.replace;
    }
}
