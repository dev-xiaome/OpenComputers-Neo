package li.cil.oc.api;

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
    /** 与 li.cil.oc.OpenComputersNeo.MODID 保持一致（Java 侧不能引用 Scala 常量）。 */
    public static final String MOD_ID = "opencomputers_neo";

    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /**
     * The creative tab used by OpenComputers.
     * <p>
     * 原版为 {@code CreativeTabs} 常量，1.21.1 改为注册表延迟持有对象；
     * 通过 {@link #instance()} 获取实际标签页。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HOLDER = REGISTRY.register(
            "opencomputers",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.opencomputers_neo"))
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
