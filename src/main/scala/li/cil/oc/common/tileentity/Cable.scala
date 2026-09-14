package li.cil.oc.common.tileentity

import li.cil.oc.api
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

/**
 * 线缆（对应 1.7.10 的 `common.tileentity.Cable`）。
 *
 * 线缆本身没有实质逻辑，只负责：
 *  - 持有一个 `Visibility.None` 的节点（把相邻方块实体接进同一个网络）；
 *  - 记录颜色（染色线缆只能与同色线缆或未染色线缆相连，见 `common.block.Cable`）；
 *  - 提供「从物品堆叠恢复颜色 / 生成掉落物品」两个钩子给方块侧调用。
 *
 * 纹理：六个面均为 `CablePart`（中心段与六个方向的连接段由渲染层按连接状态拼接）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `Item.getItemFromBlock(getBlockType)` → `new ItemStack(block)`（`Block` 即 `ItemLike`）。
 *  - 渲染包围盒：1.7.10 覆写的是 Forge 扩展方法 `getRenderBoundingBox`，NeoForge 21.1 的
 *    `IBlockEntityExtension` 已没有它（改由 `BlockEntityRenderer#getRenderBoundingBox` 提供），
 *    因此这里保留同名**普通方法**供将来的渲染器调用，不再是覆写。
 */
class Cable(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.NotAnalyzable with traits.ImmibisMicroblock with traits.Colored {

  val node = api.Network.newNode(this, Visibility.None).create()

  color = Color.LightGray

  def createItemStack(): ItemStack = {
    val stack = new ItemStack(block)
    if (color != Color.LightGray) {
      ItemColorizer.setColor(stack, color)
    }
    stack
  }

  def fromItemStack(stack: ItemStack): Unit = {
    if (ItemColorizer.hasColor(stack)) {
      color = ItemColorizer.getColor(stack)
    }
  }

  override def consumesDye: Boolean = true

  override protected def onColorChanged(): Unit = {
    super.onColorChanged()
    if (world != null && isServer) {
      // 颜色变了之后连接关系可能整体变化，重新入网让网络层重新连接邻居。
      api.Network.joinOrCreateNetwork(this)
    }
  }

  override def canUpdate: Boolean = false

  /**
   * 渲染包围盒（原 1.7.10 Forge 扩展方法 `getRenderBoundingBox`）。
   *
   * 原实现为 `common.block.Cable.bounds(world, x, y, z).offset(x, y, z)`，即把中心段与所有
   * 已连接方向的线段求并集。1.21.1 的方块形状已由 `SimpleBlockHooks#blockShape` 提供，
   * 这里就直接取当前方块状态的形状包围盒，避免与方块侧实现耦合。
   *
   * TODO(client.renderer): `BlockEntityRenderer#getRenderBoundingBox` 接入后改用它调用本方法。
   */
  def getRenderBoundingBox: AABB = {
    if (world == null) new AABB(blockPos)
    else {
      val shape = getBlockState.getShape(world, worldPosition)
      if (shape.isEmpty) new AABB(blockPos) else shape.bounds.move(worldPosition)
    }
  }
}
