package li.cil.oc.common.block

import com.mojang.serialization.MapCodec
import li.cil.oc.Settings
import li.cil.oc.common.block.Assembler.CODEC
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties, simpleCodec}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.phys.shapes.{CollisionContext => ISelectionContext}
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.{Shapes => VoxelShapes}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}

class Assembler(props: Properties) extends SimpleBlock(props) with traits.PowerAcceptor with traits.StateAware with traits.GUI with traits.Tickable {
  override def energyThroughput = Settings.get.assemblerRate

  override def codec(): MapCodec[Assembler] = CODEC

  val blockShape = {
    val bottom = Block.box(0, 0, 0, 16, 7, 16)
    val mid = Block.box(2, 7, 2, 14, 9, 14)
    val top = Block.box(0, 9, 0, 16, 16, 16)
    VoxelShapes.or(top, bottom, mid)
  }

  override def getShape(state: BlockState, world: IBlockReader, pos: BlockPos, ctx: ISelectionContext): VoxelShape = blockShape

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Assembler => MenuTypes.openAssemblerGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = new blockentity.Assembler(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.ASSEMBLER.get()
}

object Assembler {
  final val CODEC = simpleCodec(new Assembler(_))
}
