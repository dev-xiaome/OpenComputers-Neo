package li.cil.oc.common.tileentity.traits

import java.util

import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.Tag

/**
 * 支持「捆绑红石」（bundled redstone）/ RedNet 的方块实体 trait
 * （对应 1.7.10 的 `traits.BundledRedstoneAware`）。
 *
 * ==1.21.1 迁移要点==
 *  - `ForgeDirection` → `Direction`；1.21.1 的 `Direction` 序数与 1.7.10 的
 *    `ForgeDirection` 完全一致（DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5），
 *    因此 `_bundledInput` / `_bundledOutput` 的下标语义不变。
 *  - `NBTTagList` + `func_150302_c()` → `ListTag` + `IntArrayTag#getAsIntArray`。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`。
 *
 * ==降级说明==
 * 原实现实现 RedLogic 的 `IBundledEmitter` / `IBundledUpdatable` 与 ProjectRed 的
 * `IBundledTile`，并直接引用 MFR 的 `IRedNetNetworkContainer`。这些模组 API 全部未移植
 * （且 1.21.1 已移除 ASM 接口注入），因此：
 *  - 三个第三方接口不再实现，`getBundledCableStrength` / `onBundledInputChanged` /
 *    `canConnectBundled` / `getBundledSignal` 被移除；恢复集成时请在独立驱动中重新实现；
 *  - `Mods.MineFactoryReloaded` 分支（RedNet 网络刷新）与 `Mods.ProjectRedTransmission`
 *    分支被移除，仅保留 OC 自身的捆绑输入/输出 API 与对外通知。
 */
trait BundledRedstoneAware extends RedstoneAware {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  protected[tileentity] val _bundledInput = Array.fill(6)(Array.fill(16)(-1))

  protected[tileentity] val _rednetInput = Array.fill(6)(Array.fill(16)(-1))

  protected[tileentity] val _bundledOutput = Array.fill(6)(Array.fill(16)(0))

  // ----------------------------------------------------------------------- //

  override def setOutputEnabled(value: Boolean): Unit = {
    if (value != _isOutputEnabled) {
      if (!value) {
        for (i <- _bundledOutput.indices) {
          for (j <- _bundledOutput(i).indices) {
            _bundledOutput(i)(j) = 0
          }
        }
      }
    }
    super.setOutputEnabled(value)
  }

  def getBundledInput: Array[Array[Int]] = {
    (0 until 6).map(side => (0 until 16).map(color => _bundledInput(side)(color) max _rednetInput(side)(color) max 0).toArray).toArray
  }

  private def checkSide(side: Direction): Int = {
    val index = side.ordinal
    if (index >= 6) throw new IndexOutOfBoundsException(s"Bad side $side")
    index
  }

  private def checkColor(color: Int): Int = {
    if (color < 0 || color >= 16) throw new IndexOutOfBoundsException(s"Bad color $color")
    color
  }

  def getBundledInput(side: Direction): Array[Int] = {
    val sideIndex = checkSide(side)
    val bundled = _bundledInput(sideIndex)
    val rednet = _rednetInput(sideIndex)
    (bundled, rednet).zipped.map((a, b) => a max b max 0)
  }

  def getBundledInput(side: Direction, color: Int): Int = {
    val sideIndex = checkSide(side)
    val colorIndex = checkColor(color)
    val bundled = _bundledInput(sideIndex)(colorIndex)
    val rednet = _rednetInput(sideIndex)(colorIndex)
    bundled max rednet max 0
  }

  def setBundledInput(side: Direction, color: Int, newValue: Int): Unit = {
    updateInput(_bundledInput, side, color, newValue)
  }

  def setBundledInput(side: Direction, newBundledInput: Array[Int]): Unit = {
    for (color <- 0 until 16) {
      val value = if (newBundledInput == null || color >= newBundledInput.length) 0 else newBundledInput(color)
      setBundledInput(side, color, value)
    }
  }

  def setRednetInput(side: Direction, color: Int, value: Int): Unit = updateInput(_rednetInput, side, color, value)

  def updateInput(inputs: Array[Array[Int]], side: Direction, color: Int, newValue: Int): Unit = {
    val sideIndex = checkSide(side)
    val colorIndex = checkColor(color)
    val oldValue = inputs(sideIndex)(colorIndex)
    if (oldValue != newValue) {
      inputs(sideIndex)(colorIndex) = newValue
      if (oldValue != -1) {
        onRedstoneInputChanged(RedstoneChangedEventArgs(side, oldValue, newValue, colorIndex))
      }
    }
  }

  /** 捆绑输出缓存（注意：与原 1.7.10 实现一致，返回的是 `_bundledInput` 的引用）。 */
  def getBundledOutput: Array[Array[Int]] = _bundledInput

  def getBundledOutput(side: Direction): Array[Int] = _bundledOutput(checkSide(toLocal(side)))

  def getBundledOutput(side: Direction, color: Int): Int = getBundledOutput(side)(checkColor(color))

  /**
   * 通知外界某一侧的捆绑输出发生了变化。
   *
   * TODO(integration): 原实现在此处额外刷新 MFR 的 RedNet 网络
   * （`Mods.MineFactoryReloaded` 可用时对 `IRedNetNetworkContainer` 调用 `updateNetwork`）。
   * MFR 集成不再移植，该分支已移除。
   */
  def notifyChangedSide(side: Direction): Unit = {
    onRedstoneOutputChanged(side)
  }

  def setBundledOutput(side: Direction, color: Int, value: Int): Boolean = if (value != getBundledOutput(side, color)) {
    _bundledOutput(checkSide(toLocal(side)))(checkColor(color)) = value
    notifyChangedSide(side)
    true
  } else false

  def setBundledOutput(side: Direction, values: util.Map[_, _]): Boolean = {
    val sideIndex = toLocal(side).ordinal
    var changed: Boolean = false
    (0 until 16).foreach(color => {
      // due to a bug in our jnlua layer, I cannot loop the map
      valueToInt(getObjectFuzzy(values, color)) match {
        case Some(newValue: Int) =>
          if (newValue != getBundledOutput(side, color)) {
            _bundledOutput(sideIndex)(color) = newValue
            changed = true
          }
        case _ =>
      }
    })
    if (changed) {
      notifyChangedSide(side)
    }
    changed
  }

  def setBundledOutput(values: util.Map[_, _]): Boolean = {
    var changed: Boolean = false
    Direction.values().foreach(side => {
      val sideIndex = toLocal(side).ordinal
      // due to a bug in our jnlua layer, I cannot loop the map
      getObjectFuzzy(values, sideIndex) match {
        case Some(child: util.Map[_, _]) if setBundledOutput(side, child) => changed = true
        case _ =>
      }
    })
    changed
  }

  // ----------------------------------------------------------------------- //

  override def updateRedstoneInput(side: Direction): Unit = {
    super.updateRedstoneInput(side)
    // TODO(integration.util.BundledRedstone): 原为 `BundledRedstone.computeBundledInput(position, side)`。
    // 没有任何 provider 时原实现返回 `null`，`setBundledInput` 会据此把该侧 16 个颜色全部清零，
    // 这里保持完全一致的行为；恢复 RedLogic / ProjectRed / BluePower / MFR 集成时请改回 provider 链。
    setBundledInput(side, null: Array[Int])
  }

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)

    nbt.getList(Settings.namespace + "rs.bundledInput", Tag.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.getAsIntArray).zipWithIndex.foreach {
      case (input, index) if index < _bundledInput.length =>
        val safeLength = input.length min _bundledInput(index).length
        input.copyToArray(_bundledInput(index), 0, safeLength)
      case _ =>
    }
    nbt.getList(Settings.namespace + "rs.bundledOutput", Tag.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.getAsIntArray).zipWithIndex.foreach {
      case (input, index) if index < _bundledOutput.length =>
        val safeLength = input.length min _bundledOutput(index).length
        input.copyToArray(_bundledOutput(index), 0, safeLength)
      case _ =>
    }

    nbt.getList(Settings.namespace + "rs.rednetInput", Tag.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.getAsIntArray).zipWithIndex.foreach {
      case (input, index) if index < _rednetInput.length =>
        val safeLength = input.length min _rednetInput(index).length
        input.copyToArray(_rednetInput(index), 0, safeLength)
      case _ =>
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)

    nbt.setNewTagList(Settings.namespace + "rs.bundledInput", _bundledInput.map(rows => new IntArrayTag(rows)).toIndexedSeq)
    nbt.setNewTagList(Settings.namespace + "rs.bundledOutput", _bundledOutput.map(rows => new IntArrayTag(rows)).toIndexedSeq)

    nbt.setNewTagList(Settings.namespace + "rs.rednetInput", _rednetInput.map(rows => new IntArrayTag(rows)).toIndexedSeq)
  }

  // ----------------------------------------------------------------------- //

  override protected def onRedstoneOutputEnabledChanged(): Unit = {
    // TODO(integration): 原实现额外刷新 MFR 的 RedNet 网络（逐侧查找 `IRedNetNetworkContainer`）。
    // MFR 集成不再移植，该分支已移除。
    super.onRedstoneOutputEnabledChanged()
  }
}
