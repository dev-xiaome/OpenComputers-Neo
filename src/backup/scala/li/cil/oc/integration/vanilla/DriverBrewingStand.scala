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
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity

/**
 * 酿造台（`BrewingStandBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityBrewingStand` → `BrewingStandBlockEntity`
 *  - `Items.brewing_stand` → `Items.BREWING_STAND`
 *  - `getBrewTime` 在 1.21.1 已移除（`brewTime` 字段为包私有），
 *    通过 [[VanillaReflection]] 读取
 */
object DriverBrewingStand extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[BrewingStandBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[BrewingStandBlockEntity])

  final class Environment(entity: BrewingStandBlockEntity) extends ManagedTileEntityEnvironment[BrewingStandBlockEntity](entity, "brewing_stand") with NamedBlock {
    override def preferredName = "brewing_stand"

    override def priority = 0

    @Callback(doc = "function():number -- Get the number of ticks remaining of the current brewing operation.")
    def getBrewTime(context: Context, args: Arguments): Array[AnyRef] = {
      result(VanillaReflection.int(tileEntity, "brewTime"))
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && stack.is(Items.BREWING_STAND))
        classOf[Environment]
      else null
    }
  }

}
