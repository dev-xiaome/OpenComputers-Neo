package li.cil.oc.common.block

import li.cil.oc.common.block.property.PropertyCableConnection
import li.cil.oc.common.blockentity
import li.cil.oc.common.capabilities.Capabilities
import li.cil.oc.util.{Color, ItemColorizer}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.context.{BlockPlaceContext => BlockItemUseContext}
import net.minecraft.world.item.{DyeColor, ItemStack}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.{BlockEntity => TileEntity}
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.{BlockState, StateDefinition => StateContainer}
import net.minecraft.world.level.{BlockGetter => IBlockReader, Level => World, LevelAccessor => IWorld}
import net.minecraft.world.phys.shapes.{CollisionContext => ISelectionContext, Shapes => VoxelShapes, VoxelShape}
import net.minecraft.world.phys.{HitResult => RayTraceResult}
import net.minecraftforge.common.extensions.IForgeBlock

class Cable(props: Properties) extends SimpleBlock(props) with IForgeBlock {
  // For FMP part coloring.
  var colorMultiplierOverride: Option[Int] = None

  // ----------------------------------------------------------------------- //

  registerDefaultState(CableHelper.helperRegisterDefaultState(this.stateDefinition))

  override def getStateForPlacement(ctx: BlockItemUseContext): BlockState = {
    val color = Cable.getConnectionColor(ctx.getItemInHand)
    val fromPos = new BlockPos.MutableBlockPos()

    Direction.values.foldLeft(defaultBlockState) { (state, fromSide) =>
      fromPos.setWithOffset(ctx.getClickedPos, fromSide)

      val fromState = ctx.getLevel.getBlockState(fromPos)

      Cable.updateState(state, null, color, fromSide, fromState, ctx.getLevel, fromPos)
    }
  }

  override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]): Unit = {
    builder.add(
      PropertyCableConnection.DOWN,
      PropertyCableConnection.UP,
      PropertyCableConnection.NORTH,
      PropertyCableConnection.SOUTH,
      PropertyCableConnection.WEST,
      PropertyCableConnection.EAST
    )
  }

  override def getCloneItemStack(state: BlockState, target: RayTraceResult, world: IBlockReader, pos: BlockPos, player: PlayerEntity): ItemStack = {
    world.getBlockEntity(pos) match {
      case cable: blockentity.Cable => cable.createItemStack()
      case _ => createItemStack()
    }
  }

  override def getShape(state: BlockState, world: IBlockReader, pos: BlockPos, ctx: ISelectionContext): VoxelShape = {
    Cable.shape(state)
  }

  override def neighborChanged(state: BlockState, world: World, pos: BlockPos, other: Block, otherPos: BlockPos, moved: Boolean): Unit = {
    if (world.isClientSide) return

    val newState = world.getBlockEntity(pos) match {
        case cable: blockentity.Cable =>
          val fromPos = new BlockPos.MutableBlockPos()
          Direction.values.foldLeft(state) { (currentState, fromSide) => 
            fromPos.setWithOffset(pos, fromSide)

            val fromState = world.getBlockState(fromPos)

            Cable.updateState(currentState, cable, -1, fromSide, fromState, world, fromPos)
          }
        case _ => state
      }

    if (newState != state) {
      world.setBlock(pos, newState, 0x13)
    }
  }

  override def updateShape(state: BlockState, fromSide: Direction, fromState: BlockState, world: IWorld, pos: BlockPos, fromPos: BlockPos): BlockState = {
    Cable.updateState(state, world.getBlockEntity(pos), -1, fromSide, fromState, world, fromPos)
  }

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState): blockentity.Cable = {
    new blockentity.Cable(pos, state)
  }

  // ----------------------------------------------------------------------- //

  override def setPlacedBy(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity, stack: ItemStack): Unit = {
    super.setPlacedBy(world, pos, state, placer, stack)

    world.getBlockEntity(pos) match {
      case cable: blockentity.Cable =>
        cable.fromItemStack(stack)

        val currentState = world.getBlockState(pos)
        val fromPos = new BlockPos.MutableBlockPos()

        val correctedState = Direction.values.foldLeft(currentState) { (result, fromSide) =>
         fromPos.setWithOffset(pos, fromSide)

         Cable.updateState(result, cable, -1, fromSide, world.getBlockState(fromPos), world, fromPos)
        }
        
        if (correctedState != currentState) {
          world.setBlock(pos, correctedState, 0x13)
        }
        
        correctedState.updateNeighbourShapes(world, pos, 2)
        world.updateNeighborsAt(pos, correctedState.getBlock)
        
      case _ =>
    }
  }
}

object Cable {
  final val MIN = 0.375
  final val MAX = 1 - MIN

  private final val DefaultConnectionColor = Color.rgbValues(DyeColor.LIGHT_GRAY)

  final val DefaultShape: VoxelShape = VoxelShapes.box(MIN, MIN, MIN, MAX, MAX, MAX)

  final val CachedParts: Array[VoxelShape] = Array(
    VoxelShapes.box(MIN, 0, MIN, MAX, MIN, MAX), // Down
    VoxelShapes.box(MIN, MAX, MIN, MAX, 1, MAX), // Up
    VoxelShapes.box(MIN, MIN, 0, MAX, MAX, MIN), // North
    VoxelShapes.box(MIN, MIN, MAX, MAX, MAX, 1), // South
    VoxelShapes.box(0, MIN, MIN, MIN, MAX, MAX), // West
    VoxelShapes.box(MAX, MIN, MIN, 1, MAX, MAX) // East
  )

  final val CachedBounds: Array[VoxelShape] = {
    // 6 directions = 6 bits = 11111111b >> 2 = 0xFF >> 2
    (0 to (0xff >> 2)).map { mask =>
      Direction.values.foldLeft(DefaultShape) { (shape, side) =>
        if (((1 << side.get3DDataValue) & mask) != 0) {
          VoxelShapes.or(shape, CachedParts(side.ordinal()))
        } else {
          shape
        }
      }
    }.toArray
  }

  def mask(side: Direction, value: Int = 0): Int = {
    value | (1 << side.get3DDataValue)
  }

  def shape(state: BlockState): VoxelShape = {
    var result = 0

    for (side <- Direction.values) {
      val sideShape = CableHelper.getCableShape(state, side)

      if (sideShape != PropertyCableConnection.Shape.NONE) {
        result = mask(side, result)
      }
    }

    CachedBounds(result)
  }

  def updateState(state: BlockState, tileEntity: TileEntity, defaultColor: Int, fromSide: Direction, fromState: BlockState, world: IBlockReader, fromPos: BlockPos): BlockState = {
    val neighborTileEntity = world.getBlockEntity(fromPos)

    if (!hasInitializedConnectionColor(tileEntity) || !hasInitializedConnectionColor(neighborTileEntity)) {
      return CableHelper.helperSetCableShapeState(state, fromSide, PropertyCableConnection.Shape.NONE)
    }

    if (neighborTileEntity != null && neighborTileEntity.getLevel != null) {
      val neighborHasNode = hasNetworkNode(neighborTileEntity, fromSide.getOpposite)

      val canConnectColor = canConnectBasedOnColor(tileEntity, neighborTileEntity, defaultColor)

      if (neighborHasNode && canConnectColor) {
        val shape = if (fromState.is(state.getBlock)) PropertyCableConnection.Shape.CABLE else PropertyCableConnection.Shape.DEVICE

        return CableHelper.helperSetCableShapeState(state, fromSide, shape)
      }
    }

    CableHelper.helperSetCableShapeState(state, fromSide, PropertyCableConnection.Shape.NONE)
  }

  private def hasInitializedConnectionColor(tileEntity: TileEntity): Boolean = {
    tileEntity match {
      case cable: blockentity.Cable => cable.isConnectionColorInitialized
      case _ => true
    }
  }

  private def hasNetworkNode(tileEntity: TileEntity, side: Direction): Boolean = {
    if (tileEntity == null) {
      return false
    }

    if (tileEntity.isInstanceOf[blockentity.RobotProxy]) {
      return false
    }

    val sidedCapability = tileEntity.getCapability(Capabilities.SidedEnvironmentCapability, side)

    if (sidedCapability.isPresent) {
      val host = sidedCapability.orElse(null)

      if (host != null) {
        return if (tileEntity.getLevel.isClientSide) host.canConnect(side) else host.sidedNode(side) != null
      }
    }

    val environmentCapability = tileEntity.getCapability(Capabilities.EnvironmentCapability, side)

    environmentCapability.isPresent
  }

  private def getConnectionColor(stack: ItemStack): Int = {
    val color = ItemColorizer.getColor(stack)

    if (color == -1) {
      DefaultConnectionColor
    } else {
      color
    }
  }

  private def getConnectionColor(tileEntity: TileEntity): Int = {
    tileEntity match {
      case cable: blockentity.Cable if cable.controlsConnectivity =>
        return cable.getColor
      case _ =>
    }

    if (tileEntity != null) {
      val capability = tileEntity.getCapability(Capabilities.ColoredCapability, null)
      val colored = capability.orElse(null)

      if (colored != null && colored.controlsConnectivity) {
        return colored.getColor
      }
    }

    DefaultConnectionColor
  }

  private def canConnectBasedOnColor(te1: TileEntity, te2: TileEntity, c1Default: Int = DefaultConnectionColor): Boolean = {
    val c1 = if (te1 == null) c1Default else getConnectionColor(te1)
    val c2 = getConnectionColor(te2)

    c1 == c2 || c1 == DefaultConnectionColor || c2 == DefaultConnectionColor
  }
}
