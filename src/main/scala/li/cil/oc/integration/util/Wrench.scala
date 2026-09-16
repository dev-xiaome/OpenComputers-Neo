package li.cil.oc.integration.util

import java.lang.reflect.Method

import li.cil.oc.common.IMC
import li.cil.oc.util.BlockPosition
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

object Wrench {
  private val usages = mutable.LinkedHashSet.empty[Method]
  private val checks = mutable.LinkedHashSet.empty[Method]

  def addUsage(wrench: Method): Unit = usages += wrench

  def addCheck(checker: Method): Unit = checks += checker

  def isWrench(stack: ItemStack): Boolean = stack != null && checks.exists(IMC.tryInvokeStatic(_, stack)(false))

  // 1.21.1：`Player#getHeldItem` 拆成了 `getMainHandItem` / `getOffhandItem`，扳手只看主手。
  def holdsApplicableWrench(player: Player, position: BlockPosition): Boolean = {
    val held = player.getMainHandItem
    held != null && !held.isEmpty &&
      usages.exists(IMC.tryInvokeStatic(_, player, Int.box(position.x), Int.box(position.y), Int.box(position.z), Boolean.box(false))(false))
  }

  def wrenchUsed(player: Player, position: BlockPosition): Unit = {
    val held = player.getMainHandItem
    if (held != null && !held.isEmpty) {
      usages.foreach(IMC.tryInvokeStaticVoid(_, player, Int.box(position.x), Int.box(position.y), Int.box(position.z), Boolean.box(true)))
    }
  }
}
