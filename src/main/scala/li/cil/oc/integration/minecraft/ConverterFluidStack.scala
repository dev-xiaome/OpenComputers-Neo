package li.cil.oc.integration.minecraft

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util
import li.cil.oc.api
import net.neoforged.neoforge.registries.ForgeRegistries

import scala.collection.convert.ImplicitConversionsToScala._

object ConverterFluidStack extends api.driver.Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case stack: net.neoforged.neoforge.fluids.FluidStack =>
        output += "amount" -> Int.box(stack.getAmount)
        output += "hasTag" -> Boolean.box(stack.hasTag)
        val fluid = stack.getFluid
        val registryName = ForgeRegistries.FLUIDS.getKey(fluid).toString
        output += "name" -> registryName
        output += "label" -> fluid.getFluidType.getDescription(stack).getString
      case _ =>
    }
}
