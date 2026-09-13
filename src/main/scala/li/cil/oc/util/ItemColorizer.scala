package li.cil.oc.util

import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

/**
  * @author asie, Vexatos
  */
object ItemColorizer {
  /**
    * Return whether the specified armor ItemStack has a color.
    */
  def hasColor(stack: ItemStack): Boolean = stack.hasTagCompound && stack.getTagCompound.contains("display") && stack.getTagCompound.getCompound("display").contains("color")

  /**
    * Return the color for the specified armor ItemStack.
    */
  def getColor(stack: ItemStack): Int = {
    val tag = stack.getTagCompound
    if (tag != null) {
      val displayTag = tag.getCompound("display")
      if (displayTag == null) -1 else if (displayTag.contains("color")) displayTag.getInteger("color") else -1
    }
    else -1
  }

  def removeColor(stack: ItemStack): Unit = {
    val tag = stack.getTagCompound
    if (tag != null) {
      val displayTag = tag.getCompound("display")
      if (displayTag.contains("color")) displayTag.remove("color")
    }
  }

  def setColor(stack: ItemStack, color: Int): Unit = {
    var tag = stack.getTagCompound
    if (tag == null) {
      tag = new CompoundTag
      stack.put(tag)
    }
    val displayTag = tag.getCompound("display")
    if (!tag.contains("display")) {
      tag.put("display", displayTag)
    }
    displayTag.putInt("color", color)
  }
}
