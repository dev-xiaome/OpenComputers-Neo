package net.minecraft.commands.execution;

import net.minecraft.resources.ResourceLocation;

public interface TraceCallbacks extends AutoCloseable {
    void onCommand(int depth, String command);

    void onReturn(int depth, String command, int returnValue);

    void onError(String errorMessage);

    void onCall(int depth, ResourceLocation function, int commands);

    @Override
    void close();
}
