package li.cil.oc.common.capabilities

import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.item.traits.Chargeable
import li.cil.oc.integration.minecraftforge.EventHandlerNeoForge
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.capabilities.{Capabilities => NeoCapabilities, ICapabilityProvider, RegisterCapabilitiesEvent}
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.fluids.capability.IFluidHandler

import scala.jdk.CollectionConverters._

/**
 * NeoForge 1.21.1 的能力（capability）注册入口。
 *
 * 与 Forge 1.20 不同，1.21.1 不再有 `Capability` / `CapabilityManager` /
 * `CapabilityToken` / `ICapabilityProvider`（旧版）/ `AttachCapabilitiesEvent`：
 * 所有能力都必须在 [[net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent]]
 * 里按「方块实体类型 / 物品」注册一个返回值为可空的 provider。
 *
 * 查询侧的写法也相应变成（注意返回值可能为 `null`）：
 * {{{
 *   level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)
 *   stack.getCapability(Capabilities.EnergyStorage.ITEM)
 * }}}
 *
 * 本模组自己的接口（Environment / SidedEnvironment / Colored / AudioReceiver）
 * 不是 NeoForge 能力，改为直接类型判断，见 [[CapabilityEnvironment]] 等。
 */
object Capabilities {

  @SubscribeEvent
  def onRegisterCapabilities(event: RegisterCapabilitiesEvent): Unit = {
    registerChargeableItems(event)
    registerEnergyAcceptors(event)
    registerRobots(event)
  }

  // ----------------------------------------------------------------------- //
  // 物品：可充电物品（电池升级、平板、悬浮靴）暴露为 Forge 能量存储。
  // ----------------------------------------------------------------------- //

  private def registerChargeableItems(event: RegisterCapabilitiesEvent): Unit = {
    val items = BuiltInRegistries.ITEM.iterator().asScala.
      filter(_.isInstanceOf[Chargeable]).
      map(item => item: ItemLike).
      toArray
    if (items.isEmpty) return
    event.registerItem(NeoCapabilities.EnergyStorage.ITEM,
      new ICapabilityProvider[ItemStack, Void, IEnergyStorage] {
        override def getCapability(stack: ItemStack, context: Void): IEnergyStorage = stack.getItem match {
          case chargeable: Chargeable => new Chargeable.Provider(stack, chargeable)
          case _ => null
        }
      },
      items: _*)
  }

  // ----------------------------------------------------------------------- //
  // 方块实体：接受能量的机器暴露为 Forge 能量存储。
  // ----------------------------------------------------------------------- //

  private def registerEnergyAcceptors(event: RegisterCapabilitiesEvent): Unit = {
    registerEnergyAcceptor(event, BlockEntityTypes.ASSEMBLER.get())
    registerEnergyAcceptor(event, BlockEntityTypes.CASE.get())
    registerEnergyAcceptor(event, BlockEntityTypes.CHARGER.get())
    registerEnergyAcceptor(event, BlockEntityTypes.DISASSEMBLER.get())
    registerEnergyAcceptor(event, BlockEntityTypes.MICROCONTROLLER.get())
    registerEnergyAcceptor(event, BlockEntityTypes.POWER_CONVERTER.get())
    registerEnergyAcceptor(event, BlockEntityTypes.RACK.get())
    registerEnergyAcceptor(event, BlockEntityTypes.RELAY.get())
  }

  private def registerEnergyAcceptor(event: RegisterCapabilitiesEvent, beType: BlockEntityType[_ <: BlockEntity]): Unit = {
    event.registerBlockEntity(NeoCapabilities.EnergyStorage.BLOCK, asBlockEntityType(beType),
      new ICapabilityProvider[BlockEntity, Direction, IEnergyStorage] {
        override def getCapability(blockEntity: BlockEntity, side: Direction): IEnergyStorage = blockEntity match {
          case acceptor: li.cil.oc.common.blockentity.traits.PowerAcceptor => EventHandlerNeoForge.energyStorage(acceptor, side)
          case _ => null
        }
      })
  }

  // ----------------------------------------------------------------------- //
  // 方块实体：机器人（1.21.1 由 RobotProxy 承载）暴露为流体储罐。
  // ----------------------------------------------------------------------- //

  private def registerRobots(event: RegisterCapabilitiesEvent): Unit = {
    event.registerBlockEntity(NeoCapabilities.FluidHandler.BLOCK, asBlockEntityType(BlockEntityTypes.ROBOT.get()),
      new ICapabilityProvider[BlockEntity, Direction, IFluidHandler] {
        override def getCapability(blockEntity: BlockEntity, side: Direction): IFluidHandler = blockEntity match {
          case handler: IFluidHandler => handler
          case _ => null
        }
      })
  }

  /**
   * 注册接口只按方块实体类型区分，与具体的泛型参数无关；这里统一擦除成
   * `BlockEntityType[BlockEntity]`，provider 内部再做一次类型判断。
   */
  private def asBlockEntityType(beType: BlockEntityType[_ <: BlockEntity]): BlockEntityType[BlockEntity] =
    beType.asInstanceOf[BlockEntityType[BlockEntity]]
}
