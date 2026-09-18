package li.cil.oc.integration.neoforge

import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.blockentity.traits.PowerAcceptor
import li.cil.oc.integration.util.Power
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.neoforged.neoforge.capabilities.{Capabilities, RegisterCapabilitiesEvent}
import net.neoforged.neoforge.energy.IEnergyStorage

object EventHandlerNeoForge {

  // Called from EventHandler.onRegisterCapabilities
  def onRegisterCapabilities(event: RegisterCapabilitiesEvent): Unit = {
    // Register IEnergyStorage (EnergyStorage.BLOCK) for all PowerAcceptor block entities
    Seq(
      BlockEntityTypes.ADAPTER, BlockEntityTypes.ASSEMBLER, BlockEntityTypes.CABLE,
      BlockEntityTypes.CAPACITOR, BlockEntityTypes.CARPETED_CAPACITOR, BlockEntityTypes.CASE,
      BlockEntityTypes.CHARGER, BlockEntityTypes.DISASSEMBLER, BlockEntityTypes.DISK_DRIVE,
      BlockEntityTypes.GEOLYZER, BlockEntityTypes.HOLOGRAM, BlockEntityTypes.KEYBOARD,
      BlockEntityTypes.MICROCONTROLLER, BlockEntityTypes.MOTION_SENSOR, BlockEntityTypes.NET_SPLITTER,
      BlockEntityTypes.POWER_CONVERTER, BlockEntityTypes.POWER_DISTRIBUTOR, BlockEntityTypes.PRINT,
      BlockEntityTypes.PRINTER, BlockEntityTypes.RACK, BlockEntityTypes.RAID,
      BlockEntityTypes.REDSTONE_IO, BlockEntityTypes.RELAY, BlockEntityTypes.ROBOT,
      BlockEntityTypes.SCREEN, BlockEntityTypes.TRANSPOSER, BlockEntityTypes.WAYPOINT
    ).foreach { beType =>
      event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, beType.get().asInstanceOf[BlockEntityType[_]], (be: BlockEntity, side: Direction) => {
        be match {
          // Match OC 1.12 Forge Energy semantics: expose the capability for every
          // PowerAcceptor side and let IEnergyStorage.canReceive decide dynamically.
          // This also makes the returned capability safe for NeoForge consumers to cache.
          case pa: PowerAcceptor => new EnergyStorageImpl(pa, side): IEnergyStorage
          case _ => null
        }
      })
    }
  }

  def canCharge(stack: ItemStack): Boolean =
    Option(stack.getCapability(Capabilities.EnergyStorage.ITEM)).exists(_.canReceive)

  def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double =
    Option(stack.getCapability(Capabilities.EnergyStorage.ITEM)) match {
      case Some(storage) => amount - Power.fromRF(storage.receiveEnergy(Power.toRF(amount), simulate))
      case _ => amount
    }

  class EnergyStorageImpl(val tile: PowerAcceptor, val side: Direction) extends IEnergyStorage {

    override def getEnergyStored: Int = Power.toRF(tile.globalBuffer(side))

    override def getMaxEnergyStored: Int = Power.toRF(tile.globalBufferSize(side))

    override def canReceive: Boolean = tile.canConnectPower(side)

    override def receiveEnergy(maxReceive: Int, simulate: Boolean): Int =
      Power.toRF(tile.tryChangeBuffer(side, Power.fromRF(maxReceive), !simulate))

    override def canExtract: Boolean = false

    override def extractEnergy(maxExtract: Int, simulate: Boolean): Int = 0
  }
}
