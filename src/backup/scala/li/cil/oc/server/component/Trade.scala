package li.cil.oc.server.component

import java.util.UUID

import li.cil.oc.Settings
import li.cil.oc.api.machine._
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.{Merchant, MerchantOffer}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._
import scala.ref.WeakReference

class Trade(val info: TradeInfo) extends AbstractValue {
  def this() = this(new TradeInfo())

  def this(upgrade: UpgradeTrading, merchant: Merchant, recipeID: Int, merchantID: Int) =
    this(new TradeInfo(upgrade.host, merchant, recipeID, merchantID))

  def maxRange = Settings.get.tradingRange

  def isInRange = (info.merchant.get, info.host) match {
    case (Some(merchant: Entity), Some(host)) => merchant.distanceToSqr(host.xPosition, host.yPosition, host.zPosition) < maxRange * maxRange
    case _ => false
  }

  // Queue the load because when load is called we can't access the world yet
  // and we need to access it to get the Robot's BlockEntity / Drone's Entity.
  override def load(nbt: CompoundTag) = Trade.deferToServer(info.host)(() => info.load(nbt))

  override def save(nbt: CompoundTag) = info.save(nbt)

  @Callback(doc = "function():number -- Returns a sort index of the merchant that provides this trade")
  def getMerchantId(context: Context, arguments: Arguments): Array[AnyRef] =
    result(info.merchantID)

  @Callback(doc = "function():table, table -- Returns the items the merchant wants for this trade.")
  def getInput(context: Context, arguments: Arguments): Array[AnyRef] =
    result(info.recipe.map(recipe => Trade.stackOrNull(recipe.getCostA)).orNull,
      if (info.recipe.exists(recipe => !recipe.getCostB.isEmpty)) info.recipe.map(recipe => Trade.stackOrNull(recipe.getCostB)).orNull else null)

  @Callback(doc = "function():table -- Returns the item the merchant offers for this trade.")
  def getOutput(context: Context, arguments: Arguments): Array[AnyRef] =
    result(info.recipe.map(recipe => Trade.stackOrNull(recipe.getResult)).orNull)

  @Callback(doc = "function():boolean -- Returns whether the merchant currently wants to trade this.")
  def isEnabled(context: Context, arguments: Arguments): Array[AnyRef] =
    result(info.merchant.get.exists(_ => !info.recipe.exists(_.isOutOfStock))) // Make sure merchant is neither dead/gone nor the recipe has been disabled.

  @Callback(doc = "function():boolean, string -- Returns true when trade succeeds and nil, error when not.")
  def trade(context: Context, arguments: Arguments): Array[AnyRef] = {
    // Make sure we can access an inventory.
    info.inventory match {
      case Some(inventory) =>
        // Make sure merchant hasn't died, it somehow gone or moved out of range and still wants to trade this.
        info.merchant.get match {
          case Some(merchant: Entity) if merchant.isAlive && isInRange =>
            if (!merchant.isAlive) {
              result(false, "trader died")
            } else if (!isInRange) {
              result(false, "out of range")
            } else {
              info.recipe match {
                case Some(recipe) =>
                  if (recipe.isOutOfStock) {
                    result(false, "trade is disabled")
                  } else {
                    if (!hasRoomForRecipe(inventory, recipe)) {
                      result(false, "not enough inventory space to trade")
                    } else {
                      if (completeTrade(inventory, recipe, exact = true) || completeTrade(inventory, recipe, exact = false)) {
                        result(true)
                      } else {
                        result(false, "not enough items to trade")
                      }
                    }
                  }
                case _ => result(false, "trade has become invalid")
              }
            }
          case _ => result(false, "trade has become invalid")
        }
      case _ => result(false, "trading requires an inventory upgrade to be installed")
    }
  }

  def hasRoomForRecipe(inventory: IItemHandler, recipe: MerchantOffer): Boolean = {
    val remainder = recipe.getResult.copy()
    InventoryUtils.insertIntoInventory(remainder, inventory, None, remainder.getCount, simulate = true)
    remainder.isEmpty
  }

  def completeTrade(inventory: IItemHandler, recipe: MerchantOffer, exact: Boolean): Boolean = {
    // Now we'll check if we have enough items to perform the trade, caching first
    info.merchant.get match {
      case Some(merchant) => {
        val firstInputStack = recipe.getCostA
        val secondInputStack = Option(recipe.getCostB).filter(!_.isEmpty)

        def containsAccumulativeItemStack(stack: ItemStack) =
          InventoryUtils.extractFromInventory(stack, inventory, Trade.anySide, simulate = true, exact = exact) == 0

        // Check if we have enough to perform the trade.
        if (!containsAccumulativeItemStack(firstInputStack) || !secondInputStack.forall(containsAccumulativeItemStack))
          return false

        // Now we need to check if we have enough inventory space to accept the item we get for the trade.
        val outputStack = recipe.getResult.copy()

        // We established that out inventory allows to perform the trade, now actually do the trade.
        InventoryUtils.extractFromInventory(firstInputStack, inventory, Trade.anySide, exact = exact)
        secondInputStack.map(InventoryUtils.extractFromInventory(_, inventory, Trade.anySide, exact = exact))
        InventoryUtils.insertIntoInventory(outputStack, inventory, None, outputStack.getCount)

        // Tell the merchant we used the recipe, so MC can disable it and/or enable more recipes.
        // 1.7.10 的 `IMerchant.useRecipe` 在 1.21.1 更名为 `Merchant#notifyTrade`。
        merchant.notifyTrade(recipe)
        true
      }
      case _ => false
    }
  }
}

object Trade {
  /**
   * 1.7.10 的 `ForgeDirection.UNKNOWN`（即 `Direction.DOWN`）在 1.21.1 没有对应常量；
   * 本文件的抽取调用都不会真正使用面（`InventoryUtils` 的 `side` 参数仅为兼容旧签名），
   * 因此统一传 `Direction.DOWN`。
   */
  private val anySide: Direction = Direction.DOWN

  /** `ItemStack.EMPTY`（1.21.1 的空物品语义）在 Lua 侧应表现为 `nil`。 */
  private def stackOrNull(stack: ItemStack): ItemStack =
    if (stack == null || stack.isEmpty) null else stack.copy()

  /**
   * 1.7.10 用 `li.cil.oc.common.EventHandler.scheduleServer` 把动作推迟到下一个服务端 tick；
   * `common/EventHandler` 位于尚未编译完成的 common 包，本批次不能引用。
   * 这里用原版 `MinecraftServer#executeIfPossible` 达到同样的「本 tick 之后执行」语义，
   * 主机不在服务端时（理论上不会发生）退化为立即执行。
   */
  private def deferToServer(host: Option[EnvironmentHost])(f: () => Unit): Unit =
    host.flatMap(h => Option(h.world)).flatMap(l => Option(l.getServer)) match {
      case Some(server) => server.executeIfPossible(() => f())
      case _ => f()
    }
}

class TradeInfo(var host: Option[EnvironmentHost], var merchant: WeakReference[Merchant], var recipeID: Int, var merchantID: Int) {
  def this() = this(None, new WeakReference[Merchant](null), -1, -1)

  def this(host: EnvironmentHost, merchant: Merchant, recipeID: Int, merchantID: Int) =
    this(Option(host), new WeakReference[Merchant](merchant), recipeID, merchantID)

  def recipe: Option[MerchantOffer] = merchant.get.flatMap(recipeAt)

  private def recipeAt(merchant: Merchant): Option[MerchantOffer] = {
    val offers = merchant.getOffers
    if (offers != null && recipeID >= 0 && recipeID < offers.size()) Option(offers.get(recipeID)) else None
  }

  def inventory = host match {
    case Some(agent: li.cil.oc.api.internal.Agent) => Option(agent.mainInventory())
    case _ => None
  }

  def load(nbt: CompoundTag): Unit = {
    val isEntity = nbt.getBoolean("hostIsEntity")
    // If drone we find it again by its UUID, if Robot we know the X/Y/Z of the BlockEntity.
    host = if (isEntity) loadHostEntity(nbt) else loadHostTileEntity(nbt)
    merchant = new WeakReference[Merchant](loadEntity(nbt, new UUID(nbt.getLong("merchantUUIDMost"), nbt.getLong("merchantUUIDLeast"))) match {
      case Some(merchant: Merchant) => merchant
      case _ => null
    })
    recipeID = nbt.getInt("recipeID")
    merchantID = if (nbt.contains("merchantID")) nbt.getInt("merchantID") else -1
  }

  def save(nbt: CompoundTag): Unit = {
    host match {
      case Some(entity: Entity) =>
        nbt.putBoolean("hostIsEntity", true)
        TradeInfo.setDimension(nbt, entity.level())
        nbt.putLong("hostUUIDLeast", entity.getUUID.getLeastSignificantBits)
        nbt.putLong("hostUUIDMost", entity.getUUID.getMostSignificantBits)
      case Some(tileEntity: BlockEntity) =>
        nbt.putBoolean("hostIsEntity", false)
        TradeInfo.setDimension(nbt, tileEntity.getLevel)
        val pos = tileEntity.getBlockPos
        nbt.putInt("hostX", pos.getX)
        nbt.putInt("hostY", pos.getY)
        nbt.putInt("hostZ", pos.getZ)
      case _ => // Welp!
    }
    merchant.get match {
      case Some(entity: Entity) =>
        nbt.putLong("merchantUUIDLeast", entity.getUUID.getLeastSignificantBits)
        nbt.putLong("merchantUUIDMost", entity.getUUID.getMostSignificantBits)
      case _ =>
    }
    nbt.putInt("recipeID", recipeID)
    nbt.putInt("merchantID", merchantID)
  }

  private def loadEntity(nbt: CompoundTag, uuid: UUID): Option[Entity] =
    TradeInfo.dimensionOf(nbt, host.flatMap(h => Option(h.world))).flatMap(level =>
      level.getEntitiesOfClass(classOf[Entity], TradeInfo.allEntities).asScala.
        find(_.getUUID == uuid))

  private def loadHostEntity(nbt: CompoundTag): Option[EnvironmentHost] = {
    loadEntity(nbt, new UUID(nbt.getLong("hostUUIDMost"), nbt.getLong("hostUUIDLeast"))) match {
      case Some(entity: Entity with li.cil.oc.api.internal.Agent) => Option(entity: EnvironmentHost)
      case _ => None
    }
  }

  private def loadHostTileEntity(nbt: CompoundTag): Option[EnvironmentHost] = {
    val x = nbt.getInt("hostX")
    val y = nbt.getInt("hostY")
    val z = nbt.getInt("hostZ")

    TradeInfo.dimensionOf(nbt, host.flatMap(h => Option(h.world))).flatMap(level =>
      Option(level.getBlockEntity(new BlockPos(x, y, z)))) match {
      case Some(robotProxy: li.cil.oc.common.tileentity.RobotProxy) => Option(robotProxy.robot)
      case Some(agent: li.cil.oc.api.internal.Agent) => Option(agent)
      case _ => None
    }
  }
}

object TradeInfo {
  private val DimensionTag = "dimension"

  /** 覆盖整张实体列表的边界；1.21.1 的世界边界为 ±3e7。 */
  private def allEntities = new net.minecraft.world.phys.AABB(-3.0e7, -3.0e7, -3.0e7, 3.0e7, 3.0e7, 3.0e7)

  /**
   * 1.7.10 以数字维度 ID 记录宿主所在世界；1.21.1 已没有数字 ID
   * （`Level#dimension()` 返回 `ResourceKey[Level]`），因此改存维度 key 的字符串形式
   * （如 `minecraft:overworld`）。存储格式随之变化：**旧存档里的数字 `dimensionID` 不再被识别**，
   * 效果等价于「宿主丢失」——机器人 / 微控制器上的交易升级需要重新绑定；
   * 这在移植后的世界里无论如何都要重来一次，因为维度 ID 本身已不存在。
   */
  private def setDimension(nbt: CompoundTag, level: Level): Unit = {
    if (level != null) {
      nbt.putString(DimensionTag, level.dimension().location().toString)
    }
  }

  /**
   * 1.7.10 用 `DimensionManager.getProvider(dim).worldObj` 按数字 ID 取世界；
   * 1.21.1 已移除 `DimensionManager`，改为用 `MinecraftServer#getLevel` 按维度 key 查。
   * `fallback` 是宿主当前所在世界，用于存档里没有维度信息时兜底。
   */
  private def dimensionOf(nbt: CompoundTag, fallback: => Option[Level]): Option[ServerLevel] = {
    val server = fallback.flatMap(l => Option(l.getServer)).orElse(Option(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer))
    val level = for {
      s <- server
      id <- if (nbt.contains(DimensionTag)) Option(ResourceLocation.tryParse(nbt.getString(DimensionTag))) else None
      l <- Option(s.getLevel(ResourceKey.create(Registries.DIMENSION, id)))
    } yield l
    level.orElse(fallback.collect { case l: ServerLevel => l })
  }
}
