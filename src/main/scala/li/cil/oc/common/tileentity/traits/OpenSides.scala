package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/**
 * 记录「哪几个面是敞开的」的方块实体（机架箱、NetSplitter 等）
 * （对应 1.7.10 的 `common.tileentity.traits.OpenSides`，作者 Vexatos）。
 *
 * 1.21.1 迁移要点：
 *  - `ForgeDirection` → `Direction`，且**没有 `UNKNOWN`**：这里把 `null` 当作「未知面」，
 *    [[isSideOpen]] / [[setSideOpen]] 对 `null` 一律返回不处理。
 *  - `ForgeDirection#flag`（`1 << ordinal`）在 1.21.1 没有对应成员，
 *    改用 [[sideFlag]] 复刻同样的位掩码。
 *  - `NBTTagCompound#setByte` → `CompoundTag#putByte`，`hasKey` → `contains`。
 */
trait OpenSides extends TileEntity {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  protected def SideCount: Int = Direction.values().length

  protected def defaultState: Boolean = false

  var openSides: Array[Boolean] = Array.fill(SideCount)(defaultState)

  /** 原 `ForgeDirection#flag`，即 `1 << ordinal`（DOWN=0 … EAST=5）。 */
  private def sideFlag(side: Direction): Int = 1 << side.ordinal()

  def compressSides: Byte = Direction.values().foldLeft(0)((acc, side) =>
    acc | (if (openSides(side.ordinal())) sideFlag(side) else 0)).toByte

  def uncompressSides(byte: Byte): Array[Boolean] = Direction.values().map(side => (sideFlag(side) & byte) != 0)

  def isSideOpen(side: Direction): Boolean = side != null && openSides(side.ordinal())

  def setSideOpen(side: Direction, value: Boolean): Unit = if (side != null && openSides(side.ordinal()) != value) {
    openSides(side.ordinal()) = value
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "openSides"))
      openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putByte(Settings.namespace + "openSides", compressSides)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putByte(Settings.namespace + "openSides", compressSides)
  }
}
