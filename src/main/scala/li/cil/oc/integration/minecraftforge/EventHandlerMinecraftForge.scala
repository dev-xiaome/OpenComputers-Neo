package li.cil.oc.integration.minecraftforge

import li.cil.oc.OpenComputers
import li.cil.oc.common.blockentity.traits.PowerAcceptor
import li.cil.oc.integration.util.Power
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.capabilities.{Capability, ForgeCapabilities, ICapabilityProvider}
import java.util.Optional
import java.util.function.Supplier
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.event.AttachCapabilitiesEvent
import net.neoforged.bus.api.SubscribeEvent

object EventHandlerNeoForge {

  @SubscribeEvent
  def onAttachCapabilities(event: AttachCapabilitiesEvent[BlockEntity]): Unit = {
    event.getObject match {
      case tileEntity: PowerAcceptor =>
        val provider = new Provider(tileEntity)
        event.addCapability(ProviderEnergy, provider)
        event.addListener(new Runnable {
          override def run = provider.invalidate()
        })
      case _ =>
    }
  }

  def canCharge(stack: ItemStack): Boolean =
    stack.getCapability(ForgeCapabilities.ENERGY, null).orElse(null) match {
      case storage: IEnergyStorage => storage.canReceive
      case _ => false
    }

  def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double =
    stack.getCapability(ForgeCapabilities.ENERGY, null).orElse(null) match {
      case storage: IEnergyStorage => amount - Power.fromRF(storage.receiveEnergy(Power.toRF(amount), simulate))
      case _ => amount
    }

  val ProviderEnergy: ResourceLocation = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "forgeenergy")

  class Provider(be: PowerAcceptor) extends ICapabilityProvider {

    private val providers = Direction.values.map(side => java.util.Optional.of(new java.util.function.Supplier[EnergyStorageImpl] {
      override def get = new EnergyStorageImpl(be, side)
    }))
    private val nullProvider = java.util.Optional.of(new java.util.function.Supplier[EnergyStorageImpl] {
      override def get = new EnergyStorageImpl(be, null)
    })

    def invalidate(): Unit = {
      for (provider <- providers) provider.invalidate
      nullProvider.invalidate
    }

    override def getCapability[T](capability: Capability[T], facing: Direction): java.util.Optional[T] = {
      if (capability == ForgeCapabilities.ENERGY) {
        (if (facing == null) nullProvider.cast[T] else providers(facing.get3DDataValue)).cast[T]
      } else java.util.Optional.empty[T]
    }

    class EnergyStorageImpl(val be: PowerAcceptor, val side: Direction) extends IEnergyStorage {

      override def getEnergyStored: Int = Power.toRF(be.globalBuffer(side))

      override def getMaxEnergyStored: Int = Power.toRF(be.globalBufferSize(side))

      override def canReceive: Boolean = be.canConnectPower(side)

      override def receiveEnergy(maxReceive: Int, simulate: Boolean): Int = {
        Power.toRF(be.tryChangeBuffer(side, Power.fromRF(maxReceive), !simulate))
      }

      override def canExtract: Boolean = false

      override def extractEnergy(maxExtract: Int, simulate: Boolean): Int = 0
    }

  }

}
