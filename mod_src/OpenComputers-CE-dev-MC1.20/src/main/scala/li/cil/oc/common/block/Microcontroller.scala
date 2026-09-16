package li.cil.oc.common.block

import java.util
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.Tier
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.loot.LootFunctions
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption._
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.block.state.{StateDefinition => StateContainer}
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.{HitResult => RayTraceResult}
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraftforge.common.extensions.IForgeBlock

import scala.reflect.ClassTag

class Microcontroller(props: Properties)
  extends RedstoneAware(props) with IForgeBlock with traits.PowerAcceptor with traits.StateAware with traits.Tickable {

  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]) =
    builder.add(PropertyRotatable.Facing)

  // ----------------------------------------------------------------------- //

  override def getCloneItemStack(state: BlockState, target: RayTraceResult, world: IBlockReader, pos: BlockPos, player: PlayerEntity): ItemStack =
    world.getBlockEntity(pos) match {
      case mcu: blockentity.Microcontroller => mcu.info.copyItemStack()
      case _ => ItemStack.EMPTY
    }

  // ----------------------------------------------------------------------- //

  override protected def tooltipTail(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    super.tooltipTail(stack, world, tooltip, advanced)
    if (KeyBindings.showExtendedTooltips) {
      val info = new MicrocontrollerData(stack)
      for (component <- info.components if !component.isEmpty) {
        tooltip.add(ITextComponent.literal("- " + component.getHoverName.getString).setStyle(Tooltip.DefaultStyle))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput: Double = Settings.get.caseRate(Tier.One)

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Microcontroller(pos, state)

  // ----------------------------------------------------------------------- //

  override def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    if (!Wrench.holdsApplicableWrench(player, pos)) {
      if (!player.isCrouching) {
        if (!world.isClientSide) {
          world.getBlockEntity(pos) match {
            case mcu: blockentity.Microcontroller =>
              if (mcu.machine.isRunning) mcu.machine.stop()
              else mcu.machine.start()
            case _ =>
          }
        }
        true
      }
      else if (api.Items.get(heldItem) == api.Items.get(Constants.ItemName.EEPROM)) {
        if (!world.isClientSide) {
          world.getBlockEntity(pos) match {
            case mcu: blockentity.Microcontroller =>
              val newEeprom = player.getInventory.removeItem(player.getInventory.selected, 1)
              mcu.changeEEPROM(newEeprom) match {
                case SomeStack(oldEeprom) => InventoryUtils.addToPlayerInventory(oldEeprom, player)
                case _ =>
              }
          }
        }
        true
      }
      else false
    }
    else false
  }

  override def setPlacedBy(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity, stack: ItemStack): Unit = {
    super.setPlacedBy(world, pos, state, placer, stack)
    world.getBlockEntity(pos) match {
      case tileEntity: blockentity.Microcontroller if !world.isClientSide => {
        tileEntity.info.loadData(stack)
        tileEntity.snooperNode.changeBuffer(tileEntity.info.storedEnergy - tileEntity.snooperNode.localBuffer)
      }
      case _ =>
    }
  }

  override def getDrops(state: BlockState, ctx: LootParams.Builder): util.List[ItemStack] = {
    val newCtx = ctx.withDynamicDrop(LootFunctions.DYN_ITEM_DATA, f => {
      ctx.getOptionalParameter(LootContextParams.BLOCK_ENTITY) match {
        case tileEntity: blockentity.Microcontroller =>
          tileEntity.saveComponents()
          tileEntity.info.storedEnergy = tileEntity.snooperNode.localBuffer.toInt
          f.accept(tileEntity.info.createItemStack())
        case _ =>
      }
    })
    super.getDrops(state, newCtx)
  }

  override def playerWillDestroy(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity): Unit = {
    if (!world.isClientSide && player.isCreative) {
      world.getBlockEntity(pos) match {
        case tileEntity: blockentity.Microcontroller =>
          Block.dropResources(state, world, pos, tileEntity, player, player.getMainHandItem)
        case _ =>
      }
    }
    super.playerWillDestroy(world, pos, state, player)
  }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.MICROCONTROLLER.get()
}
