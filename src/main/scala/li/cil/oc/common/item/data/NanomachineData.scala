package li.cil.oc.common.item.data

import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.{Constants, Settings}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

class NanomachineData extends ItemData(Constants.ItemName.Nanomachines) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  def this(controller: ControllerImpl) = {
    this()
    uuid = controller.uuid
    val nbt = new CompoundTag()
    controller.configuration.save(nbt, forItem = true)
    configuration = Option(nbt)
  }

  var uuid = ""
  var configuration: Option[CompoundTag] = None

  override def load(nbt: CompoundTag): Unit = {
    uuid = nbt.getString(Settings.namespace + "uuid")
    if (nbt.contains(Settings.namespace + "configuration")) {
      configuration = Option(nbt.getCompound(Settings.namespace + "configuration"))
    }
    else {
      configuration = None
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putString(Settings.namespace + "uuid", uuid)
    configuration.foreach(nbt.put(Settings.namespace + "configuration", _))
  }
}
