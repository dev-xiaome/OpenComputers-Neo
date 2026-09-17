package li.cil.oc.common.tileentity

import li.cil.oc.api
import li.cil.oc.api.network.{Node, Visibility}
import li.cil.oc.api.prefab
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

/**
 * 交换器（对应 1.7.10 的 `common.tileentity.Transposer`）。
 *
 * 方块实体只是「组件宿主」：物品 / 流体的搬运逻辑在服务端组件里，方块实体负责创建它、
 * 暴露 `node`，并在存档时转发 `load` / `save`；`lastOperation` 供客户端渲染活动指示灯。
 *
 * 纹理：下 = TransposerBottom，其它五面 = TransposerSide（朝向面另见 `traits.Rotatable`）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子。
 *
 * ==降级说明==
 * TODO(server.component): 原实现为 `val transposer = new component.Transposer.Block(this)`
 * （`li.cil.oc.server.component.Transposer.Block`）。`server` 包尚未移植，这里换成本文件内的
 * [[Transposer.Placeholder]]：保留 `transposer` / `node` / `lastOperation` / `load` / `save`
 * 这些对外名字与节点形状（组件名 `transposer` + 连接器）；其中原本由组件在搬运成功后调用的
 * `ServerPacketSender.sendTransposerActivity(host)` 也一并降级（见占位类的说明）。
 */
class Transposer(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment {

  val transposer = new Transposer.Placeholder(this)

  def node: Node = transposer.node

  // Used on client side to check whether to render activity indicators.
  var lastOperation = 0L

  override def canUpdate: Boolean = false

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    transposer.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    transposer.save(nbt)
  }
}

object Transposer {

  /**
   * 交换器组件的**最小占位实现**（原 `li.cil.oc.server.component.Transposer.Block`）。
   *
   * 保留原组件的公开成员名与节点形状（组件名 `transposer` + 连接器），搬运逻辑是空实现。
   *
   * TODO(server.component): `li.cil.oc.server.component.Transposer` 移植完成后删除本类。
   * 恢复时需要的实现要点（原组件）：
   *  - `onTransferContents()` 里按 `Settings.get.transposerCost` 扣能量，失败返回
   *    `"not enough energy"`；成功后 `ServerPacketSender.sendTransposerActivity(host)`；
   *  - `Block#position = BlockPosition(host)`；
   *  - Lua 侧的 `transferItem` / `transferFluid` 等回调来自
   *    `traits.WorldInventoryAnalytics` / `WorldTankAnalytics` / `InventoryTransfer`。
   */
  private[tileentity] class Placeholder(val host: Transposer)
    extends prefab.ManagedEnvironment {

    setNode(api.Network.newNode(this, Visibility.Network).
      withComponent("transposer").
      withConnector().
      create())
  }
}
