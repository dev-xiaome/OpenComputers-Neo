package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType(
        p_304087_ -> Component.translatableEscape("arguments.nbtpath.nothing_found", p_304087_)
    );
    static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType(
        p_304088_ -> Component.translatableEscape("commands.data.modify.expected_list", p_304088_)
    );
    static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType(
        p_304089_ -> Component.translatableEscape("commands.data.modify.invalid_index", p_304089_)
    );
    private static final char INDEX_MATCH_START = '[';
    private static final char INDEX_MATCH_END = ']';
    private static final char KEY_MATCH_START = '{';
    private static final char KEY_MATCH_END = '}';
    private static final char QUOTED_KEY_START = '"';
    private static final char SINGLE_QUOTED_KEY_START = '\'';

    public static NbtPathArgument nbtPath() {
        return new NbtPathArgument();
    }

    public static NbtPathArgument.NbtPath getPath(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, NbtPathArgument.NbtPath.class);
    }

    public NbtPathArgument.NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<NbtPathArgument.Node> list = Lists.newArrayList();
        int i = reader.getCursor();
        Object2IntMap<NbtPathArgument.Node> object2intmap = new Object2IntOpenHashMap<>();
        boolean flag = true;

        while (reader.canRead() && reader.peek() != ' ') {
            NbtPathArgument.Node nbtpathargument$node = parseNode(reader, flag);
            list.add(nbtpathargument$node);
            object2intmap.put(nbtpathargument$node, reader.getCursor() - i);
            flag = false;
            if (reader.canRead()) {
                char c0 = reader.peek();
                if (c0 != ' ' && c0 != '[' && c0 != '{') {
                    reader.expect('.');
                }
            }
        }

        return new NbtPathArgument.NbtPath(reader.getString().substring(i, reader.getCursor()), list.toArray(new NbtPathArgument.Node[0]), object2intmap);
    }

    private static NbtPathArgument.Node parseNode(StringReader reader, boolean first) throws CommandSyntaxException {
        return (NbtPathArgument.Node)(switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                int i = reader.peek();
                if (i == 123) {
                    CompoundTag compoundtag1 = new TagParser(reader).readStruct();
                    reader.expect(']');
                    yield new NbtPathArgument.MatchElementNode(compoundtag1);
                } else if (i == 93) {
                    reader.skip();
                    yield NbtPathArgument.AllElementsNode.INSTANCE;
                } else {
                    int j = reader.readInt();
                    reader.expect(']');
                    yield new NbtPathArgument.IndexedElementNode(j);
                }
            }
            case '{' -> {
                if (!first) {
                    throw ERROR_INVALID_NODE.createWithContext(reader);
                }

                CompoundTag compoundtag = new TagParser(reader).readStruct();
                yield new NbtPathArgument.MatchRootObjectNode(compoundtag);
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        });
    }

    private static NbtPathArgument.Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '{') {
            CompoundTag compoundtag = new TagParser(reader).readStruct();
            return new NbtPathArgument.MatchObjectNode(name, compoundtag);
        } else {
            return new NbtPathArgument.CompoundChildNode(name);
        }
    }

    /**
     * Reads a tag name until the next special character. Throws if the result would be a 0-length string. Does not handle quoted tag names.
     */
    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }

        if (reader.getCursor() == i) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        } else {
            return reader.getString().substring(i, reader.getCursor());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /**
     * @return {@code true} if the given character is normal for a tag name; otherwise {@code false} if it has special meaning for paths.
     */
    private static boolean isAllowedInUnquotedName(char ch) {
        return ch != ' '
            && ch != '"'
            && ch != '\''
            && ch != '['
            && ch != ']'
            && ch != '.'
            && ch != '{'
            && ch != '}';
    }

    static Predicate<Tag> createTagPredicate(CompoundTag tag) {
        return p_99507_ -> NbtUtils.compareNbt(tag, p_99507_, true);
    }

    static class AllElementsNode implements NbtPathArgument.Node {
        public static final NbtPathArgument.AllElementsNode INSTANCE = new NbtPathArgument.AllElementsNode();

        private AllElementsNode() {
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag) {
                tags.addAll((CollectionTag)tag);
            }
        }

        @Override
        public void getOrCreateTag(Tag p_tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (p_tag instanceof CollectionTag<?> collectiontag) {
                if (collectiontag.isEmpty()) {
                    Tag tag = supplier.get();
                    if (collectiontag.addTag(0, tag)) {
                        tags.add(tag);
                    }
                } else {
                    tags.addAll((Collection<? extends Tag>)collectiontag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag p_tag, Supplier<Tag> supplier) {
            if (!(p_tag instanceof CollectionTag<?> collectiontag)) {
                return 0;
            } else {
                int i = collectiontag.size();
                if (i == 0) {
                    collectiontag.addTag(0, supplier.get());
                    return 1;
                } else {
                    Tag tag = supplier.get();
                    int j = i - (int)collectiontag.stream().filter(tag::equals).count();
                    if (j == 0) {
                        return 0;
                    } else {
                        collectiontag.clear();
                        if (!collectiontag.addTag(0, tag)) {
                            return 0;
                        } else {
                            for (int k = 1; k < i; k++) {
                                collectiontag.addTag(k, supplier.get());
                            }

                            return j;
                        }
                    }
                }
            }
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag<?> collectiontag) {
                int i = collectiontag.size();
                if (i > 0) {
                    collectiontag.clear();
                    return i;
                }
            }

            return 0;
        }
    }

    static class CompoundChildNode implements NbtPathArgument.Node {
        private final String name;

        public CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(Tag p_tag, List<Tag> tags) {
            if (p_tag instanceof CompoundTag) {
                Tag tag = ((CompoundTag)p_tag).get(this.name);
                if (tag != null) {
                    tags.add(tag);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag p_tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (p_tag instanceof CompoundTag compoundtag) {
                Tag tag;
                if (compoundtag.contains(this.name)) {
                    tag = compoundtag.get(this.name);
                } else {
                    tag = supplier.get();
                    compoundtag.put(this.name, tag);
                }

                tags.add(tag);
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag p_tag, Supplier<Tag> supplier) {
            if (p_tag instanceof CompoundTag compoundtag) {
                Tag tag = supplier.get();
                Tag tag1 = compoundtag.put(this.name, tag);
                if (!tag.equals(tag1)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CompoundTag compoundtag && compoundtag.contains(this.name)) {
                compoundtag.remove(this.name);
                return 1;
            }

            return 0;
        }
    }

    static class IndexedElementNode implements NbtPathArgument.Node {
        private final int index;

        public IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag<?> collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    tags.add(collectiontag.get(j));
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag p_tag, Supplier<Tag> supplier) {
            if (p_tag instanceof CollectionTag<?> collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    Tag tag = collectiontag.get(j);
                    Tag tag1 = supplier.get();
                    if (!tag1.equals(tag) && collectiontag.setTag(j, tag1)) {
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag<?> collectiontag) {
                int i = collectiontag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    collectiontag.remove(j);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchElementNode implements NbtPathArgument.Node {
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchElementNode(CompoundTag pattern) {
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof ListTag listtag) {
                listtag.stream().filter(this.predicate).forEach(tags::add);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            MutableBoolean mutableboolean = new MutableBoolean();
            if (tag instanceof ListTag listtag) {
                listtag.stream().filter(this.predicate).forEach(p_99571_ -> {
                    tags.add(p_99571_);
                    mutableboolean.setTrue();
                });
                if (mutableboolean.isFalse()) {
                    CompoundTag compoundtag = this.pattern.copy();
                    listtag.add(compoundtag);
                    tags.add(compoundtag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag p_tag, Supplier<Tag> supplier) {
            int i = 0;
            if (p_tag instanceof ListTag listtag) {
                int j = listtag.size();
                if (j == 0) {
                    listtag.add(supplier.get());
                    i++;
                } else {
                    for (int k = 0; k < j; k++) {
                        Tag tag = listtag.get(k);
                        if (this.predicate.test(tag)) {
                            Tag tag1 = supplier.get();
                            if (!tag1.equals(tag) && listtag.setTag(k, tag1)) {
                                i++;
                            }
                        }
                    }
                }
            }

            return i;
        }

        @Override
        public int removeTag(Tag tag) {
            int i = 0;
            if (tag instanceof ListTag listtag) {
                for (int j = listtag.size() - 1; j >= 0; j--) {
                    if (this.predicate.test(listtag.get(j))) {
                        listtag.remove(j);
                        i++;
                    }
                }
            }

            return i;
        }
    }

    static class MatchObjectNode implements NbtPathArgument.Node {
        private final String name;
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchObjectNode(String name, CompoundTag pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag p_tag, List<Tag> tags) {
            if (p_tag instanceof CompoundTag) {
                Tag tag = ((CompoundTag)p_tag).get(this.name);
                if (this.predicate.test(tag)) {
                    tags.add(tag);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag p_tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (p_tag instanceof CompoundTag compoundtag) {
                Tag tag = compoundtag.get(this.name);
                if (tag == null) {
                    Tag compoundtag1 = this.pattern.copy();
                    compoundtag.put(this.name, compoundtag1);
                    tags.add(compoundtag1);
                } else if (this.predicate.test(tag)) {
                    tags.add(tag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag p_tag, Supplier<Tag> supplier) {
            if (p_tag instanceof CompoundTag compoundtag) {
                Tag tag = compoundtag.get(this.name);
                if (this.predicate.test(tag)) {
                    Tag tag1 = supplier.get();
                    if (!tag1.equals(tag)) {
                        compoundtag.put(this.name, tag1);
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag p_tag) {
            if (p_tag instanceof CompoundTag compoundtag) {
                Tag tag = compoundtag.get(this.name);
                if (this.predicate.test(tag)) {
                    compoundtag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchRootObjectNode implements NbtPathArgument.Node {
        private final Predicate<Tag> predicate;

        public MatchRootObjectNode(CompoundTag tag) {
            this.predicate = NbtPathArgument.createTagPredicate(tag);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag && this.predicate.test(tag)) {
                tags.add(tag);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            return 0;
        }
    }

    public static class NbtPath {
        private final String original;
        private final Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition;
        private final NbtPathArgument.Node[] nodes;
        public static final Codec<NbtPathArgument.NbtPath> CODEC = Codec.STRING.comapFlatMap(p_335166_ -> {
            try {
                NbtPathArgument.NbtPath nbtpathargument$nbtpath = new NbtPathArgument().parse(new StringReader(p_335166_));
                return DataResult.success(nbtpathargument$nbtpath);
            } catch (CommandSyntaxException commandsyntaxexception) {
                return DataResult.error(() -> "Failed to parse path " + p_335166_ + ": " + commandsyntaxexception.getMessage());
            }
        }, NbtPathArgument.NbtPath::asString);

        public static NbtPathArgument.NbtPath of(String path) throws CommandSyntaxException {
            return new NbtPathArgument().parse(new StringReader(path));
        }

        public NbtPath(String original, NbtPathArgument.Node[] nodes, Object2IntMap<NbtPathArgument.Node> nodeToOriginPosition) {
            this.original = original;
            this.nodes = nodes;
            this.nodeToOriginalPosition = nodeToOriginPosition;
        }

        public List<Tag> get(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node nbtpathargument$node : this.nodes) {
                list = nbtpathargument$node.get(list);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(nbtpathargument$node);
                }
            }

            return list;
        }

        public int countMatching(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node nbtpathargument$node : this.nodes) {
                list = nbtpathargument$node.get(list);
                if (list.isEmpty()) {
                    return 0;
                }
            }

            return list.size();
        }

        private List<Tag> getOrCreateParents(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                NbtPathArgument.Node nbtpathargument$node = this.nodes[i];
                int j = i + 1;
                list = nbtpathargument$node.getOrCreate(list, this.nodes[j]::createPreferredParentTag);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(nbtpathargument$node);
                }
            }

            return list;
        }

        public List<Tag> getOrCreate(Tag tag, Supplier<Tag> supplier) throws CommandSyntaxException {
            List<Tag> list = this.getOrCreateParents(tag);
            NbtPathArgument.Node nbtpathargument$node = this.nodes[this.nodes.length - 1];
            return nbtpathargument$node.getOrCreate(list, supplier);
        }

        private static int apply(List<Tag> tags, Function<Tag, Integer> function) {
            return tags.stream().map(function).reduce(0, (p_99633_, p_99634_) -> p_99633_ + p_99634_);
        }

        public static boolean isTooDeep(Tag p_tag, int currentDepth) {
            if (currentDepth >= 512) {
                return true;
            } else {
                if (p_tag instanceof CompoundTag compoundtag) {
                    for (String s : compoundtag.getAllKeys()) {
                        Tag tag = compoundtag.get(s);
                        if (tag != null && isTooDeep(tag, currentDepth + 1)) {
                            return true;
                        }
                    }
                } else if (p_tag instanceof ListTag) {
                    for (Tag tag1 : (ListTag)p_tag) {
                        if (isTooDeep(tag1, currentDepth + 1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        public int set(Tag p_tag, Tag other) throws CommandSyntaxException {
            if (isTooDeep(other, this.estimatePathDepth())) {
                throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            } else {
                Tag tag = other.copy();
                List<Tag> list = this.getOrCreateParents(p_tag);
                if (list.isEmpty()) {
                    return 0;
                } else {
                    NbtPathArgument.Node nbtpathargument$node = this.nodes[this.nodes.length - 1];
                    MutableBoolean mutableboolean = new MutableBoolean(false);
                    return apply(list, p_263259_ -> nbtpathargument$node.setTag(p_263259_, () -> {
                            if (mutableboolean.isFalse()) {
                                mutableboolean.setTrue();
                                return tag;
                            } else {
                                return tag.copy();
                            }
                        }));
                }
            }
        }

        private int estimatePathDepth() {
            return this.nodes.length;
        }

        public int insert(int index, CompoundTag rootTag, List<Tag> tagsToInsert) throws CommandSyntaxException {
            List<Tag> list = new ArrayList<>(tagsToInsert.size());

            for (Tag tag : tagsToInsert) {
                Tag tag1 = tag.copy();
                list.add(tag1);
                if (isTooDeep(tag1, this.estimatePathDepth())) {
                    throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                }
            }

            Collection<Tag> collection = this.getOrCreate(rootTag, ListTag::new);
            int j = 0;
            boolean flag1 = false;

            for (Tag tag2 : collection) {
                if (!(tag2 instanceof CollectionTag<?> collectiontag)) {
                    throw NbtPathArgument.ERROR_EXPECTED_LIST.create(tag2);
                }

                boolean flag = false;
                int i = index < 0 ? collectiontag.size() + index + 1 : index;

                for (Tag tag3 : list) {
                    try {
                        if (collectiontag.addTag(i, flag1 ? tag3.copy() : tag3)) {
                            i++;
                            flag = true;
                        }
                    } catch (IndexOutOfBoundsException indexoutofboundsexception) {
                        throw NbtPathArgument.ERROR_INVALID_INDEX.create(i);
                    }
                }

                flag1 = true;
                j += flag ? 1 : 0;
            }

            return j;
        }

        public int remove(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                list = this.nodes[i].get(list);
            }

            NbtPathArgument.Node nbtpathargument$node = this.nodes[this.nodes.length - 1];
            return apply(list, nbtpathargument$node::removeTag);
        }

        private CommandSyntaxException createNotFoundException(NbtPathArgument.Node node) {
            int i = this.nodeToOriginalPosition.getInt(node);
            return NbtPathArgument.ERROR_NOTHING_FOUND.create(this.original.substring(0, i));
        }

        @Override
        public String toString() {
            return this.original;
        }

        public String asString() {
            return this.original;
        }
    }

    interface Node {
        void getTag(Tag tag, List<Tag> tags);

        void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags);

        Tag createPreferredParentTag();

        int setTag(Tag tag, Supplier<Tag> supplier);

        int removeTag(Tag tag);

        default List<Tag> get(List<Tag> tags) {
            return this.collect(tags, this::getTag);
        }

        default List<Tag> getOrCreate(List<Tag> tags, Supplier<Tag> supplier) {
            return this.collect(tags, (p_99663_, p_99664_) -> this.getOrCreateTag(p_99663_, supplier, p_99664_));
        }

        default List<Tag> collect(List<Tag> tags, BiConsumer<Tag, List<Tag>> consumer) {
            List<Tag> list = Lists.newArrayList();

            for (Tag tag : tags) {
                consumer.accept(tag, list);
            }

            return list;
        }
    }
}
