package li.cil.oc.common.block.traits

import li.cil.oc.common.block.SimpleBlockHooks
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

import scala.reflect.ClassTag

/**
 * 自定义掉落的方块（对应 1.7.10 的 `block.traits.CustomDrops`）。
 *
 * 1.7.10 覆写 `removedByPlayer` + `getDrops`：把方块实体里的内容物（磁盘、升级、机器人数据）
 * 取出来，避免原版掉落逻辑把它们弄丢。1.21.1 对应 `BlockBehaviour#playerDestroy`
 * （掉落改由战利品表驱动，OC 的机器则在此把内部物品手动掉出）。
 */
trait CustomDrops[Tile <: BlockEntity] extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>

  protected def tileTag: ClassTag[Tile]

  override def playerDestroyBlock(state: BlockState, level: Level, pos: BlockPos, player: Player,
                                  blockEntity: BlockEntity, tool: ItemStack): Unit = {
    val matcher = tileTag
    blockEntity match {
      case matcher(tile) => doCustomDrops(tile, player, true)
      case _ =>
    }
  }

  override def onBlockPreDestroy(state: BlockState, level: Level, pos: BlockPos): Unit = {}

  override def onBlockPlacedBy(state: BlockState, level: Level, pos: BlockPos,
                               placer: LivingEntity, stack: ItemStack): Unit = {
    super.onBlockPlacedBy(state, level, pos, placer, stack)
    val matcher = tileTag
    level.getBlockEntity(pos) match {
      case matcher(tile) => doCustomInit(tile, placer, stack)
      case _ =>
    }
  }

  protected def doCustomInit(tile: Tile, player: LivingEntity, stack: ItemStack): Unit = {}

  protected def doCustomDrops(tile: Tile, player: Player, willHarvest: Boolean): Unit = {}
}
