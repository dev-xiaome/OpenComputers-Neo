package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedBlockEntity
import li.cil.oc.integration.ManagedBlockEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.effect.MobEffect
import net.minecraft.core.Holder
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BeaconBlockEntity
import net.minecraft.core.registries.BuiltInRegistries

object DriverBeacon extends DriverSidedBlockEntity {
  override def getBlockEntityClass: Class[_] = classOf[BeaconBlockEntity]

  override def createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(pos).asInstanceOf[BeaconBlockEntity])

  final class Environment(tileEntity: BeaconBlockEntity) extends ManagedBlockEntityEnvironment[BeaconBlockEntity](tileEntity, "beacon") with NamedBlock {
    override def preferredName = "beacon"

    override def priority = 0

    @Callback(doc = "function():number -- Get the number of levels for this beacon.")
    def getLevels(context: Context, args: Arguments): Array[AnyRef] = {
      result(tileEntity.levels)
    }

    @Callback(doc = "function():string -- Get the name of the active primary effect.")
    def getPrimaryEffect(context: Context, args: Arguments): Array[AnyRef] = {
      result(getEffectName(tileEntity.primaryPower))
    }

    @Callback(doc = "function():string -- Get the name of the active secondary effect.")
    def getSecondaryEffect(context: Context, args: Arguments): Array[AnyRef] = {
      result(getEffectName(tileEntity.secondaryPower))
    }

    // 1.21.1 里 primaryPower / secondaryPower 的类型是 Holder[MobEffect]（且可为 null）。
    private def getEffectName(effect: Holder[MobEffect]): String = {
      if (effect == null) return null
      val key = effect.unwrapKey().orElse(null)
      if (key != null) key.location().toString
      else {
        val id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value())
        if (id == null) null else id.toString
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (!stack.isEmpty && Block.byItem(stack.getItem) == Blocks.BEACON)
        classOf[Environment]
      else null
    }
  }

}
