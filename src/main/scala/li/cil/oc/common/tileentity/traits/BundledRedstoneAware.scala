package li.cil.oc.common.tileentity.traits

import java.util

import net.neoforged.fml.common.Optional
import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedWorld._
import mods.immibis.redlogic.api.wiring.IBundledEmitter
import mods.immibis.redlogic.api.wiring.IBundledUpdatable
import mrtjp.projectred.api.IBundledTile
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraftforge.common.util.Constants.NBT
import net.minecraft.core.Direction
import powercrystals.minefactoryreloaded.api.rednet.IRedNetNetworkContainer

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = "mods.immibis.redlogic.api.wiring.IBundledEmitter", modid = Mods.IDs.RedLogic),
  new Optional.Interface(iface = "mods.immibis.redlogic.api.wiring.IBundledUpdatable", modid = Mods.IDs.RedLogic),
  new Optional.Interface(iface = "mrtjp.projectred.api.IBundledTile", modid = Mods.IDs.ProjectRedTransmission)
))
trait BundledRedstoneAware extends RedstoneAware with IBundledEmitter with IBundledUpdatable with IBundledTile {

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

  def getBundledOutput: Array[Array[Int]] = _bundledInput

  def getBundledOutput(side: Direction): Array[Int] = _bundledOutput(checkSide(toLocal(side)))

  def getBundledOutput(side: Direction, color: Int): Int = getBundledOutput(side)(checkColor(color))

  def notifyChangedSide(side: Direction): Unit = {
    if (Mods.MineFactoryReloaded.isAvailable) {
      val blockPos = BlockPosition(x, y, z).offset(side)
      world.getBlock(blockPos) match {
        case block: IRedNetNetworkContainer => block.updateNetwork(world, blockPos.x, blockPos.y, blockPos.z, side.getOpposite)
        case _ =>
      }
    }

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
    Direction.VALID_DIRECTIONS.foreach(side => {
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
    setBundledInput(side, BundledRedstone.computeBundledInput(position, side))
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)

    nbt.getList(Settings.namespace + "rs.bundledInput", NBT.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.func_150302_c()).zipWithIndex.foreach {
      case (input, index) if index < _bundledInput.length =>
        val safeLength = input.length min _bundledInput(index).length
        input.copyToArray(_bundledInput(index), 0, safeLength)
      case _ =>
    }
    nbt.getList(Settings.namespace + "rs.bundledOutput", NBT.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.func_150302_c()).zipWithIndex.foreach {
      case (input, index) if index < _bundledOutput.length =>
        val safeLength = input.length min _bundledOutput(index).length
        input.copyToArray(_bundledOutput(index), 0, safeLength)
      case _ =>
    }

    nbt.getList(Settings.namespace + "rs.rednetInput", NBT.TAG_INT_ARRAY).toArray[IntArrayTag].
      map(_.func_150302_c()).zipWithIndex.foreach {
      case (input, index) if index < _rednetInput.length =>
        val safeLength = input.length min _rednetInput(index).length
        input.copyToArray(_rednetInput(index), 0, safeLength)
      case _ =>
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)

    nbt.setNewTagList(Settings.namespace + "rs.bundledInput", _bundledInput.view)
    nbt.setNewTagList(Settings.namespace + "rs.bundledOutput", _bundledOutput.view)

    nbt.setNewTagList(Settings.namespace + "rs.rednetInput", _rednetInput.view)
  }

  // ----------------------------------------------------------------------- //

  override protected def onRedstoneOutputEnabledChanged(): Unit = {
    if (Mods.MineFactoryReloaded.isAvailable) {
      for (side <- Direction.VALID_DIRECTIONS) {
        val blockPos = BlockPosition(x, y, z).offset(side)
        world.getBlock(blockPos) match {
          case block: IRedNetNetworkContainer => block.updateNetwork(world, x, y, z, side.getOpposite)
          case _ =>
        }
      }
    }
    super.onRedstoneOutputEnabledChanged()
  }

  // ----------------------------------------------------------------------- //

  @Optional.Method(modid = Mods.IDs.RedLogic)
  def getBundledCableStrength(blockFace: Int, toDirection: Int): Array[Byte] = getBundledOutput(Direction.getOrientation(toDirection)).map(value => math.min(math.max(value, 0), 255).toByte)

  @Optional.Method(modid = Mods.IDs.RedLogic)
  def onBundledInputChanged(): Unit = checkRedstoneInputChanged()

  // ----------------------------------------------------------------------- //

  @Optional.Method(modid = Mods.IDs.ProjectRedTransmission)
  def canConnectBundled(side: Int): Boolean = _isOutputEnabled

  @Optional.Method(modid = Mods.IDs.ProjectRedTransmission)
  def getBundledSignal(side: Int): Array[Byte] = getBundledOutput(Direction.getOrientation(side)).map(value => math.min(math.max(value, 0), 255).toByte)
}
