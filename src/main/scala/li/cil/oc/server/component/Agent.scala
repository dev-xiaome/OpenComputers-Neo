package li.cil.oc.server.component

import li.cil.oc.Settings
import li.cil.oc.api.event.RobotPlaceInAirEvent
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.common.{entity => entityPackage}
import li.cil.oc.server.agent.ActivationType
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.{BlockHitResult, EntityHitResult, HitResult, Vec3}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.BlockEvent

/**
 * 机器人 / 无人机与世界交互的公共组件层。
 *
 * ==1.21.1 移植要点==
 *  - `ForgeDirection` → `Direction`；`Direction#offsetX/offsetY/offsetZ` → `getStepX/getStepY/getStepZ`；
 *    `Direction.VALID_DIRECTIONS` → `Direction.values`。
 *  - `MovingObjectPosition` → `HitResult`（`BlockHitResult` / `EntityHitResult`），
 *    `typeOfHit` → `getType`，`blockX/blockY/blockZ` → `BlockHitResult#getBlockPos`，
 *    `sideHit`（Int） → `BlockHitResult#getDirection`（`Direction`），
 *    `hitVec` → `getLocation`。
 *  - `Vec3.createVectorHelper` → `new Vec3(...)`，`addVector` → `add`。
 *  - `Player#setSneaking` → `Player#setShiftKeyDown`。
 *  - `Level#rayTraceBlocks` → `Level#clip(new ClipContext(...))`。
 *  - `EntityMinecart` → `AbstractMinecart`。
 *
 * ==降级清单==
 *  - `li.cil.oc.server.agent.Player`（机器人专用假玩家，**该包尚未移植**）：
 *    改用 [[li.cil.oc.api.internal.Agent#player]] 返回的通用假玩家，并通过
 *    `ServerPlayerGameMode`（`useItemOn` / `useItem` / `destroyBlock`）完成交互。
 *    上游在此基础上的精细逻辑（挖掘耗时计算、`RobotBreakBlockEvent` 的额外语义、
 *    击杀掉落物回收、物品栏与假玩家的双向同步）全部做了简化，逐个位置标了 `TODO(server.agent)`。
 *  - `Entity#captureDrops` / `capturedDrops` 在 1.21.1 已移除，见 [[beginConsumeDrops]] / [[endConsumeDrops]]。
 */
trait Agent extends traits.WorldControl with traits.InventoryControl with traits.InventoryWorldControl with traits.TankAware with traits.TankControl with traits.TankWorldControl {
  def agent: internal.Agent

  override def position = BlockPosition(agent)

  /**
   * 组件使用的假玩家。
   *
   * 上游直接返回 `server.agent.Player`；本移植返回 [[li.cil.oc.api.internal.Agent#player]]
   * （目前是 NeoForge 的通用 FakePlayer），拿不到时退回 [[traits.WorldAware]] 的默认实现。
   */
  override def fakePlayer = {
    val player = agent.player
    if (player != null) player else super.fakePlayer
  }

  /**
   * 把假玩家摆到机器人当前位置与朝向上，供后续交互使用。
   *
   * TODO(server.agent): 上游调用 `server.agent.Player#updatePositionAndRotation` 并
   * 用 `setInventoryPlayerItems` 把 agent 的物品栏同步到假玩家身上，交互结束后再用
   * `detectInventoryPlayerChanges` 把变化写回。`server/agent` 未移植，这里：
   *  - 位置/朝向退化为按 `facing`/`side` 计算 yaw/pitch 后 `moveTo`；
   *  - 物品栏不再同步，交互时直接把 agent 选中槽位的 `ItemStack` 传给
   *    `ServerPlayerGameMode`（[[heldStack]]），这样物品消耗/损耗会作用在真实物品上。
   */
  protected def rotatedPlayer(facing: Direction = agent.facing, side: Direction = agent.facing): Player = {
    val player = fakePlayer
    if (player != null) {
      val yaw = side.toYRot
      val pitch = facing match {
        case Direction.UP => -90f
        case Direction.DOWN => 90f
        case _ => 0f
      }
      player.moveTo(position.x + 0.5, position.y + 0.5, position.z + 0.5, yaw, pitch)
    }
    player
  }

  // ----------------------------------------------------------------------- //

  override def inventory = agent.mainInventory

  override def selectedSlot = agent.selectedSlot

  override def selectedSlot_=(value: Int): Unit = agent.setSelectedSlot(value)

  // ----------------------------------------------------------------------- //

  override def tank = agent.tank

  def selectedTank = agent.selectedTank

  override def selectedTank_=(value: Int) = agent.setSelectedTank(value)

  // ----------------------------------------------------------------------- //

  def canPlaceInAir = {
    val event = new RobotPlaceInAirEvent(agent)
    NeoForge.EVENT_BUS.post(event)
    event.isAllowed
  }

  def onWorldInteraction(context: Context, duration: Double): Unit = {
    context.pause(duration)
  }

  // ----------------------------------------------------------------------- //

  @Callback(doc = "function():string -- Get the name of the agent.")
  def name(context: Context, args: Arguments): Array[AnyRef] = result(agent.name)

  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false]]):boolean, string -- Perform a 'left click' towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  def swing(context: Context, args: Arguments): Array[AnyRef] = {
    // Swing the equipped tool (left click).
    val facing = checkSideForAction(args, 0)
    val sides =
      if (args.isInteger(1)) {
        Iterable(checkSideForFace(args, 1, facing))
      }
      else {
        // Always try the direction we're looking first.
        Iterable(facing) ++ Direction.values.filter(side => side != facing && side != facing.getOpposite).toIterable
      }
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)

    def triggerDelay(delay: Double = Settings.get.swingDelay): Unit = {
      onWorldInteraction(context, delay)
    }
    def attack(player: Player, target: Entity) = {
      // TODO(server.agent): `Player#attack` 内部只能用假玩家自己手持的物品，
      // 无法像 `ServerPlayerGameMode` 那样显式传入 agent 的物品，因此攻击伤害与
      // 工具损耗会作用在假玩家的手持物上，而不是机器人物品栏里选中的工具。
      beginConsumeDrops(target)
      player.attack(target)
      // Mine carts have to be hit quickly in succession to break, so we click
      // until it breaks. But avoid an infinite loop... you never know.
      target match {
        case _: AbstractMinecart => for (_ <- 0 until 10 if !target.isRemoved) {
          player.attack(target)
        }
        case _ =>
      }
      endConsumeDrops(player, target)
      triggerDelay()
      (true, "entity")
    }
    def click(player: Player, blockPos: BlockPos, side: Direction) = {
      val breakTime = clickBlock(player, blockPos, side)
      val broke = breakTime > 0
      if (broke) {
        triggerDelay(breakTime)
      }
      (broke, "block")
    }

    var reason: Option[String] = None
    for (side <- sides) {
      val player = rotatedPlayer(facing, side)
      player.setShiftKeyDown(sneaky)

      val (success, what) = pick(player, Settings.get.swingRange, facing, side) match {
        case hit if hit.getType == HitResult.Type.ENTITY =>
          attack(player, hit.asInstanceOf[EntityHitResult].getEntity)
        case hit if hit.getType == HitResult.Type.BLOCK =>
          val blockHit = hit.asInstanceOf[BlockHitResult]
          click(player, blockHit.getBlockPos, blockHit.getDirection)
        case _ =>
          // Retry with full block bounds, disregarding swing range.
          closestEntity[LivingEntity](side) match {
            case Some(target) =>
              attack(player, target)
            case _ =>
              if (world.extinguishFire(player, position, facing)) {
                triggerDelay()
                (true, "fire")
              }
              else (false, "air")
          }
      }

      player.setShiftKeyDown(false)
      if (success) {
        return result(true, what)
      }
      reason = reason.orElse(Option(what))
    }

    // all side attempts failed - but there could be a partial block that is hard to "see"
    val (hasBlock, _) = blockContent(facing)
    if (hasBlock) {
      val blockPos = position.offset(facing)
      val player = rotatedPlayer(facing, facing)
      player.setShiftKeyDown(sneaky)
      val (ok, why) = clickBlock(player, blockPos.toChunkCoordinates, facing) match {
        case breakTime if breakTime > 0 => (true, "block")
        case _ => (false, "block")
      }
      player.setShiftKeyDown(false)
      return result(ok, why)
    }

    result(false, reason.orNull)
  }

  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false[, duration:number=0]]]):boolean, string -- Perform a 'right click' towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  def use(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val sides =
      if (args.isInteger(1)) {
        Iterable(checkSideForFace(args, 1, facing))
      }
      else {
        // Always try the direction we're looking first.
        Iterable(facing) ++ Direction.values.filter(side => side != facing && side != facing.getOpposite).toIterable
      }
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)
    val duration =
      if (args.isDouble(3)) args.checkDouble(3)
      else 0.0

    def triggerDelay(): Unit = {
      onWorldInteraction(context, Settings.get.useDelay)
    }
    def activationResult(activationType: ActivationType.Value) =
      activationType match {
        case ActivationType.BlockActivated =>
          triggerDelay()
          (true, "block_activated")
        case ActivationType.ItemPlaced =>
          triggerDelay()
          (true, "item_placed")
        case ActivationType.ItemUsed =>
          triggerDelay()
          (true, "item_used")
        case _ => (false, "")
      }
    def interact(player: Player, target: Entity) = {
      beginConsumeDrops(target)
      val result = player.interactOn(target, InteractionHand.MAIN_HAND).consumesAction()
      endConsumeDrops(player, target)
      result
    }

    for (side <- sides) {
      val player = rotatedPlayer(facing, side)
      player.setShiftKeyDown(sneaky)

      val hit = pick(player, Settings.get.useAndPlaceRange, facing, side)
      val (success, what) = hit.getType match {
        case HitResult.Type.ENTITY if interact(player, hit.asInstanceOf[EntityHitResult].getEntity) =>
          triggerDelay()
          (true, "item_interacted")
        case HitResult.Type.BLOCK =>
          val (bx, by, bz, hx, hy, hz) = clickParamsFromHit(hit)
          activationResult(activatableBlockOrItem(player, bx, by, bz, hit.asInstanceOf[BlockHitResult].getDirection, hx, hy, hz, duration))
        case _ =>
          (if (canPlaceInAir) {
            val (bx, by, bz, hx, hy, hz) = clickParamsForPlace(facing)
            if (placeBlock(player, bx, by, bz, facing, hx, hy, hz))
              ActivationType.ItemPlaced
            else {
              val (ux, uy, uz, uhx, uhy, uhz) = clickParamsForItemUse(facing, side)
              val useHit = new BlockHitResult(
                new Vec3(ux + uhx, uy + uhy, uz + uhz),
                side.getOpposite,
                new BlockPos(ux, uy, uz),
                false)
              activatableBlockOrItem(player, ux, uy, uz, useHit.getDirection, uhx, uhy, uhz, duration)
            }
          } else ActivationType.None) match {
            case ActivationType.None =>
              if (useEquippedItem(player))
                triggerDelay()
              if (useEquippedItem(player)) (true, "item_used")
              else (false, "air")
            case activationType => activationResult(activationType)
          }
      }

      player.setShiftKeyDown(false)
      if (success) {
        return result(true, what)
      }
    }

    result(false)
  }

  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false]]):boolean -- Place a block towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  def place(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val sides =
      if (args.isInteger(1)) {
        Iterable(checkSideForFace(args, 1, facing))
      }
      else {
        // Always try the direction we're looking first.
        Iterable(facing) ++ Direction.values.filter(side => side != facing && side != facing.getOpposite).toIterable
      }
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)
    val stack = heldStack
    if (stack.isEmpty) {
      return result((), "nothing selected")
    }

    for (side <- sides) {
      val player = rotatedPlayer(facing, side)
      player.setShiftKeyDown(sneaky)
      val hit = pick(player, Settings.get.useAndPlaceRange, facing, side)
      val success = hit.getType match {
        case HitResult.Type.BLOCK =>
          val (bx, by, bz, hx, hy, hz) = clickParamsFromHit(hit)
          placeBlock(player, bx, by, bz, hit.asInstanceOf[BlockHitResult].getDirection, hx, hy, hz)
        case _ if canPlaceInAir && closestEntity[Entity](side).isEmpty =>
          val (bx, by, bz, hx, hy, hz) = clickParamsForPlace(facing)
          placeBlock(player, bx, by, bz, facing, hx, hy, hz)
        case _ => false
      }
      player.setShiftKeyDown(false)
      if (success) {
        onWorldInteraction(context, Settings.get.placeDelay)
        return result(true)
      }
    }

    result(false)
  }

  // ----------------------------------------------------------------------- //

  /**
   * 当前「手持」物品，即 agent 选中槽位里的物品。
   *
   * TODO(server.agent): 上游把 agent 物品栏同步到假玩家身上后从假玩家手里取；
   * `server/agent` 未移植，这里直接读 agent 的主物品栏。
   */
  protected def heldStack: ItemStack = {
    val stack = agent.mainInventory.getStackInSlot(agent.selectedSlot)
    if (stack == null) stack else stack
  }

  /**
   * TODO(1.21.1): 1.7.10 的 `Entity#captureDrops` / `capturedDrops` 在 1.21.1 已被移除，
   * 无法再截获击杀产生的掉落物并自动塞进机器人物品栏。
   * 这里退化为空实现：掉落物照常留在世界里（Lua 侧表现为 `robot.swing()` 击杀后不再自动拾取）。
   */
  protected def beginConsumeDrops(target: Entity): Unit = ()

  /** 见 [[beginConsumeDrops]]，同样退化为空实现。 */
  protected def endConsumeDrops(player: Player, target: Entity): Unit = ()

  // ----------------------------------------------------------------------- //

  protected def checkSideForFace(args: Arguments, n: Int, facing: Direction) = agent.toGlobal(args.checkSideForFace(n, agent.toLocal(facing)))

  /**
   * 从假玩家眼睛位置朝指定方向做一次方块/实体拾取。
   *
   * TODO(server.agent): 上游用 `server.agent.Player` 精确的 `facing` / `side` 字段构造
   * 射线起点与终点。这里按 `facing` 前进 0.5 格作为起点、`side` 方向延伸 `range` 格作为终点，
   * 语义与上游一致；不同的是终点到实体距离的比较改用了 `Entity#distanceTo`。
   */
  protected def pick(player: Player, range: Double, facing: Direction, side: Direction): HitResult = {
    val origin = new Vec3(
      player.getX + facing.getStepX * 0.5,
      player.getY + facing.getStepY * 0.5,
      player.getZ + facing.getStepZ * 0.5)
    val blockCenter = origin.add(
      facing.getStepX * 0.51,
      facing.getStepY * 0.51,
      facing.getStepZ * 0.51)
    val target = blockCenter.add(
      side.getStepX * range,
      side.getStepY * range,
      side.getStepZ * range)
    val hit = world.clip(new ClipContext(origin, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
    closestEntity[Entity](side) match {
      // 1.21.1（Scala 2.13）：不支持 Scala 3 的联合类型 `A | B | C`，
      // 改用类型守卫并列判断，语义与上游一致（只有这三类实体参与近战拾取）。
      case Some(target)
        if (target.isInstanceOf[LivingEntity] || target.isInstanceOf[AbstractMinecart] || target.isInstanceOf[entityPackage.Drone]) &&
          (hit.getType == HitResult.Type.MISS ||
            new Vec3(player.getX, player.getY, player.getZ).distanceTo(hit.getLocation) > player.distanceTo(target)) =>
        new EntityHitResult(target)
      case _ => hit
    }
  }

  protected def clickParamsFromHit(hit: HitResult) = hit match {
    case block: BlockHitResult =>
      val pos = block.getBlockPos
      val location = block.getLocation
      (pos.getX, pos.getY, pos.getZ,
        (location.x - pos.getX).toFloat,
        (location.y - pos.getY).toFloat,
        (location.z - pos.getZ).toFloat)
    case _ =>
      val location = hit.getLocation
      (Mth.floor(location.x), Mth.floor(location.y), Mth.floor(location.z), 0.5f, 0.5f, 0.5f)
  }

  protected def clickParamsForItemUse(facing: Direction, side: Direction) = {
    val blockPos = position.offset(facing).offset(side)
    (blockPos.x, blockPos.y, blockPos.z,
      0.5f - side.getStepX * 0.5f,
      0.5f - side.getStepY * 0.5f,
      0.5f - side.getStepZ * 0.5f)
  }

  protected def clickParamsForPlace(facing: Direction) = {
    (position.x, position.y, position.z,
      0.5f + facing.getStepX * 0.5f,
      0.5f + facing.getStepY * 0.5f,
      0.5f + facing.getStepZ * 0.5f)
  }

  // ----------------------------------------------------------------------- //
  // 与世界的实际交互：全部经由假玩家的 ServerPlayerGameMode 完成。
  // ----------------------------------------------------------------------- //

  /**
   * 左键点击方块（挖掘），返回挖掘耗时（0 表示没挖成）。
   *
   * TODO(server.agent): 上游会 `RobotBreakBlockEvent` 之前先算挖掘速度、处理
   * 匠魂 / 匠魂类工具的特殊破坏、撬棍 / 蜘蛛网之类的特例，并在失败时给出更细的原因。
   * 这里退化为「投递 `BlockEvent.BreakEvent` → 直接 `destroyBlock` → 用方块硬度估算耗时」。
   */
  protected def clickBlock(player: Player, blockPos: BlockPos, side: Direction): Double = {
    val state = world.getBlockState(blockPos)
    val hardness = state.getDestroySpeed(world, blockPos)
    if (hardness < 0) 0.0
    else player match {
      case serverPlayer: ServerPlayer =>
        player.swing(InteractionHand.MAIN_HAND)
        val event = new BlockEvent.BreakEvent(world, blockPos, state, player)
        NeoForge.EVENT_BUS.post(event)
        if (event.isCanceled) 0.0
        else if (serverPlayer.gameMode.destroyBlock(blockPos)) math.max(0.05, hardness * Settings.get.harvestRatio)
        else 0.0
      case _ => 0.0
    }
  }

  /**
   * 右键方块 / 用物品激活方块，返回动作类型。
   *
   * TODO(server.agent): 上游 `server.agent.Player#activateBlockOrUseItem` 会依次尝试
   * 方块激活、物品放置与物品使用，并严格区分 `BlockActivated` / `ItemPlaced` / `ItemUsed`。
   * 这里统一交给 `ServerPlayerGameMode#useItemOn`，再用「方块状态是否变化」粗判是否为方块激活。
   */
  protected def activatableBlockOrItem(player: Player, x: Int, y: Int, z: Int, side: Direction, hitX: Float, hitY: Float, hitZ: Float, duration: Double): ActivationType.Value = {
    val stack = heldStack
    if (stack.isEmpty) ActivationType.None
    else player match {
      case serverPlayer: ServerPlayer =>
        val pos = new BlockPos(x, y, z)
        val beforeState = world.getBlockState(pos)
        val beforeCount = stack.getCount
        val hit = new BlockHitResult(new Vec3(x + hitX, y + hitY, z + hitZ), side, pos, false)
        val interaction = serverPlayer.gameMode.useItemOn(serverPlayer, world, stack, InteractionHand.MAIN_HAND, hit)
        if (!interaction.consumesAction) ActivationType.None
        else if (world.getBlockState(pos) != beforeState) ActivationType.BlockActivated
        else if (stack.isEmpty || stack.getCount != beforeCount) ActivationType.ItemPlaced
        else ActivationType.ItemUsed
      case _ => ActivationType.None
    }
  }

  /** 右键空气使用手持物品（对应上游 `Player#useEquippedItem`）。 */
  protected def useEquippedItem(player: Player): Boolean = {
    val stack = heldStack
    if (stack.isEmpty) false
    else player match {
      case serverPlayer: ServerPlayer =>
        val beforeCount = stack.getCount
        val interaction = serverPlayer.gameMode.useItem(serverPlayer, world, stack, InteractionHand.MAIN_HAND)
        interaction.consumesAction || stack.getCount != beforeCount
      case _ => false
    }
  }

  /**
   * 在指定方块上放置手持方块。
   *
   * TODO(server.agent): 上游 `server.agent.Player#placeBlock` 会先把物品栏对应槽位的物品
   * 设进假玩家手里再走原版放置流程，还会处理 `RobotPlaceBlockEvent`。
   * 这里直接把 agent 选中槽位的物品交给 `ServerPlayerGameMode#useItemOn`。
   */
  protected def placeBlock(player: Player, x: Int, y: Int, z: Int, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val stack = heldStack
    if (stack.isEmpty) false
    else player match {
      case serverPlayer: ServerPlayer =>
        val pos = new BlockPos(x, y, z)
        val beforeCount = stack.getCount
        val beforeState = world.getBlockState(pos)
        val hit = new BlockHitResult(new Vec3(x + hitX, y + hitY, z + hitZ), side, pos, false)
        val interaction = serverPlayer.gameMode.useItemOn(serverPlayer, world, stack, InteractionHand.MAIN_HAND, hit)
        interaction.consumesAction || stack.getCount != beforeCount || world.getBlockState(pos) != beforeState
      case _ => false
    }
  }
}
