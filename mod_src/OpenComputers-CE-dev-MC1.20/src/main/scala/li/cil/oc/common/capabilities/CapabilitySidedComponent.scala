package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Environment, SidedComponent, SidedEnvironment}
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.{Capability, ICapabilityProvider}
import net.minecraftforge.common.util.{LazyOptional, NonNullSupplier}

object CapabilitySidedComponent {
  final val SidedComponent = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "sided_component")

  class Provider(val tileEntity: BlockEntity with Environment with SidedComponent) extends ICapabilityProvider with NonNullSupplier[Provider] with SidedEnvironment {
    private val wrapper = LazyOptional.of(this)

    def get = this

    def invalidate() = wrapper.invalidate

    override def getCapability[T](capability: Capability[T], facing: Direction): LazyOptional[T] = {
      if (capability == Capabilities.SidedEnvironmentCapability) wrapper.cast[T]
      else LazyOptional.empty[T]
    }

    override def sidedNode(side: Direction) = if (tileEntity.canConnectNode(side)) tileEntity.node else null

    override def canConnect(side: Direction) = tileEntity.canConnectNode(side)
  }
}
