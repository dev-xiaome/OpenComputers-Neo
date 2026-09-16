package li.cil.oc.common.item.data

import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ItemUtils
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.util.UUID

class NanomachineData extends ItemData(Constants.ItemName.Nanomachines) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  def this(controller: ControllerImpl) = {
    this()
    uuid = controller.uuid
    val nbt = new CompoundTag()
    controller.configuration.saveData(nbt, forItem = true)
    configuration = Option(nbt)
  }

  var uuid = ""
  var configuration: Option[CompoundTag] = None

  private final val UUIDTag = Settings.namespace + "uuid"
  private final val ConfigurationTag = Settings.namespace + "configuration"

  override def loadData(holder: DataComponentHolder): Unit = {
    uuid = holder.getComponent(OCComponents.ID).toString
    configuration = holder.getComponent(OCComponents.NANOMACHINES_NETWORK_INFO)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.ID, UUID.fromString(uuid))
    holder.setComponent(OCComponents.NANOMACHINES_NETWORK_INFO, configuration)
  }
}
