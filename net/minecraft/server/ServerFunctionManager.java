package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerFunctionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TICK_FUNCTION_TAG = ResourceLocation.withDefaultNamespace("tick");
    private static final ResourceLocation LOAD_FUNCTION_TAG = ResourceLocation.withDefaultNamespace("load");
    private final MinecraftServer server;
    private List<CommandFunction<CommandSourceStack>> ticking = ImmutableList.of();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer server, ServerFunctionLibrary library) {
        this.server = server;
        this.library = library;
        this.postReload(library);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.getCommands().getDispatcher();
    }

    public void tick() {
        if (this.server.tickRateManager().runsNormally()) {
            if (this.postReload) {
                this.postReload = false;
                Collection<CommandFunction<CommandSourceStack>> collection = this.library.getTag(LOAD_FUNCTION_TAG);
                this.executeTagFunctions(collection, LOAD_FUNCTION_TAG);
            }

            this.executeTagFunctions(this.ticking, TICK_FUNCTION_TAG);
        }
    }

    private void executeTagFunctions(Collection<CommandFunction<CommandSourceStack>> functionObjects, ResourceLocation identifier) {
        this.server.getProfiler().push(identifier::toString);

        for (CommandFunction<CommandSourceStack> commandfunction : functionObjects) {
            this.execute(commandfunction, this.getGameLoopSender());
        }

        this.server.getProfiler().pop();
    }

    public void execute(CommandFunction<CommandSourceStack> function, CommandSourceStack source) {
        ProfilerFiller profilerfiller = this.server.getProfiler();
        profilerfiller.push(() -> "function " + function.id());

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedfunction = function.instantiate(null, this.getDispatcher());
            Commands.executeCommandInContext(
                source, p_309439_ -> ExecutionContext.queueInitialFunctionCall(p_309439_, instantiatedfunction, source, CommandResultCallback.EMPTY)
            );
        } catch (FunctionInstantiationException functioninstantiationexception) {
        } catch (Exception exception) {
            LOGGER.warn("Failed to execute function {}", function.id(), exception);
        } finally {
            profilerfiller.pop();
        }
    }

    public void replaceLibrary(ServerFunctionLibrary reloader) {
        this.library = reloader;
        this.postReload(reloader);
    }

    private void postReload(ServerFunctionLibrary reloader) {
        this.ticking = ImmutableList.copyOf(reloader.getTag(TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    public Optional<CommandFunction<CommandSourceStack>> get(ResourceLocation functionIdentifier) {
        return this.library.getFunction(functionIdentifier);
    }

    public Collection<CommandFunction<CommandSourceStack>> getTag(ResourceLocation functionTagIdentifier) {
        return this.library.getTag(functionTagIdentifier);
    }

    public Iterable<ResourceLocation> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<ResourceLocation> getTagNames() {
        return this.library.getAvailableTags();
    }
}
