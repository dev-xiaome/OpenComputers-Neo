package li.cil.oc.common;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * OpenComputers 自定义数据组件。
 *
 * <p>OCCE(1.20.1 Forge) 时代物品数据直接挂在 {@code ItemStack} 的 {@code CompoundTag} 上，
 * NeoForge 1.21.1 已改为数据组件。为了最大程度保留 OCCE 的代码形态与数据布局，这里注册
 * 一个承载整棵 {@code CompoundTag} 的组件，把原来的「物品 NBT」原样装进去。
 *
 * <p>为什么不直接用原版的 {@code DataComponents.CUSTOM_DATA}：它取出来的是**副本**
 * （{@code CustomData#copyTag}），写回必须整份 {@code set} 回去；而 OC 的组件数据模型
 * （{@code api.driver.Item#dataTag}）依赖一棵**可变**的 NBT 树，用副本会静默丢写入。
 * 自定义组件里放的是同一个 {@code CompoundTag} 实例，因此可以就地修改。
 */
public final class DataComponents {
    /** 与 mod id 保持一致（Java 侧不能引用 Scala 常量）。 */
    public static final String MOD_ID = "opencomputers_neo";

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    /** 对应原版 {@code ItemStack#getTagCompound()} 的那一份数据。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> NBT =
            REGISTRY.register("nbt", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .cacheEncoding()
                    .build());

    private DataComponents() {
    }
}
