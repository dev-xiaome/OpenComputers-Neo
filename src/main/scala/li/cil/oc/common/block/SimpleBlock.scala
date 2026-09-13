package li.cil.oc.common.block

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.CreativeTab
import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import li.cil.oc.common.tileentity.traits.Colored
import li.cil.oc.common.tileentity.traits.Inventory
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.util.Color
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Material
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.Entity
import net.minecraft.entity.EnumCreatureType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.util.IIcon
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class SimpleBlock(material: Material = Material.iron) extends Block(material) {
  setHardness(2f)
  setResistance(5)
  setCreativeTab(CreativeTab)

  var showInItemList = true

  protected val validRotations_ = Array(Direction.UP, Direction.DOWN)

  def createItemStack(amount: Int = 1) = new ItemStack(this, amount)

  // ----------------------------------------------------------------------- //
  // Rendering
  // ----------------------------------------------------------------------- //

  val icons = new Array[IIcon](6)

  protected def customTextures = Array.fill[Option[String]](6)(None)

  override def getRenderType = Settings.blockRenderId

  @SideOnly(Dist.CLIENT)
  override def colorMultiplier(world: IBlockAccess, x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z) match {
      case colored: Colored => colored.color
      case _ => getRenderColor(world.getBlockMetadata(x, y, z))
    }

  @SideOnly(Dist.CLIENT)
  final override def getIcon(side: Int, metadata: Int) = getIcon(Direction.getOrientation(side), metadata)

  @SideOnly(Dist.CLIENT)
  def getIcon(side: Direction, metadata: Int) = icons(side.ordinal())

  @SideOnly(Dist.CLIENT)
  final override def getIcon(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) = getIcon(world, x, y, z, Direction.getOrientation(side), toLocal(world, x, y, z, Direction.getOrientation(side)))

  @SideOnly(Dist.CLIENT)
  def getIcon(world: IBlockAccess, x: Int, y: Int, z: Int, globalSide: Direction, localSide: Direction) = icons(localSide.ordinal())

  @SideOnly(Dist.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    icons(Direction.DOWN.ordinal) = iconRegister.registerIcon(Settings.resourceDomain + ":GenericTop")
    icons(Direction.UP.ordinal) = icons(Direction.DOWN.ordinal)
    icons(Direction.NORTH.ordinal) = iconRegister.registerIcon(Settings.resourceDomain + ":GenericSide")
    icons(Direction.SOUTH.ordinal) = icons(Direction.NORTH.ordinal)
    icons(Direction.WEST.ordinal) = icons(Direction.NORTH.ordinal)
    icons(Direction.EAST.ordinal) = icons(Direction.NORTH.ordinal)

    val custom = customTextures
    for (side <- Direction.VALID_DIRECTIONS) {
      custom(side.ordinal) match {
        case Some(name) =>
          if (name.contains(":")) icons(side.ordinal) = iconRegister.registerIcon(name)
          else icons(side.ordinal) = iconRegister.registerIcon(Settings.resourceDomain + ":" + name)
        case _ =>
      }
    }
  }

  @SideOnly(Dist.CLIENT)
  def preItemRender(metadata: Int) {}

  final override def setBlockBoundsForItemRender() = setBlockBoundsForItemRender(0)

  def setBlockBoundsForItemRender(metadata: Int) = super.setBlockBoundsForItemRender()

  final override def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) = shouldSideBeRendered(world, x, y, z, Direction.getOrientation(side))

  def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = super.shouldSideBeRendered(world, x, y, z, side.ordinal())

  // ----------------------------------------------------------------------- //
  // ItemBlock
  // ----------------------------------------------------------------------- //

  def rarity(stack: ItemStack) = EnumRarity.common

  @SideOnly(Dist.CLIENT)
  def addInformation(metadata: Int, stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltipHead(metadata, stack, player, tooltip, advanced)
    tooltipBody(metadata, stack, player, tooltip, advanced)
    tooltipTail(metadata, stack, player, tooltip, advanced)
  }

  protected def tooltipHead(metadata: Int, stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
  }

  protected def tooltipBody(metadata: Int, stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName))
  }

  protected def tooltipTail(metadata: Int, stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
  }

  // ----------------------------------------------------------------------- //
  // Rotation
  // ----------------------------------------------------------------------- //

  def getFacing(world: IBlockAccess, x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z) match {
      case tileEntity: Rotatable => tileEntity.facing
      case _ => Direction.UNKNOWN
    }

  def setFacing(world: Level, x: Int, y: Int, z: Int, value: Direction) =
    world.getTileEntity(x, y, z) match {
      case rotatable: Rotatable => rotatable.setFromFacing(value); true
      case _ => false
    }

  def setRotationFromEntityPitchAndYaw(world: Level, x: Int, y: Int, z: Int, value: Entity) =
    world.getTileEntity(x, y, z) match {
      case rotatable: Rotatable => rotatable.setFromEntityPitchAndYaw(value); true
      case _ => false
    }

  def toLocal(world: IBlockAccess, x: Int, y: Int, z: Int, value: Direction) =
    world.getTileEntity(x, y, z) match {
      case rotatable: Rotatable => rotatable.toLocal(value)
      case _ => value
    }

  // ----------------------------------------------------------------------- //
  // Block
  // ----------------------------------------------------------------------- //

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = true

  override def canHarvestBlock(player: Player, meta: Int) = true

  override def canBeReplacedByLeaves(world: IBlockAccess, x: Int, y: Int, z: Int) = false

  override def canCreatureSpawn(creature: EnumCreatureType, world: IBlockAccess, x: Int, y: Int, z: Int) = false

  override def getValidRotations(world: Level, x: Int, y: Int, z: Int) = validRotations_

  override def onBlockPreDestroy(world: Level, x: Int, y: Int, z: Int, metadata: Int) =
    if (!world.isRemote) world.getTileEntity(x, y, z) match {
      case inventory: Inventory => inventory.dropAllSlots()
      case _ => // Ignore.
    }

  // ----------------------------------------------------------------------- //

  override def rotateBlock(world: Level, x: Int, y: Int, z: Int, axis: Direction) =
    world.getTileEntity(x, y, z) match {
      case rotatable: tileentity.traits.Rotatable if rotatable.rotate(axis) =>
        world.markBlockForUpdate(x, y, z)
        true
      case _ => false
    }

  override def recolourBlock(world: Level, x: Int, y: Int, z: Int, side: Direction, colour: Int) =
    world.getTileEntity(x, y, z) match {
      case colored: Colored if colored.color != Color.byMeta(colour) =>
        colored.color = Color.byMeta(colour)
        world.markBlockForUpdate(x, y, z)
        true // Blame Vexatos.
      case _ => super.recolourBlock(world, x, y, z, side, colour)
    }

  // This function can mess things up badly in single player if not
  // synchronized because it sets fields in an instance stored in the
  // static block list... which is used by both server and client thread.
  // The other place where this is locked is in collisionRayTrace below,
  // which seems to be the only built-in function that *logically* depends
  // on the state bounds (rest is rendering which is unimportant).
  final override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) =
    this.synchronized(doSetBlockBoundsBasedOnState(world, x, y, z))

  protected def doSetBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int): Unit =
    super.setBlockBoundsBasedOnState(world, x, y, z)

  protected def setBlockBounds(bounds: AABB): Unit = {
    setBlockBounds(
      bounds.minX.toFloat,
      bounds.minY.toFloat,
      bounds.minZ.toFloat,
      bounds.maxX.toFloat,
      bounds.maxY.toFloat,
      bounds.maxZ.toFloat)
  }

  // NOTE: must not be final for immibis microblocks to work.
  override def collisionRayTrace(world: Level, x: Int, y: Int, z: Int, start: Vec3, end: Vec3) =
    this.synchronized(intersect(world, x, y, z, start, end))

  override def getCollisionBoundingBoxFromPool(world: Level, x: Int, y: Int, z: Int) = this.synchronized {
    doSetBlockBoundsBasedOnState(world, x, y, z)
    super.getCollisionBoundingBoxFromPool(world, x, y, z)
  }

  protected def intersect(world: Level, x: Int, y: Int, z: Int, start: Vec3, end: Vec3) =
    super.collisionRayTrace(world, x, y, z, start, end)

  final override def canPlaceBlockOnSide(world: Level, x: Int, y: Int, z: Int, side: Int) =
    canPlaceBlockOnSide(world, x, y, z, toLocal(world, x, y, z, Direction.getOrientation(side).getOpposite))

  def canPlaceBlockOnSide(world: Level, x: Int, y: Int, z: Int, side: Direction) =
    super.canPlaceBlockOnSide(world, x, y, z, side.getOpposite.ordinal)

  // ----------------------------------------------------------------------- //

  final override def canConnectRedstone(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) =
    canConnectRedstone(world, x, y, z, side match {
      case -1 => Direction.UP
      case 0 => Direction.NORTH
      case 1 => Direction.EAST
      case 2 => Direction.SOUTH
      case 3 => Direction.WEST
    })

  def canConnectRedstone(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = false

  final override def isProvidingStrongPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) =
    isProvidingStrongPower(world, x, y, z, Direction.getOrientation(side).getOpposite)

  def isProvidingStrongPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) =
    isProvidingWeakPower(world, x, y, z, side)

  final override def isProvidingWeakPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) =
    isProvidingWeakPower(world, x, y, z, Direction.getOrientation(side).getOpposite)

  def isProvidingWeakPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = 0

  // ----------------------------------------------------------------------- //

  // NOTE: must not be final for immibis microblocks to work.
  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean =
    world.getTileEntity(x, y, z) match {
      case colored: Colored if Color.isDye(player.getHeldItem) =>
        colored.color = Color.dyeColor(player.getHeldItem)
        world.markBlockForUpdate(x, y, z)
        if (colored.consumesDye) {
          player.getHeldItem.splitStack(1)
        }
        true
      case _ => onBlockActivated(world, x, y, z, player, Direction.getOrientation(side), hitX, hitY, hitZ)
    }

  def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = false
}
