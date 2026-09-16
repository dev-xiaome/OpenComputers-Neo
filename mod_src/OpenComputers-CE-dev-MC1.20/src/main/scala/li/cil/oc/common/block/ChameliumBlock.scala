package li.cil.oc.common.block

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.item.context.{BlockPlaceContext => BlockItemUseContext}
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.{StateDefinition => StateContainer}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.{BlockGetter => IBlockReader}

object ChameliumBlock {
  final val Color = EnumProperty.create("color", classOf[DyeColor])
}

class ChameliumBlock(props: Properties) extends SimpleBlock(props) {
  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]): Unit = {
    builder.add(ChameliumBlock.Color)
  }
  registerDefaultState(stateDefinition.any.setValue(ChameliumBlock.Color, DyeColor.BLACK))

  override def createItemStack(amount: Int = 1): ItemStack = {
    val stack = new ItemStack(this, amount)
    stack.setDamageValue(defaultBlockState.getValue(ChameliumBlock.Color).getId)
    stack
  }

  override def getCloneItemStack(world: IBlockReader, pos: BlockPos, state: BlockState): ItemStack = {
    val stack = new ItemStack(this)
    stack.setDamageValue(state.getValue(ChameliumBlock.Color).getId)
    stack
  }

  override def getStateForPlacement(ctx: BlockItemUseContext): BlockState =
    defaultBlockState.setValue(ChameliumBlock.Color, DyeColor.byId(ctx.getItemInHand.getDamageValue))
}
