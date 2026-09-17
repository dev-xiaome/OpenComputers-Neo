package li.cil.oc.common

import li.cil.oc.data.Advancements
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object Achievement {
  def init(): Unit = {
  }

  def onAssemble(stack: ItemStack, player: Player): Unit = {
    award(stack, player, Advancements.getAssemblingAdvancement)
  }

  def onCraft(stack: ItemStack, player: Player): Unit = {
    award(stack, player, Advancements.getCraftingAdvancement)
  }

  private def award(stack: ItemStack, player: Player, getAdvancement: ItemStack => ResourceLocation): Unit = {
    player match {
      case serverPlayer: ServerPlayer if !stack.isEmpty =>
        Option(getAdvancement(stack)).foreach(location => award(serverPlayer, location))
      case _ =>
    }
  }

  private def award(player: ServerPlayer, location: ResourceLocation): Unit = {
    // 1.21.1 迁移：`ServerAdvancementManager#getAdvancement(ResourceLocation)` 已改名为
    // `#get(ResourceLocation)`，且返回的是 `AdvancementHolder` 记录（不再是 `Advancement`），
    // 需要经 `value()` 取到真正的 `Advancement`，其判据表也从 `getCriteria` 变成记录访问器 `criteria()`。
    Option(player.server.getAdvancements.get(location)).foreach { advancement =>
      val progress = player.getAdvancements.getOrStartProgress(advancement)
      advancement.value().criteria().keySet().forEach { criterion =>
        if (!progress.isDone) {
          player.getAdvancements.award(advancement, criterion)
        }
      }
    }
  }
}
