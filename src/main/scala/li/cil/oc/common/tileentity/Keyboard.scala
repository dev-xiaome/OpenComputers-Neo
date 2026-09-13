package li.cil.oc.common.tileentity

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction

class Keyboard extends traits.Environment with traits.Rotatable with traits.ImmibisMicroblock with SidedEnvironment with Analyzable {
  override def validFacings = Direction.VALID_DIRECTIONS

  val keyboard = {
    val keyboardItem = api.Items.get(Constants.BlockName.Keyboard).createItemStack(1)
    api.Driver.driverFor(keyboardItem, getClass).createEnvironment(keyboardItem, this)
  }

  override def node = keyboard.node

  def hasNodeOnSide(side: Direction) : Boolean =
    side != facing && (isOnWall || side != forward.getOpposite)

  // ----------------------------------------------------------------------- //

  @SideOnly(Dist.CLIENT)
  override def canConnect(side: Direction) = hasNodeOnSide(side)

  override def sidedNode(side: Direction) = if (hasNodeOnSide(side)) node else null

  // Override automatic analyzer implementation for sided environments.
  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float) = Array(node)

  // ----------------------------------------------------------------------- //

  override def canUpdate = false

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (isServer) {
      keyboard.load(nbt.getCompound(Settings.namespace + "keyboard"))
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (isServer) {
      nbt.setNewCompoundTag(Settings.namespace + "keyboard", keyboard.save)
    }
  }

  // ----------------------------------------------------------------------- //

  private def isOnWall = facing != Direction.UP && facing != Direction.DOWN

  private def forward = if (isOnWall) Direction.UP else yaw
}
