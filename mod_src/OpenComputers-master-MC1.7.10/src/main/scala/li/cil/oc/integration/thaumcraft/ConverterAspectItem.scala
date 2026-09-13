package li.cil.oc.integration.thaumcraft

import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack
import thaumcraft.api.aspects.AspectList

import java.util
import scala.collection.convert.WrapAsScala._

object ConverterAspectItem extends Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: ItemStack if stack.hasTagCompound =>
      stack.getItem.getClass.getName match {
        case "thaumcraft.common.items.wands.ItemWandCasting" =>
          try {
            stack.getItem.getClass.getMethod("getAllVis", classOf[ItemStack]).invoke(stack.getItem, stack) match {
              case aspects: AspectList =>
                if (aspects.size() > 0)
                  output += "aspects" -> aspects
                return
              case _ => return
            }
          } catch {
            case _: Throwable =>
          }
        case _ =>
      }

      val aspects = new AspectList()
      aspects.readFromNBT(stack.getTagCompound)
      if (aspects.size() > 0)
        output += "aspects" -> aspects
    case _ =>
  }
}
