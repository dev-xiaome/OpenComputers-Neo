package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.item.{ItemStack, Items}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ComparatorBlockEntity

/**
 * 红石比较器（`ComparatorBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityComparator` → `ComparatorBlockEntity`
 *  - `getOutputSignal` 保留原名（1.7.10 也是这个名字）
 *  - `Items.comparator` → `Items.COMPARATOR`
 */
object DriverComparator extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[ComparatorBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[ComparatorBlockEntity])

  final class Environment(entity: ComparatorBlockEntity) extends ManagedTileEntityEnvironment[ComparatorBlockEntity](entity, "comparator") with NamedBlock {
    override def preferredName = "comparator"

    override def priority = 0

    @Callback(doc = "function():number -- Get the strength of the comparators output signal.")
    def getOutputSignal(context: Context, args: Arguments): Array[AnyRef] = {
      result(tileEntity.getOutputSignal)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && stack.is(Items.COMPARATOR))
        classOf[Environment]
      else null
    }
  }

}
