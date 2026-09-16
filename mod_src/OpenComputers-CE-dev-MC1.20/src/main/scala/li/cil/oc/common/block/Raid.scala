package li.cil.oc.common.block

import java.util
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.item.data.RaidData
import li.cil.oc.common.blockentity
import li.cil.oc.server.loot.LootFunctions
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.{StateDefinition => StateContainer}
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraftforge.common.extensions.IForgeBlock

class Raid(props: Properties) extends SimpleBlock(props) with IForgeBlock with traits.GUI {

  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]) =
    builder.add(PropertyRotatable.Facing)

  override protected def tooltipTail(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    super.tooltipTail(stack, world, tooltip, advanced)
    if (KeyBindings.showExtendedTooltips) {
      val data = new RaidData(stack)
      for (disk <- data.disks if !disk.isEmpty) {
        tooltip.add(ITextComponent.literal("- " + disk.getHoverName.getString).setStyle(Tooltip.DefaultStyle))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Raid => MenuTypes.openRaidGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Raid(pos, state)

  // ----------------------------------------------------------------------- //

  override def hasAnalogOutputSignal(state: BlockState): Boolean = true

  override def getAnalogOutputSignal(state: BlockState, world: World, pos: BlockPos): Int =
    world.getBlockEntity(pos) match {
      case raid: blockentity.Raid if raid.presence.forall(ok => ok) => 15
      case _ => 0
    }

  override def setPlacedBy(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity, stack: ItemStack): Unit = {
    super.setPlacedBy(world, pos, state, placer, stack)
    world.getBlockEntity(pos) match {
      case tileEntity: blockentity.Raid if !world.isClientSide => {
        val data = new RaidData(stack)
        for (i <- 0 until math.min(data.disks.length, tileEntity.getContainerSize)) {
          tileEntity.setItem(i, data.disks(i))
        }
        data.label.foreach(tileEntity.label.setLabel)
        if (!data.filesystem.isEmpty) {
          tileEntity.tryCreateRaid(data.filesystem.getCompound("node").getString("address"))
          tileEntity.filesystem.foreach(_.loadData(data.filesystem))
        }
      }
      case _ =>
    }
  }

  override def getDrops(state: BlockState, ctx: LootParams.Builder): util.List[ItemStack] = {
    val newCtx = ctx.withDynamicDrop(LootFunctions.DYN_ITEM_DATA, f => {
      ctx.getOptionalParameter(LootContextParams.BLOCK_ENTITY) match {
        case tileEntity: blockentity.Raid =>
          val stack = createItemStack()
          if (tileEntity.items.exists(!_.isEmpty)) {
            val data = new RaidData()
            data.disks = tileEntity.items.clone()
            tileEntity.filesystem.foreach(_.saveData(data.filesystem))
            data.label = Option(tileEntity.label.getLabel)
            data.saveData(stack)
          }
          f.accept(stack)
        case _ =>
      }
    })
    super.getDrops(state, newCtx)
  }

  override def playerWillDestroy(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity): Unit = {
    if (!world.isClientSide && player.isCreative) {
      world.getBlockEntity(pos) match {
        case tileEntity: blockentity.Raid if tileEntity.items.exists(!_.isEmpty) =>
          Block.dropResources(state, world, pos, tileEntity, player, player.getMainHandItem)
        case _ =>
      }
    }
    super.playerWillDestroy(world, pos, state, player)
  }
}
