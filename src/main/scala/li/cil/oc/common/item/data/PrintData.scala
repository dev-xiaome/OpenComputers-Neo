package li.cil.oc.common.item.data

import java.lang.reflect.Method

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.item.data.PrintData.Shape
import li.cil.oc.util.ExtendedAABB._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB

import scala.collection.mutable

/**
 * 3D 打印件数据（原 1.7.10 的 `PrintData`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_COMPOUND` → [[Tag.TAG_COMPOUND]]
 *  - `ListTag#map` 由 [[li.cil.oc.util.ExtendedNBT.ExtendedListTag]] 提供，直接可用
 *  - `AABB.getBoundingBox(...)` → `new AABB(...)`
 *  - `AABB#intersectsWith(other)` → `AABB#intersects(other)`
 *  - `nbt.getInteger` → `nbt.getInt`
 *  - `nbt.setNewCompoundTag(...)` 用于写 `stateOff/stateOn` 的地方改回 `setNewTagList`
 *    （[[li.cil.oc.util.ExtendedNBT]] 的实现早就只接受 `Tag` 列表）
 */
class PrintData extends ItemData(Constants.BlockName.Print) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var label: Option[String] = None
  var tooltip: Option[String] = None
  var isButtonMode: Boolean = false
  var redstoneLevel: Int = 0
  var pressurePlate: Boolean = false
  val stateOff: mutable.Set[PrintData.Shape] = mutable.Set.empty[PrintData.Shape]
  val stateOn: mutable.Set[PrintData.Shape] = mutable.Set.empty[PrintData.Shape]
  var isBeaconBase: Boolean = false
  var lightLevel: Int = 0
  var noclipOff: Boolean = false
  var noclipOn: Boolean = false

  def hasActiveState: Boolean = stateOn.nonEmpty

  def emitLight: Boolean = lightLevel > 0

  def emitRedstone: Boolean = redstoneLevel > 0

  def emitRedstone(state: Boolean): Boolean = if (state) emitRedstoneWhenOn else emitRedstoneWhenOff

  def emitRedstoneWhenOff: Boolean = emitRedstone && !hasActiveState

  def emitRedstoneWhenOn: Boolean = emitRedstone && hasActiveState

  def opacity: Float = {
    if (opacityDirty) {
      opacityDirty = false
      opacity_ = PrintData.computeApproximateOpacity(stateOn) min PrintData.computeApproximateOpacity(stateOff)
    }
    opacity_
  }

  // 惰性计算并缓存（可能较慢）。
  private var opacity_ : Float = 0f
  private var opacityDirty: Boolean = true

  override def load(nbt: CompoundTag): Unit = {
    label = if (nbt.contains("label")) Option(nbt.getString("label")) else None
    tooltip = if (nbt.contains("tooltip")) Option(nbt.getString("tooltip")) else None
    isButtonMode = nbt.getBoolean("isButtonMode")
    redstoneLevel = nbt.getInt("redstoneLevel") max 0 min 15
    if (nbt.getBoolean("emitRedstone")) redstoneLevel = 15
    pressurePlate = nbt.getBoolean("pressurePlate")
    stateOff.clear()
    stateOff ++= nbt.getList("stateOff", Tag.TAG_COMPOUND).map(PrintData.nbtToShape)
    stateOn.clear()
    stateOn ++= nbt.getList("stateOn", Tag.TAG_COMPOUND).map(PrintData.nbtToShape)
    isBeaconBase = nbt.getBoolean("isBeaconBase")
    lightLevel = (nbt.getByte("lightLevel") & 0xFF) max 0 min 15
    noclipOff = nbt.getBoolean("noclipOff")
    noclipOn = nbt.getBoolean("noclipOn")

    opacityDirty = true
  }

  override def save(nbt: CompoundTag): Unit = {
    label.foreach(nbt.putString("label", _))
    tooltip.foreach(nbt.putString("tooltip", _))
    nbt.putBoolean("isButtonMode", isButtonMode)
    nbt.putInt("redstoneLevel", redstoneLevel)
    nbt.putBoolean("pressurePlate", pressurePlate)
    setNewShapeSet(nbt, "stateOff", stateOff)
    setNewShapeSet(nbt, "stateOn", stateOn)
    nbt.putBoolean("isBeaconBase", isBeaconBase)
    nbt.putByte("lightLevel", lightLevel.toByte)
    nbt.putBoolean("noclipOff", noclipOff)
    nbt.putBoolean("noclipOn", noclipOn)
  }

  // 形状存在 Set 里，Set 无序，因此 NBT 里的形状列表顺序不固定。
  // NBT 列表比较会考虑顺序，导致「两个相同打印件」被判定为不同，
  // 所以序列化前先排序。
  private def setNewShapeSet(nbt: CompoundTag, name: String, values: Iterable[Shape]): Unit = {
    val seq = values.toSeq.sortWith(compareShape)
    nbt.setNewTagList(name, seq.map(PrintData.shapeToNBT))
  }

  private def compareShape(a: Shape, b: Shape): Boolean = {
    if (a.bounds.minX != b.bounds.minX) return a.bounds.minX > b.bounds.minX
    if (a.bounds.minY != b.bounds.minY) return a.bounds.minY > b.bounds.minY
    if (a.bounds.minZ != b.bounds.minZ) return a.bounds.minZ > b.bounds.minZ
    if (a.bounds.maxX != b.bounds.maxX) return a.bounds.maxX > b.bounds.maxX
    if (a.bounds.maxY != b.bounds.maxY) return a.bounds.maxY > b.bounds.maxY
    if (a.bounds.maxZ != b.bounds.maxZ) return a.bounds.maxZ > b.bounds.maxZ
    if (a.tint != b.tint) return a.tint.getOrElse(0) > b.tint.getOrElse(0)
    if (a.texture != b.texture) return a.texture > b.texture
    false
  }
}

object PrintData {
  // 用体积近似不透明度：精确体积既慢又不一定合适（例如点阵），
  // 因此把空间切成若干小块，检查每块里是否有形状；有则视为不透明。
  // 这样打印件永远不会完全不透光，既有一点阴影效果，又不会显得突兀。
  private val stepping = 4
  private val step = stepping / 16f
  private val invMaxVolume = 1f / (stepping * stepping * stepping)

  private val inkProviders = mutable.LinkedHashSet.empty[Method]

  def addInkProvider(provider: Method): Unit = inkProviders += provider

  def computeApproximateOpacity(shapes: Iterable[PrintData.Shape]): Float = {
    var volume = 1f
    if (shapes.nonEmpty) for (x <- 0 until 16 / stepping; y <- 0 until 16 / stepping; z <- 0 until 16 / stepping) {
      val bounds = new AABB(
        x * step, y * step, z * step,
        (x + 1) * step, (y + 1) * step, (z + 1) * step)
      if (!shapes.exists(_.bounds.intersects(bounds))) {
        volume -= invMaxVolume
      }
    }
    volume
  }

  def computeCosts(data: PrintData): Option[(Int, Int)] = {
    val totalVolume = data.stateOn.foldLeft(0)((acc, shape) => acc + shape.bounds.volume) +
      data.stateOff.foldLeft(0)((acc, shape) => acc + shape.bounds.volume)
    val totalSurface = data.stateOn.foldLeft(0)((acc, shape) => acc + shape.bounds.surface) +
      data.stateOff.foldLeft(0)((acc, shape) => acc + shape.bounds.surface)
    val multiplier = if (data.noclipOff || data.noclipOn) Settings.get.noclipMultiplier else 1

    if (totalVolume > 0) {
      val baseMaterialRequired = (totalVolume / 2) max 1
      val materialRequired =
        if (data.redstoneLevel > 0 && data.redstoneLevel < 15) baseMaterialRequired + Settings.get.printCustomRedstone
        else baseMaterialRequired
      val inkRequired = (totalSurface / 6) max 1

      Option(((materialRequired * multiplier).toInt, inkRequired))
    }
    else None
  }

  private val materialPerItem = Settings.get.printMaterialValue

  def materialValue(stack: ItemStack): Int = {
    if (api.Items.get(stack) == api.Items.get(Constants.ItemName.Chamelium))
      materialPerItem
    else if (api.Items.get(stack) == api.Items.get(Constants.BlockName.Print)) {
      val data = new PrintData(stack)
      computeCosts(data) match {
        case Some((materialRequired, _)) => (materialRequired * Settings.get.printRecycleRate).toInt
        case _ => 0
      }
    }
    else 0
  }

  /**
   * 墨水量查询：原版遍历 [[li.cil.oc.common.IMC]] 里注册的 provider。
   * `common/IMC.scala` 尚未移植，这里用**反射**调用 `IMC.tryInvokeStatic`，
   * 未移植时静默返回 0，从而不产生对未移植包的编译期依赖。
   */
  def inkValue(stack: ItemStack): Int = {
    for (provider <- inkProviders) {
      val value = invokeInkProvider(provider, stack)
      if (value > 0) {
        return value
      }
    }
    0
  }

  private def invokeInkProvider(provider: Method, stack: ItemStack): Int = {
    try {
      val imc = Class.forName("li.cil.oc.common.IMC")
      val module = imc.getField("MODULE$").get(null)
      val method = imc.getMethod("tryInvokeStatic", classOf[Method], classOf[scala.collection.immutable.Seq[_]])
      method.invoke(module, provider, Seq(stack)) match {
        case result: Array[_] if result.nonEmpty =>
          result(0) match {
            case value: java.lang.Number => value.intValue()
            case _ => 0
          }
        case _ => 0
      }
    }
    catch {
      case _: Throwable => 0
    }
  }

  def nbtToShape(nbt: CompoundTag): Shape = {
    val aabb =
      if (nbt.contains("minX")) {
        // 兼容更早的 dev 版创建的形状。
        val minX = nbt.getByte("minX") / 16f
        val minY = nbt.getByte("minY") / 16f
        val minZ = nbt.getByte("minZ") / 16f
        val maxX = nbt.getByte("maxX") / 16f
        val maxY = nbt.getByte("maxY") / 16f
        val maxZ = nbt.getByte("maxZ") / 16f
        new AABB(minX, minY, minZ, maxX, maxY, maxZ)
      }
      else {
        val bounds = nbt.getByteArray("bounds").padTo(6, 0.toByte)
        val minX = bounds(0) / 16f
        val minY = bounds(1) / 16f
        val minZ = bounds(2) / 16f
        val maxX = bounds(3) / 16f
        val maxY = bounds(4) / 16f
        val maxZ = bounds(5) / 16f
        new AABB(minX, minY, minZ, maxX, maxY, maxZ)
      }
    val texture = nbt.getString("texture")
    val tint = if (nbt.contains("tint")) Option(nbt.getInt("tint")) else None
    new Shape(aabb, texture, tint)
  }

  def shapeToNBT(shape: Shape): CompoundTag = {
    val nbt = new CompoundTag()
    nbt.putByteArray("bounds", Array(
      (shape.bounds.minX * 16).round.toByte,
      (shape.bounds.minY * 16).round.toByte,
      (shape.bounds.minZ * 16).round.toByte,
      (shape.bounds.maxX * 16).round.toByte,
      (shape.bounds.maxY * 16).round.toByte,
      (shape.bounds.maxZ * 16).round.toByte
    ))
    nbt.putString("texture", shape.texture)
    shape.tint.foreach(nbt.putInt("tint", _))
    nbt
  }

  class Shape(val bounds: AABB, val texture: String, val tint: Option[Int])
}
