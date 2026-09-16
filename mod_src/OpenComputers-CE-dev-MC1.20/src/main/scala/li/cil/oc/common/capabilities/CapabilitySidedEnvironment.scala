package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Node, SidedEnvironment}
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.{Capability, ICapabilityProvider, ICapabilitySerializable}
import net.minecraftforge.common.util.{LazyOptional, NonNullSupplier}

object CapabilitySidedEnvironment {
  final val ProviderSidedEnvironment = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "sided_environment")

  class Provider(val tileEntity: BlockEntity with SidedEnvironment) extends ICapabilitySerializable[CompoundTag] with NonNullSupplier[Provider] with SidedEnvironment {
    private val wrapper = LazyOptional.of(this)

    def get = this

    def invalidate() = wrapper.invalidate

    override def getCapability[T](capability: Capability[T], facing: Direction): LazyOptional[T] = {
      if (capability == Capabilities.SidedEnvironmentCapability) wrapper.cast[T]
      else LazyOptional.empty[T]
    }

    override def sidedNode(side: Direction) = tileEntity.sidedNode(side)

    override def canConnect(side: Direction) = tileEntity.canConnect(side)

    override def serializeNBT(): CompoundTag = new CompoundTag()

    override def deserializeNBT(nbt: CompoundTag): Unit = {}
  }

  class DefaultImpl extends SidedEnvironment {
    override def sidedNode(side: Direction): Node = null

    override def canConnect(side: Direction): Boolean = false
  }
}
