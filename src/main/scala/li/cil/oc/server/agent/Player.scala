package li.cil.oc.server.agent

import java.util.UUID

import com.mojang.authlib.GameProfile
import com.mojang.datafixers.util.Either
import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.api.event._
import li.cil.oc.api.internal
import li.cil.oc.api.network.Connector
import li.cil.oc.common.EventHandler
import li.cil.oc.integration.util.PortalGun
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.players.ServerOpListEntry
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.{MobEffectInstance, MobEffectUtil, MobEffects}
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player.{BedSleepingProblem => BedStatus}
import net.minecraft.world.entity.player.{Player => MCPlayer}
import net.minecraft.world.entity.{Entity, EntityAttachments, EntityDimensions, EquipmentSlot, LivingEntity, Pose}
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.item.{BlockItem, ItemStack, Items}
import net.minecraft.world.level.BaseCommandBlock
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.{CommandBlockEntity, SignBlockEntity}
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.{BlockHitResult, Vec3}
import net.neoforged.bus.api.{EventPriority, SubscribeEvent}
import net.neoforged.neoforge.common.util.{FakePlayer, TriState}
import net.neoforged.neoforge.common.{CommonHooks, NeoForge}
import net.neoforged.neoforge.event.EventHooks
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * 机器人 / 无人机用来与世界交互的假玩家。
 *
 * ==与 1.7.10 及 OCCE 基准的逐条差异==
 *  只有下面这些是 1.21.1 真正做不到、必须保留的降级，其余行为都已按基准恢复：
 *
 *  1. **物品栏合并**：1.7.10 与 OCCE 都把 [[Inventory]]（agent 的物品栏视图）塞进假玩家的
 *     `inventory` 字段，于是两者是同一份数据。1.21.1 的 `Player#inventory` 是 **final 字段**
 *     （`javap` 可见），无法替换，只能让它保持原版 `Inventory`。补偿手段：
 *     - [[Player.setInventoryPlayerItems]] / [[Player.detectInventoryPlayerChanges]] 在交互前后
 *       做「整份拷贝」的双向同步（主物品栏 36 格 + 装备栏 4 格）；
 *     - 假玩家的手部槽位（`EquipmentSlot.MAINHAND` / `OFFHAND`）通过 [[Player.getItemBySlot]] /
 *       [[Player.setItemSlot]] 直接映射到 `agent.equipmentInventory` 的第 0 槽，
 *       等价于 OCCE 用 offhand 当「当前手持物品」缓冲区的做法；
 *     - 挖掘速度与可采集判定用 [[Player.getDigSpeed]] / [[Player.hasCorrectToolForDrops]] 覆写，
 *       把数据源换成 agent 的工具槽（对应 OCCE 覆写 `Inventory#getDestroySpeed`）。
 *  2. **网络层**：不替换 `connection`。原因见 [[FakeNetworkManager]]——NeoForge 的 `FakePlayer`
 *     自带的 `FakePlayerNetHandler` 已经等价于 1.7.10 的空 `NetworkManager`，比换成裸连接更安全。
 *  3. **实体尺寸与视高**：1.7.10 的 `yOffset = 0.5f` / `eyeHeight = 0f` / OCCE 的
 *     `getStandingEyeHeight = 0f`。1.21.1 里 `Entity#getEyeHeight` 已经是 **final**，
 *     视高改由 `EntityDimensions#eyeHeight` 承担，这里用 `getDefaultDimensions` 给出
 *     1x1x1 且视高为 0 的碰撞箱；`getMyRidingOffset` 在 1.21.1 已不存在，无法恢复。
 *     机器人也不播放手臂摆动（`swing` 覆写为空，对应 1.7.10 的 `swingItem`）。
 *  4. **第三方集成**：`ModMagnanimousTools` / `ModTinkersConstruct`（`needsSpecialPlacement`
 *     分支）以及 BattleGear2 的物品栏反射在本项目中不存在，相关分支去掉。
 *     `PortalGun` 集成存在，因此 1.7.10 的 `isItemUseAllowed` 里的 PortalGun 检查保留。
 *  5. **GUI**：1.7.10 里为 `displayGUIMerchant` / `displayGUIHorse` / `displayGUIHopperMinecart` /
 *     `displayGUIChest` / `displayGUIWorkbench` / `displayGUIEnchantment` / `displayGUIAnvil` /
 *     `func_1461xx_a(TileEntity...)` 等一批方法做的覆盖，在 1.21.1 统一收敛成
 *     `openMenu(MenuProvider)` / `openCommandBlock` / `openMinecartCommandBlock` /
 *     `openTextEdit` / `sendMerchantOffers`，本类全部覆写为空实现，语义与基准一致
 *     （基准里这些方法也都只是空实现或清空商人客户）。
 *  6. **权限**：1.7.10 的 `canCommandSenderUseCommand(level, command)` 在 1.21.1 被
 *     `ServerPlayer#getPermissionLevel` 取代，按 OCCE 的实现覆写；1.7.10 里对
 *     `seed` / `tell` / `help` / `me` 的额外放行在新权限体系下没有对应入口，故不保留。
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

  /**
   * 把假玩家摆到 agent 的位置与朝向上（对应 1.7.10 的 `setLocationAndAngles` 加手动同步
   * 上一 tick 的角度）。
   *
   * 注意 OCCE 的版本把 yaw 与 pitch 传反了（`setYRot(pitch)` / `setXRot(yaw)`），这里沿用
   * 1.7.10 的正确顺序。
   */
  def updatePositionAndRotation(player: Player, facing: Direction, side: Direction): Unit = {
    player.facing = facing
    player.side = side
    val direction = new Vec3(
      facing.getStepX + side.getStepX,
      facing.getStepY + side.getStepY,
      facing.getStepZ + side.getStepZ).normalize()
    val yaw = Math.toDegrees(-Math.atan2(direction.x, direction.z)).toFloat
    val pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt((direction.x * direction.x) + (direction.z * direction.z)))).toFloat * 0.99f
    player.moveTo(player.agent.xPosition, player.agent.yPosition, player.agent.zPosition, yaw, pitch)
    player.yRotO = player.getYRot
    player.xRotO = player.getXRot
  }

  /**
   * 把 agent 的物品栏内容拷贝到假玩家的原版物品栏上。
   *
   * 1.7.10 与 OCCE 里假玩家的 `inventory` **就是** agent 的物品栏（同一份数据），所以这个函数
   * 只在放置方块前做一次对齐；1.21.1 的两份数据必然分开，因此这里是「整份拷贝」：
   *  - 装备栏：OCCE 会把 `agent.equipmentInventory` 的前 4 格拷到 `inventory.armor`（有些 mod
   *    按护甲槽读取玩家装备）；
   *  - 主物品栏：`inventory.items` 是 36 格（含 9 格快捷栏），逐格拷贝 `agent.mainInventory`。
   *
   * 反向写回见 [[detectInventoryPlayerChanges]]。
   */
  def setInventoryPlayerItems(player: Player): Unit = {
    val agent = player.agent
    val inventory = player.getInventory
    for (i <- 0 until equipmentSlotCount(agent)) {
      val stack = Inventory.getSlot(agent.equipmentInventory, i)
      inventory.armor.set(i, if (stack.isEmpty) ItemStack.EMPTY else stack.copy())
    }
    val size = math.min(inventory.items.size(), mainSlotCount(agent))
    for (i <- 0 until size) {
      val stack = Inventory.getSlot(agent.mainInventory, i)
      inventory.items.set(i, if (stack.isEmpty) ItemStack.EMPTY else stack.copy())
    }
    inventory.setChanged()
    if (player.inventoryMenu != null) {
      player.inventoryMenu.broadcastChanges()
    }
  }

  /**
   * 把假玩家原版物品栏里的改动写回 agent 的物品栏。
   *
   * 1.7.10 用 `inventoryContainer.detectAndSendChanges()` 通知客户端并逐槽比较回写；
   * 1.21.1 对应 `inventoryMenu.broadcastChanges()`，回写方式相同。
   */
  def detectInventoryPlayerChanges(player: Player): Unit = {
    val inventory = player.getInventory
    inventory.setChanged()
    if (player.inventoryMenu != null) {
      player.inventoryMenu.broadcastChanges()
    }
    val agent = player.agent
    for (i <- 0 until equipmentSlotCount(agent)) {
      setCopy(agent.equipmentInventory, i, inventory.armor.get(i))
    }
    val size = math.min(inventory.items.size(), mainSlotCount(agent))
    for (i <- 0 until size) {
      setCopy(agent.mainInventory, i, inventory.items.get(i))
    }
  }

  /** agent 主物品栏的槽位数（`internal.Agent#mainInventory` 是 `IItemHandler`）。 */
  private def mainSlotCount(agent: internal.Agent): Int = {
    val handler = agent.mainInventory
    if (handler == null) 0 else handler.getSlots
  }

  /** 需要同步到假玩家护甲槽的槽位数：原版护甲槽是 4 格。 */
  private def equipmentSlotCount(agent: internal.Agent): Int = {
    val handler = agent.equipmentInventory
    if (handler == null) 0 else math.min(4, handler.getSlots)
  }

  private def setCopy(inventory: IItemHandler, index: Int, item: ItemStack): Unit = {
    if (inventory == null || index < 0 || index >= inventory.getSlots) return
    val current = Inventory.getSlot(inventory, index)
    val next = if (item == null || item.isEmpty) ItemStack.EMPTY else item.copy()
    if (!ItemStack.matches(next, current)) {
      Inventory.setSlot(inventory, index, next)
    }
  }
}

class Player(val agent: internal.Agent) extends FakePlayer(agent.world.asInstanceOf[ServerLevel], Player.profileFor(agent)) {
  // ----------------------------------------------------------------------- //
  // 构造期初始化
  // ----------------------------------------------------------------------- //

  {
    val abilities = getAbilities
    abilities.mayfly = true
    abilities.invulnerable = true
    abilities.flying = true
    setOnGround(true)
    // 1.7.10 的 `yOffset = 0.5f` / `eyeHeight = 0f` / `setSize(1, 1)` 在 1.21.1 里统一由
    // 实体尺寸承担（`getEyeHeight` 已是 final，无法覆写），因此先按下面的覆写刷新一次尺寸。
    refreshDimensions()
  }

  var facing: Direction = Direction.SOUTH

  var side: Direction = Direction.SOUTH

  /**
   * agent 的物品栏视图。
   *
   * 1.7.10 与 OCCE 会把这个对象直接写进假玩家的 `inventory` 字段；1.21.1 的 `inventory` 是
   * final，无法替换，所以它只能作为独立视图存在，由本类在需要时查询 agent 的工具槽。
   */
  val agentInventory = new Inventory(agent)

  /** 机器人所在世界（1.7.10 的 `player.world`）。 */
  def world: Level = agent.world

  /**
   * 1.7.10 的 `theItemInWorldManager.setBlockReachDistance(1)`。
   * 1.21.1 的 `ServerPlayerGameMode` 没有该 API，改为覆写玩家的方块交互距离。
   */
  override def blockInteractionRange(): Double = 1.0

  /**
   * 1.7.10 的 `setSize(1, 1)` 与 `eyeHeight = 0f`。
   *
   * 1.21.1 里实体尺寸由 `getDefaultDimensions` 提供，视高由 `EntityDimensions#eyeHeight` 提供
   * （`Entity#getEyeHeight` 已是 final，无法覆写），因此把 1x1x1、视高 0 写进尺寸即可，
   * 等价于 OCCE 覆写 `getDimensions` 加 `getStandingEyeHeight`。
   */
  override def getDefaultDimensions(pose: Pose): EntityDimensions =
    new EntityDimensions(1f, 1f, 0f, EntityAttachments.createDefault(1f, 1f), true)

  override def getDisplayName: Component = Component.literal(agent.name)

  // ----------------------------------------------------------------------- //
  // 实体 / 方块查询
  // ----------------------------------------------------------------------- //

  /** 对应 1.7.10 的 `closestEntity`：取指定面方向上最近的一个实体。 */
  def closestEntity[Type <: Entity : ClassTag](side: Direction = facing): Option[Type] = {
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
        // 1.7.10 的 `delayBeforeCanPickup = 0`。OCCE 用 `setDefaultPickUpDelay()`，但那会写入
        // 10 tick 的拾取延迟，而 `ItemEntity#playerTouch` 只在 `pickupDelay == 0` 时才拾取，
        // 会让这里的回收完全失效，所以按 1.7.10 的意图显式清零。
        drop.setPickUpDelay(0)
        drop.playerTouch(this)
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 攻击 / 交互
  // ----------------------------------------------------------------------- //

  /** 对应 1.7.10 的 `attackTargetEntityWithCurrentItem`（1.21.1 是 `Player#attack`）。 */
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
   * 对应 1.7.10 的 `interactWith(entity)`（1.21.1 的入口是 `Player#interactOn`）。
   *
   * 1.7.10 自己 post `EntityInteractEvent`，再依次尝试 `Entity#interactFirst` /
   * `ItemStack#interactWithEntity` 并处理物品耗尽。1.21.1 的 NeoForge 已经把这些整条链路
   * 做进了原版 `Player#interactOn`（内部会 post `PlayerInteractEvent.EntityInteract`、
   * 处理创造模式物品返还、`interactLivingEntity` 与物品耗尽），所以这里只负责补上
   * OCCE 那层 `callUsingItemInSlot` 的物品栏簿记（回收掉落物、工具修复/损耗），
   * 交互本身交给父类，避免重复触发事件。
   */
  override def interactOn(entity: Entity, hand: InteractionHand): InteractionResult = {
    // 用局部函数把父类调用固定下来，避免在闭包里直接写 super。
    def interactWithParent(): InteractionResult = super.interactOn(entity, hand)
    callUsingItemInSlot(agent.equipmentInventory, 0, _ => interactWithParent())
  }

  /**
   * 右键方块 / 使用物品（对应 1.7.10 的 `activateBlockOrUseItem`）。
   *
   * 依次尝试 `Item#onItemUseFirst`、方块激活、放置方块、右键使用物品，并自行 post
   * NeoForge 的 `PlayerInteractEvent.RightClickBlock` 来让其它 mod 有机会拦截。
   *
   * `duration` 在 1.21.1 里由 [[useEquippedItem]] 传给 `agent.machine.pause`。
   */
  def activateBlockOrUseItem(pos: BlockPos, side: Direction, hitX: Float, hitY: Float, hitZ: Float, duration: Double): ActivationType.Value =
    callUsingItemInSlot(agent.equipmentInventory, 0, stack => {
      if (shouldCancel(() => fireRightClickBlock(pos, side))) ActivationType.None
      else {
        val item = if (stack.isEmpty) null else stack.getItem
        val state = level.getBlockState(pos)
        val traceEndPos = new Vec3(pos.getX + hitX, pos.getY + hitY, pos.getZ + hitZ)
        val traceCtx = if (state.isAir) BlockHitResult.miss(traceEndPos, side, pos) else new BlockHitResult(traceEndPos, side, pos, false)
        if (item != null && item.onItemUseFirst(stack, new UseOnContext(level, this, InteractionHand.OFF_HAND, stack, traceCtx)).consumesAction) {
          ActivationType.ItemUsed
        }
        else {
          val canActivate = !state.isAir && Settings.get.allowActivateBlocks
          val shouldActivate = canActivate && (!isCrouching || item == null || item.doesSneakBypassUse(stack, level, pos, this))
          val blockHit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), side, pos, false)
          if (shouldActivate && state.useItemOn(stack, level, this, InteractionHand.OFF_HAND, blockHit).consumesAction)
            ActivationType.BlockActivated
          else if (duration <= Double.MinPositiveValue && isItemUseAllowed(stack) && tryPlaceBlockWhileHandlingFunnySpecialCases(stack, pos, side, hitX, hitY, hitZ))
            ActivationType.ItemPlaced
          else if (useEquippedItem(duration, Option(stack)))
            ActivationType.ItemUsed
          else
            ActivationType.None
        }
      }
    })

  /** 右键空气使用手持物品（对应 1.7.10 的 `useEquippedItem`）。 */
  def useEquippedItem(duration: Double, stackOption: Option[ItemStack] = None): Boolean = {
    if (stackOption.isEmpty) {
      return callUsingItemInSlot(agent.equipmentInventory, 0, stack => useEquippedItem(duration, Option(stack)))
    }

    if (shouldCancel(() => fireRightClickAir())) {
      return false
    }

    // 把使用物品的位置沿朝向偏移半格，避免作用到机器人自己身上（弓、药水、采矿激光……）。
    setPos(getX + facing.getStepX / 2.0, getY, getZ + facing.getStepZ / 2.0)
    try {
      useItemWithHand(duration, stackOption.get)
    }
    finally {
      setPos(getX - facing.getStepX / 2.0, getY, getZ - facing.getStepZ / 2.0)
    }
  }

  /**
   * 在「按住右键」的语义下使用物品。
   *
   * 1.7.10 用 `setItemInUse` / `clearItemInUse` 手工驱动蓄力；1.21.1 对应
   * `LivingEntity#startUsingItem` / `releaseUsingItem`，并通过临时注册
   * `LivingEntityUseItemEvent.Start` 把这次使用的时长改成机器人给定的 `duration`，
   * 这样不需要真实 tick 也能让物品拿到正确的蓄力时间。
   */
  private def trySetActiveHand(duration: Double): Boolean = {
    releaseUsingItem()
    val entity = this
    val durationHandler = new {
      @SubscribeEvent(priority = EventPriority.LOWEST)
      def onItemUseStart(startUse: LivingEntityUseItemEvent.Start): Unit = {
        if (startUse.getEntity == entity && !startUse.isCanceled) {
          startUse.setDuration(duration.toInt)
        }
      }
    }
    NeoForge.EVENT_BUS.register(durationHandler)
    try {
      startUsingItem(InteractionHand.OFF_HAND)
      isUsingItem
    }
    catch {
      case _: Exception => false
    }
    finally {
      NeoForge.EVENT_BUS.unregister(durationHandler)
    }
  }

  def useItemWithHand(duration: Double, stack: ItemStack): Boolean = {
    if (!trySetActiveHand(duration)) {
      if (duration > 0) {
        return false
      }
    }

    val oldStack = if (stack == null || stack.isEmpty) ItemStack.EMPTY else stack.copy()
    if (!isItemUseAllowed(stack)) {
      return false
    }

    val maxDuration = stack.getUseDuration(this)
    val heldTicks = Math.max(0, Math.min(maxDuration, (duration * 20).toInt))
    if (agent.machine != null) {
      agent.machine.pause(heldTicks / 20.0)
    }

    // 设置「正在使用的手」同时会给出初始时长。
    val useItemResult = stack.use(level, this, InteractionHand.OFF_HAND)
    releaseUsingItem()

    if (!useItemResult.getResult.consumesAction) {
      return false
    }

    val newStack = useItemResult.getObject
    val stackChanged: Boolean =
      !ItemStack.matches(oldStack, newStack) ||
        !ItemStack.matches(oldStack, stack)

    if (stackChanged) {
      // OCCE 把结果写进 offhand 缓冲区；本移植里手部槽位直接映射到 agent 的工具槽。
      Inventory.setSlot(agent.equipmentInventory, 0, newStack)
    }
    stackChanged
  }

  /** 放置物品栏中指定槽位的方块（对应 1.7.10 的 `placeBlock`）。 */
  def placeBlock(slot: Int, pos: BlockPos, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean =
    callUsingItemInSlot(agent.mainInventory, slot, stack => {
      !shouldCancel(() => fireRightClickBlock(pos, side)) &&
        tryPlaceBlockWhileHandlingFunnySpecialCases(stack, pos, side, hitX, hitY, hitZ)
    }, repair = false)

  /**
   * 破坏方块（对应 1.7.10 的 `clickBlock`）。
   *
   * 返回破坏该方块需要的时间（秒）；返回 0 表示不能破坏。
   *
   * 1.7.10 手写了整条挖掘流程，OCCE 改为把「推进进度」和「真正破坏」交给原版
   * `ServerPlayerGameMode`（见 [[PlayerInteractionManagerHelper]]）：这里只负责
   * 可采集判定、耗时计算、`RobotBreakBlockEvent.Pre` 与启动逐 tick 的挖掘，
   * 后续由 [[DamageOverTime]] 收尾。
   */
  def clickBlock(pos: BlockPos, side: Direction): Double =
    callUsingItemInSlot(agent.equipmentInventory, 0, _ => {
      val state = level.getBlockState(pos)
      val block = state.getBlock
      if (!state.canHarvestBlock(level, pos, this)) 0.0
      else {
        val hardness = state.getDestroySpeed(level, pos)
        val cobwebOverride = block == Blocks.COBWEB && Settings.get.screwCobwebs
        val strength = getDigSpeed(state, pos)
        val breakTime =
          if (cobwebOverride) Settings.get.swingDelay
          else hardness * 1.5 / strength

        if (breakTime.isInfinity) 0.0
        else if (breakTime < 0) breakTime
        else {
          val preEvent = new RobotBreakBlockEvent.Pre(agent, level, pos.getX, pos.getY, pos.getZ, breakTime * Settings.get.harvestRatio)
          NeoForge.EVENT_BUS.post(preEvent)
          if (preEvent.isCanceled) 0.0
          else {
            val adjustedBreakTime = math.max(0.05, preEvent.getBreakTime)
            if (!PlayerInteractionManagerHelper.onBlockClicked(this, pos, side)) {
              // 没能开始挖掘：方块已经空了就当作瞬间完成，否则视为不可破坏。
              if (level.isEmptyBlock(pos)) 1.0 / 20.0
              else 0.0
            }
            else {
              // 1.7.10 用 `EventHandler.scheduleServer` 调度逐 tick 的挖掘进度。
              EventHandler.scheduleServer(() => new DamageOverTime(this, pos, side, (adjustedBreakTime * 20).toInt).tick())
              adjustedBreakTime
            }
          }
        }
      }
    })

  /**
   * ==1.21.1==
   * OCCE 通过让 [[Inventory]] 继承原版 `PlayerInventory` 并覆写 `getDestroySpeed`，让挖掘速度
   * 来自 agent 的工具槽（也就是机器人手上真正的工具）。1.21.1 的 `Player#inventory` 是 final、
   * 无法替换，因此这里等价地覆写 `getDigSpeed`：基础速度取自 agent 的工具槽，其余修正
   * （挖掘效率属性、急迫与疲劳效果、水下、离地、`PlayerEvent.BreakSpeed`）与原版保持一致。
   */
  override def getDigSpeed(state: BlockState, pos: BlockPos): Float = {
    var f = agentInventory.getDestroySpeed(state)
    if (f > 1f) {
      f += getAttributeValue(Attributes.MINING_EFFICIENCY).toFloat
    }

    if (MobEffectUtil.hasDigSpeed(this)) {
      f *= 1f + (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2f
    }

    if (hasEffect(MobEffects.DIG_SLOWDOWN)) {
      f *= (getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier match {
        case 0 => 0.3f
        case 1 => 0.09f
        case 2 => 0.0027f
        case _ => 8.1e-4f
      })
    }

    f *= getAttributeValue(Attributes.BLOCK_BREAK_SPEED).toFloat
    if (isEyeInFluid(FluidTags.WATER)) {
      f *= getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue.toFloat
    }

    if (!onGround()) {
      f /= 5f
    }

    EventHooks.getBreakSpeed(this, state, f, pos)
  }

  /**
   * 对应 1.7.10 的 `ForgeHooks.canHarvestBlock(block, this, metadata)`：它最终读的是
   * `Inventory#canHarvestBlock`，也就是「工具槽物品能否采集该方块」。1.21.1 的默认实现读的是
   * 假玩家自己那份原版物品栏的选中槽，因此这里换成 agent 的工具槽。
   */
  override def hasCorrectToolForDrops(state: BlockState): Boolean = agentInventory.canHarvestBlock(state)

  private def isItemUseAllowed(stack: ItemStack) = stack == null || stack.isEmpty || {
    (Settings.get.allowUseItemsWithDuration || stack.getUseDuration(this) <= 0) &&
      (!PortalGun.isPortalGun(stack) || PortalGun.isStandardPortalGun(stack)) &&
      !stack.is(Items.LEAD)
  }

  /**
   * 覆写 `Player#drop`（对应 1.7.10 的 `dropPlayerItemWithRandomChoice`）：
   * 按朝向在世界里生成掉落物。
   */
  override def drop(stack: ItemStack, dropAround: Boolean, traceItem: Boolean): ItemEntity =
    InventoryUtils.spawnStackInWorld(BlockPosition(agent), stack, if (dropAround) None else Option(facing))

  // ----------------------------------------------------------------------- //
  // NeoForge 交互事件注入（1.7.10 由 `ForgeEventFactory.onPlayerInteract` 完成）
  // ----------------------------------------------------------------------- //

  /** 触发 `PlayerInteractEvent.RightClickBlock`（对应 1.7.10 的 `onPlayerInteract(RIGHT_CLICK_BLOCK)`）。 */
  def fireRightClickBlock(pos: BlockPos, side: Direction): PlayerInteractEvent.RightClickBlock = {
    val hitVec = new Vec3(0.5 + side.getStepX * 0.5, 0.5 + side.getStepY * 0.5, 0.5 + side.getStepZ * 0.5)
    val event = new PlayerInteractEvent.RightClickBlock(this, InteractionHand.OFF_HAND, pos, new BlockHitResult(hitVec, side, pos, false))
    NeoForge.EVENT_BUS.post(event)
    event
  }

  /** 触发 `PlayerInteractEvent.LeftClickBlock`（对应 1.7.10 的 `onPlayerInteract(LEFT_CLICK_BLOCK)`）。 */
  def fireLeftClickBlock(pos: BlockPos, side: Direction): PlayerInteractEvent.LeftClickBlock =
    CommonHooks.onLeftClickBlock(this, pos, side, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)

  /** 触发 `PlayerInteractEvent.RightClickItem`（对应 1.7.10 的 `onPlayerInteract(RIGHT_CLICK_AIR)`）。 */
  def fireRightClickAir(): PlayerInteractEvent.RightClickItem = {
    val event = new PlayerInteractEvent.RightClickItem(this, InteractionHand.OFF_HAND)
    NeoForge.EVENT_BUS.post(event)
    event
  }

  /**
   * 判断事件是否要求取消这次交互。
   *
   * 1.7.10 检查 `Event.Result.DENY`；1.21.1 的 NeoForge 用 `TriState`，右键物品则改为
   * 检查 `getCancellationResult`。
   */
  private def shouldCancel(f: () => PlayerInteractEvent): Boolean = {
    try {
      f() match {
        case event: PlayerInteractEvent.RightClickBlock =>
          event.isCanceled || event.getUseBlock == TriState.FALSE || event.getUseItem == TriState.FALSE
        case event: PlayerInteractEvent.LeftClickBlock =>
          event.isCanceled || event.getUseBlock == TriState.FALSE || event.getUseItem == TriState.FALSE
        case event: PlayerInteractEvent.RightClickItem =>
          event.isCanceled || event.getCancellationResult == InteractionResult.FAIL
        case _ => false
      }
    }
    catch {
      case t: Throwable =>
        if (!t.getStackTrace.exists(_.getClassName.startsWith("mods.battlegear2."))) {
          OpenComputers.log.warn("Some event handler screwed up!", t)
        }
        false
    }
  }

  // ----------------------------------------------------------------------- //
  // 物品栏辅助
  // ----------------------------------------------------------------------- //

  /**
   * 在「使用某个槽位的物品」前后做统一的簿记：收集掉落物、处理物品耗尽与工具修复。
   *
   * 1.7.10 通过 `this.inventory.currentItem` 让原版逻辑知道「当前手持槽位」，并用取反的槽位号
   * 表示工具槽。1.21.1 的假玩家物品栏是原版 `Inventory`，不接受负索引，因此这里只同步选中
   * 槽编号（主物品栏用真实槽号，工具槽统一用 0）。
   */
  private def callUsingItemInSlot[T](inventory: IItemHandler, slot: Int, f: ItemStack => T, repair: Boolean = true): T = {
    val itemsBefore = adjacentItems
    val stack = Inventory.getSlot(inventory, slot)
    val oldStack = if (stack.isEmpty) ItemStack.EMPTY else stack.copy()
    val playerInventory = getInventory
    val previousSelected = playerInventory.selected
    val slotCount = playerInventory.items.size()
    playerInventory.selected =
      if (inventory eq agent.mainInventory) math.max(0, math.min(slot, slotCount - 1))
      else 0
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
          else EventHooks.onPlayerDestroyItem(this, newStack, InteractionHand.OFF_HAND)
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
   * 1.7.10 会先把 agent 的物品栏同步到假玩家身上，再调用 `ItemStack#tryPlaceItemIntoWorld`，
   * 结束后写回变化；同时为活塞类方块伪造视高。1.21.1 的放置入口是
   * `ItemStack#useOn(UseOnContext)`（会触发 NeoForge 的 `PlayerInteractEvent.RightClickBlock`
   * 与 `BlockItem#useOn`），这里保持同步与活塞处理的做法不变。
   */
  private def tryPlaceBlockWhileHandlingFunnySpecialCases(stack: ItemStack, pos: BlockPos, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean =
    !stack.isEmpty && stack.getCount > 0 && {
      val event = new RobotPlaceBlockEvent.Pre(agent, stack, level, pos.getX, pos.getY, pos.getZ)
      NeoForge.EVENT_BUS.post(event)
      if (event.isCanceled) false
      else {
        val fakeEyeHeight = if (getXRot < 0 && isSomeKindOfPiston(stack)) 1.82 else 0
        setPos(getX, getY - fakeEyeHeight, getZ)
        Player.setInventoryPlayerItems(this)
        val state = level.getBlockState(pos)
        val traceEndPos = new Vec3(pos.getX + hitX, pos.getY + hitY, pos.getZ + hitZ)
        val traceCtx = if (state.isAir) BlockHitResult.miss(traceEndPos, side, pos) else new BlockHitResult(traceEndPos, side, pos, false)
        val didPlace = stack.useOn(new UseOnContext(level, this, InteractionHand.OFF_HAND, stack, traceCtx))
        Player.detectInventoryPlayerChanges(this)
        setPos(getX, getY + fakeEyeHeight, getZ)
        if (didPlace.consumesAction) {
          NeoForge.EVENT_BUS.post(new RobotPlaceBlockEvent.Post(agent, stack, level, pos.getX, pos.getY, pos.getZ))
        }
        didPlace.consumesAction
      }
    }

  private def isSomeKindOfPiston(stack: ItemStack): Boolean =
    stack.getItem match {
      case itemBlock: BlockItem =>
        itemBlock.getBlock != null && itemBlock.getBlock.isInstanceOf[PistonBaseBlock]
      case _ => false
    }

  // ----------------------------------------------------------------------- //
  // 原版行为覆写
  // ----------------------------------------------------------------------- //

  /**
   * 1.7.10 的 `addExhaustion`；1.21.1 的对应方法是 `causeFoodExhaustion`。
   * 机器人的「疲劳」表现为扣能量加 post `RobotExhaustionEvent`。
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

  /** 1.7.10 的 `closeScreen()`；1.21.1 改名为 `closeContainer()`。假玩家不持有 GUI。 */
  override def closeContainer(): Unit = {}

  /** 1.7.10 的 `swingItem()`；1.21.1 改为 `swing(InteractionHand)`。假玩家不播放手臂动画。 */
  override def swing(hand: InteractionHand): Unit = {}

  /**
   * 1.7.10 的 `canCommandSenderUseCommand(level, command)`；1.21.1 的权限体系收敛为
   * `ServerPlayer#getPermissionLevel`（按 OCCE 的实现覆写）。
   * 1.7.10 对 `seed` / `tell` / `help` / `me` 的额外放行在新体系下没有对应入口，故不保留。
   */
  override protected def getPermissionLevel: Int = {
    val config = server.getPlayerList
    if (config.isOp(getGameProfile)) {
      config.getOps.get(getGameProfile) match {
        case opEntry: ServerOpListEntry => opEntry.getLevel
        case _ => server.getOperatorUserPermissionLevel
      }
    }
    else 0
  }

  override def canHarmPlayer(player: MCPlayer): Boolean = Settings.get.canAttackPlayers

  override def canEat(value: Boolean): Boolean = false

  /** 1.7.10 的 `isPotionApplicable`；1.21.1 改名为 `canBeAffected`。 */
  override def canBeAffected(effect: MobEffectInstance): Boolean = false

  /** 1.7.10 的 `attackEntityAsMob`；1.21.1 改名为 `doHurtTarget`。 */
  override def doHurtTarget(entity: Entity): Boolean = false

  /** 1.7.10 的 `attackEntityFrom`；1.21.1 改名为 `hurt`。 */
  override def hurt(source: DamageSource, amount: Float): Boolean = false

  override def heal(amount: Float): Unit = {}

  override def setHealth(value: Float): Unit = {}

  /**
   * 1.7.10 的 `setDead()`。1.21.1 的 `Entity#discard` 是 final，只能覆写 `remove`；
   * 基准（OCCE）在这里调用 `super.remove(RemovalReason.KILLED)`。
   */
  override def remove(reason: RemovalReason): Unit = super.remove(RemovalReason.KILLED)

  /** 1.7.10 的 `onLivingUpdate()`；1.21.1 的对应入口是 `aiStep()`。 */
  override def aiStep(): Unit = {}

  /** 1.7.10 的 `onItemPickup(entity, count)`；1.21.1 的对应入口是 `take(entity, count)`。 */
  override def take(entity: Entity, count: Int): Unit = {}

  /** 1.7.10 的 `setRevengeTarget`；1.21.1 由 `setLastHurtByMob` 承担同样的语义。 */
  override def setLastHurtByMob(entity: LivingEntity): Unit = {}

  /** 1.7.10 的 `setLastAttacker`；1.21.1 由 `setLastHurtMob` 承担同样的语义。 */
  override def setLastHurtMob(entity: Entity): Unit = {}

  /** 1.7.10 的 `mountEntity`；1.21.1 改为 `startRiding`。假玩家不骑乘任何东西。 */
  override def startRiding(entity: Entity, force: Boolean): Boolean = false

  /** 1.7.10 的 `sleepInBedAt` 返回 `OTHER_PROBLEM`；1.21.1 改为 `Either`。 */
  override def startSleepInBed(bedLocation: BlockPos): Either[BedStatus, net.minecraft.util.Unit] =
    Either.left[BedStatus, net.minecraft.util.Unit](BedStatus.OTHER_PROBLEM)

  /** 1.7.10 的 `addChatMessage(message)`；1.21.1 改为 `sendSystemMessage`。假玩家不接收消息。 */
  override def sendSystemMessage(message: Component): Unit = {}

  /** 1.7.10 的 GUI 覆写（`displayGUIChest` 等）在 1.21.1 收敛到这几个入口，全部吞掉。 */
  override def openMenu(guiOwner: MenuProvider): java.util.OptionalInt = java.util.OptionalInt.empty()

  override def openCommandBlock(commandBlock: CommandBlockEntity): Unit = {}

  override def openMinecartCommandBlock(thing: BaseCommandBlock): Unit = {}

  override def openTextEdit(signTile: SignBlockEntity, isFront: Boolean): Unit = {}

  override def sendMerchantOffers(containerId: Int, offers: MerchantOffers, villagerLevel: Int, villagerXP: Int, showProgress: Boolean, canRestock: Boolean): Unit = {}

  /**
   * 1.7.10 的 `setCurrentItemOrArmor(slot, stack)`；1.21.1 改为 `setItemSlot(EquipmentSlot, ItemStack)`。
   * 机器人只有主手工具槽（映射到 `agent.equipmentInventory` 的第 0 槽），没有护甲槽。
   */
  override def setItemSlot(slot: EquipmentSlot, stack: ItemStack): Unit = {
    if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
      if (agent.equipmentInventory != null && agent.equipmentInventory.getSlots > 0) {
        Inventory.setSlot(agent.equipmentInventory, 0, stack)
      }
    }
    // 其它装备槽（护甲等）在机器人上不受支持。
  }

  /**
   * 覆写手部槽位的读取。
   *
   * 1.7.10 与 OCCE 里「假玩家手上的物品」就是从 agent 的工具槽读出来的（OCCE 用 offhand
   * 缓冲区中转）。1.21.1 无法替换 `inventory`，因此这里直接把两个手部槽映射到
   * `agent.equipmentInventory` 的第 0 槽，`getItemInHand` / `getMainHandItem` 便与基准一致。
   */
  override def getItemBySlot(slot: EquipmentSlot): ItemStack =
    if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) agentInventory.getCurrentItem
    else super.getItemBySlot(slot)

  // ----------------------------------------------------------------------- //
  // 逐 tick 的挖掘进度
  // ----------------------------------------------------------------------- //

  /**
   * 逐 tick 推进挖掘进度（对应 1.7.10 与 OCCE 的同名内部类）。
   *
   * 1.7.10 用 `world.destroyBlockInWorldPartially` 直接画破坏进度；OCCE 改为驱动原版
   * `ServerPlayerGameMode`（[[PlayerInteractionManagerHelper]]），破坏动画与掉落物都由原版负责，
   * 这里只保留「何时推进、何时收尾、进度没到就重新排队」的调度逻辑。
   */
  class DamageOverTime(val player: Player, val pos: BlockPos, val side: Direction, val ticksTotal: Int) {
    val level: Level = player.level
    var ticks = 0
    var lastDamageSent = 0

    def tick(): Unit = {
      // 机器人停机、方块消失或世界变了就中止。
      if (level != player.level || !level.isLoaded(pos) || level.isEmptyBlock(pos) ||
        player.agent.machine == null || !player.agent.machine.isRunning) {
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, player.level.getMaxBuildHeight, 0)
        return
      }

      val damage = 10 * ticks / Math.max(ticksTotal, 1)
      if (damage < 10) {
        ticks += 1
        if (damage != lastDamageSent) {
          lastDamageSent = damage
          if (!PlayerInteractionManagerHelper.updateBlockRemoving(player)) return
        }
        EventHandler.scheduleServer(() => tick())
      }
      else {
        callUsingItemInSlot(player.agent.equipmentInventory, 0, _ => {
          // 沿挖掘面反向偏移半格，避免破坏判定落到机器人自己身上。
          this.player.setPos(this.player.getX - side.getStepX / 2.0, this.player.getY, this.player.getZ - side.getStepZ / 2.0)
          val expGained: Int = PlayerInteractionManagerHelper.blockRemoving(player, pos)
          this.player.setPos(this.player.getX + side.getStepX / 2.0, this.player.getY, this.player.getZ + side.getStepZ / 2.0)
          if (expGained >= 0) {
            NeoForge.EVENT_BUS.post(new RobotBreakBlockEvent.Post(agent, expGained))
          }
        })
      }
    }
  }
}
