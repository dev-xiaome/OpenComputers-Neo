package li.cil.oc.common.tileentity.traits

import li.cil.oc.api

/**
 * Immibis Microblocks 的「可转换方块实体」语义标记
 * （对应 1.7.10 的 `common.tileentity.traits.ImmibisMicroblock`）。
 *
 * 1.7.10 里这些 `ImmibisMicroblocks_*` 成员是给 Immibis Microblocks 的 ASM 注入识别的；
 * 1.21.1 不再移植第三方模组集成，因此本 trait 退化为**语义标记**：
 *
 * TODO(integration): Immibis Microblocks 集成不再移植（`mods.immibis.*` 不存在）。
 * 成员名保持原样，因为 `common.block.Cable` 等处仍然按名字引用它们
 * （`ImmibisMicroblocks_isSideOpen` 用于判断某个面是否敞开）。
 */
trait ImmibisMicroblock extends TileEntity {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /** 原 Immibis Microblocks 的 ASM 标记字段；1.21.1 只作为语义标记保留。 */
  val ImmibisMicroblocks_TransformableTileEntityMarker: AnyRef = null

  /** 该面是否敞开：没有微方块集成后恒为 `true`（与原实现的默认值一致）。 */
  def ImmibisMicroblocks_isSideOpen(side: Int): Boolean = true

  /** 微方块变化回调：原实现调用 `api.Network.joinOrCreateNetwork(this)` 重新入网。 */
  def ImmibisMicroblocks_onMicroblocksChanged(): Unit = {
    api.Network.joinOrCreateNetwork(this)
  }
}
