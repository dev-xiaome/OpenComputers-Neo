package li.cil.oc.api;

import li.cil.oc.OpenComputersNeo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Allows access to the creative tab used by OpenComputers.
 */
public final class CreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OpenComputersNeo.MODID);

    /**
     * The creative tab used by OpenComputers.
     * <p>
     * 原版为 {@code CreativeTabs} 常量，1.21.1 改为注册表延迟持有对象；
     * 通过 {@link #instance()} 获取实际标签页。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HOLDER = REGISTRY.register(
            "opencomputers",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.open_computers_neo"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.COMMAND_BLOCK))
                    .build());

    public static CreativeModeTab instance() {
        return HOLDER.get();
    }

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }

    private CreativeTab() {
    }
}
