package li.cil.oc.integration.vanilla

import li.cil.oc.api
import li.cil.oc.server.driver.Registry
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities

import java.util

/**
 * `IFluidContainerItem` → Lua 表的转换器。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 的 `IFluidContainerItem` 接口已不存在，物品侧统一改为 NeoForge 能力
 *    `Capabilities.FluidHandler.ITEM`（`IFluidHandlerItem`）
 *  - `getCapacity(stack)` → 各槽位 `getTankCapacity(i)` 之和
 *  - `getFluid(stack)` → `getFluidInTank(0)`
 */
object ConverterFluidContainerItem extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: ItemStack if stack != null && !stack.isEmpty =>
      val handler = Capabilities.FluidHandler.ITEM.getCapability(stack, null)
      if (handler != null) {
        var capacity = 0
        var tank = 0
        while (tank < handler.getTanks) {
          capacity += handler.getTankCapacity(tank)
          tank += 1
        }
        output.put("capacity", Int.box(capacity))

        val fluidStack = if (handler.getTanks > 0) handler.getFluidInTank(0) else null
        if (fluidStack != null && !fluidStack.isEmpty) {
          val fluidData = Registry.convert(Array[AnyRef](fluidStack))
          if (fluidData != null && fluidData.nonEmpty && fluidData(0) != null) {
            output.put("fluid", fluidData(0))
          }
        }
        if (!output.containsKey("fluid")) {
          val fluidMap = new util.HashMap[AnyRef, AnyRef]()
          fluidMap.put("amount", Int.box(0))
          output.put("fluid", fluidMap)
        }
      }
    case _ => // 忽略其它类型与空堆叠。
  }
}
