package net.minecraft.commands.execution;

@FunctionalInterface
public interface UnboundEntryAction<T> {
    void execute(T source, ExecutionContext<T> executionContext, Frame frame);

    default EntryAction<T> bind(T source) {
        return (p_309422_, p_309423_) -> this.execute(source, p_309422_, p_309423_);
    }
}
