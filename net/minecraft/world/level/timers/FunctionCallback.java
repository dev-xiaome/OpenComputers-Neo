package net.minecraft.world.level.timers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public class FunctionCallback implements TimerCallback<MinecraftServer> {
    final ResourceLocation functionId;

    public FunctionCallback(ResourceLocation functionId) {
        this.functionId = functionId;
    }

    public void handle(MinecraftServer obj, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager serverfunctionmanager = obj.getFunctions();
        serverfunctionmanager.get(this.functionId)
            .ifPresent(p_305770_ -> serverfunctionmanager.execute((CommandFunction<CommandSourceStack>)p_305770_, serverfunctionmanager.getGameLoopSender()));
    }

    public static class Serializer extends TimerCallback.Serializer<MinecraftServer, FunctionCallback> {
        public Serializer() {
            super(ResourceLocation.withDefaultNamespace("function"), FunctionCallback.class);
        }

        public void serialize(CompoundTag p_82182_, FunctionCallback p_82183_) {
            p_82182_.putString("Name", p_82183_.functionId.toString());
        }

        public FunctionCallback deserialize(CompoundTag p_82180_) {
            ResourceLocation resourcelocation = ResourceLocation.parse(p_82180_.getString("Name"));
            return new FunctionCallback(resourcelocation);
        }
    }
}
