package li.cil.oc.common.block.traits

import java.util

import li.cil.oc.common.block.SimpleBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.Level

import scala.reflect.ClassTag

trait CustomDrops[Tile <: BlockEntity] extends SimpleBlock {
  protected def tileTag: ClassTag[Tile]

  override def getDrops(world: Level, x: Int, y: Int, z: Int, metadata: Int, fortune: Int): util.ArrayList[ItemStack] = new java.util.ArrayList[ItemStack]()

  override def onBlockPreDestroy(world: Level, x: Int, y: Int, z: Int, metadata: Int): Unit = {}

  override def removedByPlayer(world: Level, player: Player, x: Int, y: Int, z: Int, willHarvest: Boolean): Boolean = {
    if (!world.isRemote) {
      val matcher = tileTag
      world.getTileEntity(x, y, z) match {
        case matcher(tileEntity) => doCustomDrops(tileEntity, player, willHarvest)
        case _ =>
      }
    }
    super.removedByPlayer(world, player, x, y, z, willHarvest)
  }

  override def onBlockPlacedBy(world: Level, x: Int, y: Int, z: Int, player: LivingEntity, stack: ItemStack): Unit = {
    super.onBlockPlacedBy(world, x, y, z, player, stack)
    val matcher = tileTag
    world.getTileEntity(x, y, z) match {
      case matcher(tileEntity) => doCustomInit(tileEntity, player, stack)
      case _ =>
    }
  }

  protected def doCustomInit(tileEntity: Tile, player: LivingEntity, stack: ItemStack): Unit = {}

  protected def doCustomDrops(tileEntity: Tile, player: Player, willHarvest: Boolean): Unit = {}
}
