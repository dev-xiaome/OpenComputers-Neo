package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {
    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType(
        p_306063_ -> Component.translatableEscape("command.forkLimit", p_306063_)
    );
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String commandInput, ContextChain<T> command) {
        this.commandInput = commandInput;
        this.command = command;
    }

    protected void execute(T originalSource, List<T> sources, ExecutionContext<T> context, Frame frame, ChainModifiers chainModifiers) {
        ContextChain<T> contextchain = this.command;
        ChainModifiers chainmodifiers = chainModifiers;
        List<T> list = sources;
        if (contextchain.getStage() != Stage.EXECUTE) {
            context.profiler().push(() -> "prepare " + this.commandInput);

            try {
                for (int i = context.forkLimit(); contextchain.getStage() != Stage.EXECUTE; contextchain = contextchain.nextStage()) {
                    CommandContext<T> commandcontext = contextchain.getTopContext();
                    if (commandcontext.isForked()) {
                        chainmodifiers = chainmodifiers.setForked();
                    }

                    RedirectModifier<T> redirectmodifier = commandcontext.getRedirectModifier();
                    if (redirectmodifier instanceof CustomModifierExecutor<?>) {
                        CustomModifierExecutor<T> custommodifierexecutor = (CustomModifierExecutor<T>) redirectmodifier;
                        custommodifierexecutor.apply(originalSource, list, contextchain, chainmodifiers, ExecutionControl.create(context, frame));
                        return;
                    }

                    if (redirectmodifier != null) {
                        context.incrementCost();
                        boolean flag = chainmodifiers.isForked();
                        List<T> list1 = new ObjectArrayList<>();

                        for (T t : list) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(commandcontext, t, (p_309424_, p_309425_, p_309426_) -> {
                                }, flag);
                                if (list1.size() + collection.size() >= i) {
                                    originalSource.handleError(ERROR_FORK_LIMIT_REACHED.create(i), flag, context.tracer());
                                    return;
                                }

                                list1.addAll(collection);
                            } catch (CommandSyntaxException commandsyntaxexception) {
                                t.handleError(commandsyntaxexception, flag, context.tracer());
                                if (!flag) {
                                    return;
                                }
                            }
                        }

                        list = list1;
                    }
                }
            } finally {
                context.profiler().pop();
            }
        }

        if (list.isEmpty()) {
            if (chainmodifiers.isReturn()) {
                context.queueNext(new CommandQueueEntry<T>(frame, FallthroughTask.instance()));
            }
        } else {
            CommandContext<T> commandcontext1 = contextchain.getTopContext();
            com.mojang.brigadier.Command<T> command = commandcontext1.getCommand();
            if (command instanceof CustomCommandExecutor<?>) {
                CustomCommandExecutor<T> customcommandexecutor = (CustomCommandExecutor<T>) command;
                ExecutionControl<T> executioncontrol = ExecutionControl.create(context, frame);

                for (T t2 : list) {
                    customcommandexecutor.run(t2, contextchain, chainmodifiers, executioncontrol);
                }
            } else {
                if (chainmodifiers.isReturn()) {
                    T t1 = list.get(0);
                    t1 = t1.withCallback(CommandResultCallback.chain(t1.callback(), frame.returnValueConsumer()));
                    list = List.of(t1);
                }

                ExecuteCommand<T> executecommand = new ExecuteCommand<>(this.commandInput, chainmodifiers, commandcontext1);
                ContinuationTask.schedule(
                    context, frame, list, (p_309428_, p_309429_) -> new CommandQueueEntry<>(p_309428_, executecommand.bind(p_309429_))
                );
            }
        }
    }

    protected void traceCommandStart(ExecutionContext<T> executionContext, Frame frame) {
        TraceCallbacks tracecallbacks = executionContext.tracer();
        if (tracecallbacks != null) {
            tracecallbacks.onCommand(frame.depth(), this.commandInput);
        }
    }

    @Override
    public String toString() {
        return this.commandInput;
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String commandInput, ContextChain<T> command, ChainModifiers modifiers, T originalSource, List<T> sources) {
            super(commandInput, command);
            this.originalSource = originalSource;
            this.sources = sources;
            this.modifiers = modifiers;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.execute(this.originalSource, this.sources, context, frame, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final T source;

        public TopLevel(String commandInput, ContextChain<T> command, T source) {
            super(commandInput, command);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(this.source, List.of(this.source), context, frame, ChainModifiers.DEFAULT);
        }
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {
        public Unbound(String p_305863_, ContextChain<T> p_305842_) {
            super(p_305863_, p_305842_);
        }

        public void execute(T p_306259_, ExecutionContext<T> p_305944_, Frame p_309714_) {
            this.traceCommandStart(p_305944_, p_309714_);
            this.execute(p_306259_, List.of(p_306259_), p_305944_, p_309714_, ChainModifiers.DEFAULT);
        }
    }
}
