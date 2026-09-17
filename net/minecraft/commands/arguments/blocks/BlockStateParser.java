package net.minecraft.commands.arguments.blocks;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockStateParser {
    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(
        Component.translatable("argument.block.tag.disallowed")
    );
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType(
        p_304118_ -> Component.translatableEscape("argument.block.id.invalid", p_304118_)
    );
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY = new Dynamic2CommandExceptionType(
        (p_304119_, p_304120_) -> Component.translatableEscape("argument.block.property.unknown", p_304119_, p_304120_)
    );
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY = new Dynamic2CommandExceptionType(
        (p_304121_, p_304122_) -> Component.translatableEscape("argument.block.property.duplicate", p_304122_, p_304121_)
    );
    public static final Dynamic3CommandExceptionType ERROR_INVALID_VALUE = new Dynamic3CommandExceptionType(
        (p_304124_, p_304125_, p_304126_) -> Component.translatableEscape("argument.block.property.invalid", p_304124_, p_304126_, p_304125_)
    );
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE = new Dynamic2CommandExceptionType(
        (p_304127_, p_304128_) -> Component.translatableEscape("argument.block.property.novalue", p_304127_, p_304128_)
    );
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES = new SimpleCommandExceptionType(
        Component.translatable("argument.block.property.unclosed")
    );
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        p_304123_ -> Component.translatableEscape("arguments.block.tag.unknown", p_304123_)
    );
    private static final char SYNTAX_START_PROPERTIES = '[';
    private static final char SYNTAX_START_NBT = '{';
    private static final char SYNTAX_END_PROPERTIES = ']';
    private static final char SYNTAX_EQUALS = '=';
    private static final char SYNTAX_PROPERTY_SEPARATOR = ',';
    private static final char SYNTAX_TAG = '#';
    private static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    private final HolderLookup<Block> blocks;
    private final StringReader reader;
    private final boolean forTesting;
    private final boolean allowNbt;
    private final Map<Property<?>, Comparable<?>> properties = Maps.newHashMap();
    private final Map<String, String> vagueProperties = Maps.newHashMap();
    private ResourceLocation id = ResourceLocation.withDefaultNamespace("");
    @Nullable
    private StateDefinition<Block, BlockState> definition;
    @Nullable
    private BlockState state;
    @Nullable
    private CompoundTag nbt;
    @Nullable
    private HolderSet<Block> tag;
    private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions = SUGGEST_NOTHING;

    private BlockStateParser(HolderLookup<Block> blocks, StringReader reader, boolean forTesting, boolean allowNbt) {
        this.blocks = blocks;
        this.reader = reader;
        this.forTesting = forTesting;
        this.allowNbt = allowNbt;
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> lookup, String input, boolean allowNbt) throws CommandSyntaxException {
        return parseForBlock(lookup, new StringReader(input), allowNbt);
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> lookup, StringReader reader, boolean allowNbt) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            BlockStateParser blockstateparser = new BlockStateParser(lookup, reader, false, allowNbt);
            blockstateparser.parse();
            return new BlockStateParser.BlockResult(blockstateparser.state, blockstateparser.properties, blockstateparser.nbt);
        } catch (CommandSyntaxException commandsyntaxexception) {
            reader.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(
        HolderLookup<Block> lookup, String input, boolean allowNbt
    ) throws CommandSyntaxException {
        return parseForTesting(lookup, new StringReader(input), allowNbt);
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(
        HolderLookup<Block> lookup, StringReader reader, boolean allowNbt
    ) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            BlockStateParser blockstateparser = new BlockStateParser(lookup, reader, true, allowNbt);
            blockstateparser.parse();
            return blockstateparser.tag != null
                ? Either.right(new BlockStateParser.TagResult(blockstateparser.tag, blockstateparser.vagueProperties, blockstateparser.nbt))
                : Either.left(new BlockStateParser.BlockResult(blockstateparser.state, blockstateparser.properties, blockstateparser.nbt));
        } catch (CommandSyntaxException commandsyntaxexception) {
            reader.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public static CompletableFuture<Suggestions> fillSuggestions(
        HolderLookup<Block> lookup, SuggestionsBuilder builder, boolean forTesting, boolean allowNbt
    ) {
        StringReader stringreader = new StringReader(builder.getInput());
        stringreader.setCursor(builder.getStart());
        BlockStateParser blockstateparser = new BlockStateParser(lookup, stringreader, forTesting, allowNbt);

        try {
            blockstateparser.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
        }

        return blockstateparser.suggestions.apply(builder.createOffset(stringreader.getCursor()));
    }

    private void parse() throws CommandSyntaxException {
        if (this.forTesting) {
            this.suggestions = this::suggestBlockIdOrTag;
        } else {
            this.suggestions = this::suggestItem;
        }

        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.readTag();
            this.suggestions = this::suggestOpenVaguePropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readVagueProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        } else {
            this.readBlock();
            this.suggestions = this::suggestOpenPropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        }

        if (this.allowNbt && this.reader.canRead() && this.reader.peek() == '{') {
            this.suggestions = SUGGEST_NOTHING;
            this.readNbt();
        }
    }

    private CompletableFuture<Suggestions> suggestPropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestPropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestVaguePropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestPropertyName(SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Property<?> property : this.state.getProperties()) {
            if (!this.properties.containsKey(property) && property.getName().startsWith(s)) {
                builder.suggest(property.getName() + "=");
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyName(SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (this.tag != null) {
            for (Holder<Block> holder : this.tag) {
                for (Property<?> property : holder.value().getStateDefinition().getProperties()) {
                    if (!this.vagueProperties.containsKey(property.getName()) && property.getName().startsWith(s)) {
                        builder.suggest(property.getName() + "=");
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.hasBlockEntity()) {
            builder.suggest(String.valueOf('{'));
        }

        return builder.buildFuture();
    }

    private boolean hasBlockEntity() {
        if (this.state != null) {
            return this.state.hasBlockEntity();
        } else {
            if (this.tag != null) {
                for (Holder<Block> holder : this.tag) {
                    if (holder.value().defaultBlockState().hasBlockEntity()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf('='));
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestNextPropertyOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        if (builder.getRemaining().isEmpty() && this.properties.size() < this.state.getProperties().size()) {
            builder.suggest(String.valueOf(','));
        }

        return builder.buildFuture();
    }

    private static <T extends Comparable<T>> SuggestionsBuilder addSuggestions(SuggestionsBuilder builder, Property<T> property) {
        for (T t : property.getPossibleValues()) {
            if (t instanceof Integer integer) {
                builder.suggest(integer);
            } else {
                builder.suggest(property.getName(t));
            }
        }

        return builder;
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyValue(SuggestionsBuilder builder, String propertyName) {
        boolean flag = false;
        if (this.tag != null) {
            for (Holder<Block> holder : this.tag) {
                Block block = holder.value();
                Property<?> property = block.getStateDefinition().getProperty(propertyName);
                if (property != null) {
                    addSuggestions(builder, property);
                }

                if (!flag) {
                    for (Property<?> property1 : block.getStateDefinition().getProperties()) {
                        if (!this.vagueProperties.containsKey(property1.getName())) {
                            flag = true;
                            break;
                        }
                    }
                }
            }
        }

        if (flag) {
            builder.suggest(String.valueOf(','));
        }

        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenVaguePropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.tag != null) {
            boolean flag = false;
            boolean flag1 = false;

            for (Holder<Block> holder : this.tag) {
                Block block = holder.value();
                flag |= !block.getStateDefinition().getProperties().isEmpty();
                flag1 |= block.defaultBlockState().hasBlockEntity();
                if (flag && flag1) {
                    break;
                }
            }

            if (flag) {
                builder.suggest(String.valueOf('['));
            }

            if (flag1) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenPropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            if (!this.definition.getProperties().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            if (this.state.hasBlockEntity()) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTag(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listTagIds().map(TagKey::location), builder, String.valueOf('#'));
    }

    private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listElementIds().map(ResourceKey::location), builder);
    }

    private CompletableFuture<Suggestions> suggestBlockIdOrTag(SuggestionsBuilder builder) {
        this.suggestTag(builder);
        this.suggestItem(builder);
        return builder.buildFuture();
    }

    private void readBlock() throws CommandSyntaxException {
        int i = this.reader.getCursor();
        this.id = ResourceLocation.read(this.reader);
        Block block = this.blocks.get(ResourceKey.create(Registries.BLOCK, this.id)).orElseThrow(() -> {
            this.reader.setCursor(i);
            return ERROR_UNKNOWN_BLOCK.createWithContext(this.reader, this.id.toString());
        }).value();
        this.definition = block.getStateDefinition();
        this.state = block.defaultBlockState();
    }

    private void readTag() throws CommandSyntaxException {
        if (!this.forTesting) {
            throw ERROR_NO_TAGS_ALLOWED.createWithContext(this.reader);
        } else {
            int i = this.reader.getCursor();
            this.reader.expect('#');
            this.suggestions = this::suggestTag;
            ResourceLocation resourcelocation = ResourceLocation.read(this.reader);
            this.tag = this.blocks.get(TagKey.create(Registries.BLOCK, resourcelocation)).orElseThrow(() -> {
                this.reader.setCursor(i);
                return ERROR_UNKNOWN_TAG.createWithContext(this.reader, resourcelocation.toString());
            });
        }
    }

    private void readProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestPropertyNameOrEnd;
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != ']') {
            this.reader.skipWhitespace();
            int i = this.reader.getCursor();
            String s = this.reader.readString();
            Property<?> property = this.definition.getProperty(s);
            if (property == null) {
                this.reader.setCursor(i);
                throw ERROR_UNKNOWN_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
            }

            if (this.properties.containsKey(property)) {
                this.reader.setCursor(i);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
            }

            this.reader.skipWhitespace();
            this.suggestions = this::suggestEquals;
            if (!this.reader.canRead() || this.reader.peek() != '=') {
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
            }

            this.reader.skip();
            this.reader.skipWhitespace();
            this.suggestions = p_234690_ -> addSuggestions(p_234690_, property).buildFuture();
            int j = this.reader.getCursor();
            this.setValue(property, this.reader.readString(), j);
            this.suggestions = this::suggestNextPropertyOrEnd;
            this.reader.skipWhitespace();
            if (this.reader.canRead()) {
                if (this.reader.peek() != ',') {
                    if (this.reader.peek() != ']') {
                        throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                    }
                    break;
                }

                this.reader.skip();
                this.suggestions = this::suggestPropertyName;
            }
        }

        if (this.reader.canRead()) {
            this.reader.skip();
        } else {
            throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readVagueProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestVaguePropertyNameOrEnd;
        int i = -1;
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != ']') {
            this.reader.skipWhitespace();
            int j = this.reader.getCursor();
            String s = this.reader.readString();
            if (this.vagueProperties.containsKey(s)) {
                this.reader.setCursor(j);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
            }

            this.reader.skipWhitespace();
            if (!this.reader.canRead() || this.reader.peek() != '=') {
                this.reader.setCursor(j);
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
            }

            this.reader.skip();
            this.reader.skipWhitespace();
            this.suggestions = p_234712_ -> this.suggestVaguePropertyValue(p_234712_, s);
            i = this.reader.getCursor();
            String s1 = this.reader.readString();
            this.vagueProperties.put(s, s1);
            this.reader.skipWhitespace();
            if (this.reader.canRead()) {
                i = -1;
                if (this.reader.peek() != ',') {
                    if (this.reader.peek() != ']') {
                        throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                    }
                    break;
                }

                this.reader.skip();
                this.suggestions = this::suggestVaguePropertyName;
            }
        }

        if (this.reader.canRead()) {
            this.reader.skip();
        } else {
            if (i >= 0) {
                this.reader.setCursor(i);
            }

            throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readNbt() throws CommandSyntaxException {
        this.nbt = new TagParser(this.reader).readStruct();
    }

    private <T extends Comparable<T>> void setValue(Property<T> property, String value, int valuePosition) throws CommandSyntaxException {
        Optional<T> optional = property.getValue(value);
        if (optional.isPresent()) {
            this.state = this.state.setValue(property, optional.get());
            this.properties.put(property, optional.get());
        } else {
            this.reader.setCursor(valuePosition);
            throw ERROR_INVALID_VALUE.createWithContext(this.reader, this.id.toString(), property.getName(), value);
        }
    }

    public static String serialize(BlockState state) {
        StringBuilder stringbuilder = new StringBuilder(state.getBlockHolder().unwrapKey().map(p_234682_ -> p_234682_.location().toString()).orElse("air"));
        if (!state.getProperties().isEmpty()) {
            stringbuilder.append('[');
            boolean flag = false;

            for (Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (flag) {
                    stringbuilder.append(',');
                }

                appendProperty(stringbuilder, entry.getKey(), entry.getValue());
                flag = true;
            }

            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    private static <T extends Comparable<T>> void appendProperty(StringBuilder builder, Property<T> property, Comparable<?> value) {
        builder.append(property.getName());
        builder.append('=');
        builder.append(property.getName((T)value));
    }

    public static record BlockResult(BlockState blockState, Map<Property<?>, Comparable<?>> properties, @Nullable CompoundTag nbt) {
    }

    public static record TagResult(HolderSet<Block> tag, Map<String, String> vagueProperties, @Nullable CompoundTag nbt) {
    }
}
