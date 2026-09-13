package li.cil.oc.common.item.data

import com.google.common.base.Strings
import li.cil.oc.Constants
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 无人机数据（原 1.7.10 的 `DroneData`）。
 *
 * 1.21.1 迁移要点：显示名从 `display.Name` 读取，语义不变；
 * `Strings.isNullOrEmpty` 仍可用。
 */
class DroneData extends MicrocontrollerData(Constants.ItemName.Drone) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var name: String = ""

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    if (nbt.contains("display") && nbt.getCompound("display").contains("Name")) {
      name = nbt.getCompound("display").getString("Name")
    }
    if (Strings.isNullOrEmpty(name)) {
      name = RobotData.randomName
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    if (!Strings.isNullOrEmpty(name)) {
      if (!nbt.contains("display")) {
        nbt.put("display", new CompoundTag())
      }
      nbt.getCompound("display").putString("Name", name)
    }
  }
}
