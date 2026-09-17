package li.cil.oc.common.tileentity.traits

import java.util

import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * 红石输入/输出状态变化事件（对应 1.7.10 的 `RedstoneChangedEventArgs`）。
 *
 * `side` 为**全局**方向；`color` 为 `-1` 表示普通（非捆绑）红石，`0..15` 表示捆绑颜色。
 */
case class RedstoneChangedEventArgs(side: Direction, oldValue: Int, newValue: Int, color: Int = -1)

/**
 * 支持原版红石输入/输出的方块实体 trait（对应 1.7.10 的 `traits.RedstoneAware`）。
 *
 * ==1.21.1 迁移要点==
 *  - `ForgeDirection` → `Direction`（`Direction` 没有 `UNKNOWN`，所有参数都是真实方向）。
 *  - `entity.ordinal` / `ordinal()` 语义不变：DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5。
 *  - `world.notifyBlocksOfNeighborChange(pos, block)` → `world.updateNeighborsAt(...)`
 *    （见 [[li.cil.oc.util.ExtendedWorld]]）。
 *  - 客户端同步原为 `ServerPacketSender.sendRedstoneState(this)`，`server` 包未移植，
 *    退化为方块更新（同步标签里已经带上 `output` / `isOutputEnabled`）。
 *
 * ==降级说明==
 * 原实现同时实现 RedLogic 的 `IConnectable` / `IRedstoneEmitter` / `IRedstoneUpdatable`
 * 三个第三方接口（`mods.immibis.redlogic.api.wiring.*`），并带 `@Optional.Method` 注解。
 * 这些接口随 1.21.1 的 ASM/接口注入层一起取消，且 RedLogic 本身未移植，因此相关方法
 * （`connects` / `connectsAroundCorner` / `getEmittedSignalStrength` / 无参
 * `onRedstoneInputChanged`）被移除，恢复 RedLogic 集成时请在独立驱动里重新实现。
 */
trait RedstoneAware extends RotationAware {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  protected[tileentity] val _input: Array[Int] = Array.fill(6)(-1)

  protected[tileentity] val _output: Array[Int] = Array.fill(6)(0)

  protected var _isOutputEnabled: Boolean = false

  def isOutputEnabled: Boolean = _isOutputEnabled

  protected var shouldUpdateInput: Boolean = true

  def setOutputEnabled(value: Boolean): Unit = {
    if (value != _isOutputEnabled) {
      _isOutputEnabled = value
      if (!value) {
        for (i <- _output.indices) {
          _output(i) = 0
        }
      }
      onRedstoneOutputEnabledChanged()
    }
  }

  // ----------------------------------------------------------------------- //

  /**
   * 兼容 1.7.10 的「模糊查表」：Lua 侧传进来的 Map 的键可能是 `Integer` 或 `Double`。
   *
   * TODO(integration): `new Integer(key)` 在 Java 9+ 已废弃，这里改用 `Integer.valueOf`（语义相同）。
   */
  protected def getObjectFuzzy(map: util.Map[_, _], key: Int): Option[AnyRef] = {
    val refMap: util.Map[AnyRef, AnyRef] = map.asInstanceOf[util.Map[AnyRef, AnyRef]]
    val boxed = Integer.valueOf(key)
    if (refMap.containsKey(boxed))
      Option(refMap.get(boxed))
    else if (refMap.containsKey(boxed * 1.0))
      Option(refMap.get(boxed * 1.0))
    else if (refMap.containsKey(key * 1.0))
      Option(refMap.get(key * 1.0))
    else
      None
  }

  protected def valueToInt(value: AnyRef): Option[Int] = {
    value match {
      case Some(num: Number) => Option(num.intValue)
      case _ => None
    }
  }

  // ----------------------------------------------------------------------- //

  def getInput: Array[Int] = _input.map(math.max(_, 0))

  def getInput(side: Direction): Int = _input(side.ordinal) max 0

  def setInput(side: Direction, newInput: Int): Unit = {
    val oldInput = _input(side.ordinal())
    _input(side.ordinal()) = newInput
    if (oldInput >= 0 && newInput != oldInput) {
      onRedstoneInputChanged(RedstoneChangedEventArgs(side, oldInput, newInput))
    }
  }

  def setInput(values: Array[Int]): Unit = {
    for (side <- Direction.values()) {
      val value = if (side.ordinal <= values.length) values(side.ordinal) else 0
      setInput(side, value)
    }
  }

  def maxInput: Int = _input.map(math.max(_, 0)).max

  def getOutput: Array[Int] = Direction.values().map { side: Direction => _output(toLocal(side).ordinal) }

  def getOutput(side: Direction): Int = Option(_output) match {
    case Some(output) => output(toLocal(side).ordinal())
    case _ => 0
  }

  def setOutput(side: Direction, value: Int): Boolean = {
    if (value == getOutput(side)) return false
    _output(toLocal(side).ordinal()) = value
    onRedstoneOutputChanged(side)
    true
  }

  def setOutput(values: util.Map[_, _]): Boolean = {
    var changed: Boolean = false
    Direction.values().foreach(side => {
      val sideIndex = toLocal(side).ordinal
      // due to a bug in our jnlua layer, I cannot loop the map
      valueToInt(getObjectFuzzy(values, sideIndex)) match {
        case Some(num: Int) if setOutput(side, num) => changed = true
        case _ =>
      }
    })
    changed
  }

  /** 请求在下一个服务端 tick 重新采样红石输入（原 `checkRedstoneInputChanged`）。 */
  def checkRedstoneInputChanged(): Unit = {
    shouldUpdateInput = isServer
  }

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    if (isServer) {
      if (shouldUpdateInput) {
        shouldUpdateInput = false
        Direction.values().foreach(updateRedstoneInput)
      }
    }
  }

  override def initialize(): Unit = {
    super.initialize()
    // 不参与 tick 的方块实体需要主动延迟一 tick 采样一次红石输入（对齐 OCCE 的 `clearRemoved` 分支）：
    // 在自身与邻居方块实体都尚未就绪时立即采样会读到错误的红石状态。
    if (!canUpdate && isServer) {
      EventHandler.scheduleServer(() => Direction.values().foreach(updateRedstoneInput))
    }
  }

  /**
   * 重新采样 `side` 方向的红石输入。
   *
   * TODO(integration.util.BundledRedstone): 原实现走 `integration.util.BundledRedstone` 的
   * provider 链（vanilla / RedLogic / ProjectRed / BluePower / MFR）。`integration` 包尚未移植，
   * 这里内联 vanilla provider 的等价逻辑
   * （`ModVanilla.RedstoneProvider.computeInput`）：取「方块自身朝该侧的输出」与
   * 「该侧邻居的间接信号」的较大值，红石线则额外取其充能等级。
   */
  def updateRedstoneInput(side: Direction): Unit = setInput(side, computeVanillaRedstoneInput(side))

  /**
   * 原版（vanilla）红石输入计算，等价于 1.7.10 的
   * `integration.vanilla.ModVanilla#computeInput(pos, side)`。
   */
  protected def computeVanillaRedstoneInput(side: Direction): Int = {
    if (world == null) return 0
    val neighbor = position.offset(side)
    if (!world.isLoaded(neighbor.toChunkCoordinates)) return 0
    val state = world.getBlockState(neighbor.toChunkCoordinates)
    val wireLevel =
      if (state.getBlock == Blocks.REDSTONE_WIRE) state.getValue(RedStoneWireBlock.POWER).intValue()
      else 0
    // `computeRedstoneSignal` 即原 `world.computeRedstoneSignal(pos, side)`：
    // max(方块自身朝该侧的输出, 来自该侧的间接信号)。
    math.max(world.computeRedstoneSignal(position, side), wireLevel)
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)

    val input = nbt.getIntArray(Settings.namespace + "rs.input")
    input.copyToArray(_input, 0, input.length min _input.length)
    val output = nbt.getIntArray(Settings.namespace + "rs.output")
    output.copyToArray(_output, 0, output.length min _output.length)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)

    nbt.putIntArray(Settings.namespace + "rs.input", _input)
    nbt.putIntArray(Settings.namespace + "rs.output", _output)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    _isOutputEnabled = nbt.getBoolean("isOutputEnabled")
    nbt.getIntArray("output").copyToArray(_output)
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("isOutputEnabled", _isOutputEnabled)
    nbt.putIntArray("output", _output)
  }

  // ----------------------------------------------------------------------- //

  protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {}

  protected def onRedstoneOutputEnabledChanged(): Unit = {
    if (world != null) {
      world.notifyBlocksOfNeighborChange(position, block)
      // 服务端发专用 RedstoneState 包，客户端走方块更新（对齐 OCCE）。
      if (isServer) ServerPacketSender.sendRedstoneState(this)
      else markBlockForUpdate()
    }
  }

  protected def onRedstoneOutputChanged(side: Direction): Unit = {
    if (world != null) {
      val blockPos = position.offset(side)
      world.notifyBlockOfNeighborChange(blockPos, block)
      world.notifyBlocksOfNeighborChange(blockPos, world.getBlock(blockPos), side.getOpposite)

      // 服务端发专用 RedstoneState 包，客户端走方块更新（对齐 OCCE）。
      if (isServer) ServerPacketSender.sendRedstoneState(this)
      else markBlockForUpdate()
    }
  }
}
