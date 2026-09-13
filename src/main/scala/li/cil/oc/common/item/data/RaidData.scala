package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.Constants.NBT

class RaidData extends ItemData(Constants.BlockName.Raid) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var disks = Array.empty[ItemStack]

  var filesystem = new CompoundTag()

  var label: Option[String] = None

  override def load(nbt: CompoundTag): Unit = {
    disks = nbt.getList(Settings.namespace + "disks", NBT.TAG_COMPOUND).
      toArray[CompoundTag].map(ItemStack.loadItemStackFromNBT)
    filesystem = nbt.getCompound(Settings.namespace + "filesystem")
    if (nbt.contains(Settings.namespace + "label")) {
      label = Option(nbt.getString(Settings.namespace + "label"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.setNewTagList(Settings.namespace + "disks", disks.toIterable)
    nbt.put(Settings.namespace + "filesystem", filesystem)
    label.foreach(nbt.putString(Settings.namespace + "label", _))
  }
}
