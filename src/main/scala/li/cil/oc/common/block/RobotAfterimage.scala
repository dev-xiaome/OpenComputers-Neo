package li.cil.oc.common.block

import java.util.Random

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.NEI
import li.cil.oc.util.Rarity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.util.IIcon
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class RobotAfterimage extends SimpleBlock with traits.SpecialBlock {
  setLightOpacity(0)
  setCreativeTab(null)
  NEI.hide(this)

  private var icon: IIcon = _

  // ----------------------------------------------------------------------- //

  @SideOnly(Dist.CLIENT)
  override def getIcon(side: Direction, metadata: Int) = icon

  @SideOnly(Dist.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    icon = iconRegister.registerIcon(Settings.resourceDomain + ":GenericTop")
  }

  override def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = false

  override def isBlockSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = false

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = false

  override def getPickBlock(target: HitResult, world: Level, x: Int, y: Int, z: Int) =
    findMovingRobot(world, x, y, z) match {
      case Some(robot) => robot.info.createItemStack()
      case _ => null
    }

  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack) = {
    val data = new RobotData(stack)
    Rarity.byTier(data.tier)
  }

  // ----------------------------------------------------------------------- //

  override def isAir(world: IBlockAccess, x: Int, y: Int, z: Int) = true

  // ----------------------------------------------------------------------- //

  override def onBlockAdded(world: Level, x: Int, y: Int, z: Int): Unit = {
    world.scheduleBlockUpdate(x, y, z, this, math.max((Settings.get.moveDelay * 20).toInt, 1) - 1)
  }

  override def updateTick(world: Level, x: Int, y: Int, z: Int, rng: Random): Unit = {
    world.setBlockToAir(x, y, z)
  }

  override def removedByPlayer(world: Level, player: Player, x: Int, y: Int, z: Int, willHarvest: Boolean) = {
    findMovingRobot(world, x, y, z) match {
      case Some(robot) if robot.isAnimatingMove &&
        robot.moveFromX == x &&
        robot.moveFromY == y &&
        robot.moveFromZ == z =>
        robot.proxy.getBlockType.removedByPlayer(world, player, robot.x, robot.y, robot.z, false)
      case _ => super.removedByPlayer(world, player, x, y, z, willHarvest) // Probably broken by the robot we represent.
    }
  }

  override protected def doSetBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int): Unit = {
    findMovingRobot(world, x, y, z) match {
      case Some(robot) =>
        val block = robot.getBlockType
        block.setBlockBoundsBasedOnState(world, robot.x, robot.y, robot.z)
        val dx = robot.x - robot.moveFromX
        val dy = robot.y - robot.moveFromY
        val dz = robot.z - robot.moveFromZ
        setBlockBounds(
          block.getBlockBoundsMinX.toFloat + dx,
          block.getBlockBoundsMinY.toFloat + dy,
          block.getBlockBoundsMinZ.toFloat + dz,
          block.getBlockBoundsMaxX.toFloat + dx,
          block.getBlockBoundsMaxY.toFloat + dy,
          block.getBlockBoundsMaxZ.toFloat + dz)
      case _ => // throw new Exception("Robot afterimage without a robot found. This is a bug!")
    }
  }

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    findMovingRobot(world, x, y, z) match {
      case Some(robot) => api.Items.get(Constants.BlockName.Robot).block.onBlockActivated(world, robot.x, robot.y, robot.z, player, side.ordinal, hitX, hitY, hitZ)
      case _ => world.setBlockToAir(x, y, z)
    }
  }

  def findMovingRobot(world: IBlockAccess, x: Int, y: Int, z: Int): Option[tileentity.Robot] = {
    for (side <- Direction.VALID_DIRECTIONS) {
      val (tx, ty, tz) = (x + side.offsetX, y + side.offsetY, z + side.offsetZ)
      if (world match {
        case world: Level => world.blockExists(tx, ty, tz)
        case _ => !world.isAirBlock(tx, ty, tz)
      }) world.getTileEntity(tx, ty, tz) match {
        case proxy: tileentity.RobotProxy if proxy.robot.moveFromX == x && proxy.robot.moveFromY == y && proxy.robot.moveFromZ == z => return Some(proxy.robot)
        case _ =>
      }
    }
    None
  }
}
