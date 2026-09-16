package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.event.GeolyzerEvent
import li.cil.oc.api.event.GeolyzerEvent.Analyze
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity.{Robot => EntityRobot, Microcontroller}
import li.cil.oc.common.entity.{Drone => EntityDrone}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedWorld._
// 1.21.1：`result` 由 `server/component/package.scala` 的包对象提供；这里显式引入同一实现，
// 使本文件即使脱离包对象也能编译（与 `Drive.scala` 的处理一致）。
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.biome.Biomes
import net.neoforged.neoforge.common.NeoForge

import scala.jdk.CollectionConverters._
import scala.language.existentials

class Geolyzer(val host: EnvironmentHost) extends prefab.ManagedEnvironment with traits.WorldControl with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("geolyzer").
    withConnector().
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Geolyzer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Terrain Analyzer MkII",
    DeviceAttribute.Capacity -> Settings.get.geolyzerRange.toString
  )

  // 1.21.1：`DeviceInfo#getDeviceInfo` 返回 `java.util.Map`，Scala 的 `Map` 需要显式转换。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override protected def checkSideForAction(args: Arguments, n: Int): Direction = {
    val side = args.checkSideAny(n)
    host match {
      case robot: EntityRobot => robot.proxy.toGlobal(side)
      case drone: EntityDrone => drone.toGlobal(side)
      case uc: Microcontroller => uc.toLocal(side) // not really sure what it is reversed for microcontrollers
      case tablet: internal.Tablet => tablet.toGlobal(side)
      case _ => side
    }
  }

  override def position: BlockPosition = host match {
    case robot: EntityRobot => robot.proxy.position
    // 1.21.1：实体不再有 `posX/posY/posZ` 与 `world` 字段。
    case drone: EntityDrone => BlockPosition(drone.getX, drone.getY, drone.getZ, drone.level())
    case uc: Microcontroller => uc.position
    // 原实现此处匹配 `common.item.TabletWrapper`（1.7.10 的物品形态平板）。
    // TODO(integration.opencomputers.DriverTablet): `TabletWrapper` 未随 1.21.1 的
    // `common/item/Tablet.scala` 一起移植，这里改为匹配 `api.internal.Tablet` 宿主；
    // 平板位置直接取自 `EnvironmentHost`，语义等价（与下面的 fallback 分支结果相同）。
    case tablet: internal.Tablet => BlockPosition(tablet.xPosition, tablet.yPosition, tablet.zPosition, tablet.world)
    case _ => BlockPosition(host)
  }

  private def canSeeSky: Boolean = {
    val blockPos = position.offset(Direction.UP)
    // 1.21.1：`world.provider.hasNoSky` → `dimensionType().hasSkyLight()`；
    // `world.canBlockSeeTheSky(x, y, z)` → `world.canSeeSky(BlockPos)`。
    host.world.dimensionType().hasSkyLight() && host.world.canSeeSky(blockPos.toChunkCoordinates)
  }

  @Callback(doc = """function():boolean -- Returns whether there is a clear line of sight to the sky directly above.""")
  def canSeeSky(computer: Context, args: Arguments): Array[AnyRef] = {
    result(canSeeSky)
  }

  @Callback(doc = """function():boolean -- Return whether the sun is currently visible directly above.""")
  def isSunVisible(computer: Context, args: Arguments): Array[AnyRef] = {
    val blockPos = BlockPosition(host).offset(Direction.UP)
    result(
      host.world.isDay &&
      canSeeSky &&
      (isDesertBiome(blockPos.toChunkCoordinates) || (!host.world.isRaining && !host.world.isThundering)))
  }

  /**
   * 该位置是否是沙漠生物群系。
   *
   * 1.7.10 的 `getWorldChunkManager.getBiomeGenAt(x, z).isInstanceOf[BiomeGenDesert]` 在 1.21.1
   * 没有直接对应（生物群系改为注册表 + 标签）。这里用「注册表键等于 `minecraft:desert`」判定，
   * 与原实现「只认沙漠本体」的范围一致（不含恶地等其它干旱群系）。
   */
  private def isDesertBiome(blockPos: BlockPos): Boolean = {
    host.world.getBiome(blockPos).unwrapKey().map[Boolean](_.equals(Biomes.DESERT)).orElse(false)
  }

  @Callback(doc = """function(x:number, z:number[, y:number, w:number, d:number, h:number][, ignoreReplaceable:boolean|options:table]):table -- Analyzes the density of the column at the specified relative coordinates.""")
  def scan(computer: Context, args: Arguments): Array[AnyRef] = {
    val (minX, minY, minZ, maxX, maxY, maxZ, optIndex) = getScanArgs(args)
    val volume = (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1)
    if (volume > 64) throw new IllegalArgumentException("volume too large (maximum is 64)")
    // 1.21.1：`mapAsJavaMap` 已随 Scala 2.13 移除，改用 `CollectionConverters#asJava`。
    // 注意 `Arguments#optTable` 的签名是 `(Int, java.util.Map) => java.util.Map`
    // （见 `api.machine.Arguments`），默认值也要先转成 `java.util.Map`。
    val options = if (args.isBoolean(optIndex)) Map[AnyRef, AnyRef]("includeReplaceable" -> Boolean.box(!args.checkBoolean(optIndex))).asJava
      else args.optTable(optIndex, Map.empty[AnyRef, AnyRef].asJava)
    if (math.abs(minX) > Settings.get.geolyzerRange || math.abs(maxX) > Settings.get.geolyzerRange ||
      math.abs(minY) > Settings.get.geolyzerRange || math.abs(maxY) > Settings.get.geolyzerRange ||
      math.abs(minZ) > Settings.get.geolyzerRange || math.abs(maxZ) > Settings.get.geolyzerRange) {
      throw new IllegalArgumentException("location out of bounds")
    }

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result((), "not enough energy")

    val event = new GeolyzerEvent.Scan(host, options, minX, minY, minZ, maxX, maxY, maxZ)
    // 1.21.1：`MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`。
    NeoForge.EVENT_BUS.post(event)
    // 1.21.1 的事件没有 `Event.Result`，取消状态统一看 `ICancellableEvent#isCanceled`。
    if (event.isCanceled) result((), "scan was canceled")
    else result(event.data)
  }

  private def getScanArgs(args: Arguments) = {
    val minX = args.checkInteger(0)
    val minZ = args.checkInteger(1)
    if (args.isInteger(2) && args.isInteger(3) && args.isInteger(4) && args.isInteger(5)) {
      val minY = args.checkInteger(2)
      val w = args.checkInteger(3)
      val d = args.checkInteger(4)
      val h = args.checkInteger(5)
      val maxX = minX + w - 1
      val maxY = minY + h - 1
      val maxZ = minZ + d - 1

      (math.min(minX, maxX), math.min(minY, maxY), math.min(minZ, maxZ),
        math.max(minX, maxX), math.max(minY, maxY), math.max(minZ, maxZ),
        6)
    }
    else {
      (minX, -32, minZ, minX, 31, minZ, 2)
    }
  }

  @Callback(doc = """function(side:number[,options:table]):table -- Get some information on a directly adjacent block.""")
  def analyze(computer: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val side = args.checkSideAny(0)
    val globalSide = host match {
      case rotatable: internal.Rotatable => rotatable.toGlobal(side)
      case _ => side
    }
    // `Arguments#optTable` 的默认值参数是 `java.util.Map`，需要先转换。
    val options = args.optTable(1, Map.empty[AnyRef, AnyRef].asJava)

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result((), "not enough energy")

    val globalPos = BlockPosition(host).offset(globalSide)
    val event = new Analyze(host, options, globalPos.x, globalPos.y, globalPos.z)
    NeoForge.EVENT_BUS.post(event)
    if (event.isCanceled) result((), "scan was canceled")
    else result(event.data)
  }
  else result((), "not enabled in config")

  @Callback(doc = """function(side:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack representation of the block on the specified side in a database component.""")
  def store(computer: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideAny(0)
    val globalSide = host match {
      case rotatable: internal.Rotatable => rotatable.toGlobal(side)
      case _ => side
    }

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result((), "not enough energy")

    val blockPos = BlockPosition(host).offset(globalSide)
    // 1.21.1：方块不再有 metadata / damage 概念，
    // `Item.getItemFromBlock(block)` → `block.asItem()`；1.7.10 对「无对应物品的方块」返回
    // `null`，1.21.1 的 `asItem()` 则退化为 `Items.AIR`，因此这里把空气也视作「无物品表示」。
    // `block.damageDropped(metadata)` 已随 metadata 移除，等价物是默认 damage 0。
    val block = host.world.getBlock(blockPos)
    val item = block.asItem()
    if (item == null || item == Items.AIR) result((), "block has no registered item representation")
    else {
      val stack = new ItemStack(item, 1)
      DatabaseAccess.withDatabase(node, args.checkString(1), database => {
        val toSlot = args.checkSlot(database.data, 2)
        // 1.21.1：空槽返回 `ItemStack.EMPTY` 而不是 `null`。
        val nonEmpty = !database.getStackInSlot(toSlot).isEmpty
        database.setStackInSlot(toSlot, stack)
        result(nonEmpty)
      })
    }
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "tablet.use") message.source.host match {
      case machine: api.machine.Machine => (machine.host, message.data) match {
        case (tablet: internal.Tablet, Array(nbt: CompoundTag, stack: ItemStack, player: Player, blockPos: BlockPosition, side: Direction, hitX: java.lang.Float, hitY: java.lang.Float, hitZ: java.lang.Float)) =>
          if (node.tryChangeBuffer(-Settings.get.geolyzerScanCost)) {
            val event = new Analyze(host, Map.empty[AnyRef, AnyRef].asJava, blockPos.x, blockPos.y, blockPos.z)
            NeoForge.EVENT_BUS.post(event)
            if (!event.isCanceled) {
              // 1.21.1：`event.data` 是 `java.util.HashMap`，用 `entrySet().asScala` 遍历，
              // 不能写 1.7.10 的 `for ((key, value) <- event.data)` 元组解包。
              for (entry <- event.data.entrySet().asScala) {
                entry.getValue match {
                  case number: java.lang.Number => nbt.putDouble(entry.getKey, number.doubleValue())
                  case string: String if string.nonEmpty => nbt.putString(entry.getKey, string)
                  case _ => // Unsupported, ignore.
                }
              }
            }
          }
        case _ => // Ignore.
      }
      case _ => // Ignore.
    }
  }
}
