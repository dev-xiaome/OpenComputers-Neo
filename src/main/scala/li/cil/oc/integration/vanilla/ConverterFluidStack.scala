package li.cil.oc.integration.vanilla

import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.neoforge.fluids.FluidStack

import java.util

/**
 * `FluidStack` → Lua 表的转换器。
 *
 * 1.21.1 迁移要点：
 *  - `stack.getFluid.getID` → `BuiltInRegistries.FLUID.getId(stack.getFluid)`
 *  - `stack.amount` → `stack.getAmount`
 *  - `stack.tag`（`NBTTagCompound`）→ 数据组件，`FluidStack` 已无 `tag` 字段，
 *    这里用「组件表非空」近似判断，键名 `hasTag` 保持不变
 *  - `fluid.getName` → `FluidType#getDescriptionId`
 *  - `fluid.getLocalizedName(stack)` → `FluidStack#getDisplayName`
 */
object ConverterFluidStack extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: FluidStack if stack != null && !stack.isEmpty =>
      if (Settings.get.insertIdsInConverters) {
        output.put("id", Int.box(BuiltInRegistries.FLUID.getId(stack.getFluid)))
      }
      output.put("amount", Int.box(stack.getAmount))
      // TODO(port): 1.21.1 的 FluidStack 用数据组件取代了 NBT tag，没有等价的“整个 tag”概念，
      // 这里退化为“是否带有任何组件”。
      output.put("hasTag", Boolean.box(stack.getComponents.size > 0))
      val fluid = stack.getFluid
      if (fluid != null) {
        output.put("name", fluid.getFluidType.getDescriptionId)
        output.put("label", stack.getDisplayName.getString)
      }
    case _ => // 忽略其它类型与空堆叠。
  }
}
