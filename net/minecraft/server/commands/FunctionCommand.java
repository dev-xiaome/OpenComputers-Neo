package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

public class FunctionCommand {
    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType(
        p_304240_ -> Component.translatableEscape("commands.function.error.argument_not_compound", p_304240_)
    );
    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType(
        p_305708_ -> Component.translatableEscape("commands.function.scheduled.no_functions", p_305708_)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (p_305709_, p_305710_) -> Component.translatableEscape("commands.function.instantiationFailure", p_305709_, p_305710_)
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (p_137719_, p_137720_) -> {
        ServerFunctionManager serverfunctionmanager = p_137719_.getSource().getServer().getFunctions();
        SharedSuggestionProvider.suggestResource(serverfunctionmanager.getTagNames(), p_137720_, "#");
        return SharedSuggestionProvider.suggestResource(serverfunctionmanager.getFunctionNames(), p_137720_);
    };
    static final FunctionCommand.Callbacks<CommandSourceStack> FULL_CONTEXT_CALLBACKS = new FunctionCommand.Callbacks<CommandSourceStack>() {
        public void signalResult(CommandSourceStack p_305828_, ResourceLocation p_306288_, int p_306112_) {
            p_305828_.sendSuccess(() -> Component.translatable("commands.function.result", Component.translationArg(p_306288_), p_306112_), true);
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("with");

        for (DataCommands.DataProvider datacommands$dataprovider : DataCommands.SOURCE_PROVIDERS) {
            datacommands$dataprovider.wrap(literalargumentbuilder, p_305702_ -> p_305702_.executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306295_) throws CommandSyntaxException {
                        return datacommands$dataprovider.access(p_306295_).getData();
                    }
                }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306208_) throws CommandSyntaxException {
                        return FunctionCommand.getArgumentTag(NbtPathArgument.getPath(p_306208_, "path"), datacommands$dataprovider.access(p_306208_));
                    }
                })));
        }

        dispatcher.register(
            Commands.literal("function")
                .requires(p_137722_ -> p_137722_.hasPermission(2))
                .then(
                    Commands.argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Nullable
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306232_) {
                            return null;
                        }
                    }).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> p_305935_) {
                            return CompoundTagArgument.getCompoundTag(p_305935_, "arguments");
                        }
                    })).then(literalargumentbuilder)
                )
        );
    }

    static CompoundTag getArgumentTag(NbtPathArgument.NbtPath nbtPath, DataAccessor dataAccessor) throws CommandSyntaxException {
        Tag tag = DataCommands.getSingleTag(nbtPath, dataAccessor);
        if (tag instanceof CompoundTag) {
            return (CompoundTag)tag;
        } else {
            throw ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }

    public static CommandSourceStack modifySenderForExecution(CommandSourceStack source) {
        return source.withSuppressedOutput().withMaximumPermission(2);
    }

    public static <T extends ExecutionCommandSource<T>> void queueFunctions(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> executionControl,
        FunctionCommand.Callbacks<T> callbacks,
        ChainModifiers chainModifiers
    ) throws CommandSyntaxException {
        if (chainModifiers.isReturn()) {
            queueFunctionsAsReturn(functions, arguments, originalSource, source, executionControl, callbacks);
        } else {
            queueFunctionsNoReturn(functions, arguments, originalSource, source, executionControl, callbacks);
        }
    }

    private static <T extends ExecutionCommandSource<T>> void instantiateAndQueueFunctions(
        @Nullable CompoundTag arguments,
        ExecutionControl<T> executionControl,
        CommandDispatcher<T> dispatcher,
        T source,
        CommandFunction<T> function,
        ResourceLocation functionId,
        CommandResultCallback resultCallback,
        boolean returnParentFrame
    ) throws CommandSyntaxException {
        try {
            InstantiatedFunction<T> instantiatedfunction = function.instantiate(arguments, dispatcher);
            executionControl.queueNext(new CallFunction<>(instantiatedfunction, resultCallback, returnParentFrame).bind(source));
        } catch (FunctionInstantiationException functioninstantiationexception) {
            throw ERROR_FUNCTION_INSTANTATION_FAILURE.create(functionId, functioninstantiationexception.messageComponent());
        }
    }

    private static <T extends ExecutionCommandSource<T>> CommandResultCallback decorateOutputIfNeeded(
        T source, FunctionCommand.Callbacks<T> callbacks, ResourceLocation function, CommandResultCallback resultCallback
    ) {
        return source.isSilent() ? resultCallback : (p_315913_, p_315914_) -> {
            callbacks.signalResult(source, function, p_315914_);
            resultCallback.onResult(p_315913_, p_315914_);
        };
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsAsReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> exectutionControl,
        FunctionCommand.Callbacks<T> callbacks
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = originalSource.dispatcher();
        T t = source.clearCallbacks();
        CommandResultCallback commandresultcallback = CommandResultCallback.chain(originalSource.callback(), exectutionControl.currentFrame().returnValueConsumer());

        for (CommandFunction<T> commandfunction : functions) {
            ResourceLocation resourcelocation = commandfunction.id();
            CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(originalSource, callbacks, resourcelocation, commandresultcallback);
            instantiateAndQueueFunctions(arguments, exectutionControl, commanddispatcher, t, commandfunction, resourcelocation, commandresultcallback1, true);
        }

        exectutionControl.queueNext(FallthroughTask.instance());
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsNoReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> executionControl,
        FunctionCommand.Callbacks<T> callbacks
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = originalSource.dispatcher();
        T t = source.clearCallbacks();
        CommandResultCallback commandresultcallback = originalSource.callback();
        if (!functions.isEmpty()) {
            if (functions.size() == 1) {
                CommandFunction<T> commandfunction = functions.iterator().next();
                ResourceLocation resourcelocation = commandfunction.id();
                CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(originalSource, callbacks, resourcelocation, commandresultcallback);
                instantiateAndQueueFunctions(arguments, executionControl, commanddispatcher, t, commandfunction, resourcelocation, commandresultcallback1, false);
            } else if (commandresultcallback == CommandResultCallback.EMPTY) {
                for (CommandFunction<T> commandfunction1 : functions) {
                    ResourceLocation resourcelocation2 = commandfunction1.id();
                    CommandResultCallback commandresultcallback2 = decorateOutputIfNeeded(originalSource, callbacks, resourcelocation2, commandresultcallback);
                    instantiateAndQueueFunctions(arguments, executionControl, commanddispatcher, t, commandfunction1, resourcelocation2, commandresultcallback2, false);
                }
            } else {
                class Accumulator {
                    boolean anyResult;
                    int sum;

                    public void add(int p_309590_) {
                        this.anyResult = true;
                        this.sum += p_309590_;
                    }
                }

                Accumulator functioncommand$1accumulator = new Accumulator();
                CommandResultCallback commandresultcallback4 = (p_309467_, p_309468_) -> functioncommand$1accumulator.add(p_309468_);

                for (CommandFunction<T> commandfunction2 : functions) {
                    ResourceLocation resourcelocation1 = commandfunction2.id();
                    CommandResultCallback commandresultcallback3 = decorateOutputIfNeeded(originalSource, callbacks, resourcelocation1, commandresultcallback4);
                    instantiateAndQueueFunctions(arguments, executionControl, commanddispatcher, t, commandfunction2, resourcelocation1, commandresultcallback3, false);
                }

                executionControl.queueNext((p_309471_, p_309472_) -> {
                    if (functioncommand$1accumulator.anyResult) {
                        commandresultcallback.onSuccess(functioncommand$1accumulator.sum);
                    }
                });
            }
        }
    }

    public interface Callbacks<T> {
        void signalResult(T source, ResourceLocation function, int commands);
    }

    abstract static class FunctionCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Nullable
        protected abstract CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        public void runGuarded(
            CommandSourceStack source, ContextChain<CommandSourceStack> contextChain, ChainModifiers chainModifiers, ExecutionControl<CommandSourceStack> executionControl
        ) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> commandcontext = contextChain.getTopContext().copyFor(source);
            Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> pair = FunctionArgument.getFunctionCollection(commandcontext, "name");
            Collection<CommandFunction<CommandSourceStack>> collection = pair.getSecond();
            if (collection.isEmpty()) {
                throw FunctionCommand.ERROR_NO_FUNCTIONS.create(Component.translationArg(pair.getFirst()));
            } else {
                CompoundTag compoundtag = this.arguments(commandcontext);
                CommandSourceStack commandsourcestack = FunctionCommand.modifySenderForExecution(source);
                if (collection.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable("commands.function.scheduled.single", Component.translationArg(collection.iterator().next().id())), true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.function.scheduled.multiple",
                                ComponentUtils.formatList(collection.stream().map(CommandFunction::id).toList(), Component::translationArg)
                            ),
                        true
                    );
                }

                FunctionCommand.queueFunctions(
                    collection, compoundtag, source, commandsourcestack, executionControl, FunctionCommand.FULL_CONTEXT_CALLBACKS, chainModifiers
                );
            }
        }
    }
}
