package li.cil.oc.common.block

import com.mojang.serialization.MapCodec
import li.cil.oc.common.block.ChameliumBlock.{CODEC, DEFAULT_COLOR}
import li.cil.oc.common.datacomponents.OCComponents
import net.minecraft.core.BlockPos
import net.minecraft.world.item.context.{BlockPlaceContext => BlockItemUseContext}
import net.minecraft.world.item.{DyeColor, ItemStack}
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties, simpleCodec}
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.{BlockState, StateDefinition => StateContainer}

class ChameliumBlock(props: Properties) extends SimpleBlock(props) {
  override def codec(): MapCodec[ChameliumBlock] = CODEC

  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]): Unit = {
    builder.add(ChameliumBlock.Color)
  }

  registerDefaultState(stateDefinition.any.setValue(ChameliumBlock.Color, DEFAULT_COLOR))

  @Deprecated
  override def getCloneItemStack(world: LevelReader, pos: BlockPos, state: BlockState): ItemStack = {
    val stack = new ItemStack(this)
    stack.set(OCComponents.CHAMELIUM_COLOR.get(), state.getValue(ChameliumBlock.Color))
    stack
  }

  override def getStateForPlacement(ctx: BlockItemUseContext): BlockState =
    defaultBlockState.setValue(ChameliumBlock.Color, ctx.getItemInHand.getOrDefault(OCComponents.CHAMELIUM_COLOR.get(), DEFAULT_COLOR))
}

object ChameliumBlock {
  final val CODEC = simpleCodec(new ChameliumBlock(_))
  final val Color = EnumProperty.create("color", classOf[DyeColor])
  final val DEFAULT_COLOR = DyeColor.BLACK
}
