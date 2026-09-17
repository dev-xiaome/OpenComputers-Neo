package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.common.item.HoverBoots
import li.cil.oc.util.PlayerUtils
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent
import net.neoforged.neoforge.event.entity.living.LivingFallEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * 悬浮靴：提供短距离飞行 / 跳跃 / 缓落，并抬高一步高度。
 *
 * 1.21.1 迁移要点：
 *  - `LivingEvent.LivingUpdateEvent` 已移除，改用 `PlayerTickEvent.Pre`
 *    （本处理器只关心玩家，二者语义一致）。
 *  - `player.getEntityData` → [[li.cil.oc.util.PlayerUtils.persistedData]]。
 *  - `player.isSneaking` → `player.isShiftKeyDown`；`capabilities.isCreativeMode` →
 *    `player.getAbilities.instabuild`。
 *  - `worldObj.getTotalWorldTime` → `level().getGameTime`；`motionX/Y/Z` →
 *    `getDeltaMovement()` / `setDeltaMovement(...)`；`addVelocity` → `push`。
 *  - `player.stepHeight = x` 在 1.21.1 不存在：一步高度改由
 *    `Attributes.STEP_HEIGHT` 属性承载（默认 0.6），这里通过 `AttributeInstance#setBaseValue`
 *    设置。
 *  - 装备栏读取：`getEquipmentInSlot(1 to 4)` → `getItemBySlot(FEET/LEGS/CHEST/HEAD)`。
 */
object HoverBootsHandler {
  /** 没有悬浮靴时的默认一步高度（1.21.1 玩家的 `Attributes.STEP_HEIGHT` 默认值）。 */
  private val DefaultStepHeight = 0.6

  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: PlayerTickEvent.Pre) => onPlayerTick(e))
    NeoForge.EVENT_BUS.addListener((e: LivingJumpEvent) => onLivingJump(e))
    NeoForge.EVENT_BUS.addListener((e: LivingFallEvent) => onLivingFall(e))
  }

  def onPlayerTick(e: PlayerTickEvent.Pre): Unit = e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] =>
      val nbt = PlayerUtils.persistedData(player)
      val hadHoverBoots = nbt.getBoolean(Settings.namespace + "hasHoverBoots")
      val hasHoverBoots = !player.isShiftKeyDown && equippedArmor(player).exists(stack => stack.getItem match {
        case boots: HoverBoots =>
          Settings.get.ignorePower || {
            if (player.onGround() && !player.getAbilities.instabuild &&
              player.level().getGameTime % Settings.get.tickFrequency == 0) {
              val motion = player.getDeltaMovement
              val velocity = motion.x * motion.x + motion.y * motion.y + motion.z * motion.z
              if (velocity > 0.015) {
                boots.charge(stack, -Settings.get.hoverBootMove, simulate = false)
              }
            }
            boots.getCharge(stack) > 0
          }
        case _ => false
      })
      if (hasHoverBoots != hadHoverBoots) {
        nbt.putBoolean(Settings.namespace + "hasHoverBoots", hasHoverBoots)
        val stepHeight = player.getAttribute(Attributes.STEP_HEIGHT)
        if (stepHeight != null) {
          stepHeight.setBaseValue(if (hasHoverBoots) 1.0 else DefaultStepHeight)
        }
      }
      if (hasHoverBoots && !player.onGround() && player.fallDistance < 5 && player.getDeltaMovement.y < 0) {
        val motion = player.getDeltaMovement
        player.setDeltaMovement(motion.x, motion.y * 0.9, motion.z)
      }
    case _ => // 忽略。
  }

  def onLivingJump(e: LivingJumpEvent): Unit = e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] && !player.isShiftKeyDown =>
      equippedArmor(player).collectFirst {
        case stack if stack.getItem.isInstanceOf[HoverBoots] =>
          val boots = stack.getItem.asInstanceOf[HoverBoots]
          val hoverJumpCost = -Settings.get.hoverBootJump
          val isCreative = Settings.get.ignorePower || player.getAbilities.instabuild
          if (isCreative || boots.charge(stack, hoverJumpCost, simulate = true) == 0) {
            if (!isCreative) boots.charge(stack, hoverJumpCost, simulate = false)
            val motion = player.getDeltaMovement
            if (player.isSprinting)
              player.push(motion.x * 0.5, 0.4, motion.z * 0.5)
            else
              player.push(0, 0.4, 0)
          }
      }
    case _ => // 忽略。
  }

  def onLivingFall(e: LivingFallEvent): Unit = if (e.getDistance > 3) e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] =>
      equippedArmor(player).collectFirst {
        case stack if stack.getItem.isInstanceOf[HoverBoots] =>
          val boots = stack.getItem.asInstanceOf[HoverBoots]
          val hoverFallCost = -Settings.get.hoverBootAbsorb
          val isCreative = Settings.get.ignorePower || player.getAbilities.instabuild
          if (isCreative || boots.charge(stack, hoverFallCost, simulate = true) == 0) {
            if (!isCreative) boots.charge(stack, hoverFallCost, simulate = false)
            e.setDistance(e.getDistance * 0.3f)
          }
      }
    case _ => // 忽略。
  }

  /** 玩家身上四件护甲（靴 → 头盔），跳过空槽。 */
  private def equippedArmor(player: Player): Seq[ItemStack] =
    Seq(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD).
      map(player.getItemBySlot).
      filter(stack => stack != null && !stack.isEmpty)
}
