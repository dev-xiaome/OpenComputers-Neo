package net.minecraft.tags;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public class TagEntry {
    private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(
        p_215937_ -> p_215937_.group(
                    ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag),
                    Codec.BOOL.optionalFieldOf("required", Boolean.valueOf(true)).forGetter(p_215952_ -> p_215952_.required)
                )
                .apply(p_215937_, TagEntry::new)
    );
    public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC)
        .xmap(
            p_215935_ -> p_215935_.map(p_215933_ -> new TagEntry(p_215933_, true), p_215946_ -> (TagEntry)p_215946_),
            p_215931_ -> p_215931_.required ? Either.left(p_215931_.elementOrTag()) : Either.right(p_215931_)
        );
    private final ResourceLocation id;
    private final boolean tag;
    private final boolean required;

    private TagEntry(ResourceLocation id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }

    private TagEntry(ExtraCodecs.TagOrElementLocation tagOrElementLocation, boolean required) {
        this.id = tagOrElementLocation.id();
        this.tag = tagOrElementLocation.tag();
        this.required = required;
    }

    private ExtraCodecs.TagOrElementLocation elementOrTag() {
        return new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
    }

    /**
     * {@return a copy of this entry with the required flag set to the given parameter}
     *
     * @param required whether the new entry is required
     */
    TagEntry withRequired(boolean required) {
        return new TagEntry(this.id, this.tag, required);
    }

    public static TagEntry element(ResourceLocation elementLocation) {
        return new TagEntry(elementLocation, false, true);
    }

    public static TagEntry optionalElement(ResourceLocation elementLocation) {
        return new TagEntry(elementLocation, false, false);
    }

    public static TagEntry tag(ResourceLocation tagLocation) {
        return new TagEntry(tagLocation, true, true);
    }

    public static TagEntry optionalTag(ResourceLocation tagLocation) {
        return new TagEntry(tagLocation, true, false);
    }

    public <T> boolean build(TagEntry.Lookup<T> lookup, Consumer<T> consumer) {
        if (this.tag) {
            Collection<T> collection = lookup.tag(this.id);
            if (collection == null) {
                return !this.required;
            }

            collection.forEach(consumer);
        } else {
            T t = lookup.element(this.id);
            if (t == null) {
                return !this.required;
            }

            consumer.accept(t);
        }

        return true;
    }

    public void visitRequiredDependencies(Consumer<ResourceLocation> visitor) {
        if (this.tag && this.required) {
            visitor.accept(this.id);
        }
    }

    public void visitOptionalDependencies(Consumer<ResourceLocation> visitor) {
        if (this.tag && !this.required) {
            visitor.accept(this.id);
        }
    }

    public boolean verifyIfPresent(Predicate<ResourceLocation> elementPredicate, Predicate<ResourceLocation> tagPredicate) {
        return !this.required || (this.tag ? tagPredicate : elementPredicate).test(this.id);
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        if (this.tag) {
            stringbuilder.append('#');
        }

        stringbuilder.append(this.id);
        if (!this.required) {
            stringbuilder.append('?');
        }

        return stringbuilder.toString();
    }

    public ResourceLocation getId() {
        return id;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isTag() {
        return tag;
    }

    public interface Lookup<T> {
        @Nullable
        T element(ResourceLocation elementLocation);
        Collection<T> tag(ResourceLocation tagLocation);
    }
}
