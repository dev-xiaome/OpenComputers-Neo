package li.cil.oc.common.block

import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.{BooleanOp => IBooleanFunction}
import net.minecraft.world.phys.shapes.{CollisionContext => ISelectionContext}
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.{Shapes => VoxelShapes}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}

class Printer(props: Properties) extends SimpleBlock(props) with traits.StateAware with traits.GUI with traits.Tickable {
  val blockShape = {
    val base = Block.box(0, 0, 0, 16, 8, 16)
    val pillars = VoxelShapes.or(Block.box(0, 8, 0, 3, 13, 3), Block.box(13, 8, 0, 16, 13, 3),
      Block.box(13, 8, 13, 16, 13, 16), Block.box(0, 8, 13, 3, 13, 16))
    val ring = VoxelShapes.join(Block.box(0, 13, 0, 16, 16, 16),
      Block.box(3, 13, 3, 13, 16, 13), IBooleanFunction.ONLY_FIRST)
    VoxelShapes.or(base, pillars, ring)
  }

  override def getShape(state: BlockState, world: IBlockReader, pos: BlockPos, ctx: ISelectionContext): VoxelShape = blockShape

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Printer => MenuTypes.openPrinterGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Printer(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.PRINTER.get()
}
