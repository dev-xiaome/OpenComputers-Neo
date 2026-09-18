package li.cil.oc.integration.minecraft

import java.util
import li.cil.oc.api
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.neoforge.fluids.FluidStack
import net.minecraft.core.component.DataComponents

import scala.collection.convert.ImplicitConversionsToScala._

object ConverterFluidStack extends api.driver.Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case stack: FluidStack =>
        output += "amount" -> Int.box(stack.getAmount)
        output += "hasTag" -> Boolean.box(stack.has(DataComponents.CUSTOM_DATA))
        val fluid = stack.getFluid
        val registryName = BuiltInRegistries.FLUID.getKey(fluid).toString
        output += "name" -> registryName
        output += "label" -> fluid.getFluidType.getDescription(stack).getString
      case _ =>
    }
}
