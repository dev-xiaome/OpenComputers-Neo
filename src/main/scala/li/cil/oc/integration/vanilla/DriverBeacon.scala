package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction, Holder}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Block, Blocks}
import net.minecraft.world.level.block.entity.BeaconBlockEntity

/**
 * 信标（`BeaconBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityBeacon` → `BeaconBlockEntity`；`world.getTileEntity(x,y,z)` → `world.getBlockEntity(new BlockPos(x,y,z))`
 *  - `Potion.potionTypes(id)`（数字 id 表）→ 状态效果改由注册表管理，
 *    这里从 `Holder[MobEffect]` 取 `BuiltInRegistries.MOB_EFFECT` 的键路径作为效果名
 *  - `getLevels` / `getPrimaryEffect` / `getSecondaryEffect` 在 1.21.1 都不再是公开 API
 *    （字段为包私有），因此通过 [[VanillaReflection]] 读取
 */
object DriverBeacon extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[BeaconBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[BeaconBlockEntity])

  final class Environment(entity: BeaconBlockEntity) extends ManagedTileEntityEnvironment[BeaconBlockEntity](entity, "beacon") with NamedBlock {
    override def preferredName = "beacon"

    override def priority = 0

    @Callback(doc = "function():number -- Get the number of levels for this beacon.")
    def getLevels(context: Context, args: Arguments): Array[AnyRef] = {
      result(VanillaReflection.int(tileEntity, "levels"))
    }

    @Callback(doc = "function():string -- Get the name of the active primary effect.")
    def getPrimaryEffect(context: Context, args: Arguments): Array[AnyRef] = {
      result(effectName(VanillaReflection.obj[Holder[MobEffect]](tileEntity, "primaryPower")))
    }

    @Callback(doc = "function():string -- Get the name of the active secondary effect.")
    def getSecondaryEffect(context: Context, args: Arguments): Array[AnyRef] = {
      result(effectName(VanillaReflection.obj[Holder[MobEffect]](tileEntity, "secondaryPower")))
    }

    private def effectName(effect: Holder[MobEffect]): String = {
      if (effect == null) null
      else {
        val key = BuiltInRegistries.MOB_EFFECT.getKey(effect.value())
        if (key == null) null else key.getPath
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && Block.byItem(stack.getItem) == Blocks.BEACON)
        classOf[Environment]
      else null
    }
  }

}
