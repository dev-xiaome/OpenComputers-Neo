package li.cil.oc.common.item.data

import li.cil.oc.Settings
import li.cil.oc.api.network.Visibility
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 组件物品的节点数据（原 1.7.10 的 `NodeData`）。
 *
 * 1.21.1 迁移要点：`getInteger` → `getInt`，`putInt` 与 `Visibility.ordinal` 语义不变。
 * 读取时用 [[Visibility#values]] 的边界做保护，避免旧存档里的越界序号直接抛异常。
 */
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
      val index = nodeNbt.getInt("visibility")
      val values = Visibility.values()
      visibility = if (index >= 0 && index < values.length) Option(values(index)) else None
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
    visibility.foreach(value => nodeNbt.putInt("visibility", value.ordinal()))
  }
}
