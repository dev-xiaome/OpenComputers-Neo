package li.cil.oc.common.block

import java.util
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.traits.Colored
import li.cil.oc.common.blockentity.traits.Inventory
import li.cil.oc.common.blockentity.traits.Rotatable
import li.cil.oc.server.loot.LootFunctions
import li.cil.oc.util.Color
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.{RenderShape => BlockRenderType}
import net.minecraft.world.level.block.{BaseEntityBlock => ContainerBlock}
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.block.entity.{BlockEntity => TileEntity}
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand, InteractionResult}
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.network.chat.Component
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.{Level => World}
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

import scala.collection.convert.ImplicitConversionsToScala._

abstract class SimpleBlock(props: Properties) extends ContainerBlock(props) {
  @Deprecated
  private var unlocalizedName = super.getDescriptionId

  @Deprecated
  private[oc] def setUnlocalizedName(name: String): Unit = unlocalizedName = "blockentity." + name

  @Deprecated
  override def getDescriptionId: String = unlocalizedName

  protected val validRotations_ : Array[Direction] = Array(Direction.UP, Direction.DOWN)

  def createItemStack(amount: Int = 1) = new ItemStack(this, amount)

  override def newBlockEntity(pos: BlockPos, state: BlockState): TileEntity = null

  override def getRenderShape(state: BlockState): BlockRenderType = BlockRenderType.MODEL

  // ----------------------------------------------------------------------- //
  // BlockItem
  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, world: BlockGetter, tooltip: util.List[Component], flag: ITooltipFlag): Unit = {
    tooltipHead(stack, world, tooltip, flag)
    tooltipBody(stack, world, tooltip, flag)
    tooltipTail(stack, world, tooltip, flag)
  }

  protected def tooltipHead(stack: ItemStack, world: BlockGetter, tooltip: util.List[Component], flag: ITooltipFlag): Unit = {
  }

  protected def tooltipBody(stack: ItemStack, world: BlockGetter, tooltip: util.List[Component], flag: ITooltipFlag): Unit = {
    for (curr <- Tooltip.get(getClass.getSimpleName.toLowerCase)) {
      tooltip.add(Component.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  protected def tooltipTail(stack: ItemStack, world: BlockGetter, tooltip: util.List[Component], flag: ITooltipFlag): Unit = {
  }

  // ----------------------------------------------------------------------- //
  // Rotation
  // ----------------------------------------------------------------------- //

  def getFacing(world: BlockGetter, pos: BlockPos): Direction =
    world.getBlockEntity(pos) match {
      case tileEntity: Rotatable => tileEntity.facing
      case _ => Direction.SOUTH
    }

  def setFacing(world: World, pos: BlockPos, value: Direction): Boolean =
    world.getBlockEntity(pos) match {
      case rotatable: Rotatable => rotatable.setFromFacing(value); true
      case _ => false
    }

  def setRotationFromEntityPitchAndYaw(world: World, pos: BlockPos, value: Entity): Boolean =
    world.getBlockEntity(pos) match {
      case rotatable: Rotatable => rotatable.setFromEntityPitchAndYaw(value); true
      case _ => false
    }

  def toLocal(world: BlockGetter, pos: BlockPos, value: Direction): Direction =
    world.getBlockEntity(pos) match {
      case rotatable: Rotatable => rotatable.toLocal(value)
      case _ => value
    }

  // ----------------------------------------------------------------------- //
  // Block
  // ----------------------------------------------------------------------- //

  override def canHarvestBlock(state: BlockState, world: BlockGetter, pos: BlockPos, player: PlayerEntity) = true

  override def canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean = false
  
  def getValidRotations(world: World, pos: BlockPos): Array[Direction] = validRotations_

  override def getDrops(state: BlockState, ctx: LootParams.Builder): util.List[ItemStack] = {
    val newCtx = ctx.getOptionalParameter(LootContextParams.BLOCK_ENTITY) match {
      case _: Inventory => ctx.withDynamicDrop(LootFunctions.DYN_VOLATILE_CONTENTS, f => {
        ctx.getOptionalParameter(LootContextParams.BLOCK_ENTITY) match {
          case inventory: Inventory => inventory.forAllLoot(f)
          case _ =>
        }
      })
      case _ => ctx
    }
    super.getDrops(state, newCtx)
  }

  override def playerWillDestroy(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity): Unit = {
    if (!world.isClientSide && player.isCreative) world.getBlockEntity(pos) match {
      case inventory: Inventory => inventory.dropAllSlots()
      case _ => // Ignore.
    }
    super.playerWillDestroy(world, pos, state, player)
  }

  // ----------------------------------------------------------------------- //

  @Deprecated
  def rotateBlock(world: World, pos: BlockPos, axis: Direction): Boolean =
    world.getBlockEntity(pos) match {
      case rotatable: blockentity.traits.Rotatable if rotatable.rotate(axis) =>
        world.sendBlockUpdated(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
        true
      case _ => false
    }

  // ----------------------------------------------------------------------- //

  override def use(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: InteractionHand, trace: BlockHitResult): InteractionResult = {
    val heldItem = player.getItemInHand(hand)
    world.getBlockEntity(pos) match {
      case colored: Colored if Color.isDye(heldItem) =>
        colored.setColor(Color.rgbValues(Color.dyeColor(heldItem)))
        world.sendBlockUpdated(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
        if (!player.isCreative && colored.consumesDye) {
          heldItem.split(1)
        }
        InteractionResult.sidedSuccess(world.isClientSide)
      case _ => {
        val loc = trace.getLocation
        val pos = trace.getBlockPos
        val x = loc.x.toFloat - pos.getX
        val y = loc.y.toFloat - pos.getY
        val z = loc.z.toFloat - pos.getZ
        if (localOnBlockActivated(world, pos, player, hand, heldItem, trace.getDirection, x, y, z))
          InteractionResult.sidedSuccess(world.isClientSide) else InteractionResult.PASS
      }
    }
  }

  def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: InteractionHand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = false
}
