package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level

class NavigationUpgradeData extends ItemData(Constants.ItemName.NavigationUpgrade) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var map = new ItemStack(net.minecraft.init.Items.filled_map)

  def mapData(world: Level) = try map.getItem.asInstanceOf[ItemMap].getMapData(map, world) catch {
    case _: Throwable => throw new Exception("invalid map")
  }

  def getSize(world: Level) = {
    val info = mapData(world)
    128 * (1 << info.scale)
  }

  override def load(stack: ItemStack): Unit = {
    if (stack.hasTagCompound) {
      load(stack.getTagCompound.getCompound(Settings.namespace + "data"))
    }
  }

  override def save(stack: ItemStack): Unit = {
    if (!stack.hasTagCompound) {
      stack.put(new CompoundTag())
    }
    save(stack.getCompound(Settings.namespace + "data"))
  }

  override def load(nbt: CompoundTag): Unit = {
    if (nbt.contains(Settings.namespace + "map")) {
      map = ItemStack.loadItemStackFromNBT(nbt.getCompound(Settings.namespace + "map"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    if (map != null) {
      nbt.setNewCompoundTag(Settings.namespace + "map", map.writeToNBT)
    }
  }
}
