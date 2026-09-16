package li.cil.oc.server.agent

import java.util.UUID

import com.mojang.authlib.GameProfile
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.event._
import li.cil.oc.api.internal
import li.cil.oc.api.network.Connector
import li.cil.oc.integration.util.PortalGun
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.{Player => MCPlayer}
import net.minecraft.world.entity.{Entity, EntityDimensions, EquipmentSlot, LivingEntity, Pose}
import net.minecraft.world.item.{ItemStack, Items}
import net.minecraft.world.level.Level
import net.minecraft.world.phys.{BlockHitResult, Vec3}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * 机器人 / 无人机用来与世界交互的假玩家。
 *
 * ==1.7.10 → 1.21.1 降级清单（逐条对应 TODO(port)）==
 *
 *  1. **物品栏**：1.7.10 把 [[Inventory]]（本包的 agent 物品栏视图，继承原版 `Inventory`）
 *     通过反射写进假玩家的 `inventory` 字段；1.21.1 的 `Player#inventory` 是 final，
 *     无法替换。现在保留原版 `Inventory`，并用
 *     [[Player.setInventoryPlayerItems]] / [[Player.detectInventoryPlayerChanges]]
 *     在 `agent.mainInventory` 与假玩家物品栏之间做「拷贝进 / 拷贝出」同步（见 `Player` 伴生对象）。
 *  2. **网络层**：不再替换 `ServerPlayer#connection`（新签名需要 `CommonListenerCookie`，
 *     且 `ServerGamePacketListenerImpl` 无法廉价构造）；NeoForge `FakePlayer` 自带的
 *     未连接 `Connection` 已经等价于 1.7.10 的空 `NetworkManager`，见 [[FakeNetworkManager]]。
 *  3. **方块/物品交互**：`ItemStack#useItemRightClick` / `tryPlaceItemIntoWorld` /
 *     `Block#onBlockActivated` / `Block#onBlockClicked` / `Block#harvestBlock` 全部消失，
 *     统一退化为 `ServerPlayerGameMode#useItemOn` / `useItem` / `destroyBlock`
 *     （即 1.21.1 原版玩家路径，NeoForge 会在其中触发
 *     `PlayerInteractEvent.RightClickBlock` / `PlayerInteractEvent.LeftClickBlock` /
 *     `BlockEvent.BreakEvent`，所以上游自己 post 的这些事件不再重复触发）。
 *  4. **GUI**：1.7.10 里为 `displayGUIMerchant` / `displayGUIHorse` / `displayGUIHopperMinecart` /
 *     `displayGUIChest` / `displayGUIWorkbench` / `displayGUIEnchantment` / `displayGUIAnvil` /
 *     `func_1461xx_a(TileEntity*)` 等一批方法做的覆盖在 1.21.1 全部无对应签名
 *     （现统一是 `openMenu(MenuProvider)` / `openHorseInventory`），整体删除：
 *     **假玩家不会打开任何 GUI**（与 1.7.10 的语义一致，那里也只是空实现）。
 *  5. **第三方集成**：`ObfuscationReflectionHelper`（BattleGear2 物品栏替换）、
 *     `ModMagnanimousTools` / `ModTinkersConstruct`（`needsSpecialPlacement` 分支）
 *     在本项目中不存在，相关分支直接去掉。
 *  6. **调度器**：`common.EventHandler.scheduleServer` 未移植，改用
 *     `MinecraftServer#execute(Runnable)`（见 [[Player.scheduleServer]]）。
 *  7. **属性**：`capabilities` → `getAbilities`；`onGround` → `setOnGround`；
 *     `yOffset` / `eyeHeight` / `setSize` → `getDefaultDimensions`；
 *     `theItemInWorldManager.setBlockReachDistance(1)` → 覆写 `blockInteractionRange()`；
 *     `setItemInUse` / `clearItemInUse` → `startUsingItem` / `stopUsingItem`；
 *     `setCurrentItemOrArmor` → `setItemSlot`；`addExhaustion` → `causeFoodExhaustion`；
 *     `canCommandSenderUseCommand`（权限系统整体重写）与
 *     `getPlayerCoordinates` / `getDefaultEyeHeight`（已无对应 API）整体删除。
 *  8. **`getHeldItem` / `Entity#interactFirst`** → `getMainHandItem` / `Player#interactOn`。
 */
object Player {
  def profileFor(agent: internal.Agent): GameProfile = {
    val uuid = agent.ownerUUID
    val randomId = (agent.world.random.nextInt(0xFFFFFF) + 1).toString
    val name = Settings.get.nameFormat.
      replace("$player$", agent.ownerName).
      replace("$random$", randomId)
    new GameProfile(uuid, name)
  }

  def determineUUID(playerUUID: Option[UUID] = None): UUID = {
    val format = Settings.get.uuidFormat
    val randomUUID = UUID.randomUUID()
    try UUID.fromString(format.
      replace("$random$", randomUUID.toString).
      replace("$player$", playerUUID.getOrElse(randomUUID).toString)) catch {
      case t: Throwable =>
        OpenComputers.log.warn("Failed determining robot UUID, check your config's `uuidFormat` entry!", t)
        randomUUID
    }
  }

  /** 把假玩家摆到 agent 的位置与朝向上（1.7.10 的 `setLocationAndAngles` + 手动同步 prev 角度）。 */
  def updatePositionAndRotation(player: Player, facing: Direction, side: Direction): Unit = {
    player.facing = facing
    player.side = side
    val direction = new Vec3(
      facing.getStepX + side.getStepX,
      facing.getStepY + side.getStepY,
      facing.getStepZ + side.getStepZ).normalize()
    val yaw = Math.toDegrees(-Math.atan2(direction.x, direction.z)).toFloat
    val pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt((direction.x * direction.x) + (direction.z * direction.z)))).toFloat * 0.99f
    val agent = player.agent
    player.moveTo(agent.xPosition(), agent.yPosition(), agent.zPosition(), yaw, pitch)
    player.yRotO = player.getYRot
    player.xRotO = player.getXRot
  }

  /**
   * 把 agent 的物品栏内容拷贝到假玩家的原版物品栏上。
   *
   * TODO(port): 1.7.10 里假玩家的 `inventory` **就是** agent 的物品栏（同一份数据），
   *  `setInventoryPlayerItems` 只在放置方块前做一次对齐；1.21.1 两份数据是分开的，
   *  所以这里做的是「整份拷贝」。拷贝方向为 agent → 假玩家，反向写回见
   *  [[detectInventoryPlayerChanges]]。
   */
  def setInventoryPlayerItems(player: Player): Unit = {
    val agent = player.agent
    val inventory = player.getInventory
    val mainSlots = agent.mainInventory.getSlots
    // 假玩家的 `items` 是 36 格（含 9 格快捷栏）。
    val size = math.min(inventory.items.size(), mainSlots)
    for (i <- 0 until size) {
      val stack = Inventory.getSlot(agent.mainInventory, i)
      inventory.items.set(i, if (stack.isEmpty) ItemStack.EMPTY else stack.copy())
    }
    inventory.setChanged()
  }

  /**
   * 把假玩家原版物品栏里的改动写回 agent 的物品栏。
   *
   * TODO(port): 1.7.10 的实现是 `player.inventoryContainer.detectAndSendChanges()` 之后
   *  逐槽比较回写；1.21.1 对应 `inventoryMenu.broadcastChanges()`，回写方式相同。
   */
  def detectInventoryPlayerChanges(player: Player): Unit = {
    val inventory = player.getInventory
    inventory.setChanged()
    if (player.inventoryMenu != null) {
      player.inventoryMenu.broadcastChanges()
    }
    val agent = player.agent
    val mainSlots = agent.mainInventory.getSlots
    val size = math.min(inventory.items.size(), mainSlots)
    for (i <- 0 until size) {
      val current = inventory.items.get(i)
      val target = Inventory.getSlot(agent.mainInventory, i)
      if (!ItemStack.matches(current, target)) {
        Inventory.setSlot(agent.mainInventory, i, if (current == null || current.isEmpty) ItemStack.EMPTY else current.copy())
      }
    }
  }
}

class Player(val agent: internal.Agent) extends FakePlayer(agent.world.asInstanceOf[ServerLevel], Player.profileFor(agent)) {
  // ----------------------------------------------------------------------- //
  // 构造期初始化
  // ----------------------------------------------------------------------- //

  // TODO(port): 1.7.10 在这里用反射（`ObfuscationReflectionHelper`）把 `agent` 物品栏
  //  塞进 `Player#inventory`（BattleGear2 兼容分支）。1.21.1 的 `inventory` 是 final
  //  字段，无法替换，改为在 `Player` 伴生对象里做双向拷贝同步（见类注释第 1 条）。
  {
    val abilities = getAbilities
    abilities.mayfly = true
    abilities.invulnerable = true
    abilities.flying = true
    setOnGround(true)
    // TODO(port): 1.7.10 的 `yOffset = 0.5f` / `eyeHeight = 0f` 在 1.21.1 已无对应字段
    //  （碰撞箱与视高改由 `EntityDimensions` / `getEyeHeight` 计算），这里贴近地用
    //  `getDefaultDimensions` 给出 1x1 的碰撞箱；视高不再单独调整。
  }

  var facing: Direction = Direction.SOUTH

  var side: Direction = Direction.SOUTH

  var customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided: ItemStack = ItemStack.EMPTY

  /** 机器人所在世界（1.7.10 的 `player.world`）。 */
  def world: Level = agent.world

  /**
   * TODO(port): 1.7.10 的 `theItemInWorldManager.setBlockReachDistance(1)`。
   *  1.21.1 的 `ServerPlayerGameMode` 没有该 API，改为覆写玩家的方块交互距离常量。
   */
  override def blockInteractionRange(): Double = 1.0

  /**
   * TODO(port): 1.7.10 的 `setSize(1, 1)`。
   *  1.21.1 的实体尺寸改为由 `getDefaultDimensions` 提供。
   */
  override def getDefaultDimensions(pose: Pose): EntityDimensions = EntityDimensions.fixed(1f, 1f)

  override def getDisplayName: Component = Component.literal(agent.name)

  // ----------------------------------------------------------------------- //
  // 实体 / 方块查询
  // ----------------------------------------------------------------------- //

  /** 对应 1.7.10 的 `closestEntity`：取指定面方向上最近的一个实体。 */
  def closestEntity[Type <: Entity : ClassTag](side: Direction = facing): Option[Type] = {
    // TODO(port): 1.7.10 用 `Level#findNearestEntityWithinAABB`；1.21.1 的
    //  `Level#getNearestEntity` 只接受 `LivingEntity`，这里改为取回候选后按距离自行挑最近。
    val candidates = entitiesOnSide[Type](side)
    if (candidates.isEmpty) None
    else Some(candidates.minBy(entity => entity.distanceToSqr(getX, getY, getZ)))
  }

  def entitiesOnSide[Type <: Entity : ClassTag](side: Direction): Seq[Type] =
    entitiesInBlock[Type](BlockPosition(agent).offset(side))

  def entitiesInBlock[Type <: Entity : ClassTag](blockPos: BlockPosition): Seq[Type] =
    world.getEntitiesOfClass(classTag[Type].runtimeClass.asInstanceOf[Class[Type]], blockPos.bounds).asScala.toSeq

  private def adjacentItems: Seq[ItemEntity] =
    world.getEntitiesOfClass(classOf[ItemEntity], BlockPosition(agent).bounds.inflate(2, 2, 2)).asScala.toSeq

  private def collectDroppedItems(itemsBefore: Seq[ItemEntity]): Unit = {
    val itemsDropped = adjacentItems.filterNot(itemsBefore.contains)
    if (itemsDropped.nonEmpty) {
      for (drop <- itemsDropped) {
        // 1.7.10: `delayBeforeCanPickup = 0` + `onCollideWithPlayer(this)`。
        drop.setPickUpDelay(0)
        drop.playerTouch(this)
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 攻击 / 交互
  // ----------------------------------------------------------------------- //

  /** 对应 1.7.10 的 `attackTargetEntityWithCurrentItem`（1.21.1 为 `Player#attack`）。 */
  override def attack(target: Entity): Unit = {
    callUsingItemInSlot(agent.equipmentInventory, 0, _ => target match {
      case player: MCPlayer if !canHarmPlayer(player) => // 不攻击玩家。
      case _ =>
        val event = new RobotAttackEntityEvent.Pre(agent, target)
        NeoForge.EVENT_BUS.post(event)
        if (!event.isCanceled) {
          super.attack(target)
          NeoForge.EVENT_BUS.post(new RobotAttackEntityEvent.Post(agent, target))
        }
    })
  }

  /**
   * 对应 1.7.10 的 `interactWith(entity)`。
   *
   * TODO(port): 1.7.10 会先 post `EntityInteractEvent`，再依次尝试
   *  `Entity#interactFirst` / `ItemStack#interactWithEntity` 并处理物品耗尽；
   *  1.21.1 里这些入口合并成了 `Player#interactOn(Entity, InteractionHand)`，
   *  因此这里退化为直接调用原版实现（NeoForge 的
   *  `PlayerInteractEvent.EntityInteract` 由原版路径自行触发）。
   */
  def interactWith(entity: Entity): Boolean =
    callUsingItemInSlot(agent.equipmentInventory, 0, _ =>
      super.interactOn(entity, InteractionHand.MAIN_HAND) != InteractionResult.PASS)

  /**
   * 右键方块 / 使用物品。
   *
   * TODO(port): 1.7.10 会依次尝试 `Item#onItemUseFirst`、`Block#onBlockActivated`、
   *  `ItemStack#tryPlaceItemIntoWorld`、`ItemStack#useItemRightClick` 四步，并自行
   *  post `PlayerInteractEvent`。1.21.1 原版把这整条链路封装在
   *  `ServerPlayerGameMode#useItemOn` / `#useItem` 里（且会触发 NeoForge 的
   *  `PlayerInteractEvent.RightClickBlock`），所以这里直接委托给 `gameMode`，
   *  无法再区分「激活方块」与「放置物品」，统一按 `BlockActivated` 上报。
   */
  def activateBlockOrUseItem(x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float, duration: Double): ActivationType.Value = {
    // `duration` 在 1.21.1 没有对应物：上游用它计算 `agent.machine.pause(...)`，
    // 现在暂停由 `server.component.Agent#onWorldInteraction` 统一处理。
    callUsingItemInSlot(agent.equipmentInventory, 0, stack => {
      val hit = blockHit(x, y, z, side, hitX, hitY, hitZ)
      if (gameMode.useItemOn(this, world, stack, InteractionHand.MAIN_HAND, hit).consumesAction()) {
        ActivationType.BlockActivated
      }
      else if (gameMode.useItem(this, world, stack, InteractionHand.MAIN_HAND).consumesAction()) {
        ActivationType.ItemUsed
      }
      else {
        ActivationType.None
      }
    })
  }

  /**
   * 右键空气使用手持物品。
   *
   * TODO(port): 1.7.10 会先 post `PlayerInteractEvent(RIGHT_CLICK_AIR)` 并检查
   *  `useItem` 是否被 DENY；1.21.1 的该事件不可由外部构造，故省略这一层拦截。
   */
  def useEquippedItem(duration: Double): Boolean =
    callUsingItemInSlot(agent.equipmentInventory, 0, stack => tryUseItem(stack, duration))

  private def tryUseItem(stack: ItemStack, duration: Double): Boolean = {
    if (stack == null || stack.isEmpty || !isItemUseAllowed(stack)) return false
    val oldStack = stack.copy()
    val heldTicks = math.max(0, math.min(stack.getUseDuration(this), (duration * 20).toInt))
    // 1.7.10 会把玩家位置沿朝向偏移 0.6，避免物品作用在机器人自己身上。
    val offset = facing
    val dx = offset.getStepX * 0.6
    val dy = offset.getStepY * 0.6
    val dz = offset.getStepZ * 0.6
    val x0 = getX
    val y0 = getY
    val z0 = getZ
    val result =
      try {
        setPos(x0 + dx, y0 + dy, z0 + dz)
        stack.use(world, this, InteractionHand.MAIN_HAND)
      }
      finally {
        setPos(x0, y0, z0)
      }
    // TODO(port): 1.7.10 用 `setItemInUse` / `onPlayerStoppedUsing` 手动驱动「蓄力」物品；
    //  1.21.1 的蓄力走 `LivingEntity#startUsingItem` / `releaseUsingItem`，
    //  且需要真实 tick，因此这里只按 duration 暂停机器，不再模拟蓄力过程。
    if (agent.machine != null) {
      agent.machine.pause(heldTicks / 20.0)
    }
    val used = result.getResult.consumesAction()
    // `ItemStack#use` 可能原地修改传入的栈（吃掉食物、消耗药水等），把结果写回工具槽。
    if (used || !ItemStack.matches(oldStack, stack)) {
      Inventory.setSlot(agent.equipmentInventory, 0, if (stack.isEmpty) ItemStack.EMPTY else stack)
    }
    used
  }

  /** 放置物品栏中指定槽位的方块。 */
  def placeBlock(slot: Int, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean =
    callUsingItemInSlot(agent.mainInventory, slot,
      stack => tryPlaceBlockWhileHandlingFunnySpecialCases(stack, x, y, z, side, hitX, hitY, hitZ),
      repair = false)

  /**
   * 破坏方块。
   *
   * TODO(port): 1.7.10 的实现是完整重演原版挖掘流程：`canCollideCheck` 判定、
   *  post `BlockEvent.BreakEvent`、`Block#onBlockClicked`、`extinguishFire`、
   *  `getBlockHardness`、创造/冒险模式判定、`ForgeHooks.canHarvestBlock`、
   *  `stack.func_150999_a`（工具损耗）、`block.harvestBlock`（掉落物）……
   *  1.21.1 这些全部收敛进 `ServerPlayerGameMode#destroyBlock`，因此这里退化为：
   *  只算「挖掘耗时」（用于 `RobotBreakBlockEvent` 与机器人的暂停时长），
   *  然后交给 `gameMode.destroyBlock`，掉落物/工具损耗/经验由原版处理。
   *  另外 `RobotBreakBlockEvent.Post` 的 `experience` 参数拿不到，固定上报 0。
   */
  def clickBlock(x: Int, y: Int, z: Int, side: Int, immediate: Boolean = false): Double = {
    callUsingItemInSlot(agent.equipmentInventory, 0, _ => {
      val pos = new BlockPos(x, y, z)
      val state = world.getBlockState(pos)
      val hardness = if (state.isAir) -1f else state.getDestroySpeed(world, pos)
      if (hardness < 0) {
        0.0
      }
      else {
        val strength = math.max(0.1f, getDestroySpeed(state))
        val breakTime = hardness * 1.5 / strength
        val preEvent = new RobotBreakBlockEvent.Pre(agent, world, x, y, z, breakTime * Settings.get.harvestRatio)
        NeoForge.EVENT_BUS.post(preEvent)
        if (preEvent.isCanceled) {
          0.0
        }
        else {
          val adjustedBreakTime = math.max(0.05, preEvent.getBreakTime)
          if (!immediate) {
            // 1.7.10 用 `EventHandler.scheduleServer` 调度逐 tick 的挖掘进度。
            scheduleServer(() => new DamageOverTime(this, x, y, z, side, (adjustedBreakTime * 20).toInt).tick())
            adjustedBreakTime
          }
          else if (gameMode.destroyBlock(pos)) {
            NeoForge.EVENT_BUS.post(new RobotBreakBlockEvent.Post(agent, 0.0))
            adjustedBreakTime
          }
          else {
            0.0
          }
        }
      }
    })
  }

  private def isItemUseAllowed(stack: ItemStack): Boolean = stack == null || stack.isEmpty || {
    (Settings.get.allowUseItemsWithDuration || stack.getUseDuration(this) <= 0) &&
      (!PortalGun.isPortalGun(stack) || PortalGun.isStandardPortalGun(stack)) &&
      !stack.is(Items.LEAD)
  }

  /**
   * 对应 1.7.10 的 `dropPlayerItemWithRandomChoice(stack, inPlace)`。
   *
   * TODO(port): 1.21.1 的 `Player` 没有这个方法（掉落改由 `Player#drop` 负责），
   *  保留同名工具方法供机器人组件调用，行为仍是「按朝向在世界里生成掉落物」。
   *
   * TODO(port): 1.7.10 的 `InventoryUtils.spawnStackInWorld` 返回 `EntityItem`，
   *  1.21.1 对应返回 `ItemEntity`；而上游本方法的返回语义是「被丢弃的那个堆叠」，
   *  因此这里丢弃掉 `ItemEntity` 返回值，改回传传入的 `stack`。
   */
  def dropPlayerItemWithRandomChoice(stack: ItemStack, inPlace: Boolean): ItemStack = {
    InventoryUtils.spawnStackInWorld(BlockPosition(agent), stack, if (inPlace) None else Option(facing))
    stack
  }

  private def blockHit(x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float): BlockHitResult = {
    // 1.7.10 的 `side` 是 `ForgeDirection` 序号，1.21.1 的 `Direction` 序号顺序一致。
    val direction =
      if (side >= 0 && side < Direction.values().length) Direction.from3DDataValue(side) else Direction.UP
    new BlockHitResult(new Vec3(x + hitX, y + hitY, z + hitZ), direction, new BlockPos(x, y, z), false)
  }

  // ----------------------------------------------------------------------- //
  // 物品栏辅助
  // ----------------------------------------------------------------------- //

  /**
   * 在「使用某个槽位的物品」前后做统一的簿记：收集掉落物、处理物品耗尽与工具修复。
   *
   * TODO(port): 1.7.10 通过 `this.inventory.currentItem` 让原版逻辑知道「当前手持槽位」。
   *  1.21.1 里 `Inventory#selected` 只对快捷栏有意义，且假玩家的物品栏与 agent 的
   *  物品栏是两份数据，所以这里只在主物品栏时同步 `selected`，其余情况置 0。
   */
  private def callUsingItemInSlot[T](inventory: IItemHandler, slot: Int, f: ItemStack => T, repair: Boolean = true): T = {
    val itemsBefore = adjacentItems
    val stack = Inventory.getSlot(inventory, slot)
    val oldStack = if (stack.isEmpty) ItemStack.EMPTY else stack.copy()
    val playerInventory = getInventory
    val previousSelected = playerInventory.selected
    playerInventory.selected = if (inventory eq agent.mainInventory) slot else 0
    try {
      f(stack)
    }
    finally {
      playerInventory.selected = previousSelected
      val newStack = Inventory.getSlot(inventory, slot)
      // 只有当 f() 原地修改了传入的 stack 对象（IC2 之类的做法）时才写回。
      if (ItemStack.matches(oldStack, newStack) && !ItemStack.matches(oldStack, stack)) {
        Inventory.setSlot(inventory, slot, stack)
      }
      if (!newStack.isEmpty) {
        if (newStack.getCount <= 0) {
          Inventory.setSlot(inventory, slot, ItemStack.EMPTY)
        }
        if (repair) {
          if (newStack.getCount > 0) tryRepair(newStack, oldStack)
          else NeoForge.EVENT_BUS.post(new PlayerDestroyItemEvent(this, newStack, InteractionHand.MAIN_HAND))
        }
      }
      collectDroppedItems(itemsBefore)
    }
  }

  private def tryRepair(stack: ItemStack, oldStack: ItemStack): Unit = {
    // 只在底层物品类型没变时处理。
    if (!stack.isEmpty && !oldStack.isEmpty && stack.is(oldStack.getItem)) {
      val damageRate = new RobotUsedToolEvent.ComputeDamageRate(agent, oldStack, stack, Settings.get.itemDamageRate)
      NeoForge.EVENT_BUS.post(damageRate)
      if (damageRate.getDamageRate < 1) {
        NeoForge.EVENT_BUS.post(new RobotUsedToolEvent.ApplyDamageRate(agent, oldStack, stack, damageRate.getDamageRate))
      }
    }
  }

  /**
   * 放置方块。
   *
   * TODO(port): 1.7.10 里 `tryPlaceItemIntoWorld` 会先 `setPosition` 并
   *  `Player.setInventoryPlayerItems(this)` 把 agent 物品栏同步到假玩家身上，
   *  结束后再 `detectInventoryPlayerChanges` 写回；同时还要为活塞类方块伪造视高
   *  （`isSomeKindOfPiston` / `fakeEyeHeight`）。
   *  1.21.1 的放置路径改为 `ServerPlayerGameMode#useItemOn`，它会自行触发
   *  NeoForge 的 `PlayerInteractEvent.RightClickBlock` 和
   *  `BlockItem#useOn(BlockPlaceContext)`；因此：
   *   - 不再手动同步物品栏（同步改由 `useItemOn` 之前的调用方负责，见 `server.component.Agent`）；
   *   - 活塞的 `fakeEyeHeight` 处理随 `ItemBlock#field_150939_a` 一并删除（1.21.1 用
   *     `BlockItem#getBlock`，但 `BlockPlaceContext` 已不再依赖玩家视高）；
   *   - `RobotPlaceBlockEvent` 仍然在前后 post，保持 API 行为。
   */
  private def tryPlaceBlockWhileHandlingFunnySpecialCases(stack: ItemStack, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    if (stack == null || stack.isEmpty) {
      false
    }
    else {
      val event = new RobotPlaceBlockEvent.Pre(agent, stack, world, x, y, z)
      NeoForge.EVENT_BUS.post(event)
      if (event.isCanceled) {
        false
      }
      else {
        val didPlace = gameMode.useItemOn(this, world, stack, InteractionHand.MAIN_HAND,
          blockHit(x, y, z, side, hitX, hitY, hitZ)).consumesAction()
        if (didPlace) {
          NeoForge.EVENT_BUS.post(new RobotPlaceBlockEvent.Post(agent, stack, world, x, y, z))
        }
        didPlace
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 原版行为覆写
  // ----------------------------------------------------------------------- //

  /**
   * TODO(port): 1.7.10 的 `setItemInUse`；1.21.1 改名为 `startUsingItem`。
   *  额外记录「正在使用的物品」，因为 1.21.1 的 `getUseItem` 对假玩家未必可靠。
   */
  override def startUsingItem(hand: InteractionHand): Unit = {
    super.startUsingItem(hand)
    customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided = getItemInHand(hand)
  }

  /** TODO(port): 1.7.10 的 `clearItemInUse`；1.21.1 改名为 `stopUsingItem`。 */
  override def stopUsingItem(): Unit = {
    super.stopUsingItem()
    customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided = ItemStack.EMPTY
  }

  /**
   * TODO(port): 1.7.10 的 `addExhaustion`；1.21.1 的对应方法是 `causeFoodExhaustion`。
   *  机器人的「疲劳」表现为扣能量 + post `RobotExhaustionEvent`。
   */
  override def causeFoodExhaustion(amount: Float): Unit = {
    if (Settings.get.robotExhaustionCost > 0) {
      agent.machine.node match {
        case connector: Connector => connector.changeBuffer(-Settings.get.robotExhaustionCost * amount)
        case _ => // 不应发生。
      }
    }
    NeoForge.EVENT_BUS.post(new RobotExhaustionEvent(agent, amount.toDouble))
  }

  /**
   * TODO(port): 1.7.10 的 `closeScreen()`。1.21.1 改名为 `closeContainer()`；
   *  假玩家不持有任何 GUI，直接吞掉。
   */
  override def closeContainer(): Unit = {}

  /** TODO(port): 1.7.10 的 `swingItem()`；1.21.1 改为 `swing(InteractionHand)`，假玩家不播放手臂动画。 */
  override def swing(hand: InteractionHand): Unit = {}

  /**
   * TODO(port): 1.7.10 的 `canCommandSenderUseCommand(level, command)`。
   *  1.21.1 的权限体系改为 `hasPermissions(int)` / `MinecraftServer#getProfilePermissions`，
   *  整体重写，机器人不再暴露任何指令权限，故删除该覆写。
   */

  override def canHarmPlayer(player: MCPlayer): Boolean = Settings.get.canAttackPlayers

  override def canEat(value: Boolean): Boolean = false

  /** TODO(port): 1.7.10 的 `isPotionApplicable`；1.21.1 改名为 `canBeAffected`。 */
  override def canBeAffected(effect: MobEffectInstance): Boolean = false

  /** TODO(port): 1.7.10 的 `attackEntityAsMob`；1.21.1 改名为 `doHurtTarget`。 */
  override def doHurtTarget(entity: Entity): Boolean = false

  /** TODO(port): 1.7.10 的 `attackEntityFrom`；1.21.1 改名为 `hurt`。 */
  override def hurt(source: DamageSource, amount: Float): Boolean = false

  override def heal(amount: Float): Unit = {}

  override def setHealth(value: Float): Unit = {}

  /**
   * TODO(port): 1.7.10 的 `setDead() = isDead = true`。
   *  1.21.1 里 `Entity#discard` 是 final，只能覆写 `remove(RemovalReason)`；
   *  这里做成空实现，避免假玩家被移除时连带清空它的物品栏。
   */
  override def remove(reason: Entity.RemovalReason): Unit = {}

  /**
   * TODO(port): 1.7.10 的 `setCurrentItemOrArmor(slot, stack)`。
   *  1.21.1 改为 `setItemSlot(EquipmentSlot, ItemStack)`；机器人只有主手工具槽
   *  （映射到 `agent.equipmentInventory` 的第 0 槽），没有护甲槽。
   */
  override def setItemSlot(slot: EquipmentSlot, stack: ItemStack): Unit = {
    if (slot == EquipmentSlot.MAINHAND && agent.equipmentInventory.getSlots > 0) {
      Inventory.setSlot(agent.equipmentInventory, 0, stack)
    }
    // 其它装备槽（护甲等）在机器人上不受支持。
  }

  /** TODO(port): 1.7.10 的 `setRevengeTarget`；1.21.1 对应的语义由 `setLastHurtByMob` 承担。 */
  override def setLastHurtByMob(entity: LivingEntity): Unit = {}

  /** TODO(port): 1.7.10 的 `setLastAttacker`；1.21.1 对应的语义由 `setLastHurtMob` 承担。 */
  override def setLastHurtMob(entity: Entity): Unit = {}

  /** TODO(port): 1.7.10 的 `mountEntity`；1.21.1 改为 `startRiding`，假玩家不骑乘任何东西。 */
  override def startRiding(entity: Entity, force: Boolean): Boolean = false

  /**
   * TODO(port): 1.7.10 的 `sleepInBedAt` / `onLivingUpdate` / `onItemPickup` /
   *  `displayGUI*` / `func_1461xx_a(TileEntity*)` / `getPlayerCoordinates` /
   *  `getDefaultEyeHeight` 等覆写整体删除：
   *   - 睡觉/拾取在 1.21.1 的签名与语义都变了（`startSleepInBed` 返回
   *     `Either<BedSleepingProblem, Unit>`、`onItemPickup` 只接受 `ItemEntity`），
   *     机器人不需要这些行为；
   *   - GUI 相关方法统一变成 `openMenu(MenuProvider)` / `openHorseInventory(...)`，
   *     假玩家不应打开任何 GUI；
   *   - `getPlayerCoordinates` / `getDefaultEyeHeight` 在 1.21.1 已不存在。
   */

  // ----------------------------------------------------------------------- //
  // 调度器
  // ----------------------------------------------------------------------- //

  /**
   * TODO(port): 1.7.10 用 `li.cil.oc.common.EventHandler.scheduleServer` 把任务排到
   *  服务端 tick 队列上。该文件在本移植中尚未就绪，这里改用
   *  `MinecraftServer#execute(Runnable)`（`BlockableEventLoop` 提供的原版等价调度）。
   */
  private def scheduleServer(f: () => Unit): Unit = {
    val server = world.getServer
    if (server != null) {
      server.execute(new Runnable {
        override def run(): Unit = f()
      })
    }
  }

  // ----------------------------------------------------------------------- //
  // 逐 tick 的挖掘进度
  // ----------------------------------------------------------------------- //

  class DamageOverTime(val player: Player, val x: Int, val y: Int, val z: Int, val side: Int, val ticksTotal: Int) {
    val world: Level = player.world
    var ticks = 0
    var lastDamageSent = 0

    def tick(): Unit = {
      val pos = new BlockPos(x, y, z)
      // 机器已停止、方块消失或世界变了就取消。
      if (world != player.world || !world.isLoaded(pos) || world.getBlockState(pos).isAir ||
        player.agent.machine == null || !player.agent.machine.isRunning) {
        world.destroyBlockProgress(-1, pos, -1)
        return
      }

      val damage = 10 * ticks / math.max(ticksTotal, 1)
      if (damage >= 10) {
        player.clickBlock(x, y, z, side, immediate = true)
      }
      else {
        ticks += 1
        if (damage != lastDamageSent) {
          lastDamageSent = damage
          world.destroyBlockProgress(-1, pos, damage)
        }
        scheduleServer(() => tick())
      }
    }
  }
}
