package li.cil.oc.common.item.data

import li.cil.oc.{Constants, Settings}
import li.cil.oc.server.component.DebugCard.AccessContext
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

class DebugCardData extends ItemData(Constants.ItemName.DebugCard) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var access: Option[AccessContext] = None

  override def load(nbt: CompoundTag): Unit = {
    access = AccessContext.load(dataTag(nbt))
  }

  override def save(nbt: CompoundTag): Unit = {
    val tag = dataTag(nbt)
    AccessContext.remove(tag)
    access.foreach(_.save(tag))
  }

  private def dataTag(nbt: CompoundTag) = {
    if (!nbt.contains(Settings.namespace + "data")) {
      nbt.put(Settings.namespace + "data", new CompoundTag())
    }
    nbt.getCompound(Settings.namespace + "data")
  }
}
