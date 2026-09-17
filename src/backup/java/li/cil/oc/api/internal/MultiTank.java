package li.cil.oc.api.internal;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Implemented by objects with multiple internal tanks.
 * <br>
 * This is specifically for containers where the side does not matter when
 * accessing the internal tanks, only the index of the tank; unlike with the
 * {@link IFluidHandler} interface.
 */
public interface MultiTank {
    /**
     * The number of tanks currently installed.
     */
    int tankCount();

    /**
     * Get the installed fluid tank with the specified index.
     * <br>
     * 1.21.1 的 NeoForge 流体 API 中不存在 1.7.10 的 {@code IFluidTank}，
     * 单个“罐”统一用 {@link IFluidHandler} 表示（通常其内部只有一个槽位）。
     *
     * @param index the index of the tank to get.
     * @return the tank with the specified index.
     */
    IFluidHandler getFluidTank(int index);
}
