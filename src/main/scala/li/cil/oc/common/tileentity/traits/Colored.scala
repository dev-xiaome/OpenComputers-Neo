package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.internal
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * 可被染色的方块实体（对应 1.7.10 的 `traits.Colored`）。
 *
 * 1.21.1 迁移要点：
 *  - 颜色同步：服务端用专用包 `ServerPacketSender.sendColorChange(this)`（对齐 OCCE），
 *    避免为一次染色同步整个方块实体；客户端侧仍由 [[BlockEntityBase]] 的 `getUpdateTag`
 *    驱动的 `writeToNBTForClient` 提供 `renderColor`。
 */
trait Colored extends TileEntity with internal.Colored {
  // 注意：Scala 的自类型不会被继承，TileEntity 的子 trait 必须重新声明。
  self: BlockEntity =>

  private var _color = 0

  def color: Int = _color

  def color_=(value: Int): Unit = if (value != _color) {
    _color = value
    onColorChanged()
  }

  def consumesDye: Boolean = false

  override def getColor: Int = color

  override def setColor(value: Int): Unit = color = value

  protected def onColorChanged(): Unit = {
    if (world != null && isServer) {
      ServerPacketSender.sendColorChange(this)
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "renderColor")) {
      _color = nbt.getInt(Settings.namespace + "renderColor")
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putInt(Settings.namespace + "renderColor", _color)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    _color = nbt.getInt("renderColor")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putInt("renderColor", _color)
  }
}
