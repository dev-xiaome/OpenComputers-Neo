package li.cil.oc.integration.minecraftforge

import li.cil.oc.common.blockentity.traits.PowerAcceptor
import li.cil.oc.integration.util.Power
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.neoforged.neoforge.capabilities.{Capabilities => NeoCapabilities}
import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * Forge（NeoForge）能量集成的公共入口。
 *
 * 1.20.1 时代这里通过 `AttachCapabilitiesEvent` 把能量能力挂到方块实体上；
 * 1.21.1 只能在 `RegisterCapabilitiesEvent` 里注册，见
 * [[li.cil.oc.common.capabilities.Capabilities#onRegisterCapabilities]]。
 */
object EventHandlerNeoForge {

  def canCharge(stack: ItemStack): Boolean =
    Option(stack.getCapability(NeoCapabilities.EnergyStorage.ITEM)) match {
      case Some(storage) => storage.canReceive
      case _ => false
    }

  def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double =
    Option(stack.getCapability(NeoCapabilities.EnergyStorage.ITEM)) match {
      case Some(storage) => amount - Power.fromRF(storage.receiveEnergy(Power.toRF(amount), simulate))
      case _ => amount
    }

  /** 能力 provider 的工厂方法：方块实体查询时按面创建存储视图。 */
  def energyStorage(be: PowerAcceptor, side: Direction): IEnergyStorage = new EnergyStorageImpl(be, side)

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
