package li.cil.oc.common.item.data

import com.google.common.base.Charsets
import com.google.common.base.Strings
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.{Constants, OpenComputers, Settings}
import li.cil.oc.api
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack

import scala.io.Source
import scala.jdk.CollectionConverters._

object RobotData {
  /** 机器人名字表（`assets/opencomputers_neo/robot.names`）。 */
  val names: Array[String] = try {
    Source.fromInputStream(getClass.getResourceAsStream(
      "/assets/" + Settings.resourceDomain + "/robot.names"))(Charsets.UTF_8).
      getLines().map(_.takeWhile(_ != '#').trim()).filter(_ != "").toArray
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn("Failed loading robot name list.", t)
      Array.empty[String]
  }

  def randomName: String = if (names.nonEmpty) names((math.random * names.length).toInt) else "Robot"

  /**
   * 判断驱动器是否是屏幕驱动。
   *
   * 原版直接与 `integration.opencomputers.DriverScreen`（单例 object）比较；
   * 该集成包属于后续阶段，为避免编译期依赖，这里按**类名**比较，
   * 同时兼容单例 object（`DriverScreen$`）与实例（`DriverScreen`）。
   */
  def isScreenDriver(driver: AnyRef): Boolean = driver != null && {
    val name = driver.getClass.getName
    name == "li.cil.oc.integration.opencomputers.DriverScreen" ||
      name == "li.cil.oc.integration.opencomputers.DriverScreen$"
  }
}

/**
 * 机器人数据（原 1.7.10 的 `RobotData`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_COMPOUND` → [[Tag.TAG_COMPOUND]]
 *  - `ItemStack.loadItemStackFromNBT` → [[StackSerializer.loadItemStack]]
 *  - `nbt.func_150296_c()`（旧版 `getKeySet`）→ [[CompoundTag#getAllKeys]]
 *  - `nbt.getInteger` → `nbt.getInt`
 *  - `Option(api.Driver.driverFor(cs))` 的匹配保留「驱动不存在则跳过」的语义
 */
class RobotData extends ItemData(Constants.BlockName.Robot) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var name: String = ""

  /** 总能量（含组件内能量）。 */
  var totalEnergy: Int = 0

  /** 机器人组件自身存储的能量（这是需要手动恢复的部分）。 */
  var robotEnergy: Int = 0

  var tier: Int = 0

  var components: Array[ItemStack] = Array.empty[ItemStack]

  var containers: Array[ItemStack] = Array.empty[ItemStack]

  var lightColor: Int = 0xF23030

  override def load(nbt: CompoundTag): Unit = {
    if (nbt.contains("display") && nbt.getCompound("display").contains("Name")) {
      name = nbt.getCompound("display").getString("Name")
    }
    if (Strings.isNullOrEmpty(name)) {
      name = RobotData.randomName
    }
    totalEnergy = nbt.getInt(Settings.namespace + "storedEnergy")
    robotEnergy = nbt.getInt(Settings.namespace + "robotEnergy")
    tier = nbt.getInt(Settings.namespace + "tier")
    components = StackSerializer.mapList(
      nbt.getList(Settings.namespace + "components", Tag.TAG_COMPOUND),
      StackSerializer.loadItemStack).toArray
    containers = StackSerializer.mapList(
      nbt.getList(Settings.namespace + "containers", Tag.TAG_COMPOUND),
      StackSerializer.loadItemStack).toArray
    if (nbt.contains(Settings.namespace + "lightColor")) {
      lightColor = nbt.getInt(Settings.namespace + "lightColor")
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    if (!Strings.isNullOrEmpty(name)) {
      if (!nbt.contains("display")) {
        nbt.put("display", new CompoundTag())
      }
      nbt.getCompound("display").putString("Name", name)
    }
    nbt.putInt(Settings.namespace + "storedEnergy", totalEnergy)
    nbt.putInt(Settings.namespace + "robotEnergy", robotEnergy)
    nbt.putInt(Settings.namespace + "tier", tier)
    nbt.setNewTagList(Settings.namespace + "components",
      components.filter(stack => stack != null && !stack.isEmpty).map(StackSerializer.toTag).toIterable)
    nbt.setNewTagList(Settings.namespace + "containers",
      containers.filter(stack => stack != null && !stack.isEmpty).map(StackSerializer.toTag).toIterable)
    nbt.putInt(Settings.namespace + "lightColor", lightColor)
  }

  def copyItemStack(): ItemStack = {
    val stack = createItemStack()
    // 清掉所有节点地址等信息。用于创造模式「拾取」机器人。
    val newInfo = new RobotData(stack)
    newInfo.components.foreach(cs => Option(api.Driver.driverFor(cs)) match {
      case Some(driver) if RobotData.isScreenDriver(driver) =>
        val nbt = driver.dataTag(cs)
        for (tagName <- nbt.getAllKeys.asScala.toArray) {
          nbt.remove(tagName)
        }
      case _ =>
    })
    // 不显示能量信息（不可靠），但把内部缓冲填满——反正只有创造模式会走到这。
    newInfo.totalEnergy = 0
    newInfo.robotEnergy = 50000
    newInfo.save(stack)
    stack
  }
}
