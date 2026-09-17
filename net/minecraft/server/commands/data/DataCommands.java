package net.minecraft.server.commands.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DataCommands {
    private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.data.merge.failed"));
    private static final DynamicCommandExceptionType ERROR_GET_NOT_NUMBER = new DynamicCommandExceptionType(
        p_304320_ -> Component.translatableEscape("commands.data.get.invalid", p_304320_)
    );
    private static final DynamicCommandExceptionType ERROR_GET_NON_EXISTENT = new DynamicCommandExceptionType(
        p_304315_ -> Component.translatableEscape("commands.data.get.unknown", p_304315_)
    );
    private static final SimpleCommandExceptionType ERROR_MULTIPLE_TAGS = new SimpleCommandExceptionType(Component.translatable("commands.data.get.multiple"));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(
        p_304316_ -> Component.translatableEscape("commands.data.modify.expected_object", p_304316_)
    );
    private static final DynamicCommandExceptionType ERROR_EXPECTED_VALUE = new DynamicCommandExceptionType(
        p_304317_ -> Component.translatableEscape("commands.data.modify.expected_value", p_304317_)
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_SUBSTRING = new Dynamic2CommandExceptionType(
        (p_304318_, p_304319_) -> Component.translatableEscape("commands.data.modify.invalid_substring", p_304318_, p_304319_)
    );
    public static final List<Function<String, DataCommands.DataProvider>> ALL_PROVIDERS = ImmutableList.of(
        EntityDataAccessor.PROVIDER, BlockDataAccessor.PROVIDER, StorageDataAccessor.PROVIDER
    );
    public static final List<DataCommands.DataProvider> TARGET_PROVIDERS = ALL_PROVIDERS.stream()
        .map(p_139450_ -> p_139450_.apply("target"))
        .collect(ImmutableList.toImmutableList());
    public static final List<DataCommands.DataProvider> SOURCE_PROVIDERS = ALL_PROVIDERS.stream()
        .map(p_139410_ -> p_139410_.apply("source"))
        .collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("data").requires(p_139381_ -> p_139381_.hasPermission(2));

        for (DataCommands.DataProvider datacommands$dataprovider : TARGET_PROVIDERS) {
            literalargumentbuilder.then(
                    datacommands$dataprovider.wrap(
                        Commands.literal("merge"),
                        p_139471_ -> p_139471_.then(
                                Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(
                                        p_142857_ -> mergeData(
                                                p_142857_.getSource(),
                                                datacommands$dataprovider.access(p_142857_),
                                                CompoundTagArgument.getCompoundTag(p_142857_, "nbt")
                                            )
                                    )
                            )
                    )
                )
                .then(
                    datacommands$dataprovider.wrap(
                        Commands.literal("get"),
                        p_139453_ -> p_139453_.executes(p_142849_ -> getData(p_142849_.getSource(), datacommands$dataprovider.access(p_142849_)))
                                .then(
                                    Commands.argument("path", NbtPathArgument.nbtPath())
                                        .executes(
                                            p_142841_ -> getData(
                                                    p_142841_.getSource(),
                                                    datacommands$dataprovider.access(p_142841_),
                                                    NbtPathArgument.getPath(p_142841_, "path")
                                                )
                                        )
                                        .then(
                                            Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                .executes(
                                                    p_142833_ -> getNumeric(
                                                            p_142833_.getSource(),
                                                            datacommands$dataprovider.access(p_142833_),
                                                            NbtPathArgument.getPath(p_142833_, "path"),
                                                            DoubleArgumentType.getDouble(p_142833_, "scale")
                                                        )
                                                )
                                        )
                                )
                    )
                )
                .then(
                    datacommands$dataprovider.wrap(
                        Commands.literal("remove"),
                        p_139413_ -> p_139413_.then(
                                Commands.argument("path", NbtPathArgument.nbtPath())
                                    .executes(
                                        p_142820_ -> removeData(
                                                p_142820_.getSource(), datacommands$dataprovider.access(p_142820_), NbtPathArgument.getPath(p_142820_, "path")
                                            )
                                    )
                            )
                    )
                )
                .then(
                    decorateModification(
                        (p_139368_, p_139369_) -> p_139368_.then(
                                    Commands.literal("insert")
                                        .then(
                                            Commands.argument("index", IntegerArgumentType.integer())
                                                .then(
                                                    p_139369_.create(
                                                        (p_142859_, p_142860_, p_142861_, p_142862_) -> p_142861_.insert(
                                                                IntegerArgumentType.getInteger(p_142859_, "index"), p_142860_, p_142862_
                                                            )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("prepend")
                                        .then(p_139369_.create((p_142851_, p_142852_, p_142853_, p_142854_) -> p_142853_.insert(0, p_142852_, p_142854_)))
                                )
                                .then(
                                    Commands.literal("append")
                                        .then(p_139369_.create((p_142843_, p_142844_, p_142845_, p_142846_) -> p_142845_.insert(-1, p_142844_, p_142846_)))
                                )
                                .then(
                                    Commands.literal("set")
                                        .then(
                                            p_139369_.create(
                                                (p_142835_, p_142836_, p_142837_, p_142838_) -> p_142837_.set(p_142836_, Iterables.getLast(p_142838_))
                                            )
                                        )
                                )
                                .then(Commands.literal("merge").then(p_139369_.create((p_142822_, p_142823_, p_142824_, p_142825_) -> {
                                    CompoundTag compoundtag = new CompoundTag();

                                    for (Tag tag : p_142825_) {
                                        if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
                                            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                                        }

                                        if (!(tag instanceof CompoundTag compoundtag1)) {
                                            throw ERROR_EXPECTED_OBJECT.create(tag);
                                        }

                                        compoundtag.merge(compoundtag1);
                                    }

                                    Collection<Tag> collection = p_142824_.getOrCreate(p_142823_, CompoundTag::new);
                                    int i = 0;

                                    for (Tag tag1 : collection) {
                                        if (!(tag1 instanceof CompoundTag compoundtag2)) {
                                            throw ERROR_EXPECTED_OBJECT.create(tag1);
                                        }

                                        CompoundTag $$12 = compoundtag2.copy();
                                        compoundtag2.merge(compoundtag);
                                        i += $$12.equals(compoundtag2) ? 0 : 1;
                                    }

                                    return i;
                                })))
                    )
                );
        }

        dispatcher.register(literalargumentbuilder);
    }

    private static String getAsText(Tag tag) throws CommandSyntaxException {
        if (tag.getType().isValue()) {
            return tag.getAsString();
        } else {
            throw ERROR_EXPECTED_VALUE.create(tag);
        }
    }

    private static List<Tag> stringifyTagList(List<Tag> tagList, DataCommands.StringProcessor processor) throws CommandSyntaxException {
        List<Tag> list = new ArrayList<>(tagList.size());

        for (Tag tag : tagList) {
            String s = getAsText(tag);
            list.add(StringTag.valueOf(processor.process(s)));
        }

        return list;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> decorateModification(
        BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, DataCommands.DataManipulatorDecorator> decorator
    ) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("modify");

        for (DataCommands.DataProvider datacommands$dataprovider : TARGET_PROVIDERS) {
            datacommands$dataprovider.wrap(
                literalargumentbuilder,
                p_264816_ -> {
                    ArgumentBuilder<CommandSourceStack, ?> argumentbuilder = Commands.argument("targetPath", NbtPathArgument.nbtPath());

                    for (DataCommands.DataProvider datacommands$dataprovider1 : SOURCE_PROVIDERS) {
                        decorator.accept(
                            argumentbuilder,
                            p_142807_ -> datacommands$dataprovider1.wrap(
                                    Commands.literal("from"),
                                    p_142812_ -> p_142812_.executes(
                                                p_264829_ -> manipulateData(
                                                        p_264829_,
                                                        datacommands$dataprovider,
                                                        p_142807_,
                                                        getSingletonSource(p_264829_, datacommands$dataprovider1)
                                                    )
                                            )
                                            .then(
                                                Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                                    .executes(
                                                        p_264842_ -> manipulateData(
                                                                p_264842_,
                                                                datacommands$dataprovider,
                                                                p_142807_,
                                                                resolveSourcePath(p_264842_, datacommands$dataprovider1)
                                                            )
                                                    )
                                            )
                                )
                        );
                        decorator.accept(
                            argumentbuilder,
                            p_264836_ -> datacommands$dataprovider1.wrap(
                                    Commands.literal("string"),
                                    p_287357_ -> p_287357_.executes(
                                                p_288732_ -> manipulateData(
                                                        p_288732_,
                                                        datacommands$dataprovider,
                                                        p_264836_,
                                                        stringifyTagList(getSingletonSource(p_288732_, datacommands$dataprovider1), p_264813_ -> p_264813_)
                                                    )
                                            )
                                            .then(
                                                Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                                    .executes(
                                                        p_288737_ -> manipulateData(
                                                                p_288737_,
                                                                datacommands$dataprovider,
                                                                p_264836_,
                                                                stringifyTagList(
                                                                    resolveSourcePath(p_288737_, datacommands$dataprovider1), p_264821_ -> p_264821_
                                                                )
                                                            )
                                                    )
                                                    .then(
                                                        Commands.argument("start", IntegerArgumentType.integer())
                                                            .executes(
                                                                p_288753_ -> manipulateData(
                                                                        p_288753_,
                                                                        datacommands$dataprovider,
                                                                        p_264836_,
                                                                        stringifyTagList(
                                                                            resolveSourcePath(p_288753_, datacommands$dataprovider1),
                                                                            p_287353_ -> substring(
                                                                                    p_287353_, IntegerArgumentType.getInteger(p_288753_, "start")
                                                                                )
                                                                        )
                                                                    )
                                                            )
                                                            .then(
                                                                Commands.argument("end", IntegerArgumentType.integer())
                                                                    .executes(
                                                                        p_288749_ -> manipulateData(
                                                                                p_288749_,
                                                                                datacommands$dataprovider,
                                                                                p_264836_,
                                                                                stringifyTagList(
                                                                                    resolveSourcePath(p_288749_, datacommands$dataprovider1),
                                                                                    p_287359_ -> substring(
                                                                                            p_287359_,
                                                                                            IntegerArgumentType.getInteger(p_288749_, "start"),
                                                                                            IntegerArgumentType.getInteger(p_288749_, "end")
                                                                                        )
                                                                                )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                )
                        );
                    }

                    decorator.accept(
                        argumentbuilder,
                        p_142799_ -> Commands.literal("value").then(Commands.argument("value", NbtTagArgument.nbtTag()).executes(p_142803_ -> {
                                List<Tag> list = Collections.singletonList(NbtTagArgument.getNbtTag(p_142803_, "value"));
                                return manipulateData(p_142803_, datacommands$dataprovider, p_142799_, list);
                            }))
                    );
                    return p_264816_.then(argumentbuilder);
                }
            );
        }

        return literalargumentbuilder;
    }

    private static String validatedSubstring(String source, int start, int end) throws CommandSyntaxException {
        if (start >= 0 && end <= source.length() && start <= end) {
            return source.substring(start, end);
        } else {
            throw ERROR_INVALID_SUBSTRING.create(start, end);
        }
    }

    private static String substring(String source, int start, int end) throws CommandSyntaxException {
        int i = source.length();
        int j = getOffset(start, i);
        int k = getOffset(end, i);
        return validatedSubstring(source, j, k);
    }

    private static String substring(String source, int start) throws CommandSyntaxException {
        int i = source.length();
        return validatedSubstring(source, getOffset(start, i), i);
    }

    private static int getOffset(int index, int length) {
        return index >= 0 ? index : length + index;
    }

    private static List<Tag> getSingletonSource(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataaccessor = dataProvider.access(context);
        return Collections.singletonList(dataaccessor.getData());
    }

    private static List<Tag> resolveSourcePath(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataaccessor = dataProvider.access(context);
        NbtPathArgument.NbtPath nbtpathargument$nbtpath = NbtPathArgument.getPath(context, "sourcePath");
        return nbtpathargument$nbtpath.get(dataaccessor.getData());
    }

    private static int manipulateData(
        CommandContext<CommandSourceStack> source, DataCommands.DataProvider dataProvider, DataCommands.DataManipulator dataManipulator, List<Tag> tags
    ) throws CommandSyntaxException {
        DataAccessor dataaccessor = dataProvider.access(source);
        NbtPathArgument.NbtPath nbtpathargument$nbtpath = NbtPathArgument.getPath(source, "targetPath");
        CompoundTag compoundtag = dataaccessor.getData();
        int i = dataManipulator.modify(source, compoundtag, nbtpathargument$nbtpath, tags);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            dataaccessor.setData(compoundtag);
            source.getSource().sendSuccess(() -> dataaccessor.getModifiedSuccess(), true);
            return i;
        }
    }

    /**
     * Removes the tag at the end of the path.
     *
     * @return 1
     */
    private static int removeData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        CompoundTag compoundtag = accessor.getData();
        int i = path.remove(compoundtag);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            accessor.setData(compoundtag);
            source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
            return i;
        }
    }

    public static Tag getSingleTag(NbtPathArgument.NbtPath path, DataAccessor accessor) throws CommandSyntaxException {
        Collection<Tag> collection = path.get(accessor.getData());
        Iterator<Tag> iterator = collection.iterator();
        Tag tag = iterator.next();
        if (iterator.hasNext()) {
            throw ERROR_MULTIPLE_TAGS.create();
        } else {
            return tag;
        }
    }

    /**
     * Gets a value, which can be of any known NBT type.
     *
     * @return The value associated with the element: length for strings, size for lists and compounds, and numeric value for primitives.
     */
    private static int getData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, accessor);
        int i;
        if (tag instanceof NumericTag) {
            i = Mth.floor(((NumericTag)tag).getAsDouble());
        } else if (tag instanceof CollectionTag) {
            i = ((CollectionTag)tag).size();
        } else if (tag instanceof CompoundTag) {
            i = ((CompoundTag)tag).size();
        } else {
            if (!(tag instanceof StringTag)) {
                throw ERROR_GET_NON_EXISTENT.create(path.toString());
            }

            i = tag.getAsString().length();
        }

        source.sendSuccess(() -> accessor.getPrintSuccess(tag), false);
        return i;
    }

    /**
     * Gets a single numeric element, scaled by the given amount.
     *
     * @return The element's value, scaled by scale.
     */
    private static int getNumeric(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, double scale) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, accessor);
        if (!(tag instanceof NumericTag)) {
            throw ERROR_GET_NOT_NUMBER.create(path.toString());
        } else {
            int i = Mth.floor(((NumericTag)tag).getAsDouble() * scale);
            source.sendSuccess(() -> accessor.getPrintSuccess(path, scale, i), false);
            return i;
        }
    }

    /**
     * Gets all NBT on the object, and applies syntax highlighting.
     *
     * @return 1
     */
    private static int getData(CommandSourceStack source, DataAccessor accessor) throws CommandSyntaxException {
        CompoundTag compoundtag = accessor.getData();
        source.sendSuccess(() -> accessor.getPrintSuccess(compoundtag), false);
        return 1;
    }

    /**
     * Merges the given NBT into the targeted object's NBT.
     *
     * @return 1
     */
    private static int mergeData(CommandSourceStack source, DataAccessor accessor, CompoundTag nbt) throws CommandSyntaxException {
        CompoundTag compoundtag = accessor.getData();
        if (NbtPathArgument.NbtPath.isTooDeep(nbt, 0)) {
            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
        } else {
            CompoundTag compoundtag1 = compoundtag.copy().merge(nbt);
            if (compoundtag.equals(compoundtag1)) {
                throw ERROR_MERGE_UNCHANGED.create();
            } else {
                accessor.setData(compoundtag1);
                source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
                return 1;
            }
        }
    }

    @FunctionalInterface
    interface DataManipulator {
        int modify(CommandContext<CommandSourceStack> context, CompoundTag nbt, NbtPathArgument.NbtPath nbtPath, List<Tag> tags) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DataManipulatorDecorator {
        ArgumentBuilder<CommandSourceStack, ?> create(DataCommands.DataManipulator dataManipulator);
    }

    public interface DataProvider {
        /**
         * Creates an accessor based on the command context. This should only refer to arguments registered in {@link createArgument}.
         */
        DataAccessor access(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        /**
         * Creates an argument used for accessing data related to this type of thing, including a literal to distinguish from other types.
         */
        ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> action
        );
    }

    @FunctionalInterface
    interface StringProcessor {
        String process(String input) throws CommandSyntaxException;
    }
}
