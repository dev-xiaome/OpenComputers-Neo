package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraft.world.level.block.state.BlockState

class Relay(props: Properties) extends SimpleBlock(props) with traits.GUI with traits.PowerAcceptor with traits.Tickable {
  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Relay => MenuTypes.openRelayGui(player, te)
    case _ =>
  }

  override def energyThroughput = Settings.get.accessPointRate

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Relay(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.RELAY.get()
}
