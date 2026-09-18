package li.cil.oc.server.driver

import com.google.common.base.Strings
import li.cil.oc.api.driver
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

class CompoundBlockDriver(val sidedBlocks: Array[DriverBlock]) extends DriverBlock {
  override def createEnvironment(level: Level, pos: BlockPos, side: Direction): CompoundBlockEnvironment = {
    val list = sidedBlocks.map {
      driver => Option(driver.createEnvironment(level, pos, side)) match {
        case Some(environment) => (driver.getClass.getName, environment)
        case _ => null
      }
    } filter (_ != null)
    if (list.isEmpty) null
    else new CompoundBlockEnvironment(cleanName(tryGetName(level, pos, list.map(_._2))), list: _*)
  }

  override def worksWith(level: Level, pos: BlockPos, side: Direction): Boolean = sidedBlocks.forall(_.worksWith(level, pos, side))

  override def equals(obj: Any): Boolean = obj match {
    case multi: CompoundBlockDriver if multi.sidedBlocks.length == sidedBlocks.length => sidedBlocks.intersect(multi.sidedBlocks).length == sidedBlocks.length
    case _ => false
  }

  // TODO rework this method
  private def tryGetName(level: Level, pos: BlockPos, environments: Seq[ManagedEnvironment]): String = {
    environments.collect {
      case named: NamedBlock => named
    }.sortBy(_.priority).lastOption match {
      case Some(named) => return named.preferredName
      case _ => // No preferred name.
    }
    try {
      val block = level.getBlockState(pos).getBlock
      val stack = if (block.asItem() != null) {
        Some(new ItemStack(block, 1))
      }
      else None
      if (stack.isDefined) {
        return stack.get.getDescriptionId.stripPrefix("tile.")
      }
    } catch {
      case _: Throwable =>
    }
    try level.getBlockEntity(pos) match {
      case tileEntity: BlockEntity =>
        val name = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tileEntity.getType).toString
        return name
    } catch {
      case _: Throwable =>
    }
    "component"
  }

  private def cleanName(name: String) = {
    val safeStart = if (name.matches( """^[^a-zA-Z_]""")) "_" + name else name
    val identifier = safeStart.replaceAll( """[^\w_]""", "_").trim
    if (Strings.isNullOrEmpty(identifier)) "component"
    else identifier.toLowerCase
  }
}
