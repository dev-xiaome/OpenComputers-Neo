package li.cil.oc.integration.create

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedBlockEntity
import li.cil.oc.integration.ManagedBlockEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.Level

object DriverCreativeMotor extends DriverSidedBlockEntity {
  override def getBlockEntityClass: Class[_] = classOf[CreativeMotorBlockEntity]

  override def createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(pos).asInstanceOf[CreativeMotorBlockEntity])

  private final class Environment(be: CreativeMotorBlockEntity) extends ManagedBlockEntityEnvironment[CreativeMotorBlockEntity](be, "creative_motor") with NamedBlock {
    override def preferredName = "creative_motor"

    override def priority = 1 // required for overwrite CC:T peripheral

    private val generatedSpeed: ScrollValueBehaviour = be.generatedSpeed

    @Callback(doc = "function() -- Sets the RPM of the creative motor.")
    def setGeneratedSpeed(context: Context, args: Arguments): Array[AnyRef] = {
      generatedSpeed.setValue(args.checkInteger(0))
      result(true)
    }

    @Callback(doc = "function():number -- Gets the current RPM of the creative motor.")
    def getGeneratedSpeed(context: Context, args: Arguments): Array[AnyRef] = {
      result(generatedSpeed.getValue.asInstanceOf[Float])
    }
  }
}
