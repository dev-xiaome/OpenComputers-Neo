package li.cil.oc.common.item.data

import com.google.common.base.Strings
import li.cil.oc.Constants
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

class DroneData extends MicrocontrollerData(Constants.ItemName.Drone) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var name = ""

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
