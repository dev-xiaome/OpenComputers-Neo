package li.cil.oc.integration.minecraft

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util
import li.cil.oc.api
import net.minecraft.core.registries.BuiltInRegistries

import scala.collection.convert.ImplicitConversionsToScala._

object ConverterFluidStack extends api.driver.Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case stack: net.neoforged.neoforge.fluids.FluidStack =>
        output += "amount" -> Int.box(stack.getAmount)
        // 1.21.1 的 FluidStack 没有 hasTag：流体附加数据已改为数据组件（DataComponentPatch），
        // 因此「有 tag」等价于「组件 patch 非空」。
        output += "hasTag" -> Boolean.box(!stack.isComponentsPatchEmpty)
        val fluid = stack.getFluid
        val registryName = BuiltInRegistries.FLUID.getKey(fluid).toString
        output += "name" -> registryName
        output += "label" -> fluid.getFluidType.getDescription(stack).getString
      case _ =>
    }
}
