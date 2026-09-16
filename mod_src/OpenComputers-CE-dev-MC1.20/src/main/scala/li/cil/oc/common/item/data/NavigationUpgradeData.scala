package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

class NavigationUpgradeData extends ItemData(Constants.ItemName.NavigationUpgrade) {
  def this(stack: ItemStack) = {
    this()
    loadData(stack)
  }

  var map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP)

  def mapData(level: Level): MapItemSavedData = {
    val data = MapItem.getSavedData(map, level)
    if (data == null) {
      throw new Exception("invalid map")
    }
    data
  }

  def getSize(level: Level) = {
    val info = mapData(level)
    128 * (1 << info.scale)
  }

  private final val DataTag = Settings.namespace + "data"
  private final val MapTag = Settings.namespace + "map"

  override def loadData(stack: ItemStack): Unit = {
    if (stack.hasTag) {
      loadData(stack.getTag.getCompound(DataTag))
    }
  }

  override def saveData(stack: ItemStack): Unit = {
    saveData(stack.getOrCreateTagElement(DataTag))
  }

  override def loadData(nbt: CompoundTag): Unit = {
    if (nbt.contains(MapTag)) {
      map = ItemStack.of(nbt.getCompound(MapTag))
    }
  }

  override def saveData(nbt: CompoundTag): Unit = {
    if (map != null) {
      nbt.setNewCompoundTag(MapTag, map.save)
    }
  }
}
