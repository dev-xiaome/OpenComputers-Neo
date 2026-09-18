package li.cil.oc.integration.minecraft

import li.cil.oc.api
import li.cil.oc.util.ExtendedArguments.TankProperties
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem

import java.util
import scala.collection.convert.ImplicitConversionsToScala._

object ConverterFluidContainerItem extends api.driver.Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]): Unit =
    value match {
      case stack: ItemStack =>
        Option(stack.getCapability(Capabilities.FluidHandler.ITEM)) match {
          case Some(fc: IFluidHandlerItem) =>
            output += "capacity" -> Int.box((0 until fc.getTanks).map(fc.getTankCapacity).sum)
            if (fc.getTanks > 1) {
              output += "fluid" -> (0 until fc.getTanks).map(i => {
                val tank = new util.HashMap[AnyRef, AnyRef]()
                tank += "capacity" -> Int.box(fc.getTankCapacity(i))
                val fluid = fc.getFluidInTank(i)
                if (fluid != null) {
                  ConverterFluidStack.convert(fluid, tank)
                } else {
                  tank += "amount" -> Int.box(0)
                }
                tank
              })
            } else {
              output += "fluid" -> fc.getFluidInTank(0)
            }
          case _ =>
        }
      case _ =>
    }
}
