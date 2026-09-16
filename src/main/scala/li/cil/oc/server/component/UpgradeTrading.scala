package li.cil.oc.server.component

import java.util
import java.util.UUID

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.phys.Vec3

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class UpgradeTrading(val host: EnvironmentHost) extends prefab.ManagedEnvironment with traits.WorldAware with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("trading").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Trading upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Capitalism H.O. 1200T"
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  override def position = BlockPosition(host)

  def maxRange = Settings.get.tradingRange

  // 1.21.1：`Vec3.createVectorHelper(x, y, z)` → `new Vec3(x, y, z)`，
  // `Entity#posX/posY/posZ` → `getX/getY/getZ`。
  def isInRange(entity: Entity) = new Vec3(entity.getX, entity.getY, entity.getZ).distanceTo(position.toVec3) <= maxRange

  @Callback(doc = "function():table -- Returns a table of trades in range as userdata objects.")
  def getTrades(context: Context, args: Arguments): Array[AnyRef] = {
    // 1.21.1：`AABB#expand` → `AABB#inflate`；
    // 商人的接口由 `net.minecraft.entity.IMerchant` 改为 `net.minecraft.world.item.trading.Merchant`，
    // 交易表由 `getRecipes(null)` 改为 `getOffers`，持久 id 由 `getPersistentID` 改为 `getUUID`。
    val merchants = entitiesInBounds[Entity](position.bounds.inflate(maxRange, maxRange, maxRange)).
      filter(isInRange).
      collect { case merchant: Merchant => merchant }
    var nextId = 1
    val idMap = mutable.Map[UUID, Int]()
    for (id: UUID <- merchants.map(merchantUuid).sorted) {
      idMap.put(id, nextId)
      nextId += 1
    }
    // sorting the result is not necessary, but will help the merchant trades line up nicely by merchant
    result(merchants.sortBy(merchantUuid).flatMap(merchant => offersOf(merchant).indices.map(index => {
      new Trade(this, merchant, index, idMap(merchantUuid(merchant)))
    })))
  }

  /** 商人的持久唯一 id（1.7.10 的 `IMerchant#getPersistentID`）。 */
  private def merchantUuid(merchant: Merchant): UUID = merchant match {
    case entity: Entity => entity.getUUID
    case _ => new UUID(0L, 0L)
  }

  /** 商人的交易列表；非实体实现（理论上不存在）时退化为空。 */
  private def offersOf(merchant: Merchant): IndexedSeq[Int] = {
    val offers = merchant.getOffers
    if (offers == null) IndexedSeq.empty else offers.indices
  }
}
