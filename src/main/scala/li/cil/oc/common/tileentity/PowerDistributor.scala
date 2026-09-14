package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.level.block.state.BlockState

/**
 * 配电箱（对应 1.7.10 的 `common.tileentity.PowerDistributor`）。
 *
 * 六个面各挂一个纯连接器节点（没有 `node`，因此自己不是网络环境的一份子），
 * 每 `Settings.tickFrequency` 刻由 [[traits.PowerBalancer]] 把六个网络里的能量均衡一次。
 *
 * 纹理：下/上 = PowerDistributorTop，其它四面 = PowerDistributorSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - 删除 `@SideOnly(Side.CLIENT)`；[[canConnect]] 本来就只在客户端调用。
 *  - `ForgeDirection` → `Direction`（`side.ordinal` 语义不变）。
 *  - `NBTTagCompound#getTagList(..., NBT.TAG_COMPOUND)` → `getList(..., Tag.TAG_COMPOUND)`；
 *    `setNewTagList` 由 [[li.cil.oc.util.ExtendedNBT]] 提供。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子。
 */
class PowerDistributor(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.PowerBalancer with traits.NotAnalyzable {

  // 本方块自己没有网络节点，只有六个面各自的连接器（与原实现一致）。
  override def node: Node = null

  private val nodes = Array.fill(6)(api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferDistributor).
    create())

  override protected def isConnected: Boolean = nodes.exists(node => node.address != null && node.network != null)

  override def canUpdate: Boolean = isServer

  // ----------------------------------------------------------------------- //

  // 只应在客户端渲染时调用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。
  override def canConnect(side: Direction): Boolean = true

  override def sidedNode(side: Direction): Node = nodes(side.ordinal)

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // 注意：1.21.1 的 `ListTag` 已经是 `AbstractCollection`，自带 `toArray` 成员，
    // 会遮蔽 `ExtendedNBT` 提供的扩展方法，因此这里按下标逐个读取。
    val connectorsNbt = nbt.getList(Settings.namespace + "connector", Tag.TAG_COMPOUND)
    for (index <- 0 until (connectorsNbt.size() min nodes.length)) {
      nodes(index).load(connectorsNbt.getCompound(index))
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    // Side check for Waila (and other mods that may call this client side).
    if (isServer) {
      nbt.setNewTagList(Settings.namespace + "connector", nodes.map(connector => {
        val connectorNbt = new CompoundTag()
        connector.save(connectorNbt)
        connectorNbt
      }))
    }
  }
}
