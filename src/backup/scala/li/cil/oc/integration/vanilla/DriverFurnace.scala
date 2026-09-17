package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{AbstractFurnaceBlock, Block}
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity

/**
 * 熔炉（`AbstractFurnaceBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityFurnace` → `AbstractFurnaceBlockEntity`（熔炉 / 高炉 / 烟熏器共用）
 *  - 字段改名：`furnaceBurnTime` → `litTime`、`furnaceCookTime` → `cookingProgress`、
 *    `currentItemBurnTime` → `litDuration`
 *  - `isBurning` 在 1.21.1 变为私有方法，改用方块状态属性 `AbstractFurnaceBlock.LIT`
 *  - 上述三个计数字段在 1.21.1 是包私有的，通过 [[VanillaReflection]] 读取
 *  - 1.7.10 只有一种熔炉；1.21.1 拆成熔炉 / 高炉 / 烟熏器三种方块，
 *    它们共用同一个方块实体类型，因此 `Provider` 用 `AbstractFurnaceBlock` 统一匹配
 */
object DriverFurnace extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[AbstractFurnaceBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[AbstractFurnaceBlockEntity])

  final class Environment(entity: AbstractFurnaceBlockEntity) extends ManagedTileEntityEnvironment[AbstractFurnaceBlockEntity](entity, "furnace") with NamedBlock {
    override def preferredName = "furnace"

    override def priority = 0

    @Callback(doc = "function():number -- The number of ticks that the furnace will keep burning from the last consumed fuel.")
    def getBurnTime(context: Context, args: Arguments): Array[AnyRef] = {
      result(VanillaReflection.int(tileEntity, "litTime"))
    }

    @Callback(doc = "function():number -- The number of ticks that the current item has been cooking for.")
    def getCookTime(context: Context, args: Arguments): Array[AnyRef] = {
      result(VanillaReflection.int(tileEntity, "cookingProgress"))
    }

    @Callback(doc = "function():number -- The number of ticks that the currently burning fuel lasts in total.")
    def getCurrentItemBurnTime(context: Context, args: Arguments): Array[AnyRef] = {
      result(VanillaReflection.int(tileEntity, "litDuration"))
    }

    @Callback(doc = "function():boolean -- Get whether the furnace is currently active.")
    def isBurning(context: Context, args: Arguments): Array[AnyRef] = {
      val level = tileEntity.getLevel
      val state = if (level == null) null else level.getBlockState(tileEntity.getBlockPos)
      result(state != null && state.getBlock.isInstanceOf[AbstractFurnaceBlock] && state.getValue(AbstractFurnaceBlock.LIT).booleanValue())
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && Block.byItem(stack.getItem).isInstanceOf[AbstractFurnaceBlock])
        classOf[Environment]
      else null
    }
  }

}
