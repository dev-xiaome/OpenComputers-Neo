package li.cil.oc.common.tileentity

import li.cil.oc.api
import li.cil.oc.api.network.{Node, Visibility}
import li.cil.oc.api.prefab
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

/**
 * 地形分析仪（对应 1.7.10 的 `common.tileentity.Geolyzer`）。
 *
 * 方块实体本身只是「组件宿主」：真正的扫描逻辑在服务端组件里，方块实体负责创建它、
 * 把 `node` 暴露出去，并在存档时转发 `load` / `save`。
 *
 * 纹理：下/上 = GeolyzerTop，其它四面 = GeolyzerSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子，需要相应修饰符。
 *
 * ==降级说明==
 * TODO(server.component): 原实现为 `val geolyzer = new component.Geolyzer(this)`
 * （`li.cil.oc.server.component.Geolyzer`）。`server` 包尚未移植，这里换成本文件内的
 * [[Geolyzer.Placeholder]]：保留 `geolyzer` / `node` / `load` / `save` 这些对外名字，
 * 节点类型与组件名（`geolyzer`）与原来一致，只是 Lua 侧的 `scan` / `analyze` / `store`
 * 等回调方法还没有实现。`server.component` 移植后把 `Placeholder` 换回真实组件即可，
 * 方块实体这里不需要再改。
 */
class Geolyzer(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment {

  val geolyzer = new Geolyzer.Placeholder(this)

  def node: Node = geolyzer.node

  override def canUpdate: Boolean = false

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    geolyzer.load(nbt)
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    geolyzer.save(nbt)
  }
}

object Geolyzer {

  /**
   * 地形分析仪组件的**最小占位实现**（原 `li.cil.oc.server.component.Geolyzer`）。
   *
   * 只保留原组件的公开成员名与节点形状（组件名 `geolyzer` + 连接器），
   * 存档沿用 `prefab.ManagedEnvironment` 的 `node` 子标签；扫描 / 分析逻辑待组件层移植。
   *
   * TODO(server.component): `li.cil.oc.server.component.Geolyzer` 移植完成后删除本类。
   */
  private[tileentity] class Placeholder(val host: api.network.EnvironmentHost)
    extends prefab.ManagedEnvironment {

    // 原实现另有 `.withConnector()`（无参重载，缓冲大小 0），这里保持一致。
    setNode(api.Network.newNode(this, Visibility.Network).
      withComponent("geolyzer").
      withConnector().
      create())

    // TODO(server.component): 原组件还带 DeviceInfo（class/description/vendor/product/capacity）
    // 以及 @Callback 的 canSeeSky / isSunVisible / scan / analyze / store，待组件层移植后补回。
  }
}
