package li.cil.oc.integration.vanilla

import li.cil.oc.api
import li.cil.oc.util.FluidUtils

import java.util

/**
 * 流体槽信息 → Lua 表的转换器。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 的 `net.minecraftforge.fluids.FluidTankInfo` 已随
 *    `IFluidHandler#getTankInfo` 一起移除，本项目用 [[FluidUtils.TankInfo]]
 *    （`fluid` + `capacity`）承载等价信息，因此转换目标类型相应改为 `TankInfo`。
 */
object ConverterFluidTankInfo extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case tankInfo: FluidUtils.TankInfo =>
      output.put("capacity", Int.box(tankInfo.capacity))
      if (tankInfo.fluid != null && !tankInfo.fluid.isEmpty) {
        ConverterFluidStack.convert(tankInfo.fluid, output)
      }
      else output.put("amount", Int.box(0))
    case _ => // 忽略其它类型。
  }
}
