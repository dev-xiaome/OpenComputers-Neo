package li.cil.oc.server.component

import java.util
import java.util.UUID

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, StringTag, Tag}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.{Entity, Mob}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 拴绳升级：把宿主附近指定方向的生物拴到宿主身上。
 *
 * ==1.21.1 迁移要点==
 *  - 1.7.10 的 `EntityLiving#allowLeashing / setLeashedToEntity / getLeashedToEntity /
 *    clearLeashed` 在 1.21.1 归入 `Leashable`（`Mob` 实现）：
 *    `allowLeashing` → `mayBeLeashed`（`!isLeashed && canBeLeashed`，语义与旧版一致），
 *    `setLeashedToEntity(e, true)` → `setLeashedTo(e, true)`，
 *    `getLeashedToEntity` → `getLeashHolder`，`clearLeashed(true, false)` → `dropLeash(true, false)`。
 *    只有 `Mob` 能被拴住，因此类型过滤从 `EntityLiving` 改为 `Mob`。
 *  - `Entity#getUniqueID` → `Entity#getUUID`。
 *  - `AABB#expand` → `inflate`，`AABB#offset` → `move`，`AABB#func_111270_a` → `minmax`（取并集）。
 *  - `ForgeDirection#offsetX/Y/Z` → `Direction#getStepX/Y/Z`。
 *  - `Constants.NBT.TAG_STRING` → `Tag.TAG_STRING`，`StringTag#func_150285_a_` → `getAsString`。
 *
 * ==已知降级==
 * TODO(server): 1.7.10 用 `li.cil.oc.common.EventHandler.scheduleServer` 把「重新拴住读档时
 * 记录的生物」推迟到服务端下一 tick（此时实体已全部载入）。该对象位于尚未移植的
 * `li.cil.oc.common` 包，这里改用 `MinecraftServer#execute`，同样是排到服务端线程的下一个执行点，
 * 语义等价。
 */
class UpgradeLeash(val host: Entity) extends prefab.ManagedEnvironment with traits.WorldAware with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("leash").
    create()

  final val MaxLeashedEntities = 8

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Leash",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "FlockControl (FC-3LS)",
    DeviceAttribute.Capacity -> MaxLeashedEntities.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  val leashedEntities = mutable.Set.empty[UUID]

  override def position = BlockPosition(host)

  @Callback(doc = """function(side:number):boolean -- Tries to put an entity on the specified side of the device onto a leash.""")
  def leash(context: Context, args: Arguments): Array[AnyRef] = {
    if (leashedEntities.size >= MaxLeashedEntities) return result(Unit, "too many leashed entities")
    val side = args.checkSideAny(0)
    val nearBounds = position.bounds
    val farBounds = nearBounds.move(side.getStepX * 2.0, side.getStepY * 2.0, side.getStepZ * 2.0)
    // `func_111270_a` 取两个包围盒的并集，对应 1.21.1 的 `AABB#minmax`。
    val bounds = nearBounds.minmax(farBounds)
    entitiesInBounds[Mob](bounds).find(_.mayBeLeashed) match {
      case Some(entity) =>
        entity.setLeashedTo(host, true)
        leashedEntities += entity.getUUID
        context.pause(0.1)
        result(true)
      case _ => result(Unit, "no unleashed entity")
    }
  }

  @Callback(doc = """function() -- Unleashes all currently leashed entities.""")
  def unleash(context: Context, args: Arguments): Array[AnyRef] = {
    unleashAll()
    null
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      unleashAll()
    }
  }

  private def unleashAll(): Unit = {
    entitiesInBounds[Mob](position.bounds.inflate(5, 5, 5)).foreach(entity => {
      if (leashedEntities.contains(entity.getUUID) && entity.getLeashHolder == host) {
        entity.dropLeash(true, false)
      }
    })
    leashedEntities.clear()
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    leashedEntities ++= nbt.getList("leashedEntities", Tag.TAG_STRING).
      map((s: StringTag) => UUID.fromString(s.getAsString))
    // Re-acquire leashed entities. Need to do this manually because leashed
    // entities only remember their leashee if it's an LivingEntity...
    scheduleServer {
      val foundEntities = mutable.Set.empty[UUID]
      entitiesInBounds[Mob](position.bounds.inflate(5, 5, 5)).foreach(entity => {
        if (leashedEntities.contains(entity.getUUID)) {
          entity.setLeashedTo(host, true)
          foundEntities += entity.getUUID
        }
      })
      val missing = leashedEntities.diff(foundEntities)
      if (missing.nonEmpty) {
        OpenComputers.log.info(s"Could not find ${missing.size} leashed entities after loading!")
        leashedEntities --= missing
      }
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.setNewTagList("leashedEntities", leashedEntities.map(_.toString))
  }

  /** 把任务排到服务端线程的下一个执行点（替代 `common.EventHandler.scheduleServer`）。 */
  private def scheduleServer(f: => Unit): Unit = host.level() match {
    case level: ServerLevel =>
      val server = level.getServer
      if (server != null) {
        try server.execute(() => f) catch {
          case _: Throwable => // 服务端已关闭等情况下直接忽略。
        }
      }
    case _ => // 客户端没有服务端调度器。
  }
}
