package li.cil.oc.common.item.data

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.util.ItemUtils
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.{DataComponentHolder, DataComponents}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.MutableDataComponentHolder

class DroneData extends MicrocontrollerData(Constants.ItemName.Drone) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  var name = ""

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)
    name = holder.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString
    if (Strings.isNullOrEmpty(name)) {
      val tag = holder.get(DataComponents.CUSTOM_DATA)
      if (tag != null) {
        for (oldName <- ItemUtils.getDisplayName(tag.copyTag())) {
          name = oldName
          return
        }
      }
      name = RobotData.randomName
    }
  }
  
  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)
    if (!Strings.isNullOrEmpty(name)) {
      holder.set(DataComponents.CUSTOM_NAME, Component.literal(name))
    }
  }
}
