package li.cil.oc.common.block

import java.util.Random

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.tileentity
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Material
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction
import org.lwjgl.opengl.GL11

class Keyboard extends SimpleBlock(Material.rock) with traits.SpecialBlock {
  setLightOpacity(0)

  // For Immibis Microblock support.
  val ImmibisMicroblocks_TransformableBlockMarker = null

  override protected def customTextures = Array(
    Some("Keyboard"),
    Some("Keyboard"),
    Some("Keyboard"),
    Some("Keyboard"),
    Some("Keyboard"),
    Some("Keyboard")
  )

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = false

  override def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = true

  override def setBlockBoundsForItemRender(metadata: Int) = setBlockBounds(Direction.NORTH, Direction.WEST)

  override def preItemRender(metadata: Int): Unit = {
    GL11.glTranslatef(-0.75f, 0, 0)
    GL11.glScalef(1.5f, 1.5f, 1.5f)
  }

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Keyboard()

  // ----------------------------------------------------------------------- //

  override def updateTick(world: Level, x: Int, y: Int, z: Int, rng: Random) =
    world.getTileEntity(x, y, z) match {
      case keyboard: tileentity.Keyboard => api.Network.joinOrCreateNetwork(keyboard)
      case _ =>
    }

  override def canPlaceBlockOnSide(world: Level, x: Int, y: Int, z: Int, side: Direction) = {
    world.isSideSolid(x + side.offsetX, y + side.offsetY, z + side.offsetZ, side.getOpposite) &&
      (world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ) match {
        case screen: tileentity.Screen => screen.facing != side.getOpposite
        case _ => true
      })
  }

  override protected def doSetBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z) match {
      case keyboard: tileentity.Keyboard => setBlockBounds(keyboard.pitch, keyboard.yaw)
      case _ =>
    }

  private def setBlockBounds(pitch: Direction, yaw: Direction): Unit = {
    val (forward, up) = pitch match {
      case side@(Direction.DOWN | Direction.UP) => (side, yaw)
      case _ => (yaw, Direction.UP)
    }
    val side = forward.getRotation(up)
    val sizes = Array(7f / 16f, 4f / 16f, 7f / 16f)
    val x0 = -up.offsetX * sizes(1) - side.offsetX * sizes(2) - forward.offsetX * sizes(0)
    val x1 = up.offsetX * sizes(1) + side.offsetX * sizes(2) - forward.offsetX * 0.5f
    val y0 = -up.offsetY * sizes(1) - side.offsetY * sizes(2) - forward.offsetY * sizes(0)
    val y1 = up.offsetY * sizes(1) + side.offsetY * sizes(2) - forward.offsetY * 0.5f
    val z0 = -up.offsetZ * sizes(1) - side.offsetZ * sizes(2) - forward.offsetZ * sizes(0)
    val z1 = up.offsetZ * sizes(1) + side.offsetZ * sizes(2) - forward.offsetZ * 0.5f
    setBlockBounds(
      math.min(x0, x1) + 0.5f, math.min(y0, y1) + 0.5f, math.min(z0, z1) + 0.5f,
      math.max(x0, x1) + 0.5f, math.max(y0, y1) + 0.5f, math.max(z0, z1) + 0.5f)
  }

  override def onNeighborBlockChange(world: Level, x: Int, y: Int, z: Int, block: Block) =
    world.getTileEntity(x, y, z) match {
      case keyboard: tileentity.Keyboard =>
        if (!canPlaceBlockOnSide(world, x, y, z, keyboard.facing.getOpposite)) {
          world.setBlockToAir(x, y, z)
          InventoryUtils.spawnStackInWorld(BlockPosition(x, y, z, world), api.Items.get(Constants.BlockName.Keyboard).createItemStack(1))
        }
      case _ =>
    }

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) =
    adjacencyInfo(world, BlockPosition(x, y, z)) match {
      case Some((keyboard, screen, position, facing)) => screen.rightClick(world, position.x, position.y, position.z, player, facing, 0, 0, 0, force = true)
      case _ => false
    }

  def adjacencyInfo(world: Level, position: BlockPosition) =
    world.getTileEntity(position) match {
      case keyboard: tileentity.Keyboard =>
        val blockPos = position.offset(keyboard.facing.getOpposite)
        world.getBlock(blockPos) match {
          case screen: Screen => Some((keyboard, screen, blockPos, keyboard.facing.getOpposite))
          case _ =>
            // Special case #1: check for screen in front of the keyboard.
            val forward = keyboard.facing match {
              case Direction.UP | Direction.DOWN => keyboard.yaw
              case _ => Direction.UP
            }
            val blockPos = position.offset(forward)
            world.getBlock(blockPos) match {
              case screen: Screen => Some((keyboard, screen, blockPos, forward))
              case _ if keyboard.facing != Direction.UP && keyboard.facing != Direction.DOWN =>
                // Special case #2: check for screen below keyboards on walls.
                val blockPos = position.offset(forward.getOpposite)
                world.getBlock(blockPos) match {
                  case screen: Screen => Some((keyboard, screen, blockPos, forward.getOpposite))
                  case _ => None
                }
              case _ => None
            }
        }
      case _ => None
    }

  override def getValidRotations(world: Level, x: Int, y: Int, z: Int) = null
}
