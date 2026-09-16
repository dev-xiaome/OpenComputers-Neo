package li.cil.oc.common.blockentity

import li.cil.oc.{Constants, Settings, api}
import li.cil.oc.api.network.{Analyzable, SidedEnvironment}
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

class Keyboard(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.KEYBOARD.get(), pos, state) with traits.Environment with traits.Rotatable with SidedEnvironment with Analyzable with IBlockEntityExtension {
  override def validFacings = Direction.values

  val keyboard = {
    val keyboardItem = api.Items.get(Constants.BlockName.Keyboard).createItemStack(1)
    api.Driver.driverFor(keyboardItem, getClass).createEnvironment(keyboardItem, this)
  }

  override def node = keyboard.node

  def hasNodeOnSide(side: Direction) : Boolean =
    side != facing && (isOnWall || side.getOpposite != forward)

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override def canConnect(side: Direction) = hasNodeOnSide(side)

  override def sidedNode(side: Direction) = if (hasNodeOnSide(side)) node else null

  // Override automatic analyzer implementation for sided environments.
  override def onAnalyze(player: PlayerEntity, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = Array(node)

  // ----------------------------------------------------------------------- //

  // ----------------------------------------------------------------------- //

  private final val KeyboardTag = Settings.namespace + "keyboard"

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    if (isServer) {
      keyboard.loadData(nbt.getCompound(KeyboardTag), provider)
    }
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    if (isServer) {
      nbt.setNewCompoundTag(KeyboardTag, (nbt: CompoundTag) => keyboard.saveData(nbt, provider))
    }
  }

  // ----------------------------------------------------------------------- //

  private def isOnWall = facing != Direction.UP && facing != Direction.DOWN

  private def forward = if (isOnWall) Direction.UP else yaw
}
