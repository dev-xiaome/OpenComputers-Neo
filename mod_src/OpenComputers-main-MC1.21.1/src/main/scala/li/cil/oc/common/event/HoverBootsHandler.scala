package li.cil.oc.common.event

import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.common.item.HoverBoots
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.ai.attributes.{AttributeModifier, Attributes}
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent
import net.neoforged.neoforge.event.entity.living.LivingFallEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

import scala.collection.convert.ImplicitConversionsToScala._

object HoverBootsHandler {
  @SubscribeEvent
  def onLivingUpdate(e: EntityTickEvent.Post): Unit = e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] =>
      val nbt = player.getPersistentData
      val hadHoverBoots = nbt.getBoolean(Settings.namespace + "hasHoverBoots")
      val hasHoverBoots = !player.isCrouching && equippedArmor(player).exists(stack => stack.getItem match {
        case boots: HoverBoots =>
          Settings.get.ignorePower || {
            if (player.onGround && !player.isCreative && player.level.getGameTime % Settings.get.tickFrequency == 0) {
              val velocity = player.getDeltaMovement.lengthSqr
              if (velocity > 0.015f) {
                boots.charge(stack, -Settings.get.hoverBootMove, simulate = false)
              }
            }
            boots.getCharge(stack) > 0
          }
        case _ => false
      })
      if (hasHoverBoots != hadHoverBoots) {
        nbt.putBoolean(Settings.namespace + "hasHoverBoots", hasHoverBoots)
        val stepHeightAttr = player.getAttribute(Attributes.STEP_HEIGHT)
        if (stepHeightAttr != null) {
          val modifierId = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "hover_boots_step")
          stepHeightAttr.removeModifier(modifierId)
          if (hasHoverBoots) {
            stepHeightAttr.addTransientModifier(new AttributeModifier(
              modifierId,
              0.5,
              AttributeModifier.Operation.ADD_VALUE
            ))
          }
        }
      }
      if (hasHoverBoots && !player.onGround && player.fallDistance < 5 && player.getDeltaMovement.y < 0) {
        player.setDeltaMovement(player.getDeltaMovement.multiply(1, 0.9, 1))
      }
      if (hasHoverBoots && !Settings.get.ignorePower && player.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null) {
        equippedArmor(player).foreach {
          case stack if stack.getItem.isInstanceOf[HoverBoots] =>
            val boots = stack.getItem.asInstanceOf[HoverBoots]
            if (boots.getCharge(stack) == 0) {
              player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1))
            }
          case _ =>
        }
      }
    case _ => // Ignore.
  }

  @SubscribeEvent
  def onLivingJump(e: LivingJumpEvent): Unit = e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] && !player.isCrouching =>
      equippedArmor(player).collectFirst {
        case stack if stack.getItem.isInstanceOf[HoverBoots] =>
          val boots = stack.getItem.asInstanceOf[HoverBoots]
          val hoverJumpCost = -Settings.get.hoverBootJump
          val isCreative = Settings.get.ignorePower || player.isCreative
          if (isCreative || boots.charge(stack, hoverJumpCost, simulate = true) == 0) {
            if (!isCreative) boots.charge(stack, hoverJumpCost, simulate = false)
            val motion = player.getDeltaMovement
            if (player.isSprinting)
              player.push(motion.x * 0.5, 0.4, motion.z * 0.5)
            else
              player.push(0, 0.4, 0)
          }
      }
    case _ => // Ignore.
  }

  @SubscribeEvent
  def onLivingFall(e: LivingFallEvent): Unit = if (e.getDistance > 3) e.getEntity match {
    case player: Player if !player.isInstanceOf[FakePlayer] =>
      equippedArmor(player).collectFirst {
        case stack if stack.getItem.isInstanceOf[HoverBoots] =>
          val boots = stack.getItem.asInstanceOf[HoverBoots]
          val hoverFallCost = -Settings.get.hoverBootAbsorb
          val isCreative = Settings.get.ignorePower || player.isCreative
          if (isCreative || boots.charge(stack, hoverFallCost, simulate = true) == 0) {
            if (!isCreative) boots.charge(stack, hoverFallCost, simulate = false)
            e.setDistance(e.getDistance * 0.3f)
          }
      }
    case _ => // Ignore.
  }

  private def equippedArmor(player: Player) = player.getInventory.armor.filter(!_.isEmpty)
}
