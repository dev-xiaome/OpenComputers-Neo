package li.cil.oc.common.item.data

import li.cil.oc.Settings
import li.cil.oc.api.network.Visibility
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

// Generic one for items that are used as components; gets the items node info.
class NodeData extends ItemData(null) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var address: Option[String] = None
  var buffer: Option[Double] = None
  var visibility: Option[Visibility] = None

  override def load(nbt: CompoundTag): Unit = {
    val nodeNbt = nbt.getCompound(Settings.namespace + "data").getCompound("node")
    if (nodeNbt.contains("address")) {
      address = Option(nodeNbt.getString("address"))
    }
    if (nodeNbt.contains("buffer")) {
      buffer = Option(nodeNbt.getDouble("buffer"))
    }
    if (nodeNbt.contains("visibility")) {
      visibility = Option(Visibility.values()(nodeNbt.getInteger("visibility")))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    if (!nbt.contains(Settings.namespace + "data")) {
      nbt.put(Settings.namespace + "data", new CompoundTag())
    }
    val dataNbt = nbt.getCompound(Settings.namespace + "data")
    if (!dataNbt.contains("node")) {
      dataNbt.put("node", new CompoundTag())
    }
    val nodeNbt = dataNbt.getCompound("node")
    address.foreach(nodeNbt.putString("address", _))
    buffer.foreach(nodeNbt.putDouble("buffer", _))
    visibility.map(_.ordinal()).foreach(nodeNbt.putInt("visibility", _))
  }
}
