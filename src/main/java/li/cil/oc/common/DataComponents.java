package li.cil.oc.common;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * OpenComputers 自定义数据组件。
 * <p>
 * 1.7.10 时代物品数据直接挂在 {@code ItemStack} 的 NBTTagCompound 上；
 * 1.21.1 之后改为数据组件（DataComponent）。这里注册一个统一的
 * {@code CompoundTag} 组件，用于承载原有 OC 的全部物品 NBT 数据，
 * 从而最大程度保留原代码的数据布局。
 */
public final class DataComponents {
    /** 与 li.cil.oc.OpenComputersNeo.MODID 保持一致（Java 侧不能引用 Scala 常量）。 */
    public static final String MOD_ID = "open_computers_neo";

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    /** 对应原版 {@code ItemStack.getTagCompound()} 的那一份数据。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> NBT =
            REGISTRY.register("nbt", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .cacheEncoding()
                    .build());

    private DataComponents() {
    }
}