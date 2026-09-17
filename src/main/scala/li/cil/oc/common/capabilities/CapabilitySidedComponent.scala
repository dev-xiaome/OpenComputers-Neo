package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Environment, SidedComponent, SidedEnvironment}
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.capabilities.{Capability, ICapabilityProvider}
import java.util.Optional
import java.util.function.Supplier

object CapabilitySidedComponent {
  final val SidedComponent = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "sided_component")

  class Provider(val tileEntity: BlockEntity with Environment with SidedComponent) extends ICapabilityProvider with java.util.function.Supplier[Provider] with SidedEnvironment {
    private val wrapper = java.util.Optional.of(this)

    def get = this

    def invalidate() = wrapper.invalidate

    override def getCapability[T](capability: Capability[T], facing: Direction): java.util.Optional[T] = {
      if (capability == Capabilities.SidedEnvironmentCapability) wrapper.cast[T]
      else java.util.Optional.empty[T]
    }

    override def sidedNode(side: Direction) = if (tileEntity.canConnectNode(side)) tileEntity.node else null

    override def canConnect(side: Direction) = tileEntity.canConnectNode(side)
  }
}
