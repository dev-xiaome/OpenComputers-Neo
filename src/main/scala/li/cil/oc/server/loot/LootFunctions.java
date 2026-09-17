package li.cil.oc.server.loot;

import li.cil.oc.OpenComputers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class LootFunctions {
    public static final ResourceLocation DYN_ITEM_DATA = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "item_data");
    public static final ResourceLocation DYN_VOLATILE_CONTENTS = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "volatile_contents");

    private static final DeferredRegister<LootItemFunctionType> DR =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, OpenComputers.ID());

    public static final DeferredHolder<LootItemFunctionType, LootItemFunctionType> SET_COLOR =
            DR.register("set_color", () -> new LootItemFunctionType(new SetColor.Serializer()));

    public static final DeferredHolder<LootItemFunctionType, LootItemFunctionType> COPY_COLOR =
            DR.register("copy_color", () -> new LootItemFunctionType(new CopyColor.Serializer()));

    public static void init(IEventBus bus) {
        DR.register(bus);
    }

    private LootFunctions() {}
}