package li.cil.oc.common.block

import com.mojang.serialization.MapCodec
import li.cil.oc.common.block.Capacitor.CODEC

import java.util.Random
import li.cil.oc.common.blockentity
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties, simpleCodec}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraft.server.level.{ServerLevel => ServerWorld}
import net.minecraft.util.RandomSource

class Capacitor(props: Properties) extends SimpleBlock(props) {
  override def codec(): MapCodec[_ <: Capacitor] = CODEC
  
  @Deprecated
  override def isRandomlyTicking(state: BlockState) = true

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Capacitor(pos, state)

  // ----------------------------------------------------------------------- //

  override def hasAnalogOutputSignal(state: BlockState): Boolean = true

  override def getAnalogOutputSignal(state: BlockState, world: World, pos: BlockPos): Int =
    world.getBlockEntity(pos) match {
      case capacitor: blockentity.Capacitor if !world.isClientSide =>
        math.round(15 * capacitor.node.localBuffer / capacitor.node.localBufferSize).toInt
      case _ => 0
    }

  override def tick(state: BlockState, world: ServerWorld, pos: BlockPos, rand: RandomSource): Unit = {
    world.updateNeighborsAt(pos, this)
  }

  @Deprecated
  override def neighborChanged(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, b: Boolean): Unit =
    world.getBlockEntity(pos) match {
      case capacitor: blockentity.Capacitor => capacitor.recomputeCapacity()
      case _ =>
    }
}

object Capacitor {
  final val CODEC = simpleCodec(new Capacitor(_))
}
