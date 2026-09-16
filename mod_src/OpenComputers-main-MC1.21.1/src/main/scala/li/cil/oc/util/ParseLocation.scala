package li.cil.oc.util

import net.minecraft.resources.ResourceLocation

object ParseLocation {
  def unapply(value: String): Option[ResourceLocation] = {
    ResourceLocation.tryParse(value) match {
      case null => None
      case value => Some(value)
    }
  }
}
