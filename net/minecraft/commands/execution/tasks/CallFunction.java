package net.minecraft.commands.execution.tasks;

import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.InstantiatedFunction;

public class CallFunction<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {
    private final InstantiatedFunction<T> function;
    private final CommandResultCallback resultCallback;
    private final boolean returnParentFrame;

    public CallFunction(InstantiatedFunction<T> function, CommandResultCallback resultCallback, boolean returnParentFrame) {
        this.function = function;
        this.resultCallback = resultCallback;
        this.returnParentFrame = returnParentFrame;
    }

    public void execute(T source, ExecutionContext<T> executionContext, Frame p_frame) {
        executionContext.incrementCost();
        List<UnboundEntryAction<T>> list = this.function.entries();
        TraceCallbacks tracecallbacks = executionContext.tracer();
        if (tracecallbacks != null) {
            tracecallbacks.onCall(p_frame.depth(), this.function.id(), this.function.entries().size());
        }

        int i = p_frame.depth() + 1;
        Frame.FrameControl frame$framecontrol = this.returnParentFrame ? p_frame.frameControl() : executionContext.frameControlForDepth(i);
        Frame frame = new Frame(i, this.resultCallback, frame$framecontrol);
        ContinuationTask.schedule(executionContext, frame, list, (p_309431_, p_309432_) -> new CommandQueueEntry<>(p_309431_, p_309432_.bind(source)));
    }
}
