package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.{Items, ItemStack, MapItem}
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.neoforged.neoforge.common.MutableDataComponentHolder

class NavigationUpgradeData extends ItemData(Constants.ItemName.NavigationUpgrade) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  var map = new ItemStack(Items.FILLED_MAP)

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

  override def loadData(holder: DataComponentHolder): Unit = {
    map = holder.getComponent(OCComponents.SOURCE_MAP_ITEM).map(_.mutableCopy()).orNull
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    if (map != null) {
      holder.setComponent(OCComponents.SOURCE_MAP_ITEM, ImmutableItemStack.copyOf(map))
    }
  }
}
